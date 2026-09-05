package dev.shvquu.betternpcs.core.skin;

import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A time-expiring cache of resolved skins.
 *
 * <p>Exists to keep BetterNPCs off Mojang's session servers, which rate limit aggressively enough
 * that a server with fifty player NPCs would be throttled within a minute of starting without it.
 *
 * <p>Entries expire rather than living forever so that a player who changes their skin is picked up
 * without a restart. Expiry is checked on read, not on a timer: a skin nobody asks for costs nothing
 * to keep, and a sweeping task would be work done for its own sake.
 *
 * <p>Thread safe. Reads happen on the main thread, writes on whichever thread a lookup completed on.
 *
 * @since 1.0.0
 */
public final class SkinCache {

    private record Entry(NpcSkin skin, long expiresAtNanos) {
    }

    private final Map<SkinSource, Entry> entries = new ConcurrentHashMap<>();
    private final Duration lifetime;
    private final LongSupplier clock;

    /**
     * Creates a cache.
     *
     * @param lifetime how long an entry stays valid; {@link Duration#ZERO} disables caching
     * @throws NullPointerException     if {@code lifetime} is {@code null}
     * @throws IllegalArgumentException if {@code lifetime} is negative
     */
    public SkinCache(Duration lifetime) {
        this(lifetime, System::nanoTime);
    }

    /**
     * Creates a cache with a supplied clock.
     *
     * <p>The clock is injected so that expiry can be tested without sleeping.
     *
     * @param lifetime how long an entry stays valid
     * @param clock    a monotonic nanosecond source
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code lifetime} is negative
     */
    public SkinCache(Duration lifetime, LongSupplier clock) {
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (lifetime.isNegative()) {
            throw new IllegalArgumentException("Skin cache lifetime must not be negative");
        }
    }

    /**
     * Returns a cached skin.
     *
     * @param source the source to look up
     * @return the skin, or empty if it is not cached or has expired
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public Optional<NpcSkin> get(SkinSource source) {
        Objects.requireNonNull(source, "source");
        if (isDisabled()) {
            return Optional.empty();
        }

        Entry entry = entries.get(source);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.getAsLong() - entry.expiresAtNanos() >= 0) {
            // Removed by value: another thread may have refreshed it between the read and here, and
            // dropping that fresher entry would cause a second lookup for no reason.
            entries.remove(source, entry);
            return Optional.empty();
        }
        return Optional.of(entry.skin());
    }

    /**
     * Stores a resolved skin.
     *
     * @param source the source it was resolved from
     * @param skin   the resolved skin
     * @throws NullPointerException if either argument is {@code null}
     */
    public void put(SkinSource source, NpcSkin skin) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(skin, "skin");
        if (isDisabled()) {
            return;
        }
        entries.put(source, new Entry(skin, clock.getAsLong() + lifetime.toNanos()));
    }

    /**
     * Drops one entry.
     *
     * @param source the source to forget
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public void invalidate(SkinSource source) {
        entries.remove(Objects.requireNonNull(source, "source"));
    }

    /**
     * Drops every entry.
     */
    public void invalidateAll() {
        entries.clear();
    }

    /**
     * Returns how many entries are held, expired ones included.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns whether caching is switched off.
     *
     * @return {@code true} if the configured lifetime is zero
     */
    public boolean isDisabled() {
        return lifetime.isZero();
    }
}
