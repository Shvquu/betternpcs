package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.api.skin.SkinService;
import dev.shvquu.betternpcs.core.action.ActionExecutor;
import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import dev.shvquu.betternpcs.core.engine.EventDispatcher;
import dev.shvquu.betternpcs.core.engine.Scheduler;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.storage.NpcRepository;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The collaborators an {@link NpcHandle} and the manager need.
 *
 * <p>Passed as one object rather than a dozen constructor arguments. That is not only tidiness: an
 * NPC handle is created for every NPC on the server, and threading fourteen references through each
 * construction is how a constructor signature becomes something nobody wants to change.
 *
 * <p>Deliberately a plain value with no behaviour of its own. Anything that would want a method here
 * belongs on one of the services it holds — a container that starts making decisions is the god
 * class this design exists to avoid.
 *
 * @param registry       the NPC index
 * @param index          the spatial grid of spawned NPCs
 * @param renderer       turns NPC state into adapter calls
 * @param visibility     decides who sees what
 * @param tracker        the periodic visibility pass
 * @param repository     where NPCs are persisted
 * @param scheduler      the server's task scheduler
 * @param events         fires the public events
 * @param actions        the registry of action handlers
 * @param actionExecutor runs action chains
 * @param skins          resolves skins
 * @param messages       renders text
 * @param config         supplies the current configuration
 * @param logger         where the engine logs
 * @since 1.0.0
 */
public record EngineServices(
        NpcRegistry registry,
        NpcSpatialIndex index,
        NpcRenderService renderer,
        VisibilityService visibility,
        NpcTracker tracker,
        NpcRepository repository,
        Scheduler scheduler,
        EventDispatcher events,
        ActionRegistry actions,
        ActionExecutor actionExecutor,
        SkinService skins,
        MessageService messages,
        Supplier<BetterNpcsConfig> config,
        Logger logger) {

    /**
     * Creates the container.
     *
     * @param registry       the NPC index
     * @param index          the spatial grid
     * @param renderer       the render service
     * @param visibility     the visibility service
     * @param tracker        the tracker
     * @param repository     the repository
     * @param scheduler      the scheduler
     * @param events         the event dispatcher
     * @param actions        the action registry
     * @param actionExecutor the action executor
     * @param skins          the skin service
     * @param messages       the message service
     * @param config         the configuration supplier
     * @param logger         the logger
     * @throws NullPointerException if any argument is {@code null}
     */
    public EngineServices {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(tracker, "tracker");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(actionExecutor, "actionExecutor");
        Objects.requireNonNull(skins, "skins");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");
    }
}
