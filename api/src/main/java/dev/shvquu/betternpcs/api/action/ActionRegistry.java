package dev.shvquu.betternpcs.api.action;

import java.util.Collection;
import java.util.Optional;

/**
 * The set of {@link NpcActionHandler}s an NPC's actions can refer to.
 *
 * <p>Holds the built-in handlers listed in {@link BuiltinActions} and whatever extensions add.
 * Lookups are by id or alias, case insensitively, matching how {@link ActionDefinition} normalises a
 * type.
 *
 * <p>Implementations are thread safe for reads; registration happens during plugin enable and is
 * expected on the main thread.
 *
 * @since 1.0.0
 */
public interface ActionRegistry {

    /**
     * Registers a handler under its id and every alias.
     *
     * @param handler the handler to add
     * @throws NullPointerException     if {@code handler} is {@code null}
     * @throws IllegalArgumentException if the handler's id or any alias is blank, or contains a
     *                                  colon or whitespace
     * @throws IllegalStateException    if the id or an alias is already taken; conflicts are an
     *                                  error rather than a silent overwrite, because an extension
     *                                  quietly replacing {@code command} would be a serious
     *                                  security surprise
     */
    void register(NpcActionHandler handler);

    /**
     * Removes a handler and its aliases.
     *
     * <p>NPCs that still refer to it keep their configuration; the action simply logs that no
     * handler serves it, so re-registering restores the behaviour.
     *
     * @param id the handler id, not an alias
     * @return {@code true} if a handler was removed
     * @throws NullPointerException if {@code id} is {@code null}
     */
    boolean unregister(String id);

    /**
     * Looks up a handler by id or alias.
     *
     * @param idOrAlias the id or alias, matched case insensitively
     * @return the handler, or empty if none is registered under that name
     * @throws NullPointerException if {@code idOrAlias} is {@code null}
     */
    Optional<NpcActionHandler> find(String idOrAlias);

    /**
     * Returns whether a handler is registered under the given name.
     *
     * @param idOrAlias the id or alias
     * @return {@code true} if {@link #find(String)} would return a handler
     * @throws NullPointerException if {@code idOrAlias} is {@code null}
     */
    default boolean isRegistered(String idOrAlias) {
        return find(idOrAlias).isPresent();
    }

    /**
     * Returns every registered handler, once each regardless of how many aliases it has.
     *
     * @return an immutable snapshot
     */
    Collection<NpcActionHandler> handlers();

    /**
     * Checks a definition against the handler that would run it.
     *
     * <p>Combines the "is there a handler" and "is the argument sensible" checks that every place
     * accepting an action needs to make.
     *
     * @param definition the definition to check
     * @throws NullPointerException     if {@code definition} is {@code null}
     * @throws IllegalArgumentException if no handler is registered for the type, or the handler
     *                                  rejects the argument
     */
    void validate(ActionDefinition definition);
}
