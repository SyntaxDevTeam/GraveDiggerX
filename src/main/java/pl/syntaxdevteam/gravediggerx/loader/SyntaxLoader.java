package pl.syntaxdevteam.gravediggerx.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java-only bootstrap: the Kotlin runtime is not available until Paper resolves the libraries. */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public final class SyntaxLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder pluginClasspath) {
        final LibraryConfig config;
        try (InputStream input = SyntaxLoader.class.getResourceAsStream("/paper-libraries.yml")) {
            if (input == null) throw new IllegalStateException("paper-libraries.yml not found in plugin JAR");
            config = readLibraryConfig(input);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot configure GraveDiggerX runtime libraries from paper-libraries.yml", failure);
        }

        // One dependency graph, so Maven mediates Kotlin and other shared transitive dependencies
        // together rather than selecting versions independently in repository-specific resolvers.
        final MavenLibraryResolver resolver = new MavenLibraryResolver();
        final Set<String> repositoryIds = new LinkedHashSet<>();
        repositoryIds.add("central");
        for (LibrarySpec library : config.libraries()) {
            repositoryIds.add(selectRepository(library, config));
        }
        for (String id : repositoryIds) {
            resolver.addRepository(config.repositories().get(id));
        }
        for (LibrarySpec library : config.libraries()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(library.coordinate()), null));
        }
        pluginClasspath.addLibrary(resolver);
    }

    static LibraryConfig readLibraryConfig(InputStream input) {
        Object document = new Yaml().load(input);
        if (!(document instanceof Map<?, ?> data)) throw new IllegalArgumentException("paper-libraries.yml must contain a mapping");
        if (!(data.get("repositories") instanceof Map<?, ?> repositoryData))
            throw new IllegalArgumentException("paper-libraries.yml must define repositories");
        Map<String, RemoteRepository> repositories = new LinkedHashMap<>();
        for (var entry : repositoryData.entrySet()) {
            String id = requiredString(entry.getKey(), "repository id");
            String configuredUrl = requiredString(entry.getValue(), "repository URL");
            String url = configuredUrl.equals("MAVEN_CENTRAL_DEFAULT_MIRROR")
                ? MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR : configuredUrl;
            if (!url.startsWith("https://") && !url.startsWith("http://"))
                throw new IllegalArgumentException("Invalid repository URL for " + id);
            repositories.put(id, new RemoteRepository.Builder(id, "default", url).build());
        }
        if (!repositories.containsKey("central")) throw new IllegalArgumentException("Missing central repository in paper-libraries.yml");
        if (!(data.get("libraries") instanceof List<?> libraryData) || libraryData.isEmpty())
            throw new IllegalArgumentException("No runtime libraries defined in paper-libraries.yml");
        List<LibrarySpec> libraries = new ArrayList<>();
        Set<String> modules = new LinkedHashSet<>();
        for (Object entry : libraryData) {
            final LibrarySpec library;
            if (entry instanceof String coordinate) {
                library = new LibrarySpec(requiredString(coordinate, "coordinate"), null);
            } else if (entry instanceof Map<?, ?> map) {
                Object repository = map.get("repository");
                library = new LibrarySpec(requiredString(map.get("coordinate"), "coordinate"),
                    repository == null ? null : requiredString(repository, "repository"));
            } else {
                throw new IllegalArgumentException("Invalid library entry: " + entry);
            }
            if (library.coordinate().contains("${")) throw new IllegalArgumentException("Unexpanded library coordinate: " + library.coordinate());
            DefaultArtifact artifact = new DefaultArtifact(library.coordinate());
            String module = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":" + artifact.getClassifier();
            if (!modules.add(module)) throw new IllegalArgumentException("Duplicate runtime module: " + module);
            libraries.add(library);
        }
        LibraryConfig config = new LibraryConfig(repositories, libraries);
        libraries.forEach(library -> selectRepository(library, config));
        return config;
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Invalid " + field);
        return text.trim();
    }

    static String selectRepository(LibrarySpec library, LibraryConfig config) {
        String id = library.repository();
        if (id == null) {
            String group = library.coordinate().split(":", 2)[0];
            boolean syntax = group.equals("pl.syntaxdevteam") || group.startsWith("pl.syntaxdevteam.");
            id = !syntax ? "central" : library.coordinate().endsWith("-SNAPSHOT") ? "syntaxdevteam-snapshots" : "syntaxdevteam-releases";
        }
        if (!config.repositories().containsKey(id))
            throw new IllegalArgumentException("Unknown repository '" + id + "' for " + library.coordinate());
        return id;
    }

    record LibraryConfig(Map<String, RemoteRepository> repositories, List<LibrarySpec> libraries) {}
    record LibrarySpec(String coordinate, String repository) {}
}
