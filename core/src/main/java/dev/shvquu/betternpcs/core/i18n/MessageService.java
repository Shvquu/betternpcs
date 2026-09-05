package dev.shvquu.betternpcs.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Renders and sends localised messages.
 *
 * <p>The single place BetterNPCs turns a {@link Message} into text a player sees. Nothing else in
 * the plugin parses MiniMessage or picks a language, which is what keeps the escaping rule in
 * {@link Placeholders} impossible to forget in one place and get wrong in another.
 *
 * @since 1.0.0
 */
public interface MessageService {

    /**
     * Renders a message in the recipient's language, without the prefix.
     *
     * @param recipient    who the message is for; decides the language. {@code null} uses the
     *                     configured default, which is what background tasks and log lines want
     * @param message      the message
     * @param placeholders the values to substitute, built with {@link Placeholders}
     * @return the rendered component
     * @throws NullPointerException if {@code message} or {@code placeholders} is {@code null}
     */
    Component render(CommandSender recipient, Message message, TagResolver... placeholders);

    /**
     * Renders a message in the default language, without the prefix.
     *
     * @param message      the message
     * @param placeholders the values to substitute
     * @return the rendered component
     * @throws NullPointerException if {@code message} or {@code placeholders} is {@code null}
     */
    default Component render(Message message, TagResolver... placeholders) {
        return render(null, message, placeholders);
    }

    /**
     * Sends a message with the prefix.
     *
     * <p>Does nothing when {@code recipient} is {@code null}, so a command that may have been run by
     * something that has since gone away does not have to guard every call.
     *
     * @param recipient    who to send it to, may be {@code null}
     * @param message      the message
     * @param placeholders the values to substitute
     * @throws NullPointerException if {@code message} or {@code placeholders} is {@code null}
     */
    void send(CommandSender recipient, Message message, TagResolver... placeholders);

    /**
     * Sends a message without the prefix.
     *
     * <p>For the body of a multi-line reply, where repeating the prefix on every line would be
     * noise.
     *
     * @param recipient    who to send it to, may be {@code null}
     * @param message      the message
     * @param placeholders the values to substitute
     * @throws NullPointerException if {@code message} or {@code placeholders} is {@code null}
     */
    void sendUnprefixed(CommandSender recipient, Message message, TagResolver... placeholders);

    /**
     * Sends an already-rendered component with the prefix.
     *
     * @param recipient who to send it to, may be {@code null}
     * @param component the component to send
     * @throws NullPointerException if {@code component} is {@code null}
     */
    void send(CommandSender recipient, Component component);

    /**
     * Renders arbitrary MiniMessage source in the recipient's context.
     *
     * <p>Used for NPC display names, nametags and hologram lines, which are written by an
     * administrator rather than translated.
     *
     * @param recipient    who the text is for, may be {@code null}
     * @param miniMessage  the MiniMessage source
     * @param placeholders the values to substitute
     * @return the rendered component
     * @throws NullPointerException if {@code miniMessage} or {@code placeholders} is {@code null}
     */
    Component renderRaw(CommandSender recipient, String miniMessage, TagResolver... placeholders);

    /**
     * Expands external placeholder tokens without parsing the result as MiniMessage.
     *
     * <p>For the action arguments that are not messages: a command line, a sound key, a set of
     * coordinates. Running MiniMessage over those would at best do nothing and at worst mangle a
     * legitimate {@code <} in a command.
     *
     * <p>The result is <em>not</em> escaped, because there is no parser to protect. Callers putting
     * it somewhere privileged — a console command — are responsible for the consequences, which is
     * why that action carries its own permission.
     *
     * @param recipient who the text is for, may be {@code null}
     * @param text      the text to expand
     * @return the expanded text
     * @throws NullPointerException if {@code text} is {@code null}
     */
    String expandPlaceholders(CommandSender recipient, String text);

    /**
     * Returns the prefix in the recipient's language.
     *
     * @param recipient who the prefix is for, may be {@code null}
     * @return the rendered prefix
     */
    Component prefix(CommandSender recipient);

    /**
     * Returns the language manager backing this service.
     *
     * @return the language manager
     */
    LanguageManager languages();
}
