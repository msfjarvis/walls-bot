/*
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import com.oath.halodb.HaloDB
import com.oath.halodb.HaloDBException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import me.ivmg.telegram.Bot
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.Message
import me.ivmg.telegram.entities.ParseMode
import me.ivmg.telegram.network.fold

fun requireNotEmpty(str: String): String {
    return if (str.isNotBlank()) str else {
        throw IllegalArgumentException("Required value was empty")
    }
}

val File.sanitizedName
    get() = nameWithoutExtension.replace('_', ' ')

fun Bot.runForOwner(props: AppProps, message: Message, forceLock: Boolean = false, toRun: Bot.() -> Unit) {
    if ((props.lockToOwner || forceLock) && props.ownerId != message.from?.id) {
        return
    }
    toRun.invoke(this)
}

fun Bot.sendPictureSafe(
    db: HaloDB,
    chatId: Long,
    baseUrl: String,
    fileToSend: Pair<File, String>,
    replyToMessageId: Long? = null,
    genericCaption: Boolean = false
) {
    val file = fileToSend.first
    val digest = fileToSend.second.toByteArray(StandardCharsets.UTF_8)
    val fileId = try {
        String(db.get(digest), StandardCharsets.UTF_8)
    } catch (_: HaloDBException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
    val caption = if (genericCaption)
        "[Link]($baseUrl/${file.name}"
    else
        "[${file.sanitizedName}]($baseUrl/${file.name})"
    sendChatAction(chatId = chatId, action = ChatAction.UPLOAD_PHOTO)
    sendPhoto(
            chatId = chatId,
            photo = fileId ?: "$baseUrl/${file.name}",
            caption = caption,
            parseMode = ParseMode.MARKDOWN,
            replyToMessageId = replyToMessageId
    ).fold({ response ->
        response?.result?.photo?.get(0)?.fileId?.apply {
            if (fileId == null) {
                db.put(this.toByteArray(StandardCharsets.UTF_8), digest)
            }
        }
    }, {
        sendChatAction(chatId = chatId, action = ChatAction.UPLOAD_DOCUMENT)
        val documentMessage = if (fileId == null) {
            sendDocument(
                    chatId = chatId,
                    document = file,
                    caption = caption,
                    parseMode = ParseMode.MARKDOWN,
                    replyToMessageId = replyToMessageId
            )
        } else {
            sendDocument(
                    chatId = chatId,
                    fileId = fileId,
                    caption = caption,
                    parseMode = ParseMode.MARKDOWN,
                    replyToMessageId = replyToMessageId
            )
        }
        documentMessage.fold({ response ->
            response?.result?.document?.fileId?.apply {
                if (fileId == null) {
                    db.put(this.toByteArray(StandardCharsets.UTF_8), digest)
                }
            }
        }, {})
    })
}

fun File.calculateMD5(): String {
    val digest: MessageDigest
    try {
        digest = MessageDigest.getInstance("MD5")
    } catch (e: NoSuchAlgorithmException) {
        println("Exception while getting digest")
        throw RuntimeException(e)
    }

    val inputStream: InputStream
    try {
        inputStream = FileInputStream(this)
    } catch (e: FileNotFoundException) {
        println("Exception while getting FileInputStream")
        throw RuntimeException(e)
    }

    val buffer = ByteArray(8192)
    var read: Int
    try {
        read = inputStream.read(buffer)
        while (read > 0) {
            digest.update(buffer, 0, read)
            read = inputStream.read(buffer)
        }
        val md5sum = digest.digest()
        val bigInt = BigInteger(1, md5sum)
        var output = bigInt.toString(16)
        // Fill to 32 chars
        output = String.format("%32s", output).replace(' ', '0')
        return output
    } catch (e: IOException) {
        throw RuntimeException("Unable to process file for MD5")
    } finally {
        try {
            inputStream.close()
        } catch (e: IOException) {
            println("Exception on closing MD5 input stream")
        }
    }
}
