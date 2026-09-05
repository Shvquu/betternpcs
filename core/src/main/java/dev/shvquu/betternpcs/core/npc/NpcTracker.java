package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.npc.property.LookMode;
import dev.shvquu.betternpcs.api.npc.property.LookSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import dev.shvquu.betternpcs.core.engine.Scheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The periodic pass that decides who currently sees which NPC, and turns the ones that follow
 * players.
 *
 * <p>This is where a plugin with thousands of NPCs is won or lost, so it is worth being explicit
 * about the shape of the work. A pass costs, per online player, one grid lookup plus an exact check
 * against the handful of NPCs the grid returned — not a walk of every NPC on the server. What is
 * left after that filter is small enough that the per-pairing checks in {@link VisibilityService}
 * can afford to be thorough.
 *
 * <p>Rotation updates piggyback on the same pass rather than running their own task, because the set
 * of NPCs that need one is exactly the set this pass has already gathered.
 *
 * <p>Runs on the main server thread.
 *
 * @since 1.0.0
 */
public final class NpcTracker {

    /**
     * How many passes go by before the widest configured view distance is recomputed.
     *
     * <p>That figure bounds the grid lookup, so it has to be right — but it changes only when an
     * administrator edits an NPC, and recomputing it on every pass would put an O(NPCs) scan back
     * into the hot path this class exists to remove.
     */
    private static final int VIEW_DISTANCE_REFRESH_PASSES = 20;

    private final NpcRegistry registry;
    private final NpcSpatialIndex index;
    private final VisibilityService visibility;
    private final NpcRenderService renderer;
    private final Scheduler scheduler;
    private final Supplier<BetterNpcsConfig> config;
    private final Supplier<Collection<? extends Player>> onlinePlayers;

    private final Map<UUID, Set<NpcHandle>> viewedByPlayer = new LinkedHashMap<>();

    private Scheduler.Task task;
    private long passes;
    private double widestViewDistance;

