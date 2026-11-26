plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("pl.syntaxdevteam.plugindeployer") version "1.0.6-R0.2-SNAPSHOT"
}

val runtimeVersions = mapOf(
    "kotlinVersion" to libs.versions.kotlin.get(),
    "syntaxcoreVersion" to libs.versions.syntaxcore.get(),
    "messagehandlerVersion" to libs.versions.messagehandler.get(),
    "syntaxdbVersion" to libs.versions.syntaxdb.get(),
    "hikariVersion" to libs.versions.hikari.get(),
    "sqliteVersion" to libs.versions.sqlite.get(),
    "mysqlVersion" to libs.versions.mysql.get(),
    "postgresqlVersion" to libs.versions.postgresql.get(),
    "protobufVersion" to libs.versions.protobuf.get(),
    "gsonVersion" to libs.versions.gson.get()
)

val databaseRuntimeProbe = configurations.create("databaseRuntimeProbe") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

val mockitoAgent = configurations.create("mockitoAgent") {
    isCanBeConsumed = false
    isCanBeResolved = true
}


group = "pl.syntaxdevteam.gravediggerx"
version = "1.2.0-R0.1-SNAPSHOT"
description = "A powerful and very effective plugin for managing tombstones after players die."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io") // Vault
  //  maven("https://repo.codemc.org/repository/maven-public/") // VaultUnlockedAPI
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    compileOnly("org.eclipse.aether:aether-api:1.1.0")
    compileOnly("org.yaml:snakeyaml:2.7")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    testImplementation("org.eclipse.aether:aether-api:1.1.0")
    testImplementation("org.yaml:snakeyaml:2.7")
    compileOnly("io.papermc.paper:paper-api:26.3.build.263-stable")
    compileOnly("pl.syntaxdevteam:syntaxcore:${libs.versions.syntaxcore.get()}")
    compileOnly("pl.syntaxdevteam:messageHandler-paper:${libs.versions.messagehandler.get()}")
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.3.1")
    compileOnly("pl.syntaxdevteam:syntaxdb-core:${libs.versions.syntaxdb.get()}")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.21")
    compileOnly("com.github.milkbowl:VaultAPI:1.7.1")
   // compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.15")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.papermc.paper:paper-api:26.3.build.263-stable")
    testImplementation("pl.syntaxdevteam:syntaxcore:${libs.versions.syntaxcore.get()}")
    testImplementation("pl.syntaxdevteam:syntaxdb-core:${libs.versions.syntaxdb.get()}")
    testImplementation("org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    add(mockitoAgent.name, "org.mockito:mockito-core:5.24.0") {
        isTransitive = false
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.20")

    // Mirror the database runtime declared in paper-libraries.yml, without the test JVM's classpath.
    listOf(
        "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
        "pl.syntaxdevteam:syntaxdb-core:${libs.versions.syntaxdb.get()}",
        "com.zaxxer:HikariCP:${libs.versions.hikari.get()}",
        "org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}",
        "com.mysql:mysql-connector-j:${libs.versions.mysql.get()}",
        "com.google.protobuf:protobuf-java:${libs.versions.protobuf.get()}",
        "org.postgresql:postgresql:${libs.versions.postgresql.get()}",
        "org.slf4j:slf4j-simple:2.0.20"
    ).forEach { add(databaseRuntimeProbe.name, it) }

    configurations.all {
        resolutionStrategy {
            force("com.google.code.gson:gson:${libs.versions.gson.get()}")
            force("com.google.guava:guava:33.8.0-jre")
        }
    }

}

val targetJavaVersion = 25
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    jar {
        archiveClassifier.set("plain")
    }
    test {
        dependsOn(shadowJar)
        useJUnitPlatform()
        inputs.files(databaseRuntimeProbe)
        inputs.file(shadowJar.flatMap { it.archiveFile })
        doFirst {
            systemProperty("gravediggerx.pluginJar", shadowJar.get().archiveFile.get().asFile.absolutePath)
            systemProperty("gravediggerx.database.runtimeClasspath", databaseRuntimeProbe.asPath)
        }
        jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
    runServer {
        minecraftVersion("26.3")
        runDirectory(file("run/paper"))
    }

    runPaper.folia.registerTask()

    processResources {
        val props = mapOf(
            "version" to version,
            "description" to description
        )
        inputs.properties(props + runtimeVersions)
        filteringCharset = "UTF-8"
        filesMatching(listOf("paper-plugin.yml")) {
            expand(props)
        }
        filesMatching("paper-libraries.yml") {
            expand(runtimeVersions)
        }
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("GraveDiggerX")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
    filesMatching("META-INF/*.kotlin_module") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    // Kotlin and SyntaxDB must have a single class identity in Paper's library classloader.
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
    }
    exclude("kotlin/**")
}

plugindeployer {
    paper { dir = "/home/debian/server/Paper/26.2/plugins" }
    folia { dir = "/home/debian/server/Folia/1.21.11/plugins" }
    spigot { dir = "/home/debian/server/Spigot/26.2/plugins" }
}