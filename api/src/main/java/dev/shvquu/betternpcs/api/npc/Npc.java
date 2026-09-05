package dev.shvquu.betternpcs.api.npc;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.animation.NpcAnimation;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

/**
 * A live handle on one NPC.
 *
 * <p>Setters take effect immediately for every player who can currently see the NPC — there is no
 * separate "apply" step. Changes are batched within a tick where the protocol allows it, so setting
 * five appearance flags in a row costs one packet, not five.
 *
 * <h2>Threading</h2>
 *
 * <p>Every method here must be called on the main server thread unless its documentation says
 * otherwise. Methods returning a {@link CompletableFuture} do their work elsewhere and complete on a
 * background thread, so a continuation that touches Bukkit has to be scheduled back.
 *
 * <h2>Persistence</h2>
 *
 * <p>Setters change the NPC in memory and mark it dirty; they do not write to storage. The engine
 * saves dirty NPCs periodically and on shutdown, and {@link #save()} forces it immediately. This
 * keeps a script that moves an NPC every tick from turning into a database write every tick.
 *
 * <h2>Lifetime</h2>
 *
 * <p>A handle is invalidated when the NPC is deleted. Every mutating method then throws
 * {@link IllegalStateException}; check {@link #isRemoved()} if a reference may have been held across
 * an administrator's {@code /npc remove}.
 *
 * @since 1.0.0
 */
public interface Npc {

    // ---------------------------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the NPC's stable unique id.
     *
     * <p>Assigned at creation and never changed, even when the NPC is renamed. Store this rather
     * than the name when an external system needs to refer to an NPC.
     *
     * @return the unique id
     */
    UUID uniqueId();

    /**
     * Returns the NPC's unique name, as used in commands.
     *
     * @return the name, never blank
     */
    String name();

    /**
     * Renames the NPC.
     *
     * @param name the new name
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank, or another NPC already has it
     * @throws IllegalStateException    if the NPC has been removed
     */
    void rename(String name);

    /**
     * Returns what the NPC is rendered as.
     *
     * @return the type
     */
    NpcType type();

    /**
     * Changes what the NPC is rendered as.
     *
     * <p>The client cannot change an existing entity's type, so this despawns and respawns the NPC
     * for everyone currently seeing it. Properties that do not apply to the new type — a skin on a
     * zombie, for instance — are kept but not sent.
     *
     * @param type the new type
     * @throws NullPointerException  if {@code type} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setType(NpcType type);

    /**
     * Returns where the NPC is in its lifecycle.
     *
     * @return the state
     */
    NpcState state();

    /**
     * Returns whether the NPC has been deleted.
     *
     * @return {@code true} if this handle is dead
     */
    default boolean isRemoved() {
        return state() == NpcState.REMOVED;
    }

    // ---------------------------------------------------------------------------------------------
    // Appearance and text
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the MiniMessage template rendered as the NPC's name.
     *
     * @return the template, or empty if the NPC's plain {@link #name()} is used
     */
    Optional<String> displayName();

    /**
     * Sets the NPC's display name from MiniMessage source.
     *
     * <p>Kept as source rather than a rendered component so that placeholders inside it resolve per
     * viewer.
     *
     * @param miniMessage the template, or {@code null} to fall back to {@link #name()}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setDisplayName(String miniMessage);

    /**
     * Sets the NPC's display name from a component.
     *
     * <p>Convenience for callers that already have one; the component is serialised back to
     * MiniMessage, so anything it contains that MiniMessage cannot express is lost. Prefer
     * {@link #setDisplayName(String)} when the source is available.
     *
     * @param displayName the component, or {@code null} to fall back to {@link #name()}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setDisplayName(Component displayName);

    /**
     * Returns the NPC's state flags.
     *
     * @return the appearance
     */
    NpcAppearance appearance();

