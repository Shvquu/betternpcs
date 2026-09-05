package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.NametagSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.engine.Scheduler;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.render.NpcView;
import dev.shvquu.betternpcs.core.render.TextView;
import dev.shvquu.betternpcs.core.version.VersionAdapter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Turns NPC state into adapter calls.
 *
 * <p>Everything that decides <em>what</em> to send lives here; the adapters decide only <em>how</em>
 * to encode it for their Minecraft version. Keeping the split at exactly this line is what makes six
 * adapters maintainable — nametag arithmetic, line ordering and per-viewer text rendering are done
 * once here rather than six times.
 *
 * <p>Called on the main server thread.
 *
 * @since 1.0.0
 */
public final class NpcRenderService {

    /**
     * How far above an NPC's feet its nametag sits, by type.
     *
     * <p>Approximate, and deliberately so: the exact value depends on the entity's hit box, which
     * the server only knows for an entity that exists, and packet NPCs do not exist. These cover the
     * types people actually use; anything else gets the player height and can be adjusted with the
     * nametag's own vertical offset.
     */
    private static final Map<EntityType, Double> NAMETAG_HEIGHTS = Map.ofEntries(
            Map.entry(EntityType.PLAYER, 2.05),
            Map.entry(EntityType.VILLAGER, 2.05),
            Map.entry(EntityType.ZOMBIE, 2.05),
            Map.entry(EntityType.SKELETON, 2.05),
            Map.entry(EntityType.CREEPER, 1.95),
            Map.entry(EntityType.ENDERMAN, 3.10),
            Map.entry(EntityType.IRON_GOLEM, 2.90),
            Map.entry(EntityType.COW, 1.60),
            Map.entry(EntityType.PIG, 1.20),
            Map.entry(EntityType.SHEEP, 1.60),
            Map.entry(EntityType.CHICKEN, 0.95),
            Map.entry(EntityType.WOLF, 1.15),
            Map.entry(EntityType.CAT, 0.80),
            Map.entry(EntityType.ARMOR_STAND, 2.05));

    private static final double DEFAULT_NAMETAG_HEIGHT = 2.05;

    private final VersionAdapter adapter;
    private final MessageService messages;
    private final Scheduler scheduler;

    /**
     * Creates the service.
     *
     * @param adapter   where packets are sent
     * @param messages  used to render per-viewer text
     * @param scheduler used for the one-tick delay before a tab list entry is withdrawn
     * @throws NullPointerException if any argument is {@code null}
     */
    public NpcRenderService(VersionAdapter adapter, MessageService messages, Scheduler scheduler) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Returns the adapter this service renders through.
     *
     * @return the version adapter
     */
    public VersionAdapter adapter() {
        return adapter;
    }

    /**
     * Allocates the entity ids an NPC needs to be rendered.
     *
     * <p>Called on spawn. The count depends on how many text lines the NPC has, so a change to its
     * nametag or hologram while spawned needs the ids reallocated — see
     * {@link #reallocateTextIds(NpcHandle)}.
     *
     * @param npc the NPC being spawned
     */
    public void allocateIds(NpcHandle npc) {
        NpcRenderState state = npc.renderState();
        state.entityId(adapter.allocateEntityId());
        state.entityUuid(UUID.randomUUID());
        state.profileName(ProfileNames.forNpc(npc.name(), npc.uniqueId()));
        reallocateTextIds(npc);
    }

    /**
     * Allocates entity ids for the NPC's text lines, releasing any it had.
     *
     * @param npc the NPC whose text lines changed
     */
    public void reallocateTextIds(NpcHandle npc) {
        NpcRenderState state = npc.renderState();
        state.nametagIds(allocate(npc.nametag().visible() ? 1 : 0));
        state.hologramIds(allocate(npc.hologram().lines().size()));
    }

