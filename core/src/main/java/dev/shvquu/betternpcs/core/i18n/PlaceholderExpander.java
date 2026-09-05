package dev.shvquu.betternpcs.core.i18n;

import org.bukkit.command.CommandSender;

/**
 * Expands one external placeholder token, such as PlaceholderAPI's {@code %player_name%}.
 *
 * <p>An interface rather than a direct PlaceholderAPI call so that core carries no optional
 * dependency: the plugin module supplies an implementation when PlaceholderAPI is installed and
 * {@link #none()} when it is not, and nothing else in the engine has to know which.
 *
 * <p>Implementations are called once per token, not once per message. That is what allows
 * {@link MiniMessageService} to escape each expanded value on its own without touching the trusted
 * message template around it.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface PlaceholderExpander {

    /**
     * Expands a single token.
     *
     * @param recipient who the message is for; may be {@code null} for the console or a background
     *                  task, in which case an implementation with nothing to resolve against should
     *                  return {@code token} unchanged
     * @param token     the token including its delimiters, for example {@code %player_name%}
     * @return the expanded value, or {@code token} unchanged if nothing claims it — returning the
     *         token keeps an unresolved placeholder visible rather than silently blanking part of a
     *         message
     */
    String expand(CommandSender recipient, String token);

    /**
     * Returns an expander that resolves nothing, leaving every token as written.
     *
     * @return the no-op expander
     */
    static PlaceholderExpander none() {
        return (recipient, token) -> token;
    }
}