    /**
     * Replaces the NPC's state flags.
     *
     * <p>Only the flags that actually changed are sent.
     *
     * @param appearance the new appearance
     * @throws NullPointerException  if {@code appearance} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setAppearance(NpcAppearance appearance);

    /**
     * Returns the NPC's nametag settings.
     *
     * @return the nametag settings
     */
    NametagSettings nametag();

    /**
     * Replaces the NPC's nametag settings.
     *
     * @param nametag the new settings
     * @throws NullPointerException  if {@code nametag} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setNametag(NametagSettings nametag);

    /**
     * Returns the NPC's hologram settings.
     *
     * @return the hologram settings
     */
    HologramSettings hologram();

    /**
     * Replaces the NPC's hologram settings.
     *
     * @param hologram the new settings
     * @throws NullPointerException  if {@code hologram} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setHologram(HologramSettings hologram);

    // ---------------------------------------------------------------------------------------------
    // Position and rotation
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns where the NPC stands.
     *
     * @return the position
     */
    NpcPosition position();

    /**
     * Moves the NPC.
     *
     * <p>A move within the same world sends a teleport packet to the players who can see the NPC. A
     * move to another world despawns it for everyone and respawns it for whoever can see it there,
     * because the client has no concept of an entity crossing dimensions.
     *
     * @param position the new position
     * @throws NullPointerException  if {@code position} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void teleport(NpcPosition position);

    /**
     * Turns the NPC in place.
     *
     * <p>Ignored while {@link #look()} is in an active mode, which would immediately overwrite it.
     *
     * @param yaw   the yaw in degrees
     * @param pitch the pitch in degrees
     * @throws IllegalStateException if the NPC has been removed
     */
    void setRotation(float yaw, float pitch);

    /**
     * Returns how the NPC turns.
     *
     * @return the look settings
     */
    LookSettings look();

    /**
     * Replaces how the NPC turns.
     *
     * @param look the new settings
     * @throws NullPointerException  if {@code look} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setLook(LookSettings look);

    // ---------------------------------------------------------------------------------------------
    // Skin
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns where the NPC's skin comes from.
     *
     * @return the skin source, or empty if the NPC uses the default skin
     */
    Optional<SkinSource> skinSource();

    /**
     * Returns the currently applied skin.
     *
     * @return the skin, or empty if none has been resolved yet
     */
    Optional<NpcSkin> skin();

    /**
     * Applies an already-resolved skin immediately.
     *
     * <p>A player NPC's skin is part of its profile, which the client reads once when the entity
     * appears, so this respawns the NPC for everyone currently seeing it. Nothing happens for
     * non-player NPCs.
     *
     * @param skin the skin, or {@code null} to fall back to the default
     * @throws IllegalStateException if the NPC has been removed
     */
    void setSkin(NpcSkin skin);

    /**
     * Resolves a skin source and applies the result.
     *
     * <p>Returns immediately; the lookup runs off the main thread and the skin is applied on the
     * main thread once it arrives. The source is remembered, so a later {@code /npc reload}
     * re-resolves it.
     *
     * @param source the source to resolve, or {@code null} to clear the skin
     * @return a future completing when the skin has been applied, or completing exceptionally if the
     *         source could not be resolved
     * @throws IllegalStateException if the NPC has been removed
     */
    CompletableFuture<Void> setSkin(SkinSource source);

    // ---------------------------------------------------------------------------------------------
    // Equipment
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns what the NPC wears and holds.
     *
     * @return the equipment
     */
    NpcEquipment equipment();

    /**
     * Replaces the NPC's equipment.
     *
     * @param equipment the new equipment
     * @throws NullPointerException  if {@code equipment} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setEquipment(NpcEquipment equipment);

    /**
     * Sets or clears one equipment slot.
     *
     * @param slot the slot to change
     * @param item the item, or {@code null} to clear the slot; cloned, so later changes to it do not
     *             affect the NPC
     * @throws NullPointerException  if {@code slot} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setEquipment(EquipmentSlot slot, ItemStack item);

    // ---------------------------------------------------------------------------------------------
    // Visibility
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns who the NPC is shown to.
     *
     * @return the visibility settings
     */
    NpcVisibility visibility();

