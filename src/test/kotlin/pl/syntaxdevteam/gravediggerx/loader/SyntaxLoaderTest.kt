package pl.syntaxdevteam.gravediggerx.loader

import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import java.io.ByteArrayInputStream

class SyntaxLoaderTest {
    private val loader = SyntaxLoader()
    private val repositories = """
        repositories:
          central: "MAVEN_CENTRAL_DEFAULT_MIRROR"
          syntaxdevteam-snapshots: "https://example.com/snapshots/"
          syntaxdevteam-releases: "https://example.com/releases/"
          custom: "https://example.com/custom/"
    """.trimIndent()

    private fun read(libraries: String) = loader.readLibraryConfig(
        ByteArrayInputStream((repositories + "\n" + libraries.trimIndent()).toByteArray())
    )

    @Test
    fun routesCentralAndSyntaxDevTeamLibraries() {
        val config = read("""
            libraries:
              - com.github.ben-manes.caffeine:caffeine:3.2.4
              - net.kyori:adventure-nbt:5.2.0
              - pl.syntaxdevteam:syntaxcore:1.4.1-R0.1-SNAPSHOT
              - pl.syntaxdevteam.licencex:SyntaxLicenceX:0.1.2-SNAPSHOT
              - pl.syntaxdevteam:syntaxcore:1.4.1
        """)
        assertEquals(
            listOf("central", "central", "syntaxdevteam-snapshots", "syntaxdevteam-snapshots", "syntaxdevteam-releases"),
            config.libraries.map { loader.selectRepository(it, config) }
        )
    }

    @Test
    fun explicitRepositoryWinsAndKeepsCentralForTransitives() {
        val config = read("""
            libraries:
              - coordinate: pl.syntaxdevteam:example:1.0-SNAPSHOT
                repository: custom
        """)
        val id = loader.selectRepository(config.libraries.single(), config)
        assertEquals("custom", id)
        assertEquals(listOf("custom", "central"), loader.repositoriesFor(id, config).map { it.id })
        assertEquals(MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR, config.repositories.getValue("central").url)
        assertEquals(listOf("central"), loader.repositoriesFor("central", config).map { it.id })
    }

    @Test
    fun syntaxDevTeamResolverAlsoGetsCentral() {
        val config = read("""
            libraries:
              - pl.syntaxdevteam:syntaxcore:1.4.1-R0.1-SNAPSHOT
        """)
        assertEquals(
            listOf("syntaxdevteam-snapshots", "central"),
            loader.repositoriesFor(loader.selectRepository(config.libraries.single(), config), config).map { it.id }
        )
    }

    @Test
    fun rejectsUnknownRepositoryBeforeBootstrap() {
        reject("""
            libraries:
              - coordinate: com.example:library:1.0
                repository: missing
        """)
    }

    @Test
    fun rejectsUnexpandedAndMalformedLibraryEntries() {
        reject("libraries: ['com.example:library:" + '$' + "{version}']")
        reject("libraries: [42]")
        reject("libraries: [{repository: custom}]")
        reject("libraries: [{coordinate: 'com.example:library:1.0', repository: 42}]")
        reject("libraries: []")
    }

    @Test
    fun rejectsMissingCentralRepository() {
        try {
            loader.readLibraryConfig(ByteArrayInputStream("repositories: {}\nlibraries: ['com.example:library:1.0']".toByteArray()))
            fail("Missing Central must stop bootstrap")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().contains("central"))
        }
    }

    @Test
    fun packagedManifestHasResolvedVersionsAndValidRepositories() {
        val input = javaClass.classLoader.getResourceAsStream("paper-libraries.yml")
            ?: error("Processed runtime manifest is missing")
        val config = input.use(loader::readLibraryConfig)
        assertTrue(config.libraries.isNotEmpty())
        config.libraries.forEach { library ->
            assertTrue(!library.coordinate.contains("\${"))
            assertTrue(loader.repositoriesFor(loader.selectRepository(library, config), config).isNotEmpty())
        }
    }

    private fun reject(libraries: String) {
        try {
            read(libraries)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: IllegalStateException) {
            return
        }
        fail("Invalid runtime manifest must stop bootstrap: $libraries")
    }
}
