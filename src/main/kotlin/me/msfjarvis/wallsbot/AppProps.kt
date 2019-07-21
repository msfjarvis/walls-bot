package me.msfjarvis.wallsbot

import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AppProps : Properties() {

    val baseUrl: String
    val botToken: String
    val ownerId: Long?
    val searchDir: String

    init {
        val configFile = File("config.prop")
        if (configFile.exists()) {
            load(FileInputStream(File("config.prop")))
        } else {
            throw IllegalArgumentException("Missing config.prop!")
        }

        baseUrl = getProperty("baseUrl")
        botToken = getProperty("botToken")
        ownerId = getProperty("botOwner").toLongOrNull()
        searchDir = getProperty("searchDir")
    }
}
