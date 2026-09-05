package dev.shvquu.betternpcs.core.render;

import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * What the engine hands a version adapter when it is enabled.
 *
 * <p>Passed as one object rather than as constructor arguments so that adapters keep a public
 * no-argument constructor — {@link dev.shvquu.betternpcs.core.version.VersionAdapterResolver}
 * instantiates the matching one reflectively, and a constructor signature would have to be kept in
 * step across six modules by hand.
 *
 * @since 1.0.0
 */
public interface AdapterContext {

    /**
     * Returns the BetterNPCs plugin instance.
     *
     * <p>Adapters need it to register a listener or schedule a task. It is deliberately the only
     * Bukkit-wide handle they are given.
     *
     * @return the plugin
     */
    Plugin plugin();

    /**
     * Returns where the adapter should log.
     *
     * @return the plugin's logger
     */
    Logger logger();

    /**
     * Returns where to report interactions read from packets.
     *
     * @return the interaction sink
     */
    NpcInteractionSink interactions();

    /**
     * Returns whether debug logging is on.
     *
     * <p>Checked before building a debug message rather than relying on the log level, because the
     * messages an adapter would produce are per packet and formatting them is not free.
     *
     * @return {@code true} if debug logging is enabled
     */
    boolean debug();
}
