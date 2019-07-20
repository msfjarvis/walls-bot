package me.msfjarvis.wallsbot

import me.ivmg.telegram.Bot
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.ParseMode
import me.ivmg.telegram.network.fold
import java.io.File

fun Bot.sendPictureSafe(
        chatId: Long,
        baseUrl: String,
        fileToSend: File,
        replyToMessageId: Long? = null
) {
    sendChatAction(chatId = chatId, action = ChatAction.UPLOAD_PHOTO)
    val msg = sendPhoto(
            chatId = chatId,
            photo = "$baseUrl/${fileToSend.name}",
            caption = "[${fileToSend.nameWithoutExtension}]($baseUrl/${fileToSend.name})",
            parseMode = ParseMode.MARKDOWN,
            replyToMessageId = replyToMessageId
    )
    msg.fold({ }, {
        sendChatAction(chatId = chatId, action = ChatAction.UPLOAD_DOCUMENT)
        sendDocument(
                chatId = chatId,
                document = fileToSend,
                caption = "[${fileToSend.nameWithoutExtension}]($baseUrl/${fileToSend.name})",
                parseMode = ParseMode.MARKDOWN,
                replyToMessageId = replyToMessageId
        )
    })
}