    /**
     * Replaces who the NPC is shown to.
     *
     * <p>Players who no longer qualify are sent a despawn on the next tracker pass, and players who
     * now qualify are sent a spawn.
     *
     * @param visibility the new settings
     * @throws NullPointerException  if {@code visibility} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void setVisibility(NpcVisibility visibility);

    /**
     * Shows the NPC to a specific player, overriding
     * {@link NpcVisibility#visibleByDefault()}.
     *
     * <p>Distance still applies: the player sees the NPC when they are close enough, not regardless
     * of where they are.
     *
     * @param player the player to show the NPC to
     * @throws NullPointerException  if {@code player} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void show(Player player);

    /**
     * Hides the NPC from a specific player regardless of every other rule.
     *
     * @param player the player to hide the NPC from
     * @throws NullPointerException  if {@code player} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void hide(Player player);

    /**
     * Clears a per-player override set by {@link #show(Player)} or {@link #hide(Player)}, returning
     * the player to the NPC's normal visibility rules.
     *
     * @param player the player whose override to clear
     * @throws NullPointerException  if {@code player} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void clearVisibilityOverride(Player player);

    /**
     * Returns whether the NPC is currently rendered for a player.
     *
     * @param player the player to check
     * @return {@code true} if the player has been sent the NPC and not since had it removed
     * @throws NullPointerException if {@code player} is {@code null}
     */
    boolean isVisibleTo(Player player);

    /**
     * Returns the players the NPC is currently rendered for.
     *
     * @return an immutable snapshot; the set changes as players move, so do not hold onto it
     */
    Collection<Player> viewers();

    /**
     * Re-evaluates visibility for every online player on the next tracker pass.
     *
     * <p>Needed after the state a custom {@link NpcVisibilityRule} depends on has changed, which the
     * engine cannot observe on its own.
     *
     * @throws IllegalStateException if the NPC has been removed
     */
    void refreshVisibility();

    // ---------------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the actions bound to each interaction.
     *
     * @return an immutable map; interactions with no actions are absent
     */
    Map<InteractionType, List<ActionDefinition>> actions();

    /**
     * Returns the actions bound to one interaction, in execution order.
     *
     * @param interaction the interaction to look up
     * @return an immutable list, empty if nothing is bound
     * @throws NullPointerException if {@code interaction} is {@code null}
     */
    List<ActionDefinition> actions(InteractionType interaction);

    /**
     * Appends an action to an interaction.
     *
     * @param interaction the interaction to bind
     * @param definition  the action to append
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if no handler is registered for the action's type, or the
     *                                  handler rejects its argument
     * @throws IllegalStateException    if the NPC has been removed
     */
    void addAction(InteractionType interaction, ActionDefinition definition);

    /**
     * Removes the action at an index.
     *
     * @param interaction the interaction to change
     * @param index       the zero-based index within {@link #actions(InteractionType)}
     * @return the removed action
     * @throws NullPointerException      if {@code interaction} is {@code null}
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     * @throws IllegalStateException     if the NPC has been removed
     */
    ActionDefinition removeAction(InteractionType interaction, int index);

    /**
     * Removes every action bound to an interaction.
     *
     * @param interaction the interaction to clear
     * @throws NullPointerException  if {@code interaction} is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void clearActions(InteractionType interaction);

    /**
     * Runs the actions bound to an interaction as if the player had triggered it.
     *
     * <p>Does not fire {@link dev.shvquu.betternpcs.api.event.NpcInteractEvent} — the interaction is
     * synthetic, and firing the event would let a listener that itself calls this method recurse.
     *
     * @param player      the player to run the actions for
     * @param interaction the interaction whose actions to run
     * @throws NullPointerException  if either argument is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void runActions(Player player, InteractionType interaction);

    // ---------------------------------------------------------------------------------------------
    // Animation
    // ---------------------------------------------------------------------------------------------

    /**
     * Plays an animation for every player who can see the NPC.
     *
     * @param animation the animation to play
     * @throws NullPointerException  if {@code animation} is {@code null}
     * @throws IllegalStateException if the NPC has been removed, or is not spawned
     */
    void playAnimation(NpcAnimation animation);

