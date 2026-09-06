package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import dev.shvquu.betternpcs.core.storage.NpcRepository;
import java.util.Objects;
import java.util.function.Supplier;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Anonymous usage statistics, submitted to <a href="https://bstats.org">bStats</a>.
 *
 * <h2>What is sent</h2>
 *
 * <p>Exactly what {@code config.yml} says and nothing else: how many NPCs exist, which storage
 * backend, which Minecraft version, which version adapter, which language, and whether PlaceholderAPI
 * is hooked. Plus the platform figures bStats collects for every plugin — server software, Java
 * version, player count, country.
 *
 * <p><b>No player data, no IP addresses, no world names, no NPC names.</b> The NPC count is a
 * number; nothing identifies an NPC, a player or a server.
 *
 * <h2>Switching it off</h2>
 *
 * <p>{@code plugin.metrics: false} prevents the {@link Metrics} object from being constructed at
 * all, so nothing is scheduled and nothing is sent. bStats also has its own global opt-out in
 * {@code plugins/bStats/config.yml} that covers every plugin on the server at once.
 *
 * @since 1.0.0
 */
final class BetterNpcsMetrics {

    /**
     * The bStats project id.
     *
     * <p>Set to {@code 0} in a fork that has not registered its own project at <a
     * href="https://bstats.org/getting-started">bstats.org</a>. Metrics then stay switched off
     * whatever the configuration says, and the reason is logged — submitting to a project that does
     * not exist would be pointless traffic from every server running that build.
     */
    private static final int PLUGIN_ID = 33885;

    private BetterNpcsMetrics() {
        throw new AssertionError("No instances");
    }

    /**
     * Starts collecting, unless it is switched off or no bStats id has been configured.
     *
     * @param plugin     the plugin bStats registers against
     * @param config     supplies the current configuration
     * @param manager    supplies the NPC count
     * @param repository names the storage backend
     * @param adapter    describes the loaded version adapter
     * @param language   the configured default language
     * @return {@code true} if metrics were started
     * @throws NullPointerException if any argument is {@code null}
     */
    static boolean start(
            JavaPlugin plugin,
            Supplier<BetterNpcsConfig> config,
            DefaultNpcManager manager,
            NpcRepository repository,
            String adapter,
            String language) {

        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(language, "language");

        if (!config.get().plugin().metrics()) {
            return false;
        }
        if (PLUGIN_ID == 0) {
            // At INFO, not FINE. Someone who left plugin.metrics on and sees nothing happen deserves
            // to be told why rather than left wondering — and on a released build carrying a real
            // id this line never fires at all.
            plugin.getLogger().info(
                    "Metrics are enabled in config.yml, but this build carries no bStats project id, "
                            + "so nothing is collected or sent.");
            return false;
        }

        Metrics metrics = new Metrics(plugin, PLUGIN_ID);

        // A count rather than anything identifying: the useful question is whether people run five
        // NPCs or five thousand, which is what decides where performance work goes.
        metrics.addCustomChart(new SingleLineChart("npcs", manager::count));

        // describe() is the same string the startup banner shows and is guaranteed credential-free,
        // but it also carries a host name for a network backend — so only the type is sent.
        metrics.addCustomChart(new SimplePie("storage",
                () -> config.get().storage().type().name()));

        metrics.addCustomChart(new SimplePie("version_adapter", () -> adapter));
        metrics.addCustomChart(new SimplePie("language", () -> language));
        metrics.addCustomChart(new SimplePie("placeholderapi",
                () -> plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null
                        ? "no"
                        : "yes"));

        return true;
    }
}
