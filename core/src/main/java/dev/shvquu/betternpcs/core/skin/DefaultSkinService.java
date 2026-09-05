package dev.shvquu.betternpcs.core.skin;

import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import dev.shvquu.betternpcs.api.skin.SkinProvider;
import dev.shvquu.betternpcs.api.skin.SkinService;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The default {@link SkinService}: a cache in front of a priority-ordered chain of providers.
 *
 * <p>Two behaviours are worth knowing about.
 *
 * <p><b>In-flight requests are shared.</b> Fifty NPCs configured with the same skin, all spawning as
 * a world loads, produce one lookup rather than fifty. Without that, a restart of a hub server would
 * open a burst of identical HTTP requests and get rate limited for it.
 *
 * <p><b>A provider that fails does not fail the request.</b> Its failure is logged and the next
 * provider is tried, because a custom skin plugin being down is not a reason to stop asking Mojang.
 * Only when every provider has been tried does the result come back empty.
 *
 * @since 1.0.0
 */
public final class DefaultSkinService implements SkinService {

    private final Map<String, SkinProvider> providers = new ConcurrentHashMap<>();
    private final Map<SkinSource, CompletableFuture<Optional<NpcSkin>>> inFlight = new ConcurrentHashMap<>();
    private final SkinCache cache;
    private final Logger logger;

    /**
     * Creates the service.
     *
     * @param cache  where resolved skins are kept
     * @param logger where provider failures are reported
     * @throws NullPointerException if either argument is {@code null}
     */
    public DefaultSkinService(SkinCache cache, Logger logger) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<Optional<NpcSkin>> resolve(SkinSource source) {
        Objects.requireNonNull(source, "source");

        // A source that already carries its texture needs no lookup and no cache entry.
        if (source instanceof SkinSource.ByTexture texture) {
            return CompletableFuture.completedFuture(Optional.of(texture.skin()));
        }

        Optional<NpcSkin> cached = cache.get(source);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        // computeIfAbsent, so that concurrent callers for the same source share one lookup. The
        // entry is removed when the future settles, which is what stops a failed lookup from being
        // remembered as permanently failed.
        return inFlight.computeIfAbsent(source, key -> {
            CompletableFuture<Optional<NpcSkin>> lookup = tryProviders(key, orderedProviders(key), 0);
            return lookup.whenComplete((result, failure) -> inFlight.remove(key));
        });
    }

    private List<SkinProvider> orderedProviders(SkinSource source) {
        List<SkinProvider> candidates = new ArrayList<>(providers.values());
        candidates.removeIf(provider -> !provider.supports(source));
        candidates.sort(Comparator.comparingInt(SkinProvider::priority).reversed());
        return candidates;
    }

    private CompletableFuture<Optional<NpcSkin>> tryProviders(
            SkinSource source, List<SkinProvider> candidates, int index) {

        if (index >= candidates.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        SkinProvider provider = candidates.get(index);
        CompletableFuture<Optional<NpcSkin>> attempt;
        try {
            attempt = provider.fetch(source);
        } catch (RuntimeException immediateFailure) {
            // A provider that throws synchronously rather than returning a failed future is still a
            // provider that did not answer. Treat both the same way.
            attempt = CompletableFuture.failedFuture(immediateFailure);
        }

        return attempt
                .exceptionally(failure -> {
                    logger.log(Level.WARNING, failure, () ->
                            "The skin provider '" + provider.id() + "' failed for " + describe(source)
                                    + ". Trying the next provider.");
                    return Optional.empty();
                })
                .thenCompose(result -> {
                    if (result.isPresent()) {
                        cache.put(source, result.get());
                        return CompletableFuture.completedFuture(result);
                    }
                    return tryProviders(source, candidates, index + 1);
                });
    }

    private static String describe(SkinSource source) {
        // Never the raw value: a texture source's value is several hundred characters of base64.
        return source.kind().name().toLowerCase(Locale.ROOT) + " '"
                + (source.kind() == SkinSource.Kind.TEXTURE ? "<texture>" : source.value()) + "'";
    }

    @Override
    public Optional<NpcSkin> cached(SkinSource source) {
        Objects.requireNonNull(source, "source");
        if (source instanceof SkinSource.ByTexture texture) {
            return Optional.of(texture.skin());
        }
        return cache.get(source);
    }

    @Override
    public void invalidate(SkinSource source) {
        cache.invalidate(source);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Override
    public void registerProvider(SkinProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = Objects.requireNonNull(provider.id(), "provider.id").trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("A skin provider id must not be blank");
        }
        if (providers.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("A skin provider with the id '" + id + "' is already registered");
        }
    }

    @Override
    public boolean unregisterProvider(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return providers.remove(providerId.trim().toLowerCase(Locale.ROOT)) != null;
    }

    /**
     * Returns the registered providers, highest priority first.
     *
     * @return an immutable snapshot
     */
    public List<SkinProvider> providers() {
        List<SkinProvider> ordered = new ArrayList<>(providers.values());
        ordered.sort(Comparator.comparingInt(SkinProvider::priority).reversed());
        return List.copyOf(ordered);
    }
}
