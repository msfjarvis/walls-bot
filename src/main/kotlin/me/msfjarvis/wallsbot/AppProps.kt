/*
 * Copyright © 2018-2020 Harsh Shandilya <me@msfjarvis.dev>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AppProps : Properties() {

    val baseUrl: String
    val botToken: String
    val databaseDir: String
    val debug: Boolean
    val ownerId: Long?
    val searchDir: String
    val sentryDsn: String

    init {
        val configFile = File("config.prop")
        if (configFile.exists()) {
            load(FileInputStream(File("config.prop")))
        } else {
            throw IllegalArgumentException("Missing config.prop!")
        }

        baseUrl = requireNotEmpty(getProperty("baseUrl"))
        botToken = requireNotEmpty(getProperty("botToken"))
        databaseDir = requireNotEmpty(getProperty("databaseDir"))
        debug = getProperty("debug")?.toBoolean() ?: false
        ownerId = getProperty("botOwner").toLongOrNull()
        searchDir = requireNotEmpty(getProperty("searchDir"))
        sentryDsn = requireNotEmpty(getProperty("sentryDsn"))

        val searchDirectory = File(searchDir)
        require(searchDirectory.isDirectory && searchDirectory.exists()) { "searchDir ($searchDir) must exist and be a directory" }
    }
}
