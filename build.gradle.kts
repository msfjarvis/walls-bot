import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
}

plugins {
    kotlin("jvm") version "1.3.41"
    id("org.jlleitschuh.gradle.ktlint") version "4.1.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    application
}

allprojects {
    group = "me.msfjarvis.wallsbot"
    version = "0.1"

    repositories {
        mavenCentral()
        jcenter()
        maven(url = "https://jitpack.io")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("io.github.seik.kotlin-telegram-bot:telegram:0.3.8")
    implementation("org.dizitart:potassium-nitrite:3.2.0")
}

application {
    // Define the main class for the application
    mainClassName = "me.msfjarvis.wallsbot.MainKt"
}

tasks {
    named<Wrapper>("wrapper") {
        gradleVersion = "5.5.1"
        distributionType = Wrapper.DistributionType.ALL
    }
    withType<Jar> {
        manifest {
            attributes["Main-Class"] = application.mainClassName
        }
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = freeCompilerArgs + listOf("-Xnew-inference")
        }
    }
}
