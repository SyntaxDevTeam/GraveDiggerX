package pl.syntaxdevteam.gravediggerx.loader

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/** Reads repository assignments from the same runtime manifest format as PunisherX. */
@Suppress("UnstableApiUsage", "unused")
class SyntaxLoader : PluginLoader {

    override fun classloader(pluginClasspath: PluginClasspathBuilder) {
        val config = try {
            val input = SyntaxLoader::class.java.classLoader.getResourceAsStream("paper-libraries.yml")
                ?: error("paper-libraries.yml not found in plugin JAR")
            input.use(::readLibraryConfig)
        } catch (failure: Exception) {
            throw IllegalStateException("Cannot configure GraveDiggerX runtime libraries from paper-libraries.yml", failure)
        }
        val resolvers = linkedMapOf<String, MavenLibraryResolver>()
        for (library in config.libraries) {
            val repositoryId = selectRepository(library, config)
            val resolver = resolvers.getOrPut(repositoryId) {
                MavenLibraryResolver().apply {
                    repositoriesFor(repositoryId, config).forEach(::addRepository)
                }
            }
            resolver.addDependency(Dependency(DefaultArtifact(library.coordinate), null))
        }
        resolvers.values.forEach(pluginClasspath::addLibrary)
    }

    internal fun readLibraryConfig(input: InputStream): LibraryConfig {
        val document = Yaml().load<Any?>(input) as? Map<*, *>
            ?: error("paper-libraries.yml must contain a mapping")
        val repositoryData = document["repositories"] as? Map<*, *>
            ?: error("paper-libraries.yml must define repositories")
        val repositories = linkedMapOf<String, RemoteRepository>()
        for ((key, value) in repositoryData) {
            val id = (key as? String)?.trim().orEmpty()
            val configuredUrl = (value as? String)?.trim().orEmpty()
            require(id.isNotEmpty() && configuredUrl.isNotEmpty()) { "Invalid repository entry: $key" }
            val url = if (configuredUrl == "MAVEN_CENTRAL_DEFAULT_MIRROR") {
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
            } else {
                configuredUrl
            }
            require(url.startsWith("https://") || url.startsWith("http://")) { "Invalid repository URL for $id" }
            repositories[id] = RemoteRepository.Builder(id, "default", url).build()
        }
        require(repositories.containsKey("central")) { "Missing central repository in paper-libraries.yml" }
        val libraryData = document["libraries"] as? List<*>
            ?: error("paper-libraries.yml must define libraries")
        require(libraryData.isNotEmpty()) { "No runtime libraries defined in paper-libraries.yml" }
        val libraries = libraryData.mapIndexed { index, entry ->
            val library = when (entry) {
                is String -> LibrarySpec(entry.trim())
                is Map<*, *> -> {
                    val coordinate = entry["coordinate"] as? String
                        ?: error("Missing coordinate in library entry ${index + 1}")
                    val repository = entry["repository"]
                    require(repository == null || repository is String) { "Invalid repository in library entry ${index + 1}" }
                    LibrarySpec(coordinate.trim(), repository?.trim())
                }
                else -> error("Invalid library entry ${index + 1}")
            }
            require(library.coordinate.isNotEmpty() && !library.coordinate.contains("\${")) {
                "Invalid or unexpanded library coordinate: ${library.coordinate}"
            }
            DefaultArtifact(library.coordinate)
            library
        }
        return LibraryConfig(repositories, libraries).also { config ->
            config.libraries.forEach { selectRepository(it, config) }
        }
    }

    internal fun selectRepository(library: LibrarySpec, config: LibraryConfig): String {
        val repositoryId = library.repository ?: when {
            library.coordinate.substringBefore(':').let { it == "pl.syntaxdevteam" || it.startsWith("pl.syntaxdevteam.") } ->
                if (library.coordinate.endsWith("-SNAPSHOT")) "syntaxdevteam-snapshots" else "syntaxdevteam-releases"
            else -> "central"
        }
        require(config.repositories.containsKey(repositoryId)) {
            "Unknown repository '$repositoryId' for ${library.coordinate}"
        }
        return repositoryId
    }

    internal fun repositoriesFor(repositoryId: String, config: LibraryConfig): List<RemoteRepository> {
        val selected = config.repositories.getValue(repositoryId)
        return if (repositoryId == "central") listOf(selected)
        else listOf(selected, config.repositories.getValue("central"))
    }

    internal data class LibraryConfig(
        val repositories: Map<String, RemoteRepository>,
        val libraries: List<LibrarySpec>
    )

    internal data class LibrarySpec(val coordinate: String, val repository: String? = null)
}