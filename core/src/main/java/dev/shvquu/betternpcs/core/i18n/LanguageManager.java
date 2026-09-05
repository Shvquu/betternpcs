package dev.shvquu.betternpcs.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads the language files and hands out the right {@link MessageBundle} for a recipient.
 *
 * <p>Language files live in {@code languages/} inside the plugin folder. The ones BetterNPCs ships
 * are written there on first start and are <em>not</em> overwritten afterwards, so a server owner's
 * edits survive an update. The cost of that is a translation that falls behind when new messages are
 * added; {@link MessageBundle#missingKeys()} makes that visible rather than silent, and the missing
 * lines fall back to English so nothing ever renders blank.
 *
 * <p>Loading is not thread safe and belongs on the main thread during enable or reload. Lookups
 * afterwards are, because the loaded map is replaced wholesale rather than mutated.
 *
 * @since 1.0.0
 */
public final class LanguageManager {

    /** The language files BetterNPCs ships. */
    public static final List<String> BUNDLED_LOCALES = List.of("en_US", "de_DE", "es_ES", "fr_FR");

    private static final String FILE_SUFFIX = ".yml";

    private final Path folder;
    private final Function<String, InputStream> bundledResources;
    private final Logger logger;

    private volatile Map<String, MessageBundle> bundles = Map.of();
    private volatile MessageBundle fallback = MessageBundle.english();
    private volatile boolean followClientLocale;

    /**
     * Creates the manager.
     *
     * @param folder           the {@code languages} folder inside the plugin's data folder
     * @param bundledResources opens a file packaged inside the plugin jar by its path, returning
     *                         {@code null} when the jar has no such file; the plugin passes its own
     *                         resource loader so that this class needs no Bukkit plugin instance
     * @param logger           where to report translation problems
     * @throws NullPointerException if any argument is {@code null}
     */
    public LanguageManager(Path folder, Function<String, InputStream> bundledResources, Logger logger) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.bundledResources = Objects.requireNonNull(bundledResources, "bundledResources");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Writes any missing shipped language file, then loads every file in the folder.
     *
     * <p>Safe to call again for a reload: the new set of bundles is swapped in only once loading has
     * finished, so a broken file leaves the previous translation in place rather than a half-loaded
     * one.
     *
     * @param defaultLocale      the locale to use for the console and as the fallback
     * @param followClientLocale whether each player is served the language their client is set to
     * @throws NullPointerException if {@code defaultLocale} is {@code null}
     * @throws IOException          if the languages folder cannot be created or read
     */
    public void load(String defaultLocale, boolean followClientLocale) throws IOException {
        Objects.requireNonNull(defaultLocale, "defaultLocale");

        Files.createDirectories(folder);
        writeMissingBundledFiles();

        Map<String, MessageBundle> loaded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (var files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(FILE_SUFFIX))
                    .forEach(file -> readFile(file).ifPresent(bundle -> loaded.put(bundle.locale(), bundle)));
        }

        MessageBundle newFallback = loaded.get(defaultLocale);
        if (newFallback == null) {
            // Refusing to start would be a harsh answer to a typo in one setting, and there is a
            // perfectly good English bundle compiled in. Say so loudly and carry on.
            logger.warning(() -> "No language file '" + defaultLocale + FILE_SUFFIX
                    + "' was found in " + folder + "; using the built-in English texts.");
            newFallback = MessageBundle.english();
        }

        // An unmodifiable view rather than Map.copyOf: the latter returns a hash map and would throw
        // away the case-insensitive comparator, so 'DE_de' in config.yml would stop resolving.
        // `loaded` is local and never escapes, so the view is effectively immutable.
        this.bundles = Collections.unmodifiableMap(loaded);
        this.fallback = newFallback;
        this.followClientLocale = followClientLocale;

        reportIncompleteTranslations(loaded.values());
    }

    private void reportIncompleteTranslations(Iterable<MessageBundle> loaded) {
        for (MessageBundle bundle : loaded) {
            if (bundle.isComplete()) {
                continue;
            }
            List<String> missing = bundle.missingKeys();
            // The full list is often long; naming a few is enough to act on, and the count says how
            // much is left.
            String examples = String.join(", ", missing.subList(0, Math.min(3, missing.size())));
            logger.warning(() -> "Language '" + bundle.locale() + "' is missing " + missing.size()
                    + " message(s) and falls back to English for them, for example: " + examples);
        }
    }

    private void writeMissingBundledFiles() {
        for (String locale : BUNDLED_LOCALES) {
            Path target = folder.resolve(locale + FILE_SUFFIX);
            if (Files.exists(target)) {
                continue;
            }
            String resource = "languages/" + locale + FILE_SUFFIX;
            try (InputStream source = bundledResources.apply(resource)) {
                if (source == null) {
                    logger.warning(() -> "The plugin jar has no packaged language file " + resource + ".");
                    continue;
                }
                Files.copy(source, target);
            } catch (IOException failure) {
                logger.log(Level.WARNING, failure,
                        () -> "Could not write the default language file " + target + ".");
            }
        }
    }

    private Optional<MessageBundle> readFile(Path file) {
        String fileName = file.getFileName().toString();
        String locale = fileName.substring(0, fileName.length() - FILE_SUFFIX.length());

        YamlConfiguration configuration = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (IOException | InvalidConfigurationException failure) {
            // One malformed file must not take the others down with it.
            logger.log(Level.WARNING, failure,
                    () -> "Skipping language file " + fileName + ": it could not be read.");
            return Optional.empty();
        }
        return Optional.of(MessageBundle.load(locale, configuration));
    }

    /**
     * Returns the bundle used for the console and wherever no recipient is known.
     *
     * @return the default bundle
     */
    public MessageBundle defaultBundle() {
        return fallback;
    }

    /**
     * Returns the bundle for a locale, falling back where necessary.
     *
     * <p>Falls back in two steps: an exact match such as {@code de_DE}, then any file for the same
     * language such as {@code de_AT} matching {@code de_DE}, then the default bundle. The middle
     * step is what makes {@code follow-client-locale} useful without a file per country.
     *
     * @param locale the requested locale name, or {@code null} for the default
     * @return the best matching bundle, never {@code null}
     */
    public MessageBundle bundle(String locale) {
        if (locale == null || locale.isBlank()) {
            return fallback;
        }
        Map<String, MessageBundle> current = bundles;

        MessageBundle exact = current.get(locale);
        if (exact != null) {
            return exact;
        }

        String language = locale.length() >= 2 ? locale.substring(0, 2).toLowerCase(Locale.ROOT) : locale;
        for (Map.Entry<String, MessageBundle> candidate : current.entrySet()) {
            if (candidate.getKey().toLowerCase(Locale.ROOT).startsWith(language)) {
                return candidate.getValue();
            }
        }
        return fallback;
    }

    /**
     * Returns the bundle for a player's client language, or the default when that is switched off.
     *
     * @param clientLocale the player's client locale, may be {@code null}
     * @return the bundle to use for that player
     */
    public MessageBundle bundleForClient(Locale clientLocale) {
        if (!followClientLocale || clientLocale == null) {
            return fallback;
        }
        // Minecraft reports its locales as "de_de"; BetterNPCs names its files "de_DE".
        String language = clientLocale.getLanguage().toLowerCase(Locale.ROOT);
        String country = clientLocale.getCountry().toUpperCase(Locale.ROOT);
        return bundle(country.isEmpty() ? language : language + "_" + country);
    }

    /**
     * Returns the locales a language file was found for.
     *
     * @return an immutable set of locale names
     */
    public Set<String> availableLocales() {
        return bundles.keySet();
    }

    /**
     * Returns whether players are served their own client language.
     *
     * @return {@code true} if {@code language.follow-client-locale} is on
     */
    public boolean followsClientLocale() {
        return followClientLocale;
    }
}
