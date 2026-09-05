package dev.shvquu.betternpcs.core.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Reads typed values out of a {@link ConfigurationSection}, reporting problems against their full
 * path.
 *
 * <p>Bukkit's own getters silently substitute a default for anything they cannot parse, so a port
 * typed as {@code "3306 "} with a stray space becomes {@code 0} and the failure surfaces much later
 * as a connection error. This reader refuses instead, and the resulting
 * {@link ConfigurationException} names the exact line to fix.
 *
 * <p>Missing values are a different matter and do fall back to the supplied default: a configuration
 * file written by an older version is missing whatever the new one added, and refusing to start over
 * that would make every update a manual migration.
 *
 * @since 1.0.0
 */
public final class ConfigReader {

    private final ConfigurationSection section;
    private final String prefix;

    private ConfigReader(ConfigurationSection section, String prefix) {
        this.section = section;
        this.prefix = prefix;
    }

    /**
     * Returns a reader over a whole configuration.
     *
     * @param section the root section
     * @return the reader
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public static ConfigReader of(ConfigurationSection section) {
        return new ConfigReader(Objects.requireNonNull(section, "section"), "");
    }

    /**
     * Returns a reader over a child section.
     *
     * <p>A missing child is not an error: it reads as an empty section, so every value in it falls
     * back to its default. That is what lets a new configuration block be added in a release without
     * breaking existing installations.
     *
     * @param key the child key
     * @return a reader over the child
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws ConfigurationException   if the key exists but is not a section
     */
    public ConfigReader section(String key) {
        Objects.requireNonNull(key, "key");
        String path = path(key);
        if (!section.contains(key)) {
            // A detached empty section, not section.createSection(key): creating it here would add
            // the empty block to the in-memory configuration and write it back on the next save.
            return new ConfigReader(new MemoryConfiguration(), path);
        }
        ConfigurationSection child = section.getConfigurationSection(key);
        if (child == null) {
            throw new ConfigurationException(path, "must be a section, not a single value");
        }
        return new ConfigReader(child, path);
    }

