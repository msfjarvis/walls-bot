/*
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AppProps : Properties() {

    val baseUrl: String
    val botToken: String
    val databaseFile: String
    val ownerId: Long?
    val searchDir: String
    val genericCaption: Boolean
    val debug: Boolean
    val lockToOwner: Boolean

    init {
        val configFile = File("config.prop")
        if (configFile.exists()) {
            load(FileInputStream(File("config.prop")))
        } else {
            throw IllegalArgumentException("Missing config.prop!")
        }

        baseUrl = requireNotEmpty(getProperty("baseUrl"))
        botToken = requireNotEmpty(getProperty("botToken"))
        databaseFile = requireNotEmpty(getProperty("databaseFile"))
        ownerId = getProperty("botOwner").toLongOrNull()
        searchDir = requireNotEmpty(getProperty("searchDir"))
        genericCaption = getProperty("genericCaption")?.toBoolean() ?: false
        debug = getProperty("debug")?.toBoolean() ?: false
        lockToOwner = getProperty("lockToOwner")?.toBoolean() ?: false
    }
}
