/*
 * Copyright © 2018-2020 Harsh Shandilya <me@msfjarvis.dev>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatAction
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.network.fold
import com.oath.halodb.HaloDB
import com.oath.halodb.HaloDBException
import com.oath.halodb.HaloDBOptions
import io.sentry.Sentry
import io.sentry.SentryClient
import io.sentry.SentryClientFactory
import java.io.File
import java.text.DecimalFormat
import java.util.TreeMap
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor

class WallsBot : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val bot: Bot
    private val db: HaloDB
    private var fileList = HashMap<File, String>()
    private var formattedDiskSize = ""
    private val props = AppProps()
    private var statsMap = TreeMap<String, Int>()
    private val sentry: SentryClient

    init {
        Sentry.init(props.sentryDsn)
        sentry = SentryClientFactory.sentryClient(props.sentryDsn)
        requireNotNull(props.ownerId) { "ownerId must be configured for the bot to function" }
        val options = HaloDBOptions().apply {
            maxFileSize = 128 * 1024 * 1024
            maxTombstoneFileSize = 64 * 1024 * 1024
            buildIndexThreads = 8
            flushDataSizeBytes = 10 * 1024 * 1024
            compactionThresholdPerFile = 0.7
            compactionJobRate = 50 * 1024 * 1024
            numberOfRecords = 10_000
            isCleanUpTombstonesDuringOpen = true
            isCleanUpInMemoryIndexOnClose = true
            isUseMemoryPool = true
        }
        db = HaloDB.open(props.databaseDir, options)
        refreshDiskCache()
        bot = bot {
            token = props.botToken
            timeout = 30
            logLevel = if (props.debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            dispatch {
                setupCommands()
            }
        }
    }

    fun startPolling() {
        bot.startPolling()
    }

    private fun Dispatcher.setupCommands() {
        command("all") { bot, update, args ->
            if (args.isEmpty()) return@command
            launch {
                update.message?.let { message ->
                    val foundFiles = filterFiles(args)
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    if (foundFiles.isEmpty()) {
                        bot.sendMessage(
                            message.chat.id,
                            "No results found for '${args.joinToString(" ")}'",
                            ParseMode.MARKDOWN,
                            true,
                            replyToMessageId = message.messageId
                        )
                    } else {
                        foundFiles.shuffled().take(15).forEach { file ->
                            val hash = fileList[file]
                            if (hash != null) {
                                launch { send(message.chat.id, Pair(file, hash), message.messageId) }
                            }
                        }
                    }
                }
            }
        }

        command("dbstats") { bot, update, _ ->
            runBlocking {
                update.message?.let { message ->
                    if (props.ownerId != message.from?.id) return@runBlocking
                    coroutineContext.cancelChildren()
                    var savedKeysLength = 0
                    db.newIterator().forEach { _ -> savedKeysLength++ }
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    bot.sendMessage(
                        message.chat.id,
                        "Total keys in db: $savedKeysLength",
                        replyToMessageId = message.messageId
                    )
                }
            }
        }

        command("next") { bot, update, args ->
            update.message?.let { message ->
                launch {
                    val next: String? = statsMap.higherKey(args.toFileName()).replace("_", " ")
                    if (next != null) {
                        bot.sendMessage(
                            message.chat.id,
                            next,
                            replyToMessageId = message.messageId
                        )
                    } else {
                        bot.sendMessage(
                            message.chat.id,
                            "Failed to find next key for ${args.joinToString(" ")}",
                            replyToMessageId = message.messageId
                        )
                    }
                }
            }
        }

        command("pic") { bot, update, args ->
            if (args.isEmpty()) return@command
            launch {
                update.message?.let { message ->
                    val fileName = args.toFileName()
                    val results = fileList.keys.filter { it.nameWithoutExtension.toLowerCase().startsWith(fileName.toLowerCase()) }
                    if (results.isEmpty()) {
                        bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                        bot.sendMessage(
                            message.chat.id,
                            "No files found for \'${args.joinToString(" ")}\'",
                            replyToMessageId = message.messageId
                        )
                        return@launch
                    }
                    val exactMatch = results.asSequence().filter { it.nameWithoutExtension == fileName }.take(1)
                    val key = exactMatch.singleOrNull() ?: results[Random.nextInt(0, results.size)]
                    val fileToSend = Pair(key, fileList[key]
                        ?: throw IllegalArgumentException("Failed to find corresponding hash for $key"))
                    send(message.chat.id, fileToSend, message.messageId)
                }
            }
        }

        command("quit") { bot, update, _ ->
            update.message?.let { message ->
                if (props.ownerId != message.from?.id) return@command
                bot.sendMessage(
                    message.chat.id,
                    "Going down!",
                    replyToMessageId = message.messageId
                )
                coroutineContext.cancelChildren()
                bot.stopPolling()
                db.apply {
                    pauseCompaction()
                    close()
                }
                exitProcess(0)
            }
        }

        command("random") { _, update ->
            launch {
                update.message?.let { message ->
                    val keys = fileList.keys.toTypedArray()
                    val randomInt = Random.nextInt(0, fileList.size)
                    val fileToSend = Pair(keys[randomInt], fileList[keys[randomInt]]
                        ?: throw IllegalArgumentException("Failed to find corresponding hash for ${keys[randomInt]}"))
                    send(message.chat.id, fileToSend, message.messageId)
                }
            }
        }

        command("search") { bot, update, args ->
            if (args.isEmpty()) return@command
            launch {
                update.message?.let { message ->
                    val foundFiles = HashSet<String>()
                    filterFiles(args).forEach {
                        foundFiles.add("[${it.sanitizedName}](${props.baseUrl}/${it.name})")
                    }
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    if (foundFiles.isEmpty()) {
                        bot.sendMessage(
                            message.chat.id,
                            "No results found for '${args.joinToString(" ")}'",
                            ParseMode.MARKDOWN,
                            true,
                            replyToMessageId = message.messageId
                        )
                    } else {
                        bot.sendMessage(
                            message.chat.id,
                            foundFiles.joinToString("\n"),
                            ParseMode.MARKDOWN,
                            true,
                            replyToMessageId = message.messageId
                        )
                    }
                }
            }
        }

        command("stats") { bot, update ->
            launch {
                update.message?.let { message ->
                    if (props.ownerId != message.from?.id) return@launch
                    var msg = "Stats\n\n"
                    statsMap.forEach { (name, count) ->
                        msg += "${name.replace("_", " ")}: $count\n"
                    }
                    msg += "\n\nTotal files : ${fileList.size} \nDisk space used : $formattedDiskSize"
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    bot.sendMessage(
                        message.chat.id,
                        msg,
                        replyToMessageId = message.messageId
                    )
                }
            }
        }

        command("update") { bot, update, _ ->
            runBlocking {
                update.message?.let { message ->
                    if (props.ownerId != message.from?.id) return@runBlocking
                    bot.sendMessage(
                        message.chat.id,
                        "Updating file list, this may take a few seconds...",
                        replyToMessageId = message.messageId
                    )
                    coroutineContext.cancelChildren()
                    refreshDiskCache()
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    bot.sendMessage(
                        message.chat.id,
                        "Updated files list!",
                        replyToMessageId = message.messageId
                    )
                }
            }
        }
    }

    /**
     * This method does a rudimentary file size check to avoid a network call, as files above 5 mB can only be sent
     * as documents through the API. In my benchmarks
     */
    private fun send(
        chatId: Long,
        fileToSend: Pair<File, String>,
        replyToMessageId: Long = -1L
    ) {
        if (fileToSend.first.length() > 5242880)
            sendDocument(chatId, fileToSend.first, fileToSend.second.toByteArray(Charsets.UTF_8), replyToMessageId)
        else
            sendPicture(chatId, fileToSend.first, fileToSend.second.toByteArray(Charsets.UTF_8), replyToMessageId)
    }

    private fun sendPicture(
        chatId: Long,
        file: File,
        digest: ByteArray,
        replyToMessageId: Long = -1L
    ) = with(bot) {
        val fileId = getIdFromDb(digest)
        val caption = "[${file.sanitizedName}](${props.baseUrl}/${file.name})"
        sendChatAction(chatId, ChatAction.UPLOAD_PHOTO)
        sendPhoto(
            chatId,
            fileId ?: "${props.baseUrl}/${file.name}",
            caption,
            ParseMode.MARKDOWN,
            replyToMessageId = if (replyToMessageId != -1L) replyToMessageId else null
        ).fold({ response ->
            response?.result?.photo?.get(0)?.fileId?.apply {
                if (fileId == null) {
                    db.put(this.toByteArray(Charsets.UTF_8), digest)
                }
            }
        }, {
            if (it.exception != null) {
                sentry.sendException(it.exception)
            }
            sendDocument(chatId, file, digest, replyToMessageId)
        })
    }

    private fun sendDocument(
        chatId: Long,
        file: File,
        digest: ByteArray,
        replyToMessageId: Long = -1L
    ) = with(bot) {
        val fileId = getIdFromDb(digest)
        val caption = "[${file.sanitizedName}](${props.baseUrl}/${file.name})"
        sendChatAction(chatId, ChatAction.UPLOAD_DOCUMENT)
        val documentMessage = if (fileId == null) {
            sendDocument(
                chatId,
                file,
                caption,
                ParseMode.MARKDOWN,
                replyToMessageId = if (replyToMessageId != -1L) replyToMessageId else null
            )
        } else {
            sendDocument(
                chatId,
                fileId,
                caption,
                ParseMode.MARKDOWN,
                replyToMessageId = if (replyToMessageId != -1L) replyToMessageId else null
            )
        }
        documentMessage.fold({ response ->
            if (fileId == null) {
                response?.result?.document?.fileId?.apply {
                    db.put(this.toByteArray(Charsets.UTF_8), digest)
                }
            }
        }, {
            if (it.exception != null) {
                sentry.sendException(it.exception)
            }
        })
    }

    private fun getIdFromDb(key: ByteArray): String? {
        return try {
            String(db.get(key), Charsets.UTF_8)
        } catch (_: HaloDBException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun filterFiles(args: List<String>): HashSet<File> {
        val foundFiles = HashSet<File>()
        fileList.keys.filter { it.name.toLowerCase().startsWith(args.toFileName().toLowerCase()) }.forEach {
            foundFiles.add(it)
        }
        return foundFiles
    }

    private fun refreshDiskCache() {
        fileList = HashMap(File(props.searchDir).listFiles()?.associate { Pair(it, it.calculateMD5()) })
        statsMap.clear()
        fileList.keys.forEach {
            val split = it.nameWithoutExtension.split("_").toTypedArray().toMutableList().apply {
                removeAt(size - 1)
            }
            val key = split.toFileName()
            val count = statsMap.getOrDefault(key, 0)
            statsMap[key] = count + 1
        }
        var diskSpace: Long = 0
        for (file in fileList.keys) {
            diskSpace += file.length()
        }
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups: Double = floor((log10(diskSpace.toDouble()) / log10(1024.0)))
        formattedDiskSize = DecimalFormat("#,##0.##")
            .format(diskSpace / 1024.0.pow(digitGroups)) + " " + units[digitGroups.toInt()]
    }
}
