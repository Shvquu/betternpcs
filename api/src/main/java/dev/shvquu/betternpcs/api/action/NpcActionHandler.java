package dev.shvquu.betternpcs.api.action;

import java.util.Set;

/**
 * Carries out one kind of NPC action.
 *
 * <p>The extension point behind {@link ActionDefinition}: registering a handler with
 * {@link ActionRegistry} makes {@code myaction: something} usable in every NPC's configuration, in
 * {@code /npc action}, and in every existing NPC without BetterNPCs knowing what it does.
 *
 * <p>Implementations must be thread safe in the sense that they hold no per-execution state —
 * {@link #execute(ActionContext)} may be entered for several players in the same tick, and a handler
 * that keeps state in a field will mix them up. Per-execution state belongs in
 * {@link ActionContext#attribute(String, Object)}.
 *
 * @since 1.0.0
 */
public interface NpcActionHandler {

    /**
     * Returns the id this handler is addressed by in configuration.
     *
     * <p>Must be lower case, and must not contain a colon or whitespace, matching the constraints
     * {@link ActionDefinition} enforces on a type.
     *
     * @return the id, for example {@code message}
     */
    String id();

    /**
     * Returns alternative ids that resolve to this handler.
     *
     * <p>Same constraints as {@link #id()}. Useful for keeping an old name working after a rename.
     *
     * @return the aliases; empty by default
     */
    default Set<String> aliases() {
        return Set.of();
    }

    /**
     * Returns a short description of what the argument should look like, shown in command help.
     *
     * @return the description, for example {@code "<MiniMessage text>"}
     */
    default String argumentDescription() {
        return "<argument>";
    }

    /**
     * Checks an argument before it is stored.
     *
     * <p>Called when an action is added through a command or loaded from storage, so that a typo is
     * reported at the point it is made rather than the first time a player clicks the NPC. The
     * default accepts anything.
     *
     * <p>Must not have side effects and must not touch the world — it runs during startup, before
     * the server is necessarily ready.
     *
     * @param argument the argument to check
     * @throws IllegalArgumentException if the argument cannot be executed, with a message suitable
     *                                  for showing to whoever typed it
     */
    default void validate(String argument) {
        // Accept anything by default; handlers with a parseable argument override this.
    }

    /**
     * Carries out the action.
     *
     * <p>Called on the main server thread. Work that would block — a database read, an HTTP call —
     * must be moved off it and its result scheduled back, exactly as anywhere else in a plugin.
     *
     * @param context everything the action needs
     * @throws RuntimeException if the action fails; the engine logs it with the NPC and player as
     *                          context and continues with the next action in the chain
     */
    void execute(ActionContext context);
}