    private int[] allocate(int count) {
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = adapter.allocateEntityId();
        }
        return ids;
    }

    /**
     * Renders an NPC for viewers who cannot currently see it.
     *
     * @param npc     the NPC to show
     * @param viewers who to show it to
     */
    public void show(NpcHandle npc, Collection<? extends Player> viewers) {
        if (viewers.isEmpty()) {
            return;
        }
        NpcView view = view(npc);
        adapter.spawnNpc(view, viewers);
        adapter.updateEquipment(view, viewers);

        if (view.isPlayer() && !npc.appearance().listedInTablist()) {
            // A tick later, not now: the client resolves the NPC's skin from its tab list entry, and
            // withdrawing the entry in the same tick races that lookup. The NPC then renders as the
            // default skin, intermittently, which is close to impossible to diagnose from a report.
            List<Player> stillViewing = List.copyOf(viewers);
            scheduler.runLater(1, () -> {
                if (npc.isSpawned()) {
                    adapter.hideFromTabList(view, stillViewing);
                }
            });
        }

        viewers.forEach(viewer -> sendText(npc, viewer, true));
    }

    /**
     * Stops rendering an NPC for viewers.
     *
     * @param npc     the NPC to hide
     * @param viewers who to hide it from
     */
    public void hide(NpcHandle npc, Collection<? extends Player> viewers) {
        if (viewers.isEmpty()) {
            return;
        }
        adapter.removeEntities(npc.renderState().allEntityIds(), viewers);
    }

    /**
     * Sends the NPC's state flags to everyone who can see it.
     *
     * @param npc the NPC whose appearance changed
     */
    public void updateMetadata(NpcHandle npc) {
        Collection<Player> viewers = npc.renderState().viewers();
        if (!viewers.isEmpty()) {
            adapter.updateMetadata(view(npc), viewers);
        }
    }

    /**
     * Sends the NPC's equipment to everyone who can see it.
     *
     * @param npc the NPC whose equipment changed
     */
    public void updateEquipment(NpcHandle npc) {
        Collection<Player> viewers = npc.renderState().viewers();
        if (!viewers.isEmpty()) {
            adapter.updateEquipment(view(npc), viewers);
        }
    }

    /**
     * Moves the NPC and its text lines for everyone who can see it.
     *
     * @param npc the NPC that moved
     */
    public void teleport(NpcHandle npc) {
        List<Player> viewers = npc.renderState().viewerSnapshot();
        if (viewers.isEmpty()) {
            return;
        }
        adapter.teleport(view(npc), viewers);
        // Text lines are separate entities and do not follow the NPC on their own.
        viewers.forEach(viewer -> sendText(npc, viewer, false));
    }

    /**
     * Turns the NPC for one viewer.
     *
     * <p>Skips the packet when the rotation has not changed since the last one sent to that viewer,
     * which is the case on most look-tracking passes.
     *
     * @param npc    the NPC to turn
     * @param viewer who to turn it for
     * @param yaw    the yaw in degrees
     * @param pitch  the pitch in degrees
     * @return {@code true} if a packet was sent
     */
    public boolean rotate(NpcHandle npc, Player viewer, float yaw, float pitch) {
        NpcRenderState state = npc.renderState();
        Float previous = state.lastSentYaw(viewer);
        if (previous != null && Math.abs(previous - yaw) < 1.0f) {
            // Below a degree the client cannot show the difference anyway: rotations go on the wire
            // as a single byte, so 256 distinct values across the whole circle.
            return false;
        }
        state.lastSentYaw(viewer, yaw);
        adapter.rotate(state.entityId(), yaw, pitch, npc.look().headOnly(), List.of(viewer));
        return true;
    }

    /**
     * Plays an animation for the given viewers.
     *
     * @param npc       the NPC to animate
     * @param animation what to play
     * @param viewers   who should see it
     */
    public void playAnimation(
            NpcHandle npc, NpcAnimation animation, Collection<? extends Player> viewers) {
        if (!viewers.isEmpty()) {
            adapter.playAnimation(view(npc), animation, viewers);
        }
    }

    /**
     * Re-renders the NPC's text lines for everyone who can see it.
     *
     * <p>Needed when the text itself changed, and periodically for text containing placeholders that
     * change on their own.
     *
     * @param npc the NPC whose text to refresh
     */
    public void refreshText(NpcHandle npc) {
        npc.renderState().viewerSnapshot().forEach(viewer -> sendText(npc, viewer, false));
    }

    /**
     * Sends the NPC's nametag and hologram lines to one viewer.
     *
     * @param npc     the NPC
     * @param viewer  who to send them to
     * @param initial {@code true} to spawn the lines, {@code false} to update ones already sent
     */
    public void sendText(NpcHandle npc, Player viewer, boolean initial) {
        NpcRenderState state = npc.renderState();
        NametagSettings nametag = npc.nametag();
        HologramSettings hologram = npc.hologram();

        double base = nametagHeight(npc.type());
        NpcPosition position = npc.position();

        int[] nametagIds = state.nametagIds();
        double nextY = base + nametag.verticalOffset();

        if (nametagIds.length > 0 && nametag.visible() && maySeeNametag(nametag, viewer)) {
            TextView view = new TextView(
                    nametagIds[0],
                    UUID.nameUUIDFromBytes(("nametag" + nametagIds[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    position.withCoordinates(position.x(), position.y() + nextY, position.z()),
                    renderNametag(npc, nametag, viewer),
                    nametag.style());
            emit(view, viewer, initial);
        }

        int[] hologramIds = state.hologramIds();
        List<String> lines = hologram.lines();
        double lineHeight = hologram.style().lineHeight();
        double hologramBase = nextY + hologram.verticalOffset() + lineHeight;

        // Lines are configured top to bottom, so the last one sits lowest.
        for (int i = 0; i < hologramIds.length && i < lines.size(); i++) {
            int fromBottom = lines.size() - 1 - i;
            double y = hologramBase + fromBottom * lineHeight;

            TextView view = new TextView(
                    hologramIds[i],
                    UUID.nameUUIDFromBytes(("hologram" + hologramIds[i]).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    position.withCoordinates(position.x(), position.y() + y, position.z()),
                    messages.renderRaw(viewer, lines.get(i), NpcPlaceholders.tags(npc, viewer)),
                    hologram.style());
            emit(view, viewer, initial);
        }
    }

    private void emit(TextView view, Player viewer, boolean initial) {
        if (initial) {
            adapter.spawnText(view, viewer);
        } else {
            adapter.updateText(view, viewer);
        }
    }

    private static boolean maySeeNametag(NametagSettings nametag, Player viewer) {
        return nametag.permission().map(viewer::hasPermission).orElse(true);
    }

    private Component renderNametag(NpcHandle npc, NametagSettings nametag, Player viewer) {
        Component displayName = displayName(npc, viewer);
        String template = nametag.text().orElse(NametagSettings.NAME_PLACEHOLDER);

        // The component resolver is listed first so that it shadows the plain-text <npc_name> that
        // NpcPlaceholders also provides. In a nametag the display name is meant to keep its own
        // formatting; in a command argument it is meant not to.
        return messages.renderRaw(
                viewer,
                template,
                TagResolver.resolver(
                        Placeholders.component("npc_name", displayName),
                        NpcPlaceholders.tags(npc, viewer)));
    }

    /**
     * Renders an NPC's display name for one viewer.
     *
     * @param npc    the NPC
     * @param viewer who the name is for, may be {@code null}
     * @return the rendered name, falling back to the NPC's plain name
     */
    public Component displayName(NpcHandle npc, Player viewer) {
        return npc.displayName()
                .map(template -> messages.renderRaw(viewer, template, NpcPlaceholders.tags(npc, viewer)))
                .orElseGet(() -> Component.text(npc.name()));
    }

    /**
     * Builds the frozen view an adapter renders from.
     *
     * @param npc the NPC to describe
     * @return the view
     */
    public NpcView view(NpcHandle npc) {
        NpcRenderState state = npc.renderState();
        return new NpcView(
                state.entityId(),
                state.entityUuid(),
                state.profileName(),
                npc.type(),
                npc.position(),
                npc.skin().orElse(null),
                npc.appearance(),
                npc.equipment(),
                displayName(npc, null));
    }

    private static double nametagHeight(NpcType type) {
        return NAMETAG_HEIGHTS.getOrDefault(type.entityType(), DEFAULT_NAMETAG_HEIGHT);
    }
}
