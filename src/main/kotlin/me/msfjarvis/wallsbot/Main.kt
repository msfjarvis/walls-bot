package me.msfjarvis.wallsbot

import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.ParseMode
import me.ivmg.telegram.network.fold
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.random.Random

fun main() {
    val props = Properties()
    val configFile = File("config.prop")
    if (configFile.exists()) {
        props.load(FileInputStream(File("config.prop")))
    } else {
        throw IllegalArgumentException("Missing config.prop!")
    }
    val searchDir = props.getProperty("searchDir")
    val baseUrl = props.getProperty("baseUrl")
    val debug = props.getProperty("debug")?.toBoolean() ?: false
    val bot = bot {
        token = props.getProperty("botToken")
        timeout = 30
        logLevel = HttpLoggingInterceptor.Level.BASIC
        dispatch {
            command("search") { bot, update, args ->
                val foundFiles = HashSet<String>()
                File(searchDir).listFiles().forEach { file ->
                    if (file.nameWithoutExtension.startsWith(args.joinToString())) {
                        foundFiles.add("[${file.name}]($baseUrl/${file.name})")
                    }
                }
                update.message?.let { message ->
                    bot.sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                    bot.sendMessage(
                            chatId = message.chat.id,
                            replyToMessageId = message.messageId,
                            text = foundFiles.joinToString("\n"),
                            parseMode = ParseMode.MARKDOWN,
                            disableWebPagePreview = true
                    )
                }
            }
            command("random") { bot, update ->
                val allFiles = File(searchDir).listFiles()
                val randomInt = Random.nextInt(0, allFiles.size)
                val fileToSend = allFiles[randomInt]
                if (debug) println("random: ${fileToSend.nameWithoutExtension}")
                update.message?.let { message ->
                    bot.sendChatAction(chatId = message.chat.id, action = ChatAction.UPLOAD_PHOTO)
                    val msg = bot.sendPhoto(
                            chatId = message.chat.id,
                            photo = fileToSend,
                            caption = "[${fileToSend.nameWithoutExtension}]($baseUrl/${fileToSend.name})",
                            parseMode = ParseMode.MARKDOWN,
                            replyToMessageId = message.messageId
                    )
                    msg.fold({ },{
                        if (debug) println(it.exception.toString())
                        bot.sendChatAction(chatId = message.chat.id, action = ChatAction.UPLOAD_DOCUMENT)
                        bot.sendDocument(
                                chatId = message.chat.id,
                                document = fileToSend,
                                caption = "[${fileToSend.nameWithoutExtension}]($baseUrl/${fileToSend.name})",
                                parseMode = ParseMode.MARKDOWN,
                                replyToMessageId = message.messageId
                        )
                    })
                }
            }
            command("pic") { bot, update, args ->
                val allFiles = File(searchDir).listFiles().filter { file ->
                    file.nameWithoutExtension.startsWith(args.joinToString("_"))
                }
                if (allFiles.isEmpty()) {
                    update.message?.let { message ->
                        bot.sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                        bot.sendMessage(
                                chatId = message.chat.id,
                                text = "No files found for \'${args.joinToString(" ")}\'",
                                replyToMessageId = message.messageId
                        )
                        return@command
                    }
                }
                val randIdx = Random.nextInt(0, allFiles.size)
                val fileToSend = allFiles[randIdx]
                if (fileToSend != null) {
                    if (debug) println("foundFile: ${fileToSend.nameWithoutExtension}")
                    update.message?.let { message ->
                        bot.sendChatAction(chatId = message.chat.id, action = ChatAction.UPLOAD_PHOTO)
                        val msg = bot.sendPhoto(
                                chatId = message.chat.id,
                                photo = "$baseUrl/${fileToSend.name}",
                                caption = "[${fileToSend.nameWithoutExtension}]($baseUrl/${fileToSend.name})",
                                parseMode = ParseMode.MARKDOWN,
                                replyToMessageId = message.messageId
                        )
                        msg.fold({ },{
                            if (debug) println(it.exception.toString())
                            bot.sendChatAction(chatId = message.chat.id, action = ChatAction.UPLOAD_DOCUMENT)
                            bot.sendDocument(
                                    chatId = message.chat.id,
                                    document = fileToSend,
                                    caption = "[${fileToSend.nameWithoutExtension}]($baseUrl/${fileToSend.name})",
                                    parseMode = ParseMode.MARKDOWN,
                                    replyToMessageId = message.messageId
                            )
                        })
                    }
                }
            }
        }
    }
    bot.startPolling()
}
