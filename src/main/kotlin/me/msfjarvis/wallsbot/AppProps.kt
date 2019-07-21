package me.msfjarvis.wallsbot

import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AppProps : Properties() {

    val searchDir: String
    val baseUrl: String
    val ownerId: Long?

    init {
        val configFile = File("config.prop")
        if (configFile.exists()) {
            load(FileInputStream(File("config.prop")))
        } else {
            throw IllegalArgumentException("Missing config.prop!")
        }

        searchDir = getProperty("searchDir")
        baseUrl = getProperty("baseUrl")
        ownerId = getProperty("botOwner").toLongOrNull()
    }
}