    /**
     * Creates the tracker.
     *
     * @param registry      every registered NPC, used to recompute the widest view distance
     * @param index         the grid of spawned NPCs
     * @param visibility    decides individual pairings
     * @param renderer      sends the resulting packets
     * @param scheduler     runs the pass
     * @param config        supplies the current configuration
     * @param onlinePlayers supplies the players to consider
     * @throws NullPointerException if any argument is {@code null}
     */
    public NpcTracker(
            NpcRegistry registry,
            NpcSpatialIndex index,
            VisibilityService visibility,
            NpcRenderService renderer,
            Scheduler scheduler,
            Supplier<BetterNpcsConfig> config,
            Supplier<Collection<? extends Player>> onlinePlayers) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.index = Objects.requireNonNull(index, "index");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
    }

    /**
     * Starts the periodic pass.
     *
     * <p>Safe to call again after {@link #stop()}; calling it while already running does nothing.
     */
    public void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        int interval = config.get().npc().trackerInterval();
        refreshWidestViewDistance();
        task = scheduler.runTimer(interval, interval, this::tick);
    }

    /**
     * Stops the periodic pass.
     *
     * <p>Leaves NPCs rendered: this is called during a reload as well as a shutdown, and removing
     * every NPC from every client only to send them all back a moment later would be a visible
     * flicker for no reason.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Returns whether the pass is running.
     *
     * @return {@code true} if started and not stopped
     */
    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    /**
     * Runs one visibility and rotation pass.
     *
     * <p>Public so that tests can drive it directly instead of waiting for a scheduler.
     */
    public void tick() {
        if (passes % VIEW_DISTANCE_REFRESH_PASSES == 0) {
            refreshWidestViewDistance();
        }
        passes++;

        int maxTracked = config.get().npc().maxTrackedPerPlayer();

        for (Player player : onlinePlayers.get()) {
            updatePlayer(player, maxTracked);
        }
    }

    private void updatePlayer(Player player, int maxTracked) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return;
        }

        Set<NpcHandle> previouslyViewed =
                viewedByPlayer.computeIfAbsent(player.getUniqueId(), key -> new LinkedHashSet<>());

        Collection<NpcHandle> candidates = index.near(
                location.getWorld().getName(), location.getX(), location.getZ(), widestViewDistance);

        Set<NpcHandle> nowVisible = selectVisible(player, candidates, location, maxTracked);

        // Hide first, then show. A player at the tracking limit who steps towards a new NPC should
        // give up the far one before being sent the near one, not briefly exceed the limit.
        List<Player> asList = List.of(player);
        for (NpcHandle npc : new ArrayList<>(previouslyViewed)) {
            if (!nowVisible.contains(npc)) {
                renderer.hide(npc, asList);
                npc.renderState().removeViewer(player);
                previouslyViewed.remove(npc);
            }
        }

        for (NpcHandle npc : nowVisible) {
            if (previouslyViewed.add(npc)) {
                npc.renderState().addViewer(player);
                renderer.show(npc, asList);
            }
        }

        updateRotations(player, nowVisible, location);
    }

    private Set<NpcHandle> selectVisible(
            Player player, Collection<NpcHandle> candidates, Location location, int maxTracked) {

        List<NpcHandle> visible = new ArrayList<>(Math.min(candidates.size(), maxTracked));
        for (NpcHandle npc : candidates) {
            if (visibility.canSee(npc, player)) {
                visible.add(npc);
            }
        }

        if (visible.size() > maxTracked) {
            // A player standing in a hub full of NPCs. Keeping the nearest is the only ordering that
            // does not look arbitrary from where they are standing.
            visible.sort(Comparator.comparingDouble(npc -> npc.position().distanceSquaredTo(location)));
            visible = visible.subList(0, maxTracked);
        }
        return new LinkedHashSet<>(visible);
    }

    private void updateRotations(Player player, Set<NpcHandle> visible, Location location) {
        for (NpcHandle npc : visible) {
            LookSettings look = npc.look();
            if (!look.isActive()) {
                continue;
            }
            if (npc.position().distanceSquaredTo(location) > look.range() * look.range()) {
                continue;
            }
            if (look.mode() == LookMode.LOOK_AT_VIEWER) {
                float[] rotation = rotationTowards(npc.position(), location);
                renderer.rotate(npc, player, rotation[0], rotation[1]);
            }
        }

        // The nearest-player mode is computed per NPC rather than per player, so it is handled once
        // per pass after every player has been walked.
        updateNearestPlayerLooks(visible);
    }

    private void updateNearestPlayerLooks(Set<NpcHandle> visible) {
        for (NpcHandle npc : visible) {
            LookSettings look = npc.look();
            if (look.mode() != LookMode.LOOK_AT_NEAREST_PLAYER) {
                continue;
            }
            List<Player> viewers = npc.renderState().viewerSnapshot();
            if (viewers.isEmpty()) {
                continue;
            }

            Player nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Player viewer : viewers) {
                double distance = npc.position().distanceSquaredTo(viewer.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = viewer;
                }
            }
            if (nearest == null || nearestDistance > look.range() * look.range()) {
                continue;
            }

            float[] rotation = rotationTowards(npc.position(), nearest.getLocation());
            for (Player viewer : viewers) {
                renderer.rotate(npc, viewer, rotation[0], rotation[1]);
            }
        }
    }

    /**
     * Returns the yaw and pitch that make an NPC face a point.
     *
     * @param from  where the NPC stands
     * @param to    what to face
     * @return a two-element array of yaw and pitch, in degrees
     */
    public static float[] rotationTowards(NpcPosition from, Location to) {
        double deltaX = to.getX() - from.x();
        // Eye height rather than feet: an NPC looking at a player's boots reads as looking past them.
        double deltaY = (to.getY() + 1.62) - (from.y() + 1.62);
        double deltaZ = to.getZ() - from.z();

        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float pitch = (float) Math.toDegrees(-Math.atan2(deltaY, horizontal));

        return new float[] {yaw, Math.clamp(pitch, -90.0f, 90.0f)};
    }

    /**
     * Forgets a player who has left.
     *
     * <p>Their client is gone, so nothing is sent; this only drops the bookkeeping that would
     * otherwise keep the {@link Player} object reachable for the lifetime of the server.
     *
     * @param player the player who quit
     * @throws NullPointerException if {@code player} is {@code null}
     */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        Set<NpcHandle> viewed = viewedByPlayer.remove(player.getUniqueId());
        if (viewed != null) {
            viewed.forEach(npc -> npc.renderState().forget(player));
        }
    }

    /**
     * Forgets an NPC that is being despawned or deleted.
     *
     * <p>Only drops the bookkeeping. Removing it from the clients that can see it is the caller's
     * job, because a despawn and a deletion send different things.
     *
     * @param npc the NPC to forget
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public void forgetNpc(NpcHandle npc) {
        Objects.requireNonNull(npc, "npc");
        viewedByPlayer.values().forEach(viewed -> viewed.remove(npc));
    }

    /**
     * Re-evaluates an NPC for everyone on the next pass.
     *
     * <p>Hides it from its current viewers and forgets it, so the next pass decides afresh. Needed
     * after the state a custom visibility rule depends on has changed, which the engine cannot
     * observe on its own.
     *
     * @param npc the NPC to re-evaluate
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public void refresh(NpcHandle npc) {
        Objects.requireNonNull(npc, "npc");
        List<Player> viewers = npc.renderState().viewerSnapshot();
        if (!viewers.isEmpty()) {
            renderer.hide(npc, viewers);
            viewers.forEach(npc.renderState()::removeViewer);
        }
        forgetNpc(npc);
    }

    /**
     * Forgets every player, without sending anything.
     *
     * <p>Used by reload, which rebuilds the registry and therefore invalidates every handle the
     * tracker is holding.
     */
    public void reset() {
        viewedByPlayer.clear();
        passes = 0;
    }

    /**
     * Returns the NPCs a player is currently being sent.
     *
     * @param player the player
     * @return an immutable snapshot
     * @throws NullPointerException if {@code player} is {@code null}
     */
    public Collection<NpcHandle> viewedBy(Player player) {
        Objects.requireNonNull(player, "player");
        Set<NpcHandle> viewed = viewedByPlayer.get(player.getUniqueId());
        return viewed == null ? List.of() : List.copyOf(viewed);
    }

    private void refreshWidestViewDistance() {
        double widest = config.get().npc().defaultViewDistance();
        for (NpcHandle npc : registry.all()) {
            widest = Math.max(widest, visibility.viewDistance(npc.visibility()));
        }
        widestViewDistance = widest;
    }

    /**
     * Returns the radius the grid is queried with.
     *
     * @return the widest configured view distance, in blocks
     */
    public double widestViewDistance() {
        return widestViewDistance;
    }
}
