/*
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
package me.msfjarvis.wallsbot

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ExtensionTests {
    @Test
    fun `sanitizing file names`() {
        assertEquals(File("Katherine_McNamara_2.jpg").sanitizedName, "Katherine McNamara 2")
        assertEquals(File("Emma_Dumont_4.jpg").sanitizedName, "Emma Dumont 4")
        assertNotEquals(File("Cara_Delevingne_1.jpg").sanitizedName, "Cara_Delevingne_1")
    }

    @Test
    fun `calculating MD5`() {
        val sampleFile = javaClass.classLoader.getResource("sample_file")?.file
        assertNotNull(sampleFile)
        assertEquals(File(sampleFile).calculateMD5(), "8d51b2297b2fcd67f1959bc407c94e5c")
    }
}
