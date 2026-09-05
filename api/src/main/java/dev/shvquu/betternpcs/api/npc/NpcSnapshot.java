package dev.shvquu.betternpcs.api.npc;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
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

/**
 * An immutable copy of everything that defines an NPC.
 *
 * <p>The unit of persistence and the boundary between the engine and storage. A live {@link Npc} is
 * a mutable handle that only the main thread may touch; a snapshot is a frozen value that can be
 * handed to a background thread, written to a database, compared against what is on disk, or sent
 * across a network, none of which would be safe with the handle itself.
 *
 * <p>It is also what makes storage implementations simple: a repository never sees an {@code Npc},
 * only this, so it cannot accidentally mutate the engine's state or read a half-applied change.
 *
 * @since 1.0.0
 */
public final class NpcSnapshot {

    private final UUID uniqueId;
    private final String name;
    private final NpcType type;
    private final NpcPosition position;
    private final String displayName;
    private final SkinSource skinSource;
    private final NpcSkin resolvedSkin;
    private final NpcEquipment equipment;
    private final NpcAppearance appearance;
    private final NpcVisibility visibility;
    private final LookSettings look;
    private final NametagSettings nametag;
    private final HologramSettings hologram;
    private final Map<InteractionType, List<ActionDefinition>> actions;
    private final Map<String, String> metadata;
    private final boolean spawnByDefault;

    private NpcSnapshot(Builder builder) {
        this.uniqueId = builder.uniqueId;
        this.name = builder.name;
        this.type = builder.type;
        this.position = builder.position;
        this.displayName = builder.displayName;
        this.skinSource = builder.skinSource;
        this.resolvedSkin = builder.resolvedSkin;
        this.equipment = builder.equipment;
        this.appearance = builder.appearance;
        this.visibility = builder.visibility;
        this.look = builder.look;
        this.nametag = builder.nametag;
        this.hologram = builder.hologram;
        this.spawnByDefault = builder.spawnByDefault;

        Map<InteractionType, List<ActionDefinition>> copiedActions = new EnumMap<>(InteractionType.class);
        builder.actions.forEach((interaction, definitions) -> {
            if (!definitions.isEmpty()) {
                copiedActions.put(interaction, List.copyOf(definitions));
            }
        });
        // Unmodifiable views over ordered maps rather than Map.copyOf, whose iteration order is
        // deliberately randomised. Storage implementations hash a snapshot to decide whether a write
        // is needed, and that hash has to be stable across restarts of the same JVM version.
        this.actions = Collections.unmodifiableMap(copiedActions);
        this.metadata = Collections.unmodifiableMap(new TreeMap<>(builder.metadata));
    }

    /**
     * Returns a builder for an NPC with the given identity, and defaults for everything else.
     *
     * @param uniqueId the NPC's stable unique id
     * @param name     the NPC's unique name
     * @param type     what the NPC is rendered as
     * @param position where the NPC stands
     * @return a new builder
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Builder builder(UUID uniqueId, String name, NpcType type, NpcPosition position) {
        return new Builder(uniqueId, name, type, position);
    }

    /**
     * Returns a builder pre-filled with this snapshot, for deriving a modified copy.
     *
     * @return a new builder holding this snapshot's values
     */
    public Builder toBuilder() {
        Builder builder = new Builder(uniqueId, name, type, position)
                .displayName(displayName)
                .skinSource(skinSource)
                .resolvedSkin(resolvedSkin)
                .equipment(equipment)
                .appearance(appearance)
                .visibility(visibility)
                .look(look)
                .nametag(nametag)
                .hologram(hologram)
                .spawnByDefault(spawnByDefault);
        actions.forEach(builder::actions);
        metadata.forEach(builder::metadata);
        return builder;
    }

    /**
     * Returns the NPC's stable unique id.
     *
     * <p>Assigned once at creation and never reused, unlike the name, which an administrator may
     * change. Storage keys off this.
     *
     * @return the unique id
     */
    public UUID uniqueId() {
        return uniqueId;
    }

    /**
     * Returns the NPC's unique name, as used in commands.
     *
     * @return the name, never blank
     */
    public String name() {
        return name;
    }

    /**
     * Returns what the NPC is rendered as.
     *
     * @return the type
     */
    public NpcType type() {
        return type;
    }

    /**
     * Returns where the NPC stands.
     *
     * @return the position
     */
    public NpcPosition position() {
        return position;
    }

    /**
     * Returns the MiniMessage template rendered as the NPC's name.
     *
     * @return the template, or empty to use {@link #name()} unchanged
     */
    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    /**
     * Returns where the NPC's skin comes from.
     *
     * @return the skin source, or empty for the default skin
     */
    public Optional<SkinSource> skinSource() {
        return Optional.ofNullable(skinSource);
    }

