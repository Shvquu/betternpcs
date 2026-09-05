package dev.shvquu.betternpcs.api.extension;

import dev.shvquu.betternpcs.api.BetterNPCsApi;
import java.util.Set;

/**
 * A unit of functionality another plugin adds to BetterNPCs.
 *
 * <p>An extension is not a plugin. It is a small object a plugin registers so that BetterNPCs can
 * tell it when to set up and tear down, and so that its handlers, providers and rules are
 * unregistered together when it goes away. A plugin that only wants to add one action handler does
 * not need one — {@link dev.shvquu.betternpcs.api.action.ActionRegistry} takes it directly. An
 * extension pays for itself once a plugin registers several related things, or once it needs to be
 * reloadable.
 *
 * <p>The engine also uses extensions to sequence startup: {@link #dependencies()} is honoured, so a
 * shop extension can rely on a dialog extension having been enabled first regardless of the order
 * the two plugins loaded in.
 *
 * @since 1.0.0
 */
public interface NpcExtension {

    /**
     * Returns a stable id, unique among registered extensions.
     *
     * <p>Used in log messages, in {@code /npc extensions}, and as the target of another extension's
     * {@link #dependencies()}. Namespace it with the owning plugin's name.
     *
     * @return the id
     */
    String id();

    /**
     * Returns a human-readable version, shown in diagnostics.
     *
     * @return the version; {@code "unknown"} by default
     */
    default String version() {
        return "unknown";
    }

    /**
     * Returns the ids of extensions that must be enabled before this one.
     *
     * <p>An extension whose dependencies are not registered stays pending rather than failing, and
     * is enabled as soon as they arrive. One whose dependencies never arrive is reported at the end
     * of startup, so a missing plugin produces a clear message rather than silence.
     *
     * @return the required extension ids; empty by default
     */
    default Set<String> dependencies() {
        return Set.of();
    }

    /**
     * Sets the extension up.
     *
     * <p>Called on the main thread, after BetterNPCs is fully initialised and after every dependency
     * has been enabled. Register action handlers, skin providers and visibility rules here rather
     * than in the owning plugin's {@code onEnable}, so that they are re-registered correctly if the
     * extension is reloaded.
     *
     * <p>Throwing marks the extension as failed: it is not enabled, anything depending on it is not
     * enabled either, and the failure is logged with the extension id. It does not stop BetterNPCs.
     *
     * @param api the API to register against
     */
    void onEnable(BetterNPCsApi api);

    /**
     * Tears the extension down.
     *
     * <p>Called on the main thread when the extension is unregistered or BetterNPCs shuts down, and
     * before any of its dependencies are disabled. Handlers, providers and rules registered during
     * {@link #onEnable(BetterNPCsApi)} are removed automatically; this is for anything else, such as
     * a scheduled task or an external connection.
     */
    default void onDisable() {
        // Most extensions have nothing of their own to clean up.
    }
}
