/*
 * Copyright © 2018-2020 Harsh Shandilya <me@msfjarvis.dev>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

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