    /**
     * Returns the last successfully resolved skin.
     *
     * <p>Persisted alongside {@link #skinSource()} so that a restart renders the NPC immediately
     * instead of showing Steve until a lookup completes — and so that an NPC keeps working while
     * Mojang's session servers are down.
     *
     * @return the resolved skin, or empty if it has never been resolved
     */
    public Optional<NpcSkin> resolvedSkin() {
        return Optional.ofNullable(resolvedSkin);
    }

    /**
     * Returns what the NPC wears and holds.
     *
     * @return the equipment
     */
    public NpcEquipment equipment() {
        return equipment;
    }

    /**
     * Returns the NPC's state flags.
     *
     * @return the appearance
     */
    public NpcAppearance appearance() {
        return appearance;
    }

    /**
     * Returns who the NPC is shown to.
     *
     * @return the visibility settings
     */
    public NpcVisibility visibility() {
        return visibility;
    }

    /**
     * Returns how the NPC turns.
     *
     * @return the look settings
     */
    public LookSettings look() {
        return look;
    }

    /**
     * Returns the NPC's nametag settings.
     *
     * @return the nametag settings
     */
    public NametagSettings nametag() {
        return nametag;
    }

    /**
     * Returns the NPC's hologram settings.
     *
     * @return the hologram settings
     */
    public HologramSettings hologram() {
        return hologram;
    }

    /**
     * Returns the actions bound to each interaction, in execution order.
     *
     * @return an immutable map; interactions with no actions are absent rather than mapped to an
     *         empty list
     */
    public Map<InteractionType, List<ActionDefinition>> actions() {
        return actions;
    }

    /**
     * Returns the actions bound to one interaction.
     *
     * @param interaction the interaction to look up
     * @return an immutable list, empty if nothing is bound
     * @throws NullPointerException if {@code interaction} is {@code null}
     */
    public List<ActionDefinition> actions(InteractionType interaction) {
        Objects.requireNonNull(interaction, "interaction");
        return actions.getOrDefault(interaction, List.of());
    }

    /**
     * Returns the arbitrary key-value data extensions have attached to the NPC.
     *
     * <p>Persisted with the NPC and otherwise untouched by the engine. Keys should be namespaced by
     * the owning plugin so that two extensions cannot collide.
     *
     * @return an immutable map
     */
    public Map<String, String> metadata() {
        return metadata;
    }

