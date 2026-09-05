/**
 * The immutable value objects an NPC is configured with — position, skin, equipment, appearance,
 * visibility, look behaviour and floating text.
 *
 * <p>All of them are immutable and safe to share between threads and between NPCs. Types with more
 * than a couple of fields are built through a nested {@code Builder} and offer {@code toBuilder()}
 * for deriving a modified copy.
 *
 * @since 1.0.0
 */
package dev.shvquu.betternpcs.api.npc.property;
