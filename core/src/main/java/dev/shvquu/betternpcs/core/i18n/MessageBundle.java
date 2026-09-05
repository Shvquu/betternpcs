package dev.shvquu.betternpcs.core.i18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

/**
 * One language's texts, with every missing key already filled in from English.
 *
 * <p>Resolving the fallback once at load time rather than on every lookup is what makes
 * {@link #text(Message)} a plain array read. It also means a partially translated language file
 * behaves predictably — the untranslated lines are English, not blank — and the gaps are reported
 * once at startup through {@link #missingKeys()} instead of being invisible.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class MessageBundle {

    private final String locale;
    private final Map<Message, String> texts;
    private final List<String> missingKeys;

    private MessageBundle(String locale, Map<Message, String> texts, List<String> missingKeys) {
        this.locale = locale;
        this.texts = texts;
        this.missingKeys = missingKeys;
    }

    /**
     * Returns the built-in English bundle, which by construction is never missing a key.
     *
     * @return the English bundle
     */
    public static MessageBundle english() {
        Map<Message, String> texts = new EnumMap<>(Message.class);
        for (Message message : Message.values()) {
            texts.put(message, message.englishText());
        }
        return new MessageBundle("en_US", Collections.unmodifiableMap(texts), List.of());
    }

    /**
     * Reads a bundle from a loaded language file.
     *
     * <p>Keys the file does not define fall back to English and are listed in
     * {@link #missingKeys()}. Keys the file defines that BetterNPCs does not know are ignored: they
     * are usually left over from an older version, and refusing to start over them would be
     * unhelpful.
     *
     * @param locale  the locale name, for example {@code de_DE}
     * @param section the loaded language file
     * @return the bundle
     * @throws NullPointerException if either argument is {@code null}
     */
    public static MessageBundle load(String locale, ConfigurationSection section) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(section, "section");

        Map<Message, String> texts = new EnumMap<>(Message.class);
        List<String> missing = new ArrayList<>();

        for (Message message : Message.values()) {
            String configured = section.getString(message.key());
            if (configured == null || configured.isEmpty()) {
                texts.put(message, message.englishText());
                missing.add(message.key());
            } else {
                texts.put(message, configured);
            }
        }

        return new MessageBundle(
                locale, Collections.unmodifiableMap(texts), List.copyOf(missing));
    }

    /**
     * Returns the locale name this bundle was loaded for.
     *
     * @return the locale name, for example {@code de_DE}
     */
    public String locale() {
        return locale;
    }

    /**
     * Returns the MiniMessage source for a message.
     *
     * @param message the message
     * @return the text, never {@code null} — an untranslated key yields the English text
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public String text(Message message) {
        Objects.requireNonNull(message, "message");
        return texts.get(message);
    }

    /**
     * Returns the keys this language file does not define, in declaration order.
     *
     * @return an immutable list, empty for a complete translation
     */
    public List<String> missingKeys() {
        return missingKeys;
    }

    /**
     * Returns whether every key is translated.
     *
     * @return {@code true} if nothing falls back to English
     */
    public boolean isComplete() {
        return missingKeys.isEmpty();
    }

    /**
     * Returns a description naming the locale and its completeness.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "MessageBundle[" + locale
                + (isComplete() ? ", complete" : ", " + missingKeys.size() + " untranslated") + ']';
    }
}
