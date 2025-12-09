plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("pl.syntaxdevteam.plugindeployer") version "1.0.4"
}

group = "pl.syntaxdevteam.gravediggerx"
version = "1.0.5-DEV"
description = "A powerful and very effective plugin for managing tombstones after players die."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("pl.syntaxdevteam:core:1.2.6")
    compileOnly("pl.syntaxdevteam:messageHandler-paper:1.0.0")
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.3")
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    compileOnly("org.xerial:sqlite-jdbc:3.51.0.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.6")

}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    test {
        useJUnitPlatform()
    }
    runServer {
        minecraftVersion("1.21.10")
        runDirectory(file("run/paper"))
    }

    runPaper.folia.registerTask()

    processResources {
        val props = mapOf(
            "version" to version,
            "description" to description
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching(listOf("paper-plugin.yml")) {
            expand(props)
        }
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("GraveDiggerX")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
}

plugindeployer {
    paper { dir = "/home/debian/poligon/Paper/1.21.11/plugins" }
    folia { dir = "/home/debian/poligon/Folia/1.21.8/plugins" }
}
