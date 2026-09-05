package dev.shvquu.betternpcs.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Static access to the running {@link BetterNPCsApi}.
 *
 * <p>Convenience over Bukkit's services manager, which is the underlying registration and works just
 * as well:
 *
 * <pre>{@code
 * BetterNPCsApi api = BetterNPCs.get();
 * Npc shop = api.npcManager().byName("shop").orElseThrow();
 * }</pre>
 *
 * <p>The API is available from the moment BetterNPCs enables. A dependent plugin should either
 * declare {@code depend: [BetterNPCs]} in its own plugin description, which makes Bukkit load it
 * afterwards, or use {@link #isAvailable()} rather than assuming.
 *
 * @since 1.0.0
 */
public final class BetterNPCs {

    private static volatile BetterNPCsApi instance;

    private BetterNPCs() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns the running API.
     *
     * @return the API
     * @throws IllegalStateException if BetterNPCs is not enabled, which usually means the calling
     *                               plugin did not declare a dependency on it and was loaded first
     */
    public static BetterNPCsApi get() {
        BetterNPCsApi current = instance;
        if (current == null) {
            throw new IllegalStateException(
                    "BetterNPCs is not enabled. Declare 'depend: [BetterNPCs]' in your plugin.yml, "
                            + "or guard the call with BetterNPCs.isAvailable().");
        }
        return current;
    }

    /**
     * Returns the running API if there is one.
     *
     * @return the API, or empty if BetterNPCs is not enabled
     */
    public static Optional<BetterNPCsApi> find() {
        return Optional.ofNullable(instance);
    }

    /**
     * Returns whether the API is available.
     *
     * @return {@code true} if {@link #get()} would succeed
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Publishes the API. Called by the BetterNPCs plugin during enable.
     *
     * @param api the implementation to publish
     * @throws NullPointerException  if {@code api} is {@code null}
     * @throws IllegalStateException if an implementation is already published, which would mean two
     *                               copies of BetterNPCs are loaded
     */
    @Internal
    public static void register(BetterNPCsApi api) {
        Objects.requireNonNull(api, "api");
        synchronized (BetterNPCs.class) {
            if (instance != null) {
                throw new IllegalStateException(
                        "A BetterNPCs API implementation is already registered. Two copies of the "
                                + "plugin appear to be installed.");
            }
            instance = api;
        }
    }

    /**
     * Withdraws the API. Called by the BetterNPCs plugin during disable.
     *
     * <p>Deliberately tolerant of being called when nothing is registered: shutdown runs after a
     * failed startup too, and a second exception there would bury the first.
     */
    @Internal
    public static void unregister() {
        synchronized (BetterNPCs.class) {
            instance = null;
        }
    }
}
