package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.api.event.NpcDespawnEvent;
import dev.shvquu.betternpcs.api.event.NpcSaveEvent;
import dev.shvquu.betternpcs.api.event.NpcSpawnEvent;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcState;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.LookSettings;
import dev.shvquu.betternpcs.api.npc.property.NametagSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcEquipment;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The engine's {@link Npc} implementation.
 *
 * <p>Every setter follows the same three steps: refuse if the handle is dead, change the field, then
 * do the smallest thing that makes clients agree with it. "Smallest" is the interesting part — a
 * change of appearance is one metadata packet, a change of position is one teleport, but a change of
 * entity type or skin is a full respawn, because the client cannot be told either of those about an
 * entity it already knows.
 *
 * <p>Setters mark the NPC dirty rather than writing to storage. A script that moves an NPC every
 * tick would otherwise be a database write every tick.
 *
 * <p>Not thread safe. Every method must be called on the main server thread, which the API states.
 *
 * @since 1.0.0
 */
public final class NpcHandle implements Npc {

    private final UUID uniqueId;
    private final EngineServices services;
    private final NpcRenderState renderState = new NpcRenderState();

    private String name;
    private NpcType type;
    private NpcPosition position;
    private String displayName;
    private SkinSource skinSource;
    private NpcSkin skin;
    private NpcEquipment equipment;
    private NpcAppearance appearance;
    private NpcVisibility visibility;
    private LookSettings look;
    private NametagSettings nametag;
    private HologramSettings hologram;
    private final Map<InteractionType, List<ActionDefinition>> actions = new EnumMap<>(InteractionType.class);
    private final Map<String, String> metadata = new TreeMap<>();
    private boolean spawnByDefault;

    private NpcState state;
    private boolean dirty;

    /**
     * Creates a handle from stored or freshly built state.
     *
     * @param snapshot the state to start from
     * @param services the engine's collaborators
     * @param state    the lifecycle state to start in; {@link NpcState#CREATED} for a new NPC,
     *                 {@link NpcState#DESPAWNED} for one loaded from storage
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code state} is not a state an NPC can start in
     */
    public NpcHandle(NpcSnapshot snapshot, EngineServices services, NpcState state) {
        Objects.requireNonNull(snapshot, "snapshot");
        this.services = Objects.requireNonNull(services, "services");
        this.state = Objects.requireNonNull(state, "state");

        if (state != NpcState.CREATED && state != NpcState.DESPAWNED) {
            throw new IllegalArgumentException("An NPC cannot start in the state " + state);
        }

        this.uniqueId = snapshot.uniqueId();
        applyInternal(snapshot);
        this.dirty = state == NpcState.CREATED;
    }

    // ---------------------------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------------------------

    @Override
    public UUID uniqueId() {
        return uniqueId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void rename(String newName) {
        checkAlive();
        Objects.requireNonNull(newName, "name");
        String trimmed = newName.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("An NPC name must not be blank");
        }
        if (trimmed.equals(name)) {
            return;
        }

        // The registry owns the name index, so it decides whether the new name is free. Doing the
        // check there rather than here means there is no window in which a lookup finds neither name.
        services.registry().rename(this, trimmed);
        this.name = trimmed;
        markDirty();

        if (isSpawned()) {
            // The profile name is derived from the NPC name, and a client cannot be told a new
            // profile for an entity it already knows about.
            respawnForViewers();
        }
    }

    @Override
    public NpcType type() {
        return type;
    }

    @Override
    public void setType(NpcType newType) {
        checkAlive();
        Objects.requireNonNull(newType, "type");
        if (newType.equals(type)) {
            return;
        }
        this.type = newType;
        markDirty();

        if (isSpawned()) {
            respawnForViewers();
        }
    }

    @Override
    public NpcState state() {
        return state;
    }

    // ---------------------------------------------------------------------------------------------
    // Appearance and text
    // ---------------------------------------------------------------------------------------------

    @Override
    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    @Override
    public void setDisplayName(String miniMessage) {
        checkAlive();
        this.displayName = miniMessage == null || miniMessage.isBlank() ? null : miniMessage;
        markDirty();
        services.renderer().refreshText(this);
    }

    @Override
    public void setDisplayName(Component component) {
        // Serialised back to MiniMessage rather than stored as a component, because the display name
        // is persisted and re-rendered per viewer. Documented on the API as lossy for anything
        // MiniMessage cannot express.
        setDisplayName(component == null ? null : MiniMessage.miniMessage().serialize(component));
    }

