package me.msfjarvis.wallsbot

import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.ParseMode
import okhttp3.logging.HttpLoggingInterceptor
import org.dizitart.kno2.nitrite
import java.io.File
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

fun main() {
    val props = AppProps()
    val db = nitrite {
        file = File(props.databaseFile)
        autoCommitBufferSize = 2048
        compress = true
        autoCompact = false
    }
    val repository = db.getRepository(CachedFile::class.java)
    println("repo_items.size=${repository.find().size()}")
    val bot = bot {
        token = props.botToken
        timeout = 30
        logLevel = HttpLoggingInterceptor.Level.BASIC
        dispatch {
            command("search") { bot, update, args ->
                val foundFiles = HashSet<String>()
                File(props.searchDir).listFiles().forEach { file ->
                    if (file.nameWithoutExtension.startsWith(args.joinToString())) {
                        foundFiles.add("[${file.name}](${props.baseUrl}/${file.name})")
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
                if (props.ownerId != null) {
                    update.message?.let { message ->
                        val messageFrom: Long = message.from?.id ?: 0
                        println("Message from $messageFrom, Owner ID is $props.ownerId")
                        if (messageFrom != props.ownerId) {
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
                val allFiles = File(props.searchDir).listFiles()
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
                val allFiles = File(props.searchDir).listFiles()
                val randomInt = Random.nextInt(0, allFiles.size)
                val fileToSend = allFiles[randomInt]
                update.message?.let { message ->
                    bot.sendPictureSafe(repository, message.chat.id, props.baseUrl, fileToSend, message.messageId)
                }
            }

            command("pic") { bot, update, args ->
                val allFiles = File(props.searchDir).listFiles().filter { file ->
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
                    update.message?.let { message ->
                        bot.sendPictureSafe(repository, message.chat.id, props.baseUrl, fileToSend, message.messageId)
                    }
                }
            }
        }
    }
    bot.startPolling()
}
