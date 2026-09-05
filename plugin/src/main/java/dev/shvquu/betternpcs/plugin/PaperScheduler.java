package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.core.engine.Scheduler;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The {@link Scheduler} backed by the server's own task scheduler.
 *
 * <p>Deliberately thin. The one decision it makes is in {@link #runOnMainThread(Runnable)}, which
 * runs work inline when the caller is already on the main thread: an engine method that always
 * deferred would turn {@code npc.spawn()} into something that has not happened yet when it returns,
 * which no caller expects.
 *
 * @since 1.0.0
 */
final class PaperScheduler implements Scheduler {

    private final Plugin plugin;

    /**
     * Creates the scheduler.
     *
     * @param plugin the plugin tasks are registered under
     * @throws NullPointerException if {@code plugin} is {@code null}
     */
    PaperScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runOnMainThread(Runnable work) {
        Objects.requireNonNull(work, "work");
        if (isMainThread()) {
            work.run();
            return;
        }
        if (!plugin.isEnabled()) {
            // Scheduling against a disabled plugin throws. This happens when a skin lookup finishes
            // after shutdown has begun, and dropping the work is the right answer — there is nothing
            // left to apply it to.
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, work);
    }

    @Override
    public void runLater(long delayTicks, Runnable work) {
        Objects.requireNonNull(work, "work");
        if (delayTicks < 1) {
            throw new IllegalArgumentException("The delay must be at least one tick, was " + delayTicks);
        }
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, work, delayTicks);
        }
    }

    @Override
    public Task runTimer(long delayTicks, long intervalTicks, Runnable work) {
        Objects.requireNonNull(work, "work");
        if (intervalTicks < 1) {
            throw new IllegalArgumentException(
                    "The interval must be at least one tick, was " + intervalTicks);
        }
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, work, Math.max(0, delayTicks), intervalTicks);
        return new BukkitTaskHandle(task);
    }

    @Override
    public void runAsync(Runnable work) {
        Objects.requireNonNull(work, "work");
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, work);
        }
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    private record BukkitTaskHandle(BukkitTask task) implements Task {

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