    @Override
    public NpcAppearance appearance() {
        return appearance;
    }

    @Override
    public void setAppearance(NpcAppearance newAppearance) {
        checkAlive();
        Objects.requireNonNull(newAppearance, "appearance");
        if (newAppearance.equals(appearance)) {
            return;
        }
        boolean tablistChanged = newAppearance.listedInTablist() != appearance.listedInTablist();
        this.appearance = newAppearance;
        markDirty();

        if (isSpawned()) {
            if (tablistChanged) {
                // Whether the NPC is listed is decided as part of the spawn sequence, so changing it
                // means running that sequence again.
                respawnForViewers();
            } else {
                services.renderer().updateMetadata(this);
            }
        }
    }

    @Override
    public NametagSettings nametag() {
        return nametag;
    }

    @Override
    public void setNametag(NametagSettings newNametag) {
        checkAlive();
        Objects.requireNonNull(newNametag, "nametag");
        if (newNametag.equals(nametag)) {
            return;
        }
        boolean lineCountChanged = newNametag.visible() != nametag.visible();
        this.nametag = newNametag;
        markDirty();
        refreshTextEntities(lineCountChanged);
    }

    @Override
    public HologramSettings hologram() {
        return hologram;
    }

    @Override
    public void setHologram(HologramSettings newHologram) {
        checkAlive();
        Objects.requireNonNull(newHologram, "hologram");
        if (newHologram.equals(hologram)) {
            return;
        }
        boolean lineCountChanged = newHologram.lines().size() != hologram.lines().size();
        this.hologram = newHologram;
        markDirty();
        refreshTextEntities(lineCountChanged);
    }

    /**
     * Re-sends the NPC's text, respawning the line entities when their number changed.
     *
     * @param lineCountChanged {@code true} if entity ids have to be reallocated
     */
    private void refreshTextEntities(boolean lineCountChanged) {
        if (!isSpawned()) {
            return;
        }
        if (!lineCountChanged) {
            services.renderer().refreshText(this);
            return;
        }

        // A line that has gone needs its entity removed, and a new line needs an id. Neither can be
        // expressed as an update, so the text entities are torn down and rebuilt.
        List<Player> viewers = renderState.viewerSnapshot();
        services.renderer().hide(this, viewers);
        services.renderer().reallocateTextIds(this);
        services.renderer().show(this, viewers);
    }

    // ---------------------------------------------------------------------------------------------
    // Position and rotation
    // ---------------------------------------------------------------------------------------------

    @Override
    public NpcPosition position() {
        return position;
    }

    @Override
    public void teleport(NpcPosition newPosition) {
        checkAlive();
        Objects.requireNonNull(newPosition, "position");
        if (newPosition.equals(position)) {
            return;
        }

        boolean worldChanged = !newPosition.isSameWorld(position);
        this.position = newPosition;
        markDirty();

        if (!isSpawned()) {
            return;
        }
        services.index().place(this);

        if (worldChanged) {
            // The client has no concept of an entity crossing dimensions, so everyone who could see
            // the NPC is told it is gone and the next tracker pass shows it to whoever is near it in
            // the new world.
            services.tracker().refresh(this);
        } else {
            services.renderer().teleport(this);
        }
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        checkAlive();
        NpcPosition rotated = position.withRotation(yaw, pitch);
        if (rotated.equals(position)) {
            return;
        }
        this.position = rotated;
        markDirty();

        if (isSpawned() && !look.isActive()) {
            // Skipped while look tracking is on, because the next pass would overwrite it within a
            // tick or two and the packet would be pure waste.
            services.renderer().adapter().rotate(
                    renderState.entityId(),
                    rotated.yaw(),
                    rotated.pitch(),
                    look.headOnly(),
                    renderState.viewerSnapshot());
        }
    }

    @Override
    public LookSettings look() {
        return look;
    }

    @Override
    public void setLook(LookSettings newLook) {
        checkAlive();
        Objects.requireNonNull(newLook, "look");
        this.look = newLook;
        markDirty();
    }

    // ---------------------------------------------------------------------------------------------
    // Skin
    // ---------------------------------------------------------------------------------------------

    @Override
    public Optional<SkinSource> skinSource() {
        return Optional.ofNullable(skinSource);
    }

    @Override
    public Optional<NpcSkin> skin() {
        return Optional.ofNullable(skin);
    }