    /**
     * Reads a string.
     *
     * @param key          the key
     * @param defaultValue the value to use when the key is absent
     * @return the configured value, or {@code defaultValue}
     * @throws ConfigurationException if the key holds a list or a section
     */
    public String string(String key, String defaultValue) {
        if (!section.contains(key)) {
            return defaultValue;
        }
        Object raw = section.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Iterable<?> || raw instanceof ConfigurationSection) {
            throw new ConfigurationException(path(key), "must be a single value");
        }
        return String.valueOf(raw);
    }

    /**
     * Reads a boolean.
     *
     * @param key          the key
     * @param defaultValue the value to use when the key is absent
     * @return the configured value, or {@code defaultValue}
     * @throws ConfigurationException if the value is not {@code true} or {@code false}
     */
    public boolean bool(String key, boolean defaultValue) {
        if (!section.contains(key)) {
            return defaultValue;
        }
        Object raw = section.get(key);
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = String.valueOf(raw).trim();
        if (text.equalsIgnoreCase("true")) {
            return true;
        }
        if (text.equalsIgnoreCase("false")) {
            return false;
        }
        throw new ConfigurationException(path(key), "must be true or false, was '" + text + "'");
    }

    /**
     * Reads an integer within a range.
     *
     * @param key          the key
     * @param defaultValue the value to use when the key is absent
     * @param min          the smallest accepted value, inclusive
     * @param max          the largest accepted value, inclusive
     * @return the configured value, or {@code defaultValue}
     * @throws ConfigurationException if the value is not a whole number, or is outside the range
     */
    public int integer(String key, int defaultValue, int min, int max) {
        long value = number(key, defaultValue, min, max);
        return (int) value;
    }

    /**
     * Reads a long within a range.
     *
     * @param key          the key
     * @param defaultValue the value to use when the key is absent
     * @param min          the smallest accepted value, inclusive
     * @param max          the largest accepted value, inclusive
     * @return the configured value, or {@code defaultValue}
     * @throws ConfigurationException if the value is not a whole number, or is outside the range
     */
    public long number(String key, long defaultValue, long min, long max) {
        if (!section.contains(key)) {
            return defaultValue;
        }
        Object raw = section.get(key);
        long value;
        if (raw instanceof Number parsed) {
            if (parsed.doubleValue() != Math.floor(parsed.doubleValue())) {
                throw new ConfigurationException(path(key), "must be a whole number, was " + parsed);
            }
            value = parsed.longValue();
        } else {
            try {
                value = Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException notANumber) {
                throw new ConfigurationException(
                        path(key), "must be a whole number, was '" + raw + "'", notANumber);
            }
        }
        if (value < min || value > max) {
            throw new ConfigurationException(
                    path(key), "must be between " + min + " and " + max + ", was " + value);
        }
        return value;
    }

    /**
     * Reads a decimal within a range.
     *
     * @param key          the key
     * @param defaultValue the value to use when the key is absent
     * @param min          the smallest accepted value, inclusive
     * @param max          the largest accepted value, inclusive
     * @return the configured value, or {@code defaultValue}
     * @throws ConfigurationException if the value is not a finite number, or is outside the range
     */
    public double decimal(String key, double defaultValue, double min, double max) {
        if (!section.contains(key)) {
            return defaultValue;
        }
        Object raw = section.get(key);
        double value;
        if (raw instanceof Number parsed) {
            value = parsed.doubleValue();
        } else {
            try {
                value = Double.parseDouble(String.valueOf(raw).trim());
            } catch (NumberFormatException notANumber) {
                throw new ConfigurationException(
                        path(key), "must be a number, was '" + raw + "'", notANumber);
            }
        }
        if (!Double.isFinite(value)) {
            throw new ConfigurationException(path(key), "must be a finite number, was " + value);
        }
        if (value < min || value > max) {
            throw new ConfigurationException(
                    path(key), "must be between " + min + " and " + max + ", was " + value);
        }
        return value;
    }

    /**
     * Reads a section of string keys and values, used for pass-through driver properties.
     *
     * @param key the key of the child section
     * @return the entries in file order, empty if the section is absent
     * @throws ConfigurationException if the key exists but is not a section, or a value is itself a
     *                                section
     */
    public Map<String, String> stringMap(String key) {
        Objects.requireNonNull(key, "key");
        if (!section.contains(key)) {
            return Map.of();
        }
        ConfigurationSection child = section.getConfigurationSection(key);
        if (child == null) {
            throw new ConfigurationException(path(key), "must be a section of key-value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String childKey : child.getKeys(false)) {
            Object raw = child.get(childKey);
            if (raw instanceof ConfigurationSection) {
                throw new ConfigurationException(
                        path(key) + "." + childKey, "must be a single value, not a nested section");
            }
            values.put(childKey, String.valueOf(raw));
        }
        return values;
    }

    /**
     * Reads an enum constant, case insensitively.
     *
     * @param key          the key
     * @param defaultValue the value to use when the key is absent
     * @param type         the enum type
     * @param <E>          the enum type
     * @return the configured constant, or {@code defaultValue}
     * @throws ConfigurationException if the value names no constant of {@code type}
     */
    public <E extends Enum<E>> E enumeration(String key, E defaultValue, Class<E> type) {
        Objects.requireNonNull(type, "type");
        String raw = string(key, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(normalised)) {
                return candidate;
            }
        }
        throw new ConfigurationException(
                path(key), "must be one of " + String.join(", ", names(type)) + ", was '" + raw + "'");
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    /**
     * Turns an {@link IllegalArgumentException} from a value object's own validation into a
     * {@link ConfigurationException} carrying the configuration path.
     *
     * <p>Keeps validation rules in one place: the settings records already refuse impossible values,
     * and this makes their complaints readable to an administrator rather than a stack trace.
     *
     * @param key      the key the value came from, or the section being built
     * @param supplier the construction to attempt
     * @param <T>      the constructed type
     * @return the constructed value
     * @throws ConfigurationException if construction failed
     */
    public <T> T build(String key, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException | NullPointerException rejected) {
            throw new ConfigurationException(
                    path(key), "is invalid: " + rejected.getMessage(), rejected);
        }
    }

    /**
     * Returns the full path of a key below this reader's section.
     *
     * @param key the key
     * @return the dotted path
     */
    public String path(String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    /**
     * Returns the section this reader wraps.
     *
     * @return the section
     */
    public ConfigurationSection section() {
        return section;
    }
}