    /**
     * Returns whether the NPC should be spawned automatically when its world loads.
     *
     * @return {@code true} if the NPC spawns on load
     */
    public boolean spawnByDefault() {
        return spawnByDefault;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NpcSnapshot snapshot
                && uniqueId.equals(snapshot.uniqueId)
                && name.equals(snapshot.name)
                && type.equals(snapshot.type)
                && position.equals(snapshot.position)
                && Objects.equals(displayName, snapshot.displayName)
                && Objects.equals(skinSource, snapshot.skinSource)
                && Objects.equals(resolvedSkin, snapshot.resolvedSkin)
                && equipment.equals(snapshot.equipment)
                && appearance.equals(snapshot.appearance)
                && visibility.equals(snapshot.visibility)
                && look.equals(snapshot.look)
                && nametag.equals(snapshot.nametag)
                && hologram.equals(snapshot.hologram)
                && actions.equals(snapshot.actions)
                && metadata.equals(snapshot.metadata)
                && spawnByDefault == snapshot.spawnByDefault;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                uniqueId, name, type, position, displayName, skinSource, resolvedSkin, equipment,
                appearance, visibility, look, nametag, hologram, actions, metadata, spawnByDefault);
    }

    /**
     * Returns a short description identifying the NPC.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "NpcSnapshot[" + name + " (" + type + ") at " + position + ']';
    }

    /**
     * Builds {@link NpcSnapshot} instances.
     *
     * <p>Not thread safe; build on one thread and share the immutable result.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private final UUID uniqueId;
        private String name;
        private NpcType type;
        private NpcPosition position;
        private String displayName;
        private SkinSource skinSource;
        private NpcSkin resolvedSkin;
        private NpcEquipment equipment = NpcEquipment.EMPTY;
        private NpcAppearance appearance = NpcAppearance.DEFAULT;
        private NpcVisibility visibility = NpcVisibility.DEFAULT;
        private LookSettings look = LookSettings.NONE;
        private NametagSettings nametag = NametagSettings.DEFAULT;
        private HologramSettings hologram = HologramSettings.NONE;
        private final Map<InteractionType, List<ActionDefinition>> actions =
                new EnumMap<>(InteractionType.class);
        private final Map<String, String> metadata = new TreeMap<>();
        private boolean spawnByDefault = true;

        private Builder(UUID uniqueId, String name, NpcType type, NpcPosition position) {
            this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
            name(name);
            type(type);
            position(position);
        }

        /**
         * Sets the NPC's unique name.
         *
         * @param value the name
         * @return this builder
         * @throws NullPointerException     if {@code value} is {@code null}
         * @throws IllegalArgumentException if {@code value} is blank
         */
        public Builder name(String value) {
            Objects.requireNonNull(value, "name");
            if (value.isBlank()) {
                throw new IllegalArgumentException("NPC name must not be blank");
            }
            this.name = value.trim();
            return this;
        }

        /**
         * Sets what the NPC is rendered as.
         *
         * @param value the type
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder type(NpcType value) {
            this.type = Objects.requireNonNull(value, "type");
            return this;
        }

        /**
         * Sets where the NPC stands.
         *
         * @param value the position
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder position(NpcPosition value) {
            this.position = Objects.requireNonNull(value, "position");
            return this;
        }

        /**
         * Sets the MiniMessage template rendered as the NPC's name.
         *
         * @param value the template, or {@code null} to use the NPC's name unchanged
         * @return this builder
         */
        public Builder displayName(String value) {
            this.displayName = value;
            return this;
        }

        /**
         * Sets where the NPC's skin comes from.
         *
         * @param value the skin source, or {@code null} for the default skin
         * @return this builder
         */
        public Builder skinSource(SkinSource value) {
            this.skinSource = value;
            return this;
        }

        /**
         * Sets the cached resolution of {@link #skinSource(SkinSource)}.
         *
         * @param value the resolved skin, or {@code null} if it has not been resolved
         * @return this builder
         */
        public Builder resolvedSkin(NpcSkin value) {
            this.resolvedSkin = value;
            return this;
        }

        /**
         * Sets what the NPC wears and holds.
         *
         * @param value the equipment
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder equipment(NpcEquipment value) {
            this.equipment = Objects.requireNonNull(value, "equipment");
            return this;
        }

        /**
         * Sets the NPC's state flags.
         *
         * @param value the appearance
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder appearance(NpcAppearance value) {
            this.appearance = Objects.requireNonNull(value, "appearance");
            return this;
        }

        /**
         * Sets who the NPC is shown to.
         *
         * @param value the visibility settings
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder visibility(NpcVisibility value) {
            this.visibility = Objects.requireNonNull(value, "visibility");
            return this;
        }

        /**
         * Sets how the NPC turns.
         *
         * @param value the look settings
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder look(LookSettings value) {
            this.look = Objects.requireNonNull(value, "look");
            return this;
        }

        /**
         * Sets the NPC's nametag settings.
         *
         * @param value the nametag settings
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder nametag(NametagSettings value) {
            this.nametag = Objects.requireNonNull(value, "nametag");
            return this;
        }

        /**
         * Sets the NPC's hologram settings.
         *
         * @param value the hologram settings
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder hologram(HologramSettings value) {
            this.hologram = Objects.requireNonNull(value, "hologram");
            return this;
        }

        /**
         * Replaces the actions bound to one interaction.
         *
         * @param interaction the interaction to bind
         * @param definitions the actions, in execution order
         * @return this builder
         * @throws NullPointerException if any argument or element is {@code null}
         */
        public Builder actions(InteractionType interaction, Collection<ActionDefinition> definitions) {
            Objects.requireNonNull(interaction, "interaction");
            Objects.requireNonNull(definitions, "definitions");
            List<ActionDefinition> copy = new ArrayList<>(definitions.size());
            for (ActionDefinition definition : definitions) {
                copy.add(Objects.requireNonNull(definition, "definition"));
            }
            actions.put(interaction, copy);
            return this;
        }

        /**
         * Appends one action to an interaction.
         *
         * @param interaction the interaction to bind
         * @param definition  the action to append
         * @return this builder
         * @throws NullPointerException if either argument is {@code null}
         */
        public Builder action(InteractionType interaction, ActionDefinition definition) {
            Objects.requireNonNull(interaction, "interaction");
            Objects.requireNonNull(definition, "definition");
            actions.computeIfAbsent(interaction, ignored -> new ArrayList<>()).add(definition);
            return this;
        }

        /**
         * Sets one metadata entry.
         *
         * @param key   the key, ideally namespaced by the owning plugin
         * @param value the value, or {@code null} to remove the entry
         * @return this builder
         * @throws NullPointerException     if {@code key} is {@code null}
         * @throws IllegalArgumentException if {@code key} is blank
         */
        public Builder metadata(String key, String value) {
            Objects.requireNonNull(key, "key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("Metadata key must not be blank");
            }
            if (value == null) {
                metadata.remove(key);
            } else {
                metadata.put(key, value);
            }
            return this;
        }

        /**
         * Replaces every metadata entry.
         *
         * @param values the entries
         * @return this builder
         * @throws NullPointerException if {@code values} or any key is {@code null}
         */
        public Builder metadata(Map<String, String> values) {
            Objects.requireNonNull(values, "values");
            metadata.clear();
            values.forEach(this::metadata);
            return this;
        }

        /**
         * Sets whether the NPC spawns automatically when its world loads.
         *
         * @param value {@code true} to spawn on load
         * @return this builder
         */
        public Builder spawnByDefault(boolean value) {
            this.spawnByDefault = value;
            return this;
        }

        /**
         * Builds the immutable snapshot.
         *
         * @return the snapshot
         */
        public NpcSnapshot build() {
            return new NpcSnapshot(this);
        }
    }
}
