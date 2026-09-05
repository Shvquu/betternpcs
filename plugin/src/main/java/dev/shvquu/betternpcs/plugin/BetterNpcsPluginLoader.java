package dev.shvquu.betternpcs.plugin;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Fetches the database libraries BetterNPCs needs at runtime.
 *
 * <h2>Why these are not shaded into the jar</h2>
 *
 * <p>Shading a JDBC driver means relocating its packages, and the SQLite driver resolves its native
 * methods by fully qualified class name — relocated, it fails to link at the first query, with an
 * error that says nothing about relocation. Letting Paper download the drivers avoids that entirely,
 * and keeps the plugin jar small enough to be worth downloading.
 *
 * <h2>Only what is configured</h2>
 *
 * <p>HikariCP and the SQLite driver are always fetched: SQLite is the default backend and both are
 * small. The MySQL, MariaDB and PostgreSQL drivers are several megabytes each and are fetched only
 * when {@code config.yml} names them, which on the overwhelming majority of servers is never.
 *
 * <p>On a first start there is no {@code config.yml} yet, so only the defaults are fetched. That is
 * correct: the file is written during enable, and a server owner who switches backend restarts
 * anyway.
 *
 * @since 1.0.0
 */
public final class BetterNpcsPluginLoader implements PluginLoader {

    /** Where the coordinates live, expanded from the version catalogue at build time. */
    private static final String LIBRARIES_RESOURCE = "/betternpcs-libraries.properties";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        Properties libraries = readLibraries();

        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default", centralUrl()).build());

        List<String> wanted = new ArrayList<>();
        wanted.add("hikaricp");
        wanted.add("sqlite");
        wanted.addAll(driverFor(configuredStorageType(classpathBuilder)));

        for (String key : wanted) {
            String coordinates = libraries.getProperty(key);
            if (coordinates != null && !coordinates.isBlank()) {
                resolver.addDependency(new Dependency(new DefaultArtifact(coordinates.trim()), null));
            }
        }

        classpathBuilder.addLibrary(resolver);
    }

    /**
     * Returns the repository to download from.
     *
     * <p>Honours the mirror a server owner has configured, which is what large networks use to keep
     * plugin downloads inside their own infrastructure.
     *
     * <p>Read from the environment rather than through
     * {@code MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR}: that constant is present in the
     * published {@code paper-api} artifact but absent from older Paper server builds, so referencing
     * it compiles cleanly and then fails at load time with a {@code NoSuchFieldError} — on exactly
     * the versions this plugin promises to support.
     *
     * @return the repository URL
     */
    private static String centralUrl() {
        // The same variable Paper itself reads for its own library downloads, so a network that has
        // already pointed Paper at an internal mirror does not have to configure this separately.
        String mirror = System.getenv("PAPER_DEFAULT_CENTRAL_REPOSITORY");
        return mirror == null || mirror.isBlank() ? "https://repo1.maven.org/maven2/" : mirror.trim();
    }

    private Properties readLibraries() {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getResourceAsStream(LIBRARIES_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "The BetterNPCs jar is missing " + LIBRARIES_RESOURCE
                                + ". It was not built correctly.");
            }
            properties.load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read " + LIBRARIES_RESOURCE, failure);
        }
        return properties;
    }

    private static List<String> driverFor(String storageType) {
        return switch (storageType) {
            case "MYSQL" -> List.of("mysql");
            case "MARIADB" -> List.of("mariadb");
            case "POSTGRESQL" -> List.of("postgresql");
            default -> List.of();
        };
    }

    /**
     * Reads the configured storage type without loading the plugin's configuration machinery.
     *
     * <p>The loader runs long before the plugin does, so none of the usual configuration code is
     * available. This is a deliberately minimal scan for one setting: it looks for {@code type:}
     * inside the {@code storage:} block and gives up quietly on anything it does not recognise,
     * because being wrong here costs one unnecessary download, while throwing would stop the server.
     *
     * @param classpathBuilder supplies the plugin's data directory
     * @return the configured type in upper case, or an empty string
     */
    private static String configuredStorageType(PluginClasspathBuilder classpathBuilder) {
        Path config = classpathBuilder.getContext().getDataDirectory().resolve("config.yml");
        if (!Files.isRegularFile(config)) {
            return "";
        }

        try {
            boolean inStorage = false;
            for (String rawLine : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String line = rawLine.stripTrailing();
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }

                boolean topLevel = !Character.isWhitespace(line.charAt(0));
                if (topLevel) {
                    inStorage = line.startsWith("storage:");
                    continue;
                }
                if (!inStorage) {
                    continue;
                }

                String trimmed = line.strip();
                if (trimmed.startsWith("type:")) {
                    return trimmed.substring("type:".length())
                            .strip()
                            .replace("\"", "")
                            .replace("'", "")
                            .toUpperCase(Locale.ROOT);
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // An unreadable config is the plugin's problem to report properly during enable, with a
            // message that names the file. Here it just means the defaults are fetched.
            return "";
        }
        return "";
    }
}
