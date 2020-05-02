/*
 * Copyright © 2018-2020 Harsh Shandilya <me@msfjarvis.dev>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import com.oath.halodb.HaloDB
import com.oath.halodb.HaloDBException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import me.ivmg.telegram.Bot
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.ParseMode
import me.ivmg.telegram.network.fold

fun requireNotEmpty(str: String): String {
    return if (str.isNotBlank()) str else {
        throw IllegalArgumentException("Required value was empty")
    }
}

fun List<String>.toFileName(): String {
    return joinToString("_")
}

val File.sanitizedName
    get() = nameWithoutExtension.replace('_', ' ')

fun String.toByteArray() = toByteArray(StandardCharsets.UTF_8)

fun Bot.sendPictureSafe(
    db: HaloDB,
    chatId: Long,
    baseUrl: String,
    fileToSend: Pair<File, String>,
    replyToMessageId: Long = -1,
    genericCaption: Boolean = false
) {
    val file = fileToSend.first
    val digest = fileToSend.second.toByteArray()
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
    sendChatAction(chatId, ChatAction.UPLOAD_PHOTO)
    sendPhoto(
        chatId,
        fileId ?: "$baseUrl/${file.name}",
        caption,
        ParseMode.MARKDOWN,
        replyToMessageId = if (replyToMessageId != -1L) replyToMessageId else null
    ).fold({ response ->
        response?.result?.photo?.get(0)?.fileId?.apply {
            if (fileId == null) {
                db.put(this.toByteArray(), digest)
            }
        }
    }, {
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
                    db.put(this.toByteArray(), digest)
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
    inputStream.use {
        read = it.read(buffer)
        while (read > 0) {
            digest.update(buffer, 0, read)
            read = it.read(buffer)
        }
        val md5sum = digest.digest()
        val bigInt = BigInteger(1, md5sum)
        var output = bigInt.toString(16)
        // Fill to 32 chars
        output = String.format("%32s", output).replace(' ', '0')
        return output
    }
}
