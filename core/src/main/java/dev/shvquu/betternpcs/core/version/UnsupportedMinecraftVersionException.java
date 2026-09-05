package dev.shvquu.betternpcs.core.version;

import java.io.Serial;

/**
 * Thrown when no {@link VersionAdapter} covers the Minecraft version the server is running.
 *
 * <p>Callers are expected to turn this into a clear, localised console message and disable the
 * plugin cleanly — never a stack trace the server owner has to interpret.
 */
public class UnsupportedMinecraftVersionException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient MinecraftVersion version;
    private final String supportedRange;

    /**
     * Creates an exception for an unsupported version.
     *
     * @param version        the version the server is running
     * @param supportedRange a human-readable description of what this build does support
     */
    public UnsupportedMinecraftVersionException(MinecraftVersion version, String supportedRange) {
        this(version, supportedRange, null);
    }

    /**
     * Creates an exception for an unsupported version with an underlying cause.
     *
     * @param version        the version the server is running
     * @param supportedRange a human-readable description of what this build does support
     * @param cause          the failure that prevented the adapter from loading, may be {@code null}
     */
    public UnsupportedMinecraftVersionException(
            MinecraftVersion version, String supportedRange, Throwable cause) {
        super("Minecraft " + version + " is not supported (supported: " + supportedRange + ")", cause);
        this.version = version;
        this.supportedRange = supportedRange;
    }

    /**
     * Returns the Minecraft version that could not be served.
     *
     * @return the running version
     */
    public MinecraftVersion version() {
        return version;
    }

    /**
     * Returns a description of the versions this build supports.
     *
     * @return the supported range
     */
    public String supportedRange() {
        return supportedRange;
    }
}