    @Override
    public void setSkin(NpcSkin newSkin) {
        checkAlive();
        if (Objects.equals(newSkin, skin)) {
            return;
        }
        this.skin = newSkin;
        markDirty();

        if (isSpawned() && type.isPlayer()) {
            // A player NPC's skin lives in its profile, which the client reads once when the entity
            // appears. There is no packet that changes it afterwards.
            respawnForViewers();
        }
    }

    @Override
    public CompletableFuture<Void> setSkin(SkinSource source) {
        checkAlive();

        if (source == null) {
            this.skinSource = null;
            setSkin((NpcSkin) null);
            return CompletableFuture.completedFuture(null);
        }

        this.skinSource = source;
        markDirty();

        return services.skins().resolve(source).thenAccept(resolved -> {
            // The lookup finished on a background thread; everything from here touches engine state.
            services.scheduler().runOnMainThread(() -> {
                if (isRemoved() || !source.equals(skinSource)) {
                    // The NPC was deleted, or its skin was changed again while this was in flight.
                    // Applying a stale result would silently undo the newer decision.
                    return;
                }
                setSkin(resolved.orElse(null));
            });
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Equipment
    // ---------------------------------------------------------------------------------------------

    @Override
    public NpcEquipment equipment() {
        return equipment;
    }

    @Override
    public void setEquipment(NpcEquipment newEquipment) {
        checkAlive();
        Objects.requireNonNull(newEquipment, "equipment");
        if (newEquipment.equals(equipment)) {
            return;
        }
        this.equipment = newEquipment;
        markDirty();

        if (isSpawned()) {
            services.renderer().updateEquipment(this);
        }
    }

    @Override
    public void setEquipment(EquipmentSlot slot, ItemStack item) {
        setEquipment(equipment.with(slot, item));
    }

    // ---------------------------------------------------------------------------------------------
    // Visibility
    // ---------------------------------------------------------------------------------------------

    @Override
    public NpcVisibility visibility() {
        return visibility;
    }

    @Override
    public void setVisibility(NpcVisibility newVisibility) {
        checkAlive();
        Objects.requireNonNull(newVisibility, "visibility");
        if (newVisibility.equals(visibility)) {
            return;
        }
        this.visibility = newVisibility;
        markDirty();
        refreshVisibility();
    }

    @Override
    public void show(Player player) {
        checkAlive();
        renderState.override(player, true);
        refreshVisibility();
    }

    @Override
    public void hide(Player player) {
        checkAlive();
        renderState.override(player, false);
        refreshVisibility();
    }

    @Override
    public void clearVisibilityOverride(Player player) {
        checkAlive();
        renderState.clearOverride(player);
        refreshVisibility();
    }

    @Override
    public boolean isVisibleTo(Player player) {
        Objects.requireNonNull(player, "player");
        return renderState.isViewer(player);
    }

    @Override
    public Collection<Player> viewers() {
        return renderState.viewerSnapshot();
    }

    @Override
    public void refreshVisibility() {
        checkAlive();
        if (isSpawned()) {
            services.tracker().refresh(this);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------------

    @Override
    public Map<InteractionType, List<ActionDefinition>> actions() {
        Map<InteractionType, List<ActionDefinition>> copy = new EnumMap<>(InteractionType.class);
        actions.forEach((interaction, definitions) -> {
            if (!definitions.isEmpty()) {
                copy.put(interaction, List.copyOf(definitions));
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public List<ActionDefinition> actions(InteractionType interaction) {
        Objects.requireNonNull(interaction, "interaction");
        List<ActionDefinition> definitions = actions.get(interaction);
        return definitions == null ? List.of() : List.copyOf(definitions);
    }

    @Override
    public void addAction(InteractionType interaction, ActionDefinition definition) {
        checkAlive();
        Objects.requireNonNull(interaction, "interaction");
        Objects.requireNonNull(definition, "definition");

        // Validated here rather than at execution time, so a typo is reported to whoever typed it
        // instead of to the first player who clicks the NPC.
        services.actions().validate(definition);

        actions.computeIfAbsent(interaction, key -> new ArrayList<>()).add(definition);
        markDirty();
    }

    @Override
    public ActionDefinition removeAction(InteractionType interaction, int index) {
        checkAlive();
        Objects.requireNonNull(interaction, "interaction");
        List<ActionDefinition> definitions = actions.get(interaction);
        if (definitions == null || index < 0 || index >= definitions.size()) {
            throw new IndexOutOfBoundsException(
                    "There is no action " + index + " for " + interaction + " on '" + name + "'");
        }
        ActionDefinition removed = definitions.remove(index);
        if (definitions.isEmpty()) {
            actions.remove(interaction);
        }
        markDirty();
        return removed;
    }

    @Override
    public void clearActions(InteractionType interaction) {
        checkAlive();
        Objects.requireNonNull(interaction, "interaction");
        if (actions.remove(interaction) != null) {
            markDirty();
        }
    }

    @Override
    public void runActions(Player player, InteractionType interaction) {
        checkAlive();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(interaction, "interaction");
        services.actionExecutor().execute(this, player, interaction, actions(interaction));
    }

    // ---------------------------------------------------------------------------------------------
    // Animation
    // ---------------------------------------------------------------------------------------------

    @Override
    public void playAnimation(NpcAnimation animation) {
        checkAlive();
        Objects.requireNonNull(animation, "animation");
        if (!isSpawned()) {
            throw new IllegalStateException("'" + name + "' is not spawned and cannot be animated");
        }
        services.renderer().playAnimation(this, animation, renderState.viewerSnapshot());
    }

    @Override
    public void playAnimation(NpcAnimation animation, Player player) {
        checkAlive();
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(player, "player");
        if (!renderState.isViewer(player)) {
            // The client has no entity to apply the animation to. Silently doing nothing is right:
            // an action bound to an NPC should not throw because the player walked away mid-chain.
            return;
        }
        services.renderer().playAnimation(this, animation, List.of(player));
    }

    // ---------------------------------------------------------------------------------------------
    // Metadata
    // ---------------------------------------------------------------------------------------------

    @Override
    public Map<String, String> metadata() {
        return Map.copyOf(metadata);
    }

    @Override
    public Optional<String> metadata(String key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(metadata.get(key));
    }

    @Override
    public void setMetadata(String key, String value) {
        checkAlive();
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("A metadata key must not be blank");
        }
        String previous = value == null ? metadata.remove(key) : metadata.put(key, value);
        if (!Objects.equals(previous, value)) {
            markDirty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean spawn() {
        return spawn(false);
    }

    /**
     * Activates the NPC, recording whether the engine or a caller asked for it.
     *
     * @param automatic {@code true} when the engine is spawning the NPC because its world loaded
     * @return {@code true} if the NPC is spawned afterwards
     */
    public boolean spawn(boolean automatic) {
        checkAlive();
        if (isSpawned()) {
            return true;
        }
        if (!services.events().fireAndCheck(new NpcSpawnEvent(this, automatic))) {
            return false;
        }

        services.renderer().allocateIds(this);
        this.state = NpcState.SPAWNED;
        services.registry().refreshEntityIds(this);
        services.index().place(this);
        // Viewers are decided by the next tracker pass rather than here: this method does not know
        // who is online, and duplicating that decision is how the two would drift apart.
        return true;
    }

    @Override
    public boolean despawn() {
        return despawn(NpcDespawnEvent.Reason.REQUESTED);
    }

    /**
     * Deactivates the NPC, recording why.
     *
     * @param reason why the NPC is being despawned
     * @return {@code true} if the NPC was spawned and is now despawned
     */
    public boolean despawn(NpcDespawnEvent.Reason reason) {
        Objects.requireNonNull(reason, "reason");
        if (state != NpcState.SPAWNED) {
            return false;
        }

        services.renderer().hide(this, renderState.viewerSnapshot());
        services.tracker().forgetNpc(this);
        services.index().remove(this);

        this.state = NpcState.DESPAWNED;
        renderState.reset();
        services.registry().refreshEntityIds(this);

        services.events().fire(new NpcDespawnEvent(this, reason));
        return true;
    }

    /**
     * Marks the handle dead after the NPC has been deleted.
     *
     * <p>Called by the manager, which owns deletion. Every mutating method throws afterwards, which
     * is what turns "an extension is holding a deleted NPC" from silent corruption into an
     * exception at the point of misuse.
     */
    public void markRemoved() {
        this.state = NpcState.REMOVED;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Clears the dirty flag after a successful write.
     */
    public void markClean() {
        this.dirty = false;
    }

    /**
     * Marks the NPC as having unsaved changes.
     */
    public void markDirty() {
        this.dirty = true;
    }

    @Override
    public CompletableFuture<Void> save() {
        checkAlive();

        NpcSaveEvent event = services.events().fire(new NpcSaveEvent(this, snapshot()));
        if (event.isCancelled()) {
            // Left dirty on purpose, so the next save cycle tries again rather than quietly dropping
            // the change.
            return CompletableFuture.completedFuture(null);
        }

        markClean();
        return services.repository().save(event.getSnapshot()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                // Restore the flag so the change is not lost. Done without the scheduler because a
                // boolean write is safe from any thread and hopping would delay the retry.
                markDirty();
            }
        });
    }

    @Override
    public NpcSnapshot snapshot() {
        NpcSnapshot.Builder builder = NpcSnapshot.builder(uniqueId, name, type, position)
                .displayName(displayName)
                .skinSource(skinSource)
                .resolvedSkin(skin)
                .equipment(equipment)
                .appearance(appearance)
                .visibility(visibility)
                .look(look)
                .nametag(nametag)
                .hologram(hologram)
                .metadata(metadata)
                .spawnByDefault(spawnByDefault);
        actions.forEach(builder::actions);
        return builder.build();
    }

    @Override
    public void apply(NpcSnapshot snapshot) {
        checkAlive();
        Objects.requireNonNull(snapshot, "snapshot");

        if (!snapshot.name().equalsIgnoreCase(name) && services.registry().contains(snapshot.name())) {
            throw new IllegalArgumentException(
                    "An NPC called '" + snapshot.name() + "' already exists");
        }

        boolean wasSpawned = isSpawned();
        if (wasSpawned) {
            // Applying a whole snapshot can change the type, the skin and the number of text lines
            // at once. Rebuilding is both simpler and less error-prone than working out which subset
            // of updates would have covered it.
            services.renderer().hide(this, renderState.viewerSnapshot());
        }

        if (!snapshot.name().equals(name)) {
            services.registry().rename(this, snapshot.name());
        }
        applyInternal(snapshot);
        markDirty();

        if (wasSpawned) {
            services.index().place(this);
            services.tracker().refresh(this);
        }
    }

    private void applyInternal(NpcSnapshot snapshot) {
        this.name = snapshot.name();
        this.type = snapshot.type();
        this.position = snapshot.position();
        this.displayName = snapshot.displayName().orElse(null);
        this.skinSource = snapshot.skinSource().orElse(null);
        this.skin = snapshot.resolvedSkin().orElse(null);
        this.equipment = snapshot.equipment();
        this.appearance = snapshot.appearance();
        this.visibility = snapshot.visibility();
        this.look = snapshot.look();
        this.nametag = snapshot.nametag();
        this.hologram = snapshot.hologram();
        this.spawnByDefault = snapshot.spawnByDefault();

        actions.clear();
        snapshot.actions().forEach((interaction, definitions) ->
                actions.put(interaction, new ArrayList<>(definitions)));

        metadata.clear();
        metadata.putAll(snapshot.metadata());
    }

    // ---------------------------------------------------------------------------------------------
    // Engine internals
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the transient rendering state.
     *
     * @return the render state
     */
    public NpcRenderState renderState() {
        return renderState;
    }

    /**
     * Returns whether the NPC should spawn automatically when its world loads.
     *
     * @return {@code true} if it spawns on load
     */
    public boolean spawnByDefault() {
        return spawnByDefault;
    }

    /**
     * Sets whether the NPC spawns automatically when its world loads.
     *
     * @param value {@code true} to spawn on load
     */
    public void setSpawnByDefault(boolean value) {
        checkAlive();
        if (value != spawnByDefault) {
            this.spawnByDefault = value;
            markDirty();
        }
    }

    private void respawnForViewers() {
        List<Player> viewers = renderState.viewerSnapshot();
        services.renderer().hide(this, viewers);

        // Fresh ids. Reusing the old ones would ask clients to accept a different entity under an id
        // they already have, which several client versions handle by rendering neither.
        services.renderer().allocateIds(this);
        services.registry().refreshEntityIds(this);

        services.renderer().show(this, viewers);
    }

    private void checkAlive() {
        if (state == NpcState.REMOVED) {
            throw new IllegalStateException(
                    "The NPC '" + name + "' has been removed and this handle is no longer usable");
        }
    }

    @Override
    public boolean equals(Object other) {
        // Identity, not value: two handles for the same NPC would be a registry bug, and comparing
        // by unique id would hide it behind code that appeared to work.
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "Npc[" + name + " (" + type + ") " + state + " at " + position + ']';
    }
}
