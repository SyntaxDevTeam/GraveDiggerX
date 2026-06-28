plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.3"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("pl.syntaxdevteam.plugindeployer") version "1.0.6-R0.2-SNAPSHOT"
}

val mockitoAgent by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}


group = "pl.syntaxdevteam.gravediggerx"
version = "1.0.6-R0.2-SNAPSHOT"
description = "A powerful and very effective plugin for managing tombstones after players die."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("pl.syntaxdevteam:syntaxcore:1.4.0-R0.1-SNAPSHOT")
    compileOnly("pl.syntaxdevteam:messageHandler-paper:1.2.0-R0.1-SNAPSHOT")
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("org.xerial:sqlite-jdbc:3.51.3.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.8")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.16")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0-M1")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("pl.syntaxdevteam:syntaxcore:1.4.0-R0.1-SNAPSHOT")
    testImplementation("com.zaxxer:HikariCP:7.1.0")
    testImplementation("org.xerial:sqlite-jdbc:3.51.3.0")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    mockitoAgent("org.mockito:mockito-core:5.23.0") {
        isTransitive = false
    }
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")

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
        jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
    runServer {
        minecraftVersion("26.2")
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
    paper { dir = "/home/debian/server/Paper/26.2/plugins" }
    folia { dir = "/home/debian/server/Folia/1.21.11/plugins" }
    spigot { dir = "/home/debian/server/Spigot/26.2/plugins" }
}
