package dev.shvquu.betternpcs.api.skin;

import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A source of skin data that {@link SkinService} can consult.
 *
 * <p>The built-in provider talks to Mojang's session servers. Registering another one is how a
 * network with its own skin database, a custom skin plugin or an offline-mode setup supplies skins
 * without BetterNPCs knowing anything about it. Providers are tried in descending
 * {@link #priority()} order and the first non-empty answer wins, so a custom provider can shadow
 * Mojang for some names and defer for the rest.
 *
 * <p>Implementations are called off the main thread and must never block it.
 *
 * @since 1.0.0
 */
public interface SkinProvider {

    /**
     * The priority of the built-in Mojang provider.
     *
     * <p>Register above this to take precedence over Mojang, below it to act as a fallback.
     */
    int MOJANG_PRIORITY = 0;

    /**
     * Returns a stable id for this provider, used in configuration and diagnostics.
     *
     * @return the id, unique among registered providers
     */
    String id();

    /**
     * Returns this provider's position in the lookup order.
     *
     * <p>Higher values are consulted first.
     *
     * @return the priority; defaults to just above {@link #MOJANG_PRIORITY} so that a provider
     *         registered without thought still gets a chance before Mojang is asked
     */
    default int priority() {
        return MOJANG_PRIORITY + 1;
    }

    /**
     * Returns whether this provider can even attempt the given source.
     *
     * <p>Checked before {@link #fetch(SkinSource)} so that a provider which only understands, say,
     * player names is never asked about a unique id.
     *
     * @param source the source to resolve
     * @return {@code true} if {@link #fetch(SkinSource)} may be called with {@code source}
     */
    boolean supports(SkinSource source);

    /**
     * Fetches the skin for a source.
     *
     * <p>Called on a background thread. An empty result means "I do not know this one", and the next
     * provider is tried; a failed future means the lookup broke, which is logged and also falls
     * through to the next provider. Implementations must apply their own timeout — a provider that
     * hangs would otherwise stall every skin request behind it.
     *
     * @param source the source to resolve, guaranteed to satisfy {@link #supports(SkinSource)}
     * @return a future completing with the skin, or with an empty value if this provider has none
     */
    CompletableFuture<Optional<NpcSkin>> fetch(SkinSource source);
}
