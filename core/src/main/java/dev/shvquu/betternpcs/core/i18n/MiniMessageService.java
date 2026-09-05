package dev.shvquu.betternpcs.core.i18n;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The MiniMessage-backed {@link MessageService}.
 *
 * <p>Rendering is two steps in a fixed order. External placeholder tokens are expanded first, one at
 * a time and each escaped on its own; the result is then parsed as MiniMessage. The order and the
 * granularity are both load-bearing — see {@link #expandTokens(CommandSender, String)}.
 *
 * <p>Thread safe: {@link MiniMessage} is, and {@link LanguageManager} replaces its bundles wholesale
 * rather than mutating them.
 *
 * @since 1.0.0
 */
public final class MiniMessageService implements MessageService {

    /**
     * One external placeholder token.
     *
     * <p>Deliberately conservative. Real identifiers are letters, digits and the few punctuation
     * characters expansions use as separators; anything looser would start matching arbitrary prose
     * that happens to sit between two percent signs. Whitespace and a second percent sign end a
     * token, which is what keeps a sentence about percentages from becoming one enormous
     * placeholder.
     */
    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("%[A-Za-z0-9_.:+-]+%");

    private final MiniMessage miniMessage;
    private final LanguageManager languages;
    private final PlaceholderExpander expander;

    /**
     * Creates the service.
     *
     * @param languages the loaded language files
     * @param expander  expands external placeholder tokens; use
     *                  {@link PlaceholderExpander#none()} when PlaceholderAPI is not installed
     * @throws NullPointerException if either argument is {@code null}
     */
    public MiniMessageService(LanguageManager languages, PlaceholderExpander expander) {
        this.languages = Objects.requireNonNull(languages, "languages");
        this.expander = Objects.requireNonNull(expander, "expander");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public Component render(CommandSender recipient, Message message, TagResolver... placeholders) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(placeholders, "placeholders");
        return parse(recipient, bundleFor(recipient).text(message), placeholders);
    }

    @Override
    public Component renderRaw(CommandSender recipient, String source, TagResolver... placeholders) {
        Objects.requireNonNull(source, "miniMessage");
        Objects.requireNonNull(placeholders, "placeholders");
        return parse(recipient, source, placeholders);
    }

    @Override
    public String expandPlaceholders(CommandSender recipient, String text) {
        Objects.requireNonNull(text, "text");
        return expandTokens(recipient, text, false);
    }

    private Component parse(CommandSender recipient, String source, TagResolver... placeholders) {
        return miniMessage.deserialize(
                expandTokens(recipient, source, true), TagResolver.resolver(placeholders));
    }

    /**
     * Expands each external placeholder token on its own and escapes what comes back.
     *
     * <p>The obvious implementation — hand the whole line to PlaceholderAPI, then parse the result —
     * cannot be made safe. Escaping the result afterwards would destroy the message's own
     * formatting, which comes from a trusted language file; not escaping it lets a placeholder value
     * become live MiniMessage. A player named {@code <click:run_command:/op me>} would then produce
     * a clickable command inside a message shown to an administrator.
     *
     * <p>Expanding token by token separates the two concerns cleanly: template text is never
     * touched, and every expanded value is escaped in full, so a value can only ever become literal
     * text.
     *
     * @param recipient who the message is for
     * @param source    the message source
     * @param escape    {@code true} when the result will be parsed as MiniMessage and values must
     *                  therefore be neutralised; {@code false} for text that no parser will see,
     *                  such as a command line, where escaping would corrupt it instead
     * @return the source with tokens replaced by their values
     */
    private String expandTokens(CommandSender recipient, String source, boolean escape) {
        if (source.indexOf('%') < 0) {
            // Almost every line has no token at all, and this runs on every message sent. A single
            // character scan is far cheaper than starting a regex match.
            return source;
        }

        Matcher tokens = PLACEHOLDER_TOKEN.matcher(source);
        StringBuilder expanded = new StringBuilder(source.length());

        while (tokens.find()) {
            String token = tokens.group();
            String value = expander.expand(recipient, token);

            // An expander returns the token unchanged when nothing claims it. Passing that through
            // as-is keeps an unresolved token visible, which is also what happens when PlaceholderAPI
            // is not installed — one behaviour instead of two.
            String replacement = token.equals(value) || !escape ? value : miniMessage.escapeTags(value);

            tokens.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        tokens.appendTail(expanded);
        return expanded.toString();
    }

    @Override
    public void send(CommandSender recipient, Message message, TagResolver... placeholders) {
        if (recipient == null) {
            return;
        }
        recipient.sendMessage(prefix(recipient).append(render(recipient, message, placeholders)));
    }

    @Override
    public void sendUnprefixed(CommandSender recipient, Message message, TagResolver... placeholders) {
        if (recipient == null) {
            return;
        }
        recipient.sendMessage(render(recipient, message, placeholders));
    }

    @Override
    public void send(CommandSender recipient, Component component) {
        Objects.requireNonNull(component, "component");
        if (recipient == null) {
            return;
        }
        recipient.sendMessage(prefix(recipient).append(component));
    }

    @Override
    public Component prefix(CommandSender recipient) {
        return miniMessage.deserialize(bundleFor(recipient).text(Message.PREFIX));
    }

    @Override
    public LanguageManager languages() {
        return languages;
    }

    private MessageBundle bundleFor(CommandSender recipient) {
        if (recipient instanceof Player player) {
            return languages.bundleForClient(player.locale());
        }
        return languages.defaultBundle();
    }
}
