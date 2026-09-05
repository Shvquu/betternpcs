/**
 * NPC handles, the registry that owns them, and the immutable snapshots they serialise to.
 *
 * <p>{@link dev.shvquu.betternpcs.api.npc.NpcManager} creates and looks up NPCs,
 * {@link dev.shvquu.betternpcs.api.npc.Npc} is a live handle on one, and
 * {@link dev.shvquu.betternpcs.api.npc.NpcSnapshot} is the thread-safe value a handle freezes into.
 *
 * @since 1.0.0
 */
package dev.shvquu.betternpcs.api.npc;
