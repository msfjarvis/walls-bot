/*
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import java.io.File
import java.text.DecimalFormat
import java.util.TreeMap
import kotlin.coroutines.CoroutineContext
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.ParseMode
import okhttp3.logging.HttpLoggingInterceptor
import org.dizitart.kno2.nitrite

class WallsBot : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Job() + Dispatchers.IO

    private val bot: Bot
    private var fileList = HashMap<File, String>()
    private var formattedDiskSize: String = ""
    private val props: AppProps = AppProps()
    private var statsMap = TreeMap<String, Int>()

    init {
        requireNotNull(props.ownerId) { "ownerId must be configured for the bot to function" }
        val db = nitrite {
            file = File(props.databaseFile)
            autoCommitBufferSize = 2048
            compress = true
            autoCompact = false
        }
        val repository = db.getRepository(props.botToken, CachedFile::class.java)
        refreshDiskCache()
        bot = bot {
            token = props.botToken
            timeout = 30
            logLevel = if (props.debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            dispatch {
                command("all") { bot, update, args ->
                    launch {
                        update.message?.let { message ->
                            bot.runForOwner(props, message) {
                                if (args.isEmpty()) {
                                    sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                    sendMessage(
                                            chatId = message.chat.id,
                                            text = "No arguments supplied!",
                                            replyToMessageId = message.messageId
                                    )
                                    return@runForOwner
                                }
                                val foundFiles = filterFiles(args)
                                sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                if (foundFiles.isEmpty()) {
                                    sendMessage(
                                            chatId = message.chat.id,
                                            replyToMessageId = message.messageId,
                                            text = "No results found for '${args.joinToString(" ")}'",
                                            parseMode = ParseMode.MARKDOWN,
                                            disableWebPagePreview = true
                                    )
                                } else {
                                    foundFiles.forEach {
                                        runBlocking {
                                            bot.sendPictureSafe(
                                                    repository,
                                                    message.chat.id,
                                                    props.baseUrl,
                                                    Pair(it, fileList[it] ?: throw IllegalArgumentException("Failed to find corresponding hash for $it")),
                                                    message.messageId,
                                                    genericCaption = props.genericCaption
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
                        coroutineContext.cancelChildren()
                        update.message?.let { message ->
                            bot.runForOwner(props, message, true) {
                                val savedKeysLength: Int = repository.find().size()
                                sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                sendMessage(
                                        chatId = message.chat.id,
                                        text = "Total keys in db: $savedKeysLength",
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
                                            chatId = message.chat.id,
                                            text = next,
                                            replyToMessageId = message.messageId
                                    )
                                } else {
                                    sendMessage(
                                            chatId = message.chat.id,
                                            text = "Failed to find next key for ${args.joinToString(" ")}",
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
                                    sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                    sendMessage(
                                            chatId = message.chat.id,
                                            text = "No arguments supplied!",
                                            replyToMessageId = message.messageId
                                    )
                                    return@runForOwner
                                }
                                val fileName = args.joinToString("_")
                                val results = fileList.keys.filter { it.nameWithoutExtension.toLowerCase().startsWith(fileName.toLowerCase()) }
                                if (results.isEmpty()) {
                                    sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                    sendMessage(
                                            chatId = message.chat.id,
                                            text = "No files found for \'${args.joinToString(" ")}\'",
                                            replyToMessageId = message.messageId
                                    )
                                    return@runForOwner
                                }
                                val exactMatch = results.asSequence().filter { it.nameWithoutExtension == fileName }.take(1)
                                val key = exactMatch.singleOrNull() ?: results[Random.nextInt(0, results.size)]
                                val fileToSend = Pair(key, fileList[key] ?: throw IllegalArgumentException("Failed to find corresponding hash for $key"))
                                sendPictureSafe(
                                        repository,
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
                                    chatId = message.chat.id,
                                    text = "Going down!",
                                    replyToMessageId = message.messageId
                            )
                            coroutineContext.cancelChildren()
                            db.commit()
                            db.close()
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
                                        repository,
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
                                    sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                    sendMessage(
                                            chatId = message.chat.id,
                                            text = "No arguments supplied!",
                                            replyToMessageId = message.messageId
                                    )
                                    return@runForOwner
                                }
                                val foundFiles = HashSet<String>()
                                filterFiles(args).forEach {
                                    foundFiles.add("[${it.sanitizedName}](${props.baseUrl}/${it.name})")
                                }
                                sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                if (foundFiles.isEmpty()) {
                                    sendMessage(
                                            chatId = message.chat.id,
                                            replyToMessageId = message.messageId,
                                            text = "No results found for '${args.joinToString(" ")}'",
                                            parseMode = ParseMode.MARKDOWN,
                                            disableWebPagePreview = true
                                    )
                                } else {
                                    sendMessage(
                                            chatId = message.chat.id,
                                            replyToMessageId = message.messageId,
                                            text = foundFiles.joinToString("\n"),
                                            parseMode = ParseMode.MARKDOWN,
                                            disableWebPagePreview = true
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
                                sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                sendMessage(
                                        chatId = message.chat.id,
                                        text = msg,
                                        replyToMessageId = message.messageId
                                )
                            }
                        }
                    }
                }

                command("update") { bot, update, _ ->
                    runBlocking(coroutineContext) {
                        coroutineContext.cancelChildren()
                        refreshDiskCache()
                        update.message?.let { message ->
                            bot.runForOwner(props, message, true) {
                                sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                                sendMessage(
                                        chatId = message.chat.id,
                                        text = "Updated files list!",
                                        replyToMessageId = message.messageId
                                )
                            }
                        }
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
