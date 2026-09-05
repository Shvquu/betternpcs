package dev.shvquu.betternpcs.api.skin;

import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves {@link SkinSource}s into {@link NpcSkin}s, with caching.
 *
 * <p>Every method that may need a network call returns a {@link CompletableFuture} and does its work
 * off the main thread. That is not a style preference: a Mojang lookup takes tens to hundreds of
 * milliseconds, and a server tick is fifty, so a synchronous lookup during a chunk load would stall
 * the whole server. The only synchronous method here, {@link #cached(SkinSource)}, is explicitly the
 * one that never talks to the network.
 *
 * <p>Returned futures complete on a background thread. Bukkit API calls in a continuation must be
 * scheduled back onto the main thread.
 *
 * @since 1.0.0
 */
public interface SkinService {

    /**
     * Resolves a skin, consulting the cache first and the registered providers second.
     *
     * @param source the source to resolve
     * @return a future completing with the skin, or with an empty value if no provider knows it;
     *         the future completes exceptionally only if every provider failed
     * @throws NullPointerException if {@code source} is {@code null}
     */
    CompletableFuture<Optional<NpcSkin>> resolve(SkinSource source);

    /**
     * Returns a skin from the cache without any lookup.
     *
     * <p>Safe to call on the main thread. Use it to render immediately when the skin happens to be
     * known and fall back to {@link #resolve(SkinSource)} otherwise.
     *
     * @param source the source to look up
     * @return the cached skin, or empty if it has not been resolved yet or the entry has expired
     * @throws NullPointerException if {@code source} is {@code null}
     */
    Optional<NpcSkin> cached(SkinSource source);

    /**
     * Drops the cached skin for a source, so that the next resolution fetches it again.
     *
     * @param source the source to forget
     * @throws NullPointerException if {@code source} is {@code null}
     */
    void invalidate(SkinSource source);

    /**
     * Drops every cached skin.
     *
     * <p>Called by {@code /npc reload}. Cheap — the next render of each NPC re-resolves what it
     * needs — but it does mean a burst of lookups on a busy server, so it is not something to do on
     * a timer.
     */
    void invalidateAll();

    /**
     * Registers a provider.
     *
     * @param provider the provider to add
     * @throws NullPointerException  if {@code provider} is {@code null}
     * @throws IllegalStateException if a provider with the same {@link SkinProvider#id()} is already
     *                               registered
     */
    void registerProvider(SkinProvider provider);

    /**
     * Removes a previously registered provider.
     *
     * @param providerId the id of the provider to remove
     * @return {@code true} if a provider was removed
     * @throws NullPointerException if {@code providerId} is {@code null}
     */
    boolean unregisterProvider(String providerId);
}