    /**
     * Plays an animation for one player only.
     *
     * <p>Does nothing if the player cannot currently see the NPC, since the client would have no
     * entity to apply it to.
     *
     * @param animation the animation to play
     * @param player    the player to play it for
     * @throws NullPointerException  if either argument is {@code null}
     * @throws IllegalStateException if the NPC has been removed
     */
    void playAnimation(NpcAnimation animation, Player player);

    // ---------------------------------------------------------------------------------------------
    // Metadata
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the arbitrary data extensions have attached to the NPC.
     *
     * @return an immutable snapshot
     */
    Map<String, String> metadata();

    /**
     * Returns one metadata value.
     *
     * @param key the key
     * @return the value, or empty if the key is not set
     * @throws NullPointerException if {@code key} is {@code null}
     */
    Optional<String> metadata(String key);

    /**
     * Sets or removes one metadata value.
     *
     * <p>Persisted with the NPC. Namespace keys with the owning plugin's name so that two extensions
     * cannot collide.
     *
     * @param key   the key
     * @param value the value, or {@code null} to remove the entry
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     * @throws IllegalStateException    if the NPC has been removed
     */
    void setMetadata(String key, String value);

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    /**
     * Activates the NPC so that it is tracked and rendered.
     *
     * <p>Does nothing if it is already spawned. Fires
     * {@link dev.shvquu.betternpcs.api.event.NpcSpawnEvent}, which may cancel it.
     *
     * @return {@code true} if the NPC is spawned afterwards
     * @throws IllegalStateException if the NPC has been removed
     */
    boolean spawn();

    /**
     * Deactivates the NPC, removing it from every client that can see it.
     *
     * <p>The NPC keeps existing and can be spawned again. Fires
     * {@link dev.shvquu.betternpcs.api.event.NpcDespawnEvent}.
     *
     * @return {@code true} if the NPC was spawned and is now despawned
     * @throws IllegalStateException if the NPC has been removed
     */
    boolean despawn();

    /**
     * Returns whether the NPC is active.
     *
     * @return {@code true} if the state is {@link NpcState#SPAWNED}
     */
    default boolean isSpawned() {
        return state() == NpcState.SPAWNED;
    }

    /**
     * Returns whether the NPC has unsaved changes.
     *
     * @return {@code true} if a save is pending
     */
    boolean isDirty();

    /**
     * Writes the NPC to storage now, whether or not it is dirty.
     *
     * <p>Rarely needed: the engine saves dirty NPCs periodically and on shutdown. Use it when a
     * change must survive a crash that happens in the next second.
     *
     * @return a future completing when the write has finished, or completing exceptionally if it
     *         failed
     * @throws IllegalStateException if the NPC has been removed
     */
    CompletableFuture<Void> save();

    /**
     * Returns an immutable copy of everything that defines this NPC.
     *
     * <p>Safe to hand to another thread, unlike the handle itself.
     *
     * @return the snapshot
     */
    NpcSnapshot snapshot();

    /**
     * Applies every property of a snapshot to this NPC, except its unique id.
     *
     * <p>How an undo, a template or a migration restores an NPC in one step.
     *
     * @param snapshot the snapshot to apply
     * @throws NullPointerException     if {@code snapshot} is {@code null}
     * @throws IllegalArgumentException if the snapshot's name belongs to a different NPC
     * @throws IllegalStateException    if the NPC has been removed
     */
    void apply(NpcSnapshot snapshot);
}
