package dev.shvquu.betternpcs.core.engine;

import dev.shvquu.betternpcs.core.action.ActionExecutor;
import dev.shvquu.betternpcs.core.action.BuiltinActionHandlers;
import dev.shvquu.betternpcs.core.action.DefaultActionRegistry;
import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import dev.shvquu.betternpcs.core.i18n.LanguageManager;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.i18n.MiniMessageService;
import dev.shvquu.betternpcs.core.i18n.PlaceholderExpander;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import dev.shvquu.betternpcs.core.npc.EngineServices;
import dev.shvquu.betternpcs.core.npc.NpcRegistry;
import dev.shvquu.betternpcs.core.npc.NpcRenderService;
import dev.shvquu.betternpcs.core.npc.NpcSpatialIndex;
import dev.shvquu.betternpcs.core.npc.NpcTracker;
import dev.shvquu.betternpcs.core.npc.VisibilityService;
import dev.shvquu.betternpcs.core.skin.DefaultSkinService;
import dev.shvquu.betternpcs.core.skin.SkinCache;
import dev.shvquu.betternpcs.core.storage.InMemoryNpcRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Wires a complete engine against test doubles.
 *
 * <p>Everything the plugin assembles at startup, minus the plugin: a recording adapter instead of
 * NMS, an in-memory repository instead of a database, an inline scheduler instead of Bukkit's, and a
 * dispatcher a test can listen on. The engine itself is the real thing.
 *
 * <p>Online players are supplied by the caller rather than read from a server, so a test controls
 * exactly who the tracker considers.
 */
public final class EngineHarness {

    /** Records what the engine tried to send. */
    public final RecordingAdapter adapter = new RecordingAdapter();

    /** Runs scheduled work under the test's control. */
    public final DirectScheduler scheduler = new DirectScheduler();

    /** Records the events the engine fires. */
    public final RecordingEventDispatcher events = new RecordingEventDispatcher();

    /** Holds NPCs without a database. */
    public final InMemoryNpcRepository repository = new InMemoryNpcRepository();

    /** The action handlers, with the built-ins registered. */
    public final DefaultActionRegistry actions = new DefaultActionRegistry();

    /** The NPC index. */
    public final NpcRegistry registry = new NpcRegistry();

    /** The spatial grid. */
    public final NpcSpatialIndex index = new NpcSpatialIndex();

    /** Decides who sees what. */
    public final VisibilityService visibility;

    /** Turns NPC state into adapter calls. */
    public final NpcRenderService renderer;

    /** The visibility pass. */
    public final NpcTracker tracker;

    /** The manager under test. */
    public final DefaultNpcManager manager;

    /** The wired services, for constructing handles directly. */
    public final EngineServices services;

    /** Renders text. */
    public final MessageService messages;

    private BetterNpcsConfig config = BetterNpcsConfig.defaults();
    private Collection<? extends Player> onlinePlayers = List.of();

    /**
     * Creates a harness.
     *
     * @param languagesFolder a temporary folder for the language files
     * @throws IOException if the language folder cannot be prepared
     */
    public EngineHarness(Path languagesFolder) throws IOException {
        Logger logger = Logger.getLogger("BetterNPCsTest");
        // Warnings are expected in several tests — a skipped action, an unloadable NPC — and letting
        // them print would bury the actual assertion failures in noise.
        logger.setLevel(Level.OFF);

        LanguageManager languages = new LanguageManager(languagesFolder, resource -> null, logger);
        languages.load("en_US", false);
        this.messages = new MiniMessageService(languages, PlaceholderExpander.none());

        BuiltinActionHandlers.registerAll(actions);

        this.visibility = new VisibilityService(this::config);
        this.renderer = new NpcRenderService(adapter, messages, scheduler);
        this.tracker = new NpcTracker(
                registry, index, visibility, renderer, scheduler, this::config, this::onlinePlayers);

        this.services = new EngineServices(
                registry,
                index,
                renderer,
                visibility,
                tracker,
                repository,
                scheduler,
                events,
                actions,
                new ActionExecutor(actions, messages, logger),
                new DefaultSkinService(new SkinCache(Duration.ofHours(1)), logger),
                messages,
                this::config,
                logger);

        this.manager = new DefaultNpcManager(services);
        adapter.enable(new TestAdapterContext(logger));
    }

    /**
     * Returns the configuration the engine reads.
     *
     * @return the current configuration
     */
    public BetterNpcsConfig config() {
        return config;
    }

    /**
     * Replaces the configuration, as a reload would.
     *
     * @param newConfig the configuration to use from now on
     */
    public void config(BetterNpcsConfig newConfig) {
        this.config = newConfig;
    }

    /**
     * Returns who the tracker considers online.
     *
     * @return the online players
     */
    public Collection<? extends Player> onlinePlayers() {
        return onlinePlayers;
    }

    /**
     * Sets who the tracker considers online.
     *
     * @param players the online players
     */
    public void onlinePlayers(Collection<? extends Player> players) {
        this.onlinePlayers = players;
    }

    /**
     * Runs one tracker pass.
     */
    public void tick() {
        tracker.tick();
    }

    /**
     * Runs one tracker pass and then any work it scheduled for the next tick.
     */
    public void tickAndSettle() {
        tracker.tick();
        scheduler.runDelayed();
    }

    private record TestAdapterContext(Logger logger) implements dev.shvquu.betternpcs.core.render.AdapterContext {

        @Override
        public org.bukkit.plugin.Plugin plugin() {
            throw new UnsupportedOperationException(
                    "The recording adapter never needs a plugin instance");
        }

        @Override
        public dev.shvquu.betternpcs.core.render.NpcInteractionSink interactions() {
            return (player, entityId, interaction, hand, clickedAt) -> {
            };
        }

        @Override
        public boolean debug() {
            return false;
        }
    }
}
