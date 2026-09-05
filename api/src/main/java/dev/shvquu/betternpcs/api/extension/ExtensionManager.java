package dev.shvquu.betternpcs.api.extension;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Registers and sequences {@link NpcExtension}s.
 *
 * <p>Obtained from {@link dev.shvquu.betternpcs.api.BetterNPCsApi#extensionManager()}. All methods
 * must be called on the main server thread.
 *
 * @since 1.0.0
 */
public interface ExtensionManager {

    /**
     * The state an extension is in.
     *
     * @since 1.0.0
     */
    enum State {

        /** Registered, waiting for a dependency that is not registered yet. */
        PENDING,

        /** Enabled and running. */
        ENABLED,

        /** {@link NpcExtension#onEnable} threw, or a dependency failed. */
        FAILED,

        /** Was enabled and has since been disabled. */
        DISABLED
    }

    /**
     * Registers an extension, enabling it immediately if its dependencies allow.
     *
     * <p>Registering an extension can enable others: anything that was {@link State#PENDING} waiting
     * for this one is enabled as part of the same call, in dependency order.
     *
     * @param extension the extension to register
     * @throws NullPointerException  if {@code extension} is {@code null}
     * @throws IllegalStateException if an extension with the same id is already registered
     */
    void register(NpcExtension extension);

    /**
     * Disables and removes an extension, along with everything registered while it was enabled.
     *
     * <p>Extensions that depend on it are disabled first, and become {@link State#PENDING} again
     * rather than being removed, so re-registering restores the whole chain.
     *
     * @param id the extension id
     * @return {@code true} if an extension was removed
     * @throws NullPointerException if {@code id} is {@code null}
     */
    boolean unregister(String id);

    /**
     * Looks an extension up by id.
     *
     * @param id the extension id
     * @return the extension, or empty if none is registered under that id
     * @throws NullPointerException if {@code id} is {@code null}
     */
    Optional<NpcExtension> find(String id);

    /**
     * Returns the state of an extension.
     *
     * @param id the extension id
     * @return the state, or empty if none is registered under that id
     * @throws NullPointerException if {@code id} is {@code null}
     */
    Optional<State> state(String id);

    /**
     * Returns whether an extension is registered and enabled.
     *
     * <p>What another extension checks before using an optional integration.
     *
     * @param id the extension id
     * @return {@code true} if the extension is in {@link State#ENABLED}
     * @throws NullPointerException if {@code id} is {@code null}
     */
    default boolean isEnabled(String id) {
        return state(id).filter(State.ENABLED::equals).isPresent();
    }

    /**
     * Returns every registered extension, whatever its state.
     *
     * @return an immutable snapshot
     */
    Collection<NpcExtension> extensions();

    /**
     * Returns the ids that registered extensions depend on but which nothing provides.
     *
     * <p>Reported at the end of startup, so that a forgotten plugin is a clear message rather than
     * an extension silently doing nothing.
     *
     * @return an immutable set of unsatisfied dependency ids
     */
    Set<String> unsatisfiedDependencies();
}
