package dev.shvquu.betternpcs.core.config;

import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The whole of {@code config.yml}, parsed and validated.
 *
 * <p>Parsed once at startup into immutable records rather than read from the
 * {@link ConfigurationSection} whenever a value is needed. That has two consequences worth having: a
 * bad value is reported at startup instead of mid-tick, and the tracker — which reads
 * {@link Npc#trackerInterval()} thousands of times a second — reads a field rather than walking a
 * map and parsing a string.
 *
 * <p>A reload builds a new instance and swaps it in. Nothing mutates in place, so a component that
 * captured the old configuration keeps working with a consistent set of values instead of seeing
 * half a reload.
 *
 * @param plugin   general plugin behaviour
 * @param storage  where NPCs are persisted
 * @param npc      NPC engine tuning
 * @param language localisation
 * @param updates  update checking
 * @since 1.0.0
 */
public record BetterNpcsConfig(
        Plugin plugin,
        StorageSettings storage,
        Npc npc,
        Language language,
        Updates updates) {

    /**
     * Creates the configuration.
     *
     * @param plugin   general plugin behaviour
     * @param storage  where NPCs are persisted
     * @param npc      NPC engine tuning
     * @param language localisation
     * @param updates  update checking
     * @throws NullPointerException if any argument is {@code null}
     */
    public BetterNpcsConfig {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(updates, "updates");
    }

    /**
     * Parses a configuration.
     *
     * <p>Every value is optional and falls back to the documented default, so a file written by an
     * older release still loads. A value that is present but unusable is an error.
     *
     * @param root the root configuration section
     * @return the parsed configuration
     * @throws NullPointerException   if {@code root} is {@code null}
     * @throws ConfigurationException if any value is present but unusable
     */
    public static BetterNpcsConfig load(ConfigurationSection root) {
        ConfigReader reader = ConfigReader.of(root);
        return new BetterNpcsConfig(
                Plugin.load(reader.section("plugin")),
                loadStorage(reader.section("storage")),
                Npc.load(reader.section("npc")),
                Language.load(reader.section("language")),
                Updates.load(reader.section("updates")));
    }

    /**
     * Returns the configuration BetterNPCs uses when {@code config.yml} does not exist yet.
     *
     * <p>Identical to what parsing the shipped default file produces; the two are kept in step by
     * {@code BetterNpcsConfigTest}, which parses the packaged file and compares.
     *
     * @return the default configuration
     */
    public static BetterNpcsConfig defaults() {
        return new BetterNpcsConfig(
                Plugin.DEFAULT,
                new StorageSettings(
                        StorageType.SQLITE,
                        new StorageSettings.Sqlite("npcs.db"),
                        defaultJdbc(3306, "root"),
                        defaultJdbc(5432, "postgres"),
                        new StorageSettings.Mongo("mongodb://localhost:27017", "betternpcs")),
                Npc.DEFAULT,
                Language.DEFAULT,
                Updates.DEFAULT);
    }

    private static StorageSettings.Jdbc defaultJdbc(int port, String username) {
        return new StorageSettings.Jdbc(
                "localhost", port, "betternpcs", username, "", 10, 10_000L, Map.of());
    }

    private static StorageSettings loadStorage(ConfigReader reader) {
        StorageType type = reader.enumeration("type", StorageType.SQLITE, StorageType.class);

        ConfigReader sqlite = reader.section("sqlite");
        ConfigReader mysql = reader.section("mysql");
        ConfigReader postgresql = reader.section("postgresql");
        ConfigReader mongodb = reader.section("mongodb");

        // Every block is parsed, not only the active one, so that a typo in the MySQL block is
        // reported now rather than the first time somebody switches to it.
        return new StorageSettings(
                type,
                sqlite.build("file", () ->
                        new StorageSettings.Sqlite(sqlite.string("file", "npcs.db"))),
                loadJdbc(mysql, 3306, "root"),
                loadJdbc(postgresql, 5432, "postgres"),
                mongodb.build("connection-string", () -> new StorageSettings.Mongo(
                        mongodb.string("connection-string", "mongodb://localhost:27017"),
                        mongodb.string("database", "betternpcs"))));
    }

    private static StorageSettings.Jdbc loadJdbc(ConfigReader reader, int defaultPort, String defaultUser) {
        String host = reader.string("host", "localhost");
        int port = reader.integer("port", defaultPort, 1, 65535);
        String database = reader.string("database", "betternpcs");
        String username = reader.string("username", defaultUser);
        String password = reader.string("password", "");
        int poolSize = reader.integer("pool-size", 10, 1, 100);
        long timeout = reader.number("connection-timeout", 10_000L, 250L, 600_000L);
        Map<String, String> properties = reader.stringMap("properties");

        return reader.build("host", () -> new StorageSettings.Jdbc(
                host, port, database, username, password, poolSize, timeout, properties));
    }

    /**
     * General plugin behaviour.
     *
     * @param debug   whether to log detail useful only when diagnosing a problem
     * @param metrics whether to submit anonymous usage statistics
     * @since 1.0.0
     */
    public record Plugin(boolean debug, boolean metrics) {

        /** Debug off, metrics on. */
        public static final Plugin DEFAULT = new Plugin(false, true);

        static Plugin load(ConfigReader reader) {
            return new Plugin(
                    reader.bool("debug", DEFAULT.debug()),
                    reader.bool("metrics", DEFAULT.metrics()));
        }
    }

    /**
     * NPC engine tuning.
     *
     * <p>The defaults are chosen for a server with a few hundred NPCs. The two that matter on a
     * larger one are {@link #trackerInterval()} and {@link #defaultViewDistance()}: tracking cost
     * grows with the number of players multiplied by the number of NPCs near them, and both of these
     * divide into it directly.
     *
     * @param defaultViewDistance    how far away an NPC is rendered, in blocks, unless it overrides it
     * @param trackerInterval        how many ticks pass between visibility passes
     * @param saveInterval           how many seconds pass between writes of changed NPCs
     * @param interactionCooldown    how many milliseconds an interaction is ignored for after the
     *                               previous one from the same player
     * @param cacheSkins             whether resolved skins are cached
     * @param skinCacheDuration      how many seconds a cached skin stays valid
     * @param skinRequestTimeout     how many milliseconds a skin lookup may take before it is
     *                               abandoned
     * @param usePackets             whether NPCs are rendered as packets rather than server entities
     * @param maxTrackedPerPlayer    the most NPCs one player is sent at a time
     * @since 1.0.0
     */
    public record Npc(
            double defaultViewDistance,
            int trackerInterval,
            int saveInterval,
            int interactionCooldown,
            boolean cacheSkins,
            long skinCacheDuration,
            long skinRequestTimeout,
            boolean usePackets,
            int maxTrackedPerPlayer) {

        /** The shipped defaults. */
        public static final Npc DEFAULT = new Npc(32.0, 2, 300, 250, true, 3600L, 5_000L, true, 200);

        /**
         * Creates the settings.
         *
         * @param defaultViewDistance how far away an NPC is rendered
         * @param trackerInterval     ticks between visibility passes
         * @param saveInterval        seconds between saves
         * @param interactionCooldown milliseconds between accepted interactions
         * @param cacheSkins          whether resolved skins are cached
         * @param skinCacheDuration   seconds a cached skin stays valid
         * @param skinRequestTimeout  milliseconds a skin lookup may take
         * @param usePackets          whether NPCs are packet-only
         * @param maxTrackedPerPlayer the most NPCs sent to one player
         * @throws IllegalArgumentException if any value is outside its usable range
         */
        public Npc {
            if (!(defaultViewDistance > 0.0) || defaultViewDistance > 512.0) {
                throw new IllegalArgumentException(
                        "default-view-distance must be between 0 and 512, was " + defaultViewDistance);
            }
            if (trackerInterval < 1 || trackerInterval > 100) {
                throw new IllegalArgumentException(
                        "tracker-interval must be between 1 and 100 ticks, was " + trackerInterval);
            }
            if (saveInterval < 0) {
                throw new IllegalArgumentException(
                        "save-interval must not be negative, was " + saveInterval);
            }
            if (interactionCooldown < 0) {
                throw new IllegalArgumentException(
                        "interaction-cooldown must not be negative, was " + interactionCooldown);
            }
            if (skinCacheDuration < 0) {
                throw new IllegalArgumentException(
                        "skin-cache-duration must not be negative, was " + skinCacheDuration);
            }
            if (skinRequestTimeout < 500 || skinRequestTimeout > 60_000) {
                throw new IllegalArgumentException(
                        "skin-request-timeout must be between 500 and 60000 ms, was " + skinRequestTimeout);
            }
            if (maxTrackedPerPlayer < 1) {
                throw new IllegalArgumentException(
                        "max-tracked-per-player must be at least 1, was " + maxTrackedPerPlayer);
            }
        }

        /**
         * Returns whether periodic saving is switched off.
         *
         * @return {@code true} if NPCs are written only on shutdown and on explicit request
         */
        public boolean isAutoSaveDisabled() {
            return saveInterval == 0;
        }

        static Npc load(ConfigReader reader) {
            double viewDistance = reader.decimal(
                    "default-view-distance", DEFAULT.defaultViewDistance(), 1.0, 512.0);
            int trackerInterval = reader.integer("tracker-interval", DEFAULT.trackerInterval(), 1, 100);
            int saveInterval = reader.integer("save-interval", DEFAULT.saveInterval(), 0, 86_400);
            int interactionCooldown =
                    reader.integer("interaction-cooldown", DEFAULT.interactionCooldown(), 0, 60_000);
            boolean cacheSkins = reader.bool("cache-skins", DEFAULT.cacheSkins());
            long skinCacheDuration =
                    reader.number("skin-cache-duration", DEFAULT.skinCacheDuration(), 0L, 2_592_000L);
            long skinRequestTimeout =
                    reader.number("skin-request-timeout", DEFAULT.skinRequestTimeout(), 500L, 60_000L);
            boolean usePackets = reader.bool("use-packets", DEFAULT.usePackets());
            int maxTracked =
                    reader.integer("max-tracked-per-player", DEFAULT.maxTrackedPerPlayer(), 1, 10_000);

            return reader.build("npc", () -> new Npc(
                    viewDistance, trackerInterval, saveInterval, interactionCooldown,
                    cacheSkins, skinCacheDuration, skinRequestTimeout, usePackets, maxTracked));
        }
    }

    /**
     * Localisation.
     *
     * @param defaultLocale   the language file to use, for example {@code en_US}
     * @param followClientLocale whether each player is served the language their client is set to,
     *                        falling back to {@link #defaultLocale()} when that language has no file
     * @since 1.0.0
     */
    public record Language(String defaultLocale, boolean followClientLocale) {

        /** English, not following the client. */
        public static final Language DEFAULT = new Language("en_US", false);

        /**
         * Creates the settings.
         *
         * @param defaultLocale      the language file to use
         * @param followClientLocale whether to serve each player their client language
         * @throws NullPointerException     if {@code defaultLocale} is {@code null}
         * @throws IllegalArgumentException if {@code defaultLocale} is blank or is not a plain file
         *                                  name
         */
        public Language {
            Objects.requireNonNull(defaultLocale, "defaultLocale");
            defaultLocale = defaultLocale.trim();
            if (defaultLocale.isEmpty()) {
                throw new IllegalArgumentException("language.default must not be blank");
            }
            // The value is turned into a file name under the languages folder, so it has to be a
            // plain identifier and nothing that could point somewhere else on disk.
            if (!defaultLocale.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException(
                        "language.default must contain only letters, digits, '_' and '-', was '"
                                + defaultLocale + "'");
            }
        }

        static Language load(ConfigReader reader) {
            String locale = reader.string("default", DEFAULT.defaultLocale());
            boolean follow = reader.bool("follow-client-locale", DEFAULT.followClientLocale());
            return reader.build("default", () -> new Language(locale, follow));
        }
    }

    /**
     * Update checking.
     *
     * <p>BetterNPCs only ever reports that a newer version exists. It does not download anything and
     * has no mechanism to replace itself: a plugin that updates itself is a plugin that can break a
     * server while nobody is watching.
     *
     * @param check           whether to look for a newer release at startup
     * @param disableOnUpdate whether to shut BetterNPCs down after reporting one
     * @since 1.0.0
     */
    public record Updates(boolean check, boolean disableOnUpdate) {

        /** Checking enabled, and shutting down when a newer release is found. */
        public static final Updates DEFAULT = new Updates(true, true);

        static Updates load(ConfigReader reader) {
            return new Updates(
                    reader.bool("check", DEFAULT.check()),
                    reader.bool("disable-on-update", DEFAULT.disableOnUpdate()));
        }
    }
}
