package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.core.i18n.PlaceholderExpander;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Resolves PlaceholderAPI tokens, without BetterNPCs depending on PlaceholderAPI.
 *
 * <h2>Why reflection</h2>
 *
 * <p>PlaceholderAPI is optional. Referencing its classes directly would make this class fail to load
 * when it is absent, and a soft dependency handled with a {@code try/catch} around a direct call
 * would still need the class present to verify the method. One method handle, resolved once at
 * construction, keeps the dependency genuinely optional at the cost of a single indirection per
 * token.
 *
 * <p>The handle is resolved eagerly rather than per call, so a PlaceholderAPI that is present but
 * unusable — a version whose signature changed — is discovered at startup rather than on the first
 * message.
 *
 * @since 1.0.0
 */
final class PlaceholderApiExpander implements PlaceholderExpander {

    private static final String PLACEHOLDER_API = "me.clip.placeholderapi.PlaceholderAPI";

    private final MethodHandle setPlaceholders;

    /**
     * Creates the expander.
     *
     * @throws IllegalStateException if PlaceholderAPI is present but does not expose the expected
     *                               method, which means the caller should fall back to
     *                               {@link PlaceholderExpander#none()}
     */
    PlaceholderApiExpander() {
        try {
            Class<?> api = Class.forName(PLACEHOLDER_API);
            this.setPlaceholders = MethodHandles.publicLookup().findStatic(
                    api,
                    "setPlaceholders",
                    MethodType.methodType(String.class, OfflinePlayer.class, String.class));
        } catch (ReflectiveOperationException unusable) {
            throw new IllegalStateException(
                    "PlaceholderAPI is installed but does not expose "
                            + "setPlaceholders(OfflinePlayer, String). Its placeholders will not resolve.",
                    unusable);
        }
    }

    @Override
    public String expand(CommandSender recipient, String token) {
        if (!(recipient instanceof Player player)) {
            // Every PlaceholderAPI placeholder is relative to a player, so there is nothing to
            // resolve against. Returning the token keeps it visible rather than blanking it.
            return token;
        }
        try {
            return (String) setPlaceholders.invoke((OfflinePlayer) player, token);
        } catch (Throwable failure) {
            // An expansion that throws is that expansion's problem. Returning the token unchanged
            // costs one placeholder; letting the exception out would cost the whole message.
            return token;
        }
    }
}
