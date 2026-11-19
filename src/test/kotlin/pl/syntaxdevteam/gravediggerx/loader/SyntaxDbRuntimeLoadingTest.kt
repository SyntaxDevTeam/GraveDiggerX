package pl.syntaxdevteam.gravediggerx.loader

import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import pl.syntaxdevteam.syntaxdb.Database
import pl.syntaxdevteam.syntaxdb.config.DatabaseType
import pl.syntaxdevteam.syntaxdb.config.databaseConfig
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SyntaxDbRuntimeLoadingTest {
    private val pluginJar = File(requireNotNull(System.getProperty("gravediggerx.pluginJar")))
    private val libraries = requireNotNull(System.getProperty("gravediggerx.database.runtimeClasspath"))
        .split(File.pathSeparator).map(::File)

    @Test
    fun `published jar has a Java bootstrap and does not contain Kotlin or SyntaxDB classes`() {
        JarFile(pluginJar).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            assertFalse(entries.any { it.startsWith("kotlin/") && it.endsWith(".class") })
            assertFalse(entries.any { it.startsWith("pl/syntaxdevteam/syntaxdb/") && it.endsWith(".class") })
            val bootstrap = jar.getJarEntry("pl/syntaxdevteam/gravediggerx/loader/SyntaxLoader.class")
            assertTrue(bootstrap != null)
            val bytecode = jar.getInputStream(bootstrap).use { String(it.readBytes(), Charsets.ISO_8859_1) }
            assertFalse(bytecode.contains("kotlin/"), "Bootstrap must work before Kotlin is loaded")
            val text = jar.getInputStream(jar.getJarEntry("paper-libraries.yml")).bufferedReader().use { it.readText() }
            assertFalse(text.contains("\${"))
            val manifest = Yaml().load<Map<String, Any>>(text)
            val coordinates = (manifest["libraries"] as List<*>).map {
                if (it is String) it else (it as Map<*, *>)["coordinate"] as String
            }
            for (module in listOf("org.jetbrains.kotlin:kotlin-stdlib", "pl.syntaxdevteam:syntaxdb-core",
                "com.zaxxer:HikariCP", "org.xerial:sqlite-jdbc", "com.mysql:mysql-connector-j",
                "com.google.protobuf:protobuf-java", "org.postgresql:postgresql")) {
                val coordinate = coordinates.single { it.startsWith("$module:") }
                val artifact = module.substringAfter(':')
                val version = coordinate.substringAfterLast(':')
                assertTrue(libraries.any { it.name == "$artifact-$version.jar" }, "Runtime manifest differs from resolved classpath: $coordinate")
            }
        }
    }

    @Test
    fun `plugin classloader calls SyntaxDB DSL using the exact library Kotlin type`() {
        val kotlinJar = libraries.single { it.name.startsWith("kotlin-stdlib-") && !it.name.startsWith("kotlin-stdlib-jdk") }
        val runtime = listOf(kotlinJar) + libraries.filterNot { it == kotlinJar }
        URLClassLoader(runtime.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { libraryLoader ->
            val probeClasses = SyntaxDbLinkageProbe::class.java.protectionDomain.codeSource.location
            // Child-first for the plugin and Kotlin, as Paper looks in the plugin JAR before its library loader.
            val pluginLoader = object : URLClassLoader(arrayOf(pluginJar.toURI().toURL(), probeClasses), libraryLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
                    if (name.startsWith("kotlin.") || name.startsWith("pl.syntaxdevteam.gravediggerx.")) {
                        val selected = findLoadedClass(name) ?: try { findClass(name) } catch (_: ClassNotFoundException) { super.loadClass(name, false) }
                        if (resolve) resolveClass(selected)
                        selected
                    } else super.loadClass(name, resolve)
                }
            }
            pluginLoader.use {
                val function = "kotlin.jvm.functions.Function1"
                assertSame(libraryLoader.loadClass(function), it.loadClass(function))
                assertSame(libraryLoader, it.loadClass("pl.syntaxdevteam.syntaxdb.config.DatabaseConfigKt").classLoader)
                val thread = Thread.currentThread()
                val previous = thread.contextClassLoader
                thread.contextClassLoader = it
                try {
                    val probe = it.loadClass(SyntaxDbLinkageProbe::class.java.name)
                    assertSame(it, probe.classLoader)
                    assertEquals("Zażółć", probe.getMethod("run").invoke(null))
                } finally {
                    thread.contextClassLoader = previous
                }
            }
        }
    }
}

/** Invoked across two classloaders, not on Gradle's flat test classpath. */
object SyntaxDbLinkageProbe {
    @JvmStatic fun run(): String {
        val config = databaseConfig(DatabaseType.SQLITE) { file = ":memory:" }
        val db = Database.create(config)
        try {
            db.start()
            db.execute("CREATE TABLE runtime_probe (id INTEGER PRIMARY KEY, content TEXT)")
            db.execute("INSERT INTO runtime_probe VALUES (?, ?)", listOf(1, "Zażółć"))
            return requireNotNull(db.query("SELECT content FROM runtime_probe WHERE id = ?", listOf(1)).single().string("content"))
        } finally {
            db.close()
        }
    }
}
