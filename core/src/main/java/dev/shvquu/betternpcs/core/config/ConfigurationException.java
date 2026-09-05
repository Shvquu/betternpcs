package dev.shvquu.betternpcs.core.config;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when {@code config.yml} contains a value BetterNPCs cannot use.
 *
 * <p>Always names the offending path, because "invalid value" in a console log tells an
 * administrator nothing about which of a hundred lines to look at.
 *
 * <p>Never carries a value that might be a secret: {@link #getMessage()} is written straight to the
 * console, and the failing setting could be a password or a connection string.
 *
 * @since 1.0.0
 */
public class ConfigurationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String path;

    /**
     * Creates the exception.
     *
     * @param path   the configuration path that is wrong, for example {@code storage.mysql.port}
     * @param detail what is wrong with it; must not contain the value if it could be a secret
     */
    public ConfigurationException(String path, String detail) {
        this(path, detail, null);
    }

    /**
     * Creates the exception with an underlying cause.
     *
     * @param path   the configuration path that is wrong
     * @param detail what is wrong with it
     * @param cause  the failure that revealed it, may be {@code null}
     */
    public ConfigurationException(String path, String detail, Throwable cause) {
        super("config.yml: '" + Objects.requireNonNull(path, "path") + "' " + detail, cause);
        this.path = path;
    }

    /**
     * Returns the configuration path that is wrong.
     *
     * @return the path
     */
    public String path() {
        return path;
    }
}
