package me.msfjarvis.wallsbot

import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.ParseMode
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import java.util.Properties
import kotlin.collections.HashSet
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
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
    val ownerId = props.getProperty("botOwner").toLongOrNull()
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

            command("status") { bot, update ->
                if (ownerId != null) {
                    update.message?.let { message ->
                        val messageFrom: Long = message.from?.id ?: 0
                        println("Message from $messageFrom, Owner ID is $ownerId")
                        if (messageFrom != ownerId) {
                            bot.sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                            bot.sendMessage(
                                    chatId = message.chat.id,
                                    text = "Unauthorized!",
                                    replyToMessageId = message.messageId
                            )
                            return@command
                        }
                    }
                }
                val allFiles = File(searchDir).listFiles()
                var diskSpace: Long = 0
                for (file in allFiles) {
                    diskSpace += file.length()
                }
                val units = arrayOf("B", "KB", "MB", "GB", "TB")
                val digitGroups: Double = floor((log10(diskSpace.toDouble()) / log10(1024.0)))
                val decimalFormat = DecimalFormat("#,##0.##")
                        .format(diskSpace / 1024.0.pow(digitGroups)) + " " + units[digitGroups.toInt()]
                update.message?.let { message ->
                    bot.sendChatAction(chatId = message.chat.id, action = ChatAction.TYPING)
                    bot.sendMessage(
                            chatId = message.chat.id,
                            text = "Total files : ${allFiles.size} \nDisk space used : $decimalFormat",
                            replyToMessageId = message.messageId
                    )
                }
            }

            command("random") { bot, update ->
                val allFiles = File(searchDir).listFiles()
                val randomInt = Random.nextInt(0, allFiles.size)
                val fileToSend = allFiles[randomInt]
                if (debug) println("random: ${fileToSend.nameWithoutExtension}")
                update.message?.let { message ->
                    bot.sendPictureSafe(message.chat.id, baseUrl, fileToSend, message.messageId)
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
                        bot.sendPictureSafe(message.chat.id, baseUrl, fileToSend, message.messageId)
                    }
                }
            }
        }
    }
    bot.startPolling()
}
