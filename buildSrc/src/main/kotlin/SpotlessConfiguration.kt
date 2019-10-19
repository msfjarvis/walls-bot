/*
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

val kotlinLicenseHeader = """/*
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: MIT
 */
""".trimIndent()

fun Project.configureSpotless() {
    apply<SpotlessPlugin>()

    configure<SpotlessExtension> {
        kotlinGradle {
            target("**/*.gradle.kts", "*.gradle.kts")
            ktlint("0.33.0").userData(mapOf("indent_size" to "4", "continuation_indent_size" to "4"))
            @Suppress("INACCESSIBLE_TYPE")
            licenseHeader(kotlinLicenseHeader, "import|tasks|apply|plugins|include|buildscript|rootProject")
            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }

        kotlin {
            target("**/src/**/*.kt", "buildSrc/**/*.kt")
            ktlint("0.33.0").userData(mapOf("indent_size" to "4", "continuation_indent_size" to "8"))
            @Suppress("INACCESSIBLE_TYPE")
            licenseHeader(kotlinLicenseHeader, "import|package|class|object|@file")
            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }
    }
}
