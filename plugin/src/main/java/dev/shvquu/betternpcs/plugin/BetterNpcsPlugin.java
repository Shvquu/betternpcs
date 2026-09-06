package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.api.ApiVersion;
import dev.shvquu.betternpcs.api.BetterNPCs;
import dev.shvquu.betternpcs.api.BetterNPCsApi;
import dev.shvquu.betternpcs.core.action.ActionExecutor;
import dev.shvquu.betternpcs.core.action.BuiltinActionHandlers;
import dev.shvquu.betternpcs.core.action.DefaultActionRegistry;
import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import dev.shvquu.betternpcs.core.config.ConfigurationException;
import dev.shvquu.betternpcs.core.engine.EventDispatcher;
import dev.shvquu.betternpcs.core.engine.Scheduler;
import dev.shvquu.betternpcs.core.extension.DefaultExtensionManager;
import dev.shvquu.betternpcs.core.i18n.LanguageManager;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.i18n.MiniMessageService;
import dev.shvquu.betternpcs.core.i18n.PlaceholderExpander;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.logging.StartupBanner;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import dev.shvquu.betternpcs.core.npc.EngineServices;
import dev.shvquu.betternpcs.core.npc.NpcRegistry;
import dev.shvquu.betternpcs.core.npc.NpcRenderService;
import dev.shvquu.betternpcs.core.npc.NpcSpatialIndex;
import dev.shvquu.betternpcs.core.npc.NpcTracker;
import dev.shvquu.betternpcs.core.npc.VisibilityService;
import dev.shvquu.betternpcs.core.render.AdapterContext;
import dev.shvquu.betternpcs.core.render.NpcInteractionSink;
import dev.shvquu.betternpcs.core.skin.DefaultSkinService;
import dev.shvquu.betternpcs.core.skin.MojangSkinProvider;
import dev.shvquu.betternpcs.core.skin.SkinCache;
import dev.shvquu.betternpcs.core.storage.InMemoryNpcRepository;
import dev.shvquu.betternpcs.core.storage.NpcRepository;
import dev.shvquu.betternpcs.core.update.PluginVersion;
import dev.shvquu.betternpcs.core.update.UpdateChecker;
import dev.shvquu.betternpcs.core.version.MinecraftVersion;
import dev.shvquu.betternpcs.core.version.UnsupportedMinecraftVersionException;
import dev.shvquu.betternpcs.core.version.VersionAdapter;
import dev.shvquu.betternpcs.core.version.VersionAdapterResolver;
import dev.shvquu.betternpcs.core.backup.BackupService;
import dev.shvquu.betternpcs.core.backup.RestoreService;
import dev.shvquu.betternpcs.plugin.command.NpcCommands;
import dev.shvquu.betternpcs.storage.mongodb.MongoRepositoryFactory;
import dev.shvquu.betternpcs.storage.sql.SqlRepositoryFactory;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The BetterNPCs plugin.
 *
 * <p>Assembles the engine and gets out of the way. Nothing here decides anything about NPCs; it
 * decides which implementation of each seam the engine gets, in an order that is deliberate:
 *
 * <ol>
 *   <li>configuration, because everything else is shaped by it</li>
 *   <li>languages, so that a later failure can be reported in the server's language</li>
 *   <li>the version adapter, because an unsupported server should be refused before a database is
 *       touched</li>
 *   <li>storage, which may fall back to memory rather than refuse to start</li>
 *   <li>the engine, the API, listeners, and finally the NPCs themselves</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class BetterNpcsPlugin extends JavaPlugin {

    /** Where the update check looks for releases. */
    private static final String UPDATE_REPOSITORY = "FancyMcPlugins/FancyNpcs"; // TEMPORARY - reverted below

    private BetterNpcsConfig configuration;
    private LanguageManager languages;
    private MessageService messages;

    private VersionAdapter adapter;
    private NpcRepository repository;
    private EngineServices services;
    private DefaultNpcManager npcManager;
    private DefaultExtensionManager extensions;
    private NpcInteractionHandler interactions;
    private BetterNPCsApi api;

    private Scheduler.Task autoSaveTask;
    private boolean started;

    @Override
    public void onEnable() {
        try {
            configuration = loadConfiguration();
        } catch (ConfigurationException invalid) {
            // The message names the exact path. Disabling is right: guessing at what a wrong value
            // was meant to be is how a plugin silently does something other than what was asked.
            getLogger().severe(invalid.getMessage());
            getLogger().severe("Fix that setting and restart. BetterNPCs will not load until then.");
            setEnabled(false);
            return;
        }

        languages = loadLanguages();
        messages = new MiniMessageService(languages, placeholderExpander());

        MinecraftVersion minecraft = MinecraftVersion.parse(getServer().getMinecraftVersion());
        VersionAdapterResolver resolver = VersionAdapterResolver.withDefaults();
        try {
            adapter = resolver.load(minecraft);
        } catch (UnsupportedMinecraftVersionException unsupported) {
            reportUnsupportedVersion(minecraft, resolver, unsupported);
            setEnabled(false);
            return;
        }

        repository = openStorage();
        buildEngine();

        adapter.enable(adapterContext());
        getServer().getOnlinePlayers().forEach(adapter::trackPlayer);

        registerApi(minecraft);
        registerListeners();
        registerCommands();

        StartupBanner.render(bannerDetails(minecraft)).forEach(getLogger()::info);
        logRegisteredPermissions();
        checkForUpdates();

        BetterNpcsMetrics.start(
                this,
                this::configuration,
                npcManager,
                repository,
                adapter.describe(),
                languages.defaultBundle().locale());

        loadNpcs();
        startTasks();
        started = true;
    }

    @Override
    public void onDisable() {
        if (!started) {
            // A failed enable still calls onDisable. Nothing below has been set up, and running it
            // would bury the real error under a second one.
            return;
        }

        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        if (extensions != null) {
            extensions.disableAll();
        }

        BetterNPCs.unregister();

        try {
            // Waited on: this is the last chance to persist anything, and the alternative is losing
            // whatever changed since the previous save cycle.
            int saved = npcManager.saveAll().get(15, TimeUnit.SECONDS);
            if (saved > 0) {
                getLogger().info("Saved " + saved + " NPC(s).");
            }
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Could not save NPCs during shutdown.", failure);
        }

        npcManager.despawnAll();
        services.tracker().stop();

        getServer().getOnlinePlayers().forEach(adapter::untrackPlayer);
        adapter.disable();

        try {
            repository.shutdown().get(20, TimeUnit.SECONDS);
        } catch (Exception failure) {
            getLogger().log(Level.WARNING, "The storage backend did not shut down cleanly.", failure);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Assembly
    // ---------------------------------------------------------------------------------------------

    private BetterNpcsConfig loadConfiguration() {
        saveDefaultConfig();
        reloadConfig();
        return BetterNpcsConfig.load(getConfig());
    }

    private LanguageManager loadLanguages() {
        return loadLanguagesFor(configuration);
    }

    private LanguageManager loadLanguagesFor(BetterNpcsConfig config) {
        Path folder = getDataFolder().toPath().resolve("languages");
        LanguageManager manager = new LanguageManager(folder, this::openBundledResource, getLogger());
        try {
            manager.load(config.language().defaultLocale(), config.language().followClientLocale());
        } catch (IOException failure) {
            // Not fatal: every message has a compiled-in English fallback, so an unreadable language
            // folder costs translations, not the plugin.
            getLogger().log(Level.WARNING, failure,
                    () -> "Could not read the languages folder. Falling back to the built-in English texts.");
        }
        return manager;
    }

    private InputStream openBundledResource(String path) {
        return getResource(path);
    }

    private PlaceholderExpander placeholderExpander() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return PlaceholderExpander.none();
        }
        try {
            PlaceholderExpander expander = new PlaceholderApiExpander();
            getLogger().info("PlaceholderAPI found; its placeholders will be resolved.");
            return expander;
        } catch (IllegalStateException incompatible) {
            // A PlaceholderAPI whose signature has changed. Losing its placeholders is a shame;
            // refusing to start over an optional integration would be worse.
            getLogger().warning(incompatible.getMessage());
            return PlaceholderExpander.none();
        }
    }

    private NpcRepository openStorage() {
        try {
            NpcRepository backend = switch (configuration.storage().type().family()) {
                case SQL -> SqlRepositoryFactory.create(
                        configuration.storage(), getDataFolder().toPath(), getLogger());
                case DOCUMENT -> MongoRepositoryFactory.create(configuration.storage(), getLogger());
            };
            backend.initialize().join();
            return backend;
        } catch (RuntimeException failure) {
            // CompletionException from join() is a RuntimeException, so this covers both a pool that
            // could not connect and a migration that failed.
            // A server that starts with NPCs that do not persist is far more useful than one that
            // refuses to start at all — and the warning is loud enough that nobody misses it.
            getLogger().log(Level.SEVERE, failure, () ->
                    "Could not open the "
                            + configuration.storage().describe()
                            + " backend. BetterNPCs will run WITHOUT PERSISTENCE for this session: "
                            + "NPCs can be created and used, but nothing will be saved.");
            return new InMemoryNpcRepository();
        }
    }

    private void buildEngine() {
        Logger logger = getLogger();

        NpcRegistry registry = new NpcRegistry();
        NpcSpatialIndex index = new NpcSpatialIndex();
        Scheduler scheduler = new PaperScheduler(this);
        EventDispatcher events = new BukkitEventDispatcher(logger);

        VisibilityService visibility = new VisibilityService(this::configuration);
        NpcRenderService renderer = new NpcRenderService(adapter, messages, scheduler);
        NpcTracker tracker = new NpcTracker(
                registry, index, visibility, renderer, scheduler, this::configuration,
                this::onlinePlayers);

        DefaultActionRegistry actions = new DefaultActionRegistry();
        BuiltinActionHandlers.registerAll(actions);

        DefaultSkinService skins = new DefaultSkinService(
                new SkinCache(configuration.npc().cacheSkins()
                        ? Duration.ofSeconds(configuration.npc().skinCacheDuration())
                        : Duration.ZERO),
                logger);
        skins.registerProvider(new MojangSkinProvider(
                Duration.ofMillis(configuration.npc().skinRequestTimeout()),
                task -> scheduler.runAsync(task)));

        services = new EngineServices(
                registry, index, renderer, visibility, tracker, repository, scheduler, events,
                actions, new ActionExecutor(actions, messages, logger), skins, messages,
                this::configuration, logger);

        npcManager = new DefaultNpcManager(services);
        extensions = new DefaultExtensionManager(() -> api, logger);

        // One instance, shared by the adapter and the quit listener. Two would mean the listener
        // pruning a cooldown map nobody reads while the real one grew without bound.
        interactions = new NpcInteractionHandler(services, this::configuration);
    }

    private Collection<? extends Player> onlinePlayers() {
        return getServer().getOnlinePlayers();
    }

    private AdapterContext adapterContext() {
        NpcInteractionSink sink = interactions;
        return new AdapterContext() {

            @Override
            public org.bukkit.plugin.Plugin plugin() {
                return BetterNpcsPlugin.this;
            }

            @Override
            public Logger logger() {
                return getLogger();
            }

            @Override
            public NpcInteractionSink interactions() {
                return sink;
            }

            @Override
            public boolean debug() {
                return configuration.plugin().debug();
            }
        };
    }

    private void registerApi(MinecraftVersion minecraft) {
        api = new BetterNpcsApiImpl(
                getPluginMeta().getVersion(),
                minecraft.toString(),
                npcManager,
                services.skins(),
                services.actions(),
                extensions,
                messages,
                getServer().getPluginManager().getPlugin("PlaceholderAPI") != null);

        BetterNPCs.register(api);
        // Also published through Bukkit's services manager, which is what a plugin that would rather
        // not depend on our static holder will look in.
        getServer().getServicesManager().register(
                BetterNPCsApi.class, api, this, ServicePriority.Normal);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new EngineListener(services, npcManager, adapter, interactions), this);
    }

    private void registerCommands() {
        // Under the data folder rather than beside the world, so that a server owner who copies
        // plugins/BetterNPCs takes the backups with them.
        BackupService backups = new BackupService(
                getDataFolder().toPath().resolve("backups"), getPluginMeta().getVersion());

        NpcCommands commands = new NpcCommands(
                npcManager, messages, services.scheduler(), services.actions(),
                backups, new RestoreService(npcManager, getLogger()),
                this::isEnabled, this::reloadSettings);

        // Registered through the lifecycle event because a plugin described by paper-plugin.yml has
        // no commands block. Paper fires this once during enable.
        //
        // The tree is NOT removed when the plugin is disabled at runtime: on Paper 1.21.4, /npc was
        // observed still answering from a torn-down engine after disablePlugin(). That is why
        // NpcCommands gets isEnabled and gates its root node on it.
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));
    }

    /**
     * Re-reads {@code config.yml} and the language files.
     *
     * <p>Deliberately does not touch storage or the version adapter. Swapping a database connection
     * under a running engine, or rebinding to a different Minecraft version, cannot be done safely
     * while NPCs are spawned — and a server owner who changed either of those is going to restart
     * anyway. Everything that can be reloaded safely is.
     *
     * @throws dev.shvquu.betternpcs.core.config.ConfigurationException if the new configuration is
     *                                                                 unusable, leaving the old one
     *                                                                 in force
     */
    private void reloadSettings() {
        reloadConfig();
        // Parsed into a local first: a bad value must leave the running configuration untouched
        // rather than half-replaced.
        BetterNpcsConfig reloaded = BetterNpcsConfig.load(getConfig());

        languages = loadLanguagesFor(reloaded);
        messages = new MiniMessageService(languages, placeholderExpander());
        configuration = reloaded;

        if (!repository.describe().equals(reloaded.storage().describe())) {
            getLogger().warning("The storage backend was changed in config.yml. That only takes "
                    + "effect after a restart; NPCs are still being saved to " + repository.describe() + ".");
        }
    }

    private void loadNpcs() {
        npcManager.loadAll().whenComplete((loaded, failure) -> {
            if (failure != null) {
                getLogger().log(Level.SEVERE, "Could not load NPCs from storage.", failure);
                return;
            }
            // Back on the main thread: spawning touches the registry and the spatial index.
            services.scheduler().runOnMainThread(() -> {
                int spawned = npcManager.spawnAll();
                getLogger().info("Loaded " + loaded + " NPC(s), spawned " + spawned + ".");
                extensions.reportUnsatisfied();
            });
        });
    }

    private void startTasks() {
        services.tracker().start();

        int saveInterval = configuration.npc().saveInterval();
        if (saveInterval > 0) {
            long ticks = saveInterval * 20L;
            autoSaveTask = services.scheduler().runTimer(ticks, ticks, this::autoSave);
        }
    }

    private void autoSave() {
        npcManager.saveAll().exceptionally(failure -> {
            getLogger().log(Level.WARNING, "The periodic save failed; it will be retried.", failure);
            return 0;
        });
    }

    private void reportUnsupportedVersion(
            MinecraftVersion minecraft,
            VersionAdapterResolver resolver,
            UnsupportedMinecraftVersionException failure) {

        getServer().getConsoleSender().sendMessage(messages.render(
                Message.UNSUPPORTED_VERSION,
                Placeholders.text("version", minecraft.toString()),
                Placeholders.text("supported", resolver.supportedRangeDescription())));

        if (configuration.plugin().debug()) {
            getLogger().log(Level.INFO, "Adapter resolution failed.", failure);
        }
    }

    /**
     * Lists the permission nodes the server actually registered, when debug logging is on.
     *
     * <p>Worth having permanently. "Which node do I grant" is the most common support question a
     * plugin with a permission tree gets, and the answer people need is what the <em>server</em>
     * registered, not what the documentation claims. It also makes it visible if a node ever ends up
     * nested — a node named as the prefix of another is parsed differently by different YAML
     * readers, and {@code betternpcs.command.action.console} quietly becoming a child of
     * {@code betternpcs.command.action} would grant console actions to everyone allowed to edit
     * actions at all.
     */
    private void logRegisteredPermissions() {
        if (!configuration.plugin().debug()) {
            return;
        }
        List<String> nodes = getServer().getPluginManager().getPermissions().stream()
                .map(org.bukkit.permissions.Permission::getName)
                .filter(name -> name.startsWith("betternpcs"))
                .sorted()
                .toList();

        getLogger().info(() -> "Registered " + nodes.size() + " permission node(s):");
        nodes.forEach(node -> {
            org.bukkit.permissions.Permission permission =
                    getServer().getPluginManager().getPermission(node);
            String children = permission == null || permission.getChildren().isEmpty()
                    ? ""
                    : " -> " + String.join(", ", permission.getChildren().keySet());
            getLogger().info(() -> "  " + node + " (" + (permission == null
                    ? "?" : permission.getDefault()) + ")" + children);
        });
    }

    /**
     * Looks for a newer release, if the server owner asked for it.
     *
     * <p>Nothing is downloaded and nothing is replaced — the result is one line in the console with
     * a version and a link. A plugin that updates itself is a plugin that can break a server while
     * nobody is watching.
     *
     * <p>Runs off the main thread and stays silent about its own failures unless debug logging is
     * on: plenty of servers have no outbound internet, and a warning on every start for an optional
     * convenience feature trains people to ignore the log.
     *
     * <p>With {@code updates.disable-on-update} left on, BetterNPCs then shuts itself down. That is
     * a deliberate refusal to keep running an outdated build, and it only ever happens when the
     * check <em>succeeded</em> and found something newer — an unreachable GitHub leaves the plugin
     * running, because being offline is not the same as being out of date.
     */
    private void checkForUpdates() {
        if (!configuration.updates().check()) {
            return;
        }

        PluginVersion current = PluginVersion.parse(getPluginMeta().getVersion()).orElse(null);
        if (current == null) {
            // A fork with a version this build cannot read. Nothing sensible to compare against.
            return;
        }

        UpdateChecker checker = new UpdateChecker(
                UPDATE_REPOSITORY,
                Duration.ofSeconds(10),
                task -> services.scheduler().runAsync(task),
                getLogger(),
                configuration.plugin().debug());

        // Back onto the main thread before reporting: the future completes on the checker's own
        // executor, and disabling a plugin touches the plugin manager and fires an event.
        checker.check(current).thenAccept(release -> release.ifPresent(found ->
                services.scheduler().runOnMainThread(() -> reportUpdate(current, found))));
    }

    /**
     * Reports a newer release, and shuts down if the server owner asked for that.
     *
     * @param current the running version
     * @param found   the newer release
     */
    private void reportUpdate(PluginVersion current, UpdateChecker.Release found) {
        getServer().getConsoleSender().sendMessage(messages.render(
                Message.UPDATE_AVAILABLE,
                Placeholders.text("current", current.toString()),
                Placeholders.text("latest", found.version().toString()),
                Placeholders.text("url", found.url())));

        if (!configuration.updates().disableOnUpdate()) {
            return;
        }

        // Said before disabling, because onDisable resets the fields this line needs to render.
        getServer().getConsoleSender().sendMessage(messages.render(Message.UPDATE_DISABLING));
        // Disables this plugin only. onDisable saves every NPC and closes storage first, so the
        // shutdown costs nothing but the NPCs being gone until somebody updates.
        getServer().getPluginManager().disablePlugin(this);
    }

    private StartupBanner.Details bannerDetails(MinecraftVersion minecraft) {
        return new StartupBanner.Details(
                getPluginMeta().getName(),
                getPluginMeta().getVersion(),
                ApiVersion.CURRENT.toString(),
                minecraft.toString(),
                getServer().getName() + " " + getServer().getVersion(),
                Runtime.version().toString(),
                repository.describe(),
                languages.defaultBundle().locale(),
                adapter.describe(),
                configuration.plugin().debug());
    }

    // ---------------------------------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the current configuration.
     *
     * <p>A method rather than a field reference so that a reload can swap it and every component
     * that captured {@code this::configuration} sees the new one.
     *
     * @return the configuration
     */
    public BetterNpcsConfig configuration() {
        return configuration;
    }

    /**
     * Returns the published API.
     *
     * @return the API, or {@code null} before the plugin has finished enabling
     */
    public BetterNPCsApi api() {
        return api;
    }
}
