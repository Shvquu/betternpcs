package dev.shvquu.betternpcs.api.npc.property;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The items an NPC is wearing and holding.
 *
 * <p>Immutable, which is the point: {@link ItemStack} is mutable, and a plugin that hands an item to
 * an NPC and then modifies it would otherwise silently desynchronise every client that already
 * received the equipment packet. Items are therefore cloned on the way in and on the way out, so a
 * caller can never reach the instance the engine sends.
 *
 * <p>Empty and air stacks are treated as "slot not set" and are dropped, so an equipment object
 * never has to distinguish between an absent entry and an entry holding air.
 *
 * <p>Which slots exist depends on the running server version — 1.21.5 added a saddle slot, for
 * instance. Rather than enumerate them, this type accepts any {@link EquipmentSlot} the server
 * knows about and lets the version adapter decide what it can send.
 *
 * @since 1.0.0
 */
public final class NpcEquipment {

    /** An NPC wearing and holding nothing. */
    public static final NpcEquipment EMPTY = new NpcEquipment(new EnumMap<>(EquipmentSlot.class));

    private final Map<EquipmentSlot, ItemStack> items;

    private NpcEquipment(Map<EquipmentSlot, ItemStack> items) {
        this.items = Collections.unmodifiableMap(items);
    }

    /**
     * Returns a builder for an empty equipment set.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns equipment holding a single item.
     *
     * @param slot the slot to fill
     * @param item the item, may be {@code null} or air for none
     * @return the equipment
     * @throws NullPointerException if {@code slot} is {@code null}
     */
    public static NpcEquipment of(EquipmentSlot slot, ItemStack item) {
        return builder().set(slot, item).build();
    }

    /**
     * Returns a builder pre-filled with this equipment, for deriving a modified copy.
     *
     * @return a new builder holding this equipment's items
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        items.forEach(builder::set);
        return builder;
    }

    /**
     * Returns the item in a slot.
     *
     * @param slot the slot to read
     * @return a defensive copy of the item, or empty if the slot is unset
     * @throws NullPointerException if {@code slot} is {@code null}
     */
    public Optional<ItemStack> get(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "slot");
        ItemStack item = items.get(slot);
        return item == null ? Optional.empty() : Optional.of(item.clone());
    }

    /**
     * Returns the slots that hold an item.
     *
     * @return an immutable set of filled slots
     */
    public Set<EquipmentSlot> filledSlots() {
        return items.keySet();
    }

    /**
     * Returns every filled slot with a defensive copy of its item.
     *
     * <p>Intended for the version adapters, which need the whole set to build one equipment packet.
     *
     * @return a new map from slot to item copy
     */
    public Map<EquipmentSlot, ItemStack> asMap() {
        Map<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        items.forEach((slot, item) -> copy.put(slot, item.clone()));
        return copy;
    }

    /**
     * Returns whether no slot is filled.
     *
     * @return {@code true} if the NPC wears and holds nothing
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Returns a copy of this equipment with one slot changed.
     *
     * @param slot the slot to change
     * @param item the item, or {@code null} to clear the slot
     * @return the derived equipment
     * @throws NullPointerException if {@code slot} is {@code null}
     */
    public NpcEquipment with(EquipmentSlot slot, ItemStack item) {
        return toBuilder().set(slot, item).build();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NpcEquipment equipment && items.equals(equipment.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    /**
     * Returns a description listing the filled slots and their item types.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        if (items.isEmpty()) {
            return "NpcEquipment[empty]";
        }
        StringBuilder description = new StringBuilder("NpcEquipment[");
        items.forEach((slot, item) -> {
            if (description.charAt(description.length() - 1) != '[') {
                description.append(", ");
            }
            description.append(slot.name()).append('=').append(item.getType().name());
        });
        return description.append(']').toString();
    }

    /**
     * Builds {@link NpcEquipment} instances.
     *
     * <p>Not thread safe; build on one thread and share the immutable result.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private final Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);

        private Builder() {
        }

        /**
         * Sets or clears a slot.
         *
         * <p>The item is cloned, so later changes to the argument do not affect the built equipment.
         *
         * @param slot the slot to set
         * @param item the item, or {@code null} or air to clear the slot
         * @return this builder
         * @throws NullPointerException if {@code slot} is {@code null}
         */
        public Builder set(EquipmentSlot slot, ItemStack item) {
            Objects.requireNonNull(slot, "slot");
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                items.remove(slot);
            } else {
                items.put(slot, item.clone());
            }
            return this;
        }

        /**
         * Sets the helmet slot.
         *
         * @param item the item, or {@code null} to clear
         * @return this builder
         */
        public Builder helmet(ItemStack item) {
            return set(EquipmentSlot.HEAD, item);
        }

        /**
         * Sets the chestplate slot.
         *
         * @param item the item, or {@code null} to clear
         * @return this builder
         */
        public Builder chestplate(ItemStack item) {
            return set(EquipmentSlot.CHEST, item);
        }

        /**
         * Sets the leggings slot.
         *
         * @param item the item, or {@code null} to clear
         * @return this builder
         */
        public Builder leggings(ItemStack item) {
            return set(EquipmentSlot.LEGS, item);
        }

        /**
         * Sets the boots slot.
         *
         * @param item the item, or {@code null} to clear
         * @return this builder
         */
        public Builder boots(ItemStack item) {
            return set(EquipmentSlot.FEET, item);
        }

        /**
         * Sets the main hand slot.
         *
         * @param item the item, or {@code null} to clear
         * @return this builder
         */
        public Builder mainHand(ItemStack item) {
            return set(EquipmentSlot.HAND, item);
        }

        /**
         * Sets the off hand slot.
         *
         * @param item the item, or {@code null} to clear
         * @return this builder
         */
        public Builder offHand(ItemStack item) {
            return set(EquipmentSlot.OFF_HAND, item);
        }

        /**
         * Builds the immutable equipment.
         *
         * @return the equipment
         */
        public NpcEquipment build() {
            return items.isEmpty() ? EMPTY : new NpcEquipment(new EnumMap<>(items));
        }
    }
}
