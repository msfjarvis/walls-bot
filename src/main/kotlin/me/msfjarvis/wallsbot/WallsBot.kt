/*
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import com.oath.halodb.HaloDB
import com.oath.halodb.HaloDBOptions
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.Dispatcher
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.ParseMode
import okhttp3.logging.HttpLoggingInterceptor

class WallsBot : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val bot: Bot
    private var fileList = HashMap<File, String>()
    private var formattedDiskSize: String = ""
    private val props: AppProps = AppProps()
    private var statsMap = TreeMap<String, Int>()

    init {
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
        val db = HaloDB.open(props.databaseDir, options)
        refreshDiskCache()
        bot = bot {
            token = props.botToken
            timeout = 30
            logLevel = if (props.debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            dispatch {
                setupCommands(db)
            }
        }
    }

    private fun Dispatcher.setupCommands(db: HaloDB) {
        command("all") { bot, update, args ->
            launch {
                update.message?.let { message ->
                    bot.runForOwner(props, message) {
                        if (args.isEmpty()) {
                            sendChatAction(message.chat.id, ChatAction.TYPING)
                            sendMessage(
                                message.chat.id,
                                "No arguments supplied!",
                                replyToMessageId = message.messageId
                            )
                            return@runForOwner
                        }
                        val foundFiles = filterFiles(args)
                        sendChatAction(message.chat.id, ChatAction.TYPING)
                        if (foundFiles.isEmpty()) {
                            sendMessage(
                                message.chat.id,
                                "No results found for '${args.joinToString(" ")}'",
                                ParseMode.MARKDOWN,
                                true,
                                replyToMessageId = message.messageId
                            )
                        } else {
                            foundFiles.forEach {
                                launch {
                                    bot.sendPictureSafe(
                                        db,
                                        message.chat.id,
                                        props.baseUrl,
                                        Pair(it, fileList[it] ?: throw IllegalArgumentException("Failed to find corresponding hash for $it")),
                                        message.messageId,
                                        props.genericCaption
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        command("dbstats") { bot, update, _ ->
            runBlocking {
                cancel()
                update.message?.let { message ->
                    bot.runForOwner(props, message, true) {
                        var savedKeysLength = 0
                        db.newIterator().forEach { _ -> savedKeysLength++ }
                        sendChatAction(message.chat.id, ChatAction.TYPING)
                        sendMessage(
                            message.chat.id,
                            "Total keys in db: $savedKeysLength",
                            replyToMessageId = message.messageId
                        )
                    }
                }
            }
        }

        command("next") { bot, update, args ->
            update.message?.let { message ->
                bot.runForOwner(props, message, true) {
                    launch {
                        val next: String? = statsMap.higherKey(args.joinToString("_")).replace("_", " ")
                        if (next != null) {
                            sendMessage(
                                message.chat.id,
                                next,
                                replyToMessageId = message.messageId
                            )
                        } else {
                            sendMessage(
                                message.chat.id,
                                "Failed to find next key for ${args.joinToString(" ")}",
                                replyToMessageId = message.messageId
                            )
                        }
                    }
                }
            }
        }

        command("pic") { bot, update, args ->
            launch {
                update.message?.let { message ->
                    bot.runForOwner(props, message) {
                        if (args.isEmpty()) {
                            sendChatAction(message.chat.id, ChatAction.TYPING)
                            sendMessage(
                                message.chat.id,
                                "No arguments supplied!",
                                replyToMessageId = message.messageId
                            )
                            return@runForOwner
                        }
                        val fileName = args.joinToString("_")
                        val results = fileList.keys.filter { it.nameWithoutExtension.toLowerCase().startsWith(fileName.toLowerCase()) }
                        if (results.isEmpty()) {
                            sendChatAction(message.chat.id, ChatAction.TYPING)
                            sendMessage(
                                message.chat.id,
                                "No files found for \'${args.joinToString(" ")}\'",
                                replyToMessageId = message.messageId
                            )
                            return@runForOwner
                        }
                        val exactMatch = results.asSequence().filter { it.nameWithoutExtension == fileName }.take(1)
                        val key = exactMatch.singleOrNull() ?: results[Random.nextInt(0, results.size)]
                        val fileToSend = Pair(key, fileList[key] ?: throw IllegalArgumentException("Failed to find corresponding hash for $key"))
                        sendPictureSafe(
                            db,
                            message.chat.id,
                            props.baseUrl,
                            fileToSend,
                            message.messageId,
                            genericCaption = props.genericCaption
                        )
                    }
                }
            }
        }

        command("quit") { bot, update, _ ->
            update.message?.let { message ->
                bot.runForOwner(props, message, true) {
                    sendMessage(
                        message.chat.id,
                        "Going down!",
                        replyToMessageId = message.messageId
                    )
                    cancel()
                    stopPolling()
                    db.apply {
                        pauseCompaction()
                        close()
                    }
                    exitProcess(0)
                }
            }
        }

        command("random") { bot, update ->
            launch {
                update.message?.let { message ->
                    bot.runForOwner(props, message) {
                        val keys = fileList.keys.toTypedArray()
                        val randomInt = Random.nextInt(0, fileList.size)
                        val fileToSend = Pair(keys[randomInt], fileList[keys[randomInt]] ?: throw IllegalArgumentException("Failed to find corresponding hash for ${keys[randomInt]}"))
                        sendPictureSafe(
                            db,
                            message.chat.id,
                            props.baseUrl,
                            fileToSend,
                            message.messageId,
                            genericCaption = props.genericCaption
                        )
                    }
                }
            }
        }

        command("search") { bot, update, args ->
            launch {
                update.message?.let { message ->
                    bot.runForOwner(props, message) {
                        if (args.isEmpty()) {
                            sendChatAction(message.chat.id, ChatAction.TYPING)
                            sendMessage(
                                message.chat.id,
                                "No arguments supplied!",
                                replyToMessageId = message.messageId
                            )
                            return@runForOwner
                        }
                        val foundFiles = HashSet<String>()
                        filterFiles(args).forEach {
                            foundFiles.add("[${it.sanitizedName}](${props.baseUrl}/${it.name})")
                        }
                        sendChatAction(message.chat.id, ChatAction.TYPING)
                        if (foundFiles.isEmpty()) {
                            sendMessage(
                                message.chat.id,
                                "No results found for '${args.joinToString(" ")}'",
                                ParseMode.MARKDOWN,
                                true,
                                replyToMessageId = message.messageId
                            )
                        } else {
                            sendMessage(
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
        }

        command("stats") { bot, update ->
            launch {
                update.message?.let { message ->
                    bot.runForOwner(props, message, true) {
                        var msg = "Stats\n\n"
                        statsMap.forEach { (name, count) ->
                            msg += "${name.replace("_", " ")}: $count\n"
                        }
                        msg += "\n\nTotal files : ${fileList.size} \nDisk space used : $formattedDiskSize"
                        sendChatAction(message.chat.id, ChatAction.TYPING)
                        sendMessage(
                            message.chat.id,
                            msg,
                            replyToMessageId = message.messageId
                        )
                    }
                }
            }
        }

        command("update") { bot, update, _ ->
            runBlocking {
                update.message?.let { message ->
                    bot.runForOwner(props, message, true) {
                        sendMessage(
                            message.chat.id,
                            "Updating file list, this may take a few seconds...",
                            replyToMessageId = message.messageId
                        )
                        cancel()
                        refreshDiskCache()
                        sendChatAction(message.chat.id, ChatAction.TYPING)
                        sendMessage(
                            message.chat.id,
                            "Updated files list!",
                            replyToMessageId = message.messageId
                        )
                    }
                }
            }
        }
    }

    fun startPolling() {
        bot.startPolling()
    }

    private fun filterFiles(args: List<String>): HashSet<File> {
        val foundFiles = HashSet<File>()
        fileList.keys.filter { it.name.toLowerCase().startsWith(args.joinToString("_").toLowerCase()) }.forEach {
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
            val key = split.joinToString("_")
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
