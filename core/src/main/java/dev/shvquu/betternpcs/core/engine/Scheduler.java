package dev.shvquu.betternpcs.core.engine;

/**
 * The engine's view of the server's task scheduler.
 *
 * <p>An interface rather than direct Bukkit calls for one reason: an NPC engine that can only be
 * exercised with a running server is an NPC engine that is not really tested. Every timing decision
 * the engine makes — the tracker interval, the tick of delay before a tab list entry is removed, the
 * hop back from a skin lookup — goes through here, so a test can run them synchronously and assert
 * on the result instead of sleeping and hoping.
 *
 * <p>The production implementation wraps Paper's scheduler. It is deliberately thin; anything
 * cleverer belongs in the caller.
 *
 * @since 1.0.0
 */
public interface Scheduler {

    /**
     * A handle on something scheduled to repeat.
     *
     * @since 1.0.0
     */
    interface Task {

        /**
         * Stops the task from running again.
         *
         * <p>Must tolerate being called more than once, and after the scheduler has shut down.
         */
        void cancel();

        /**
         * Returns whether the task has been cancelled.
         *
         * @return {@code true} if it will not run again
         */
        boolean isCancelled();
    }

    /**
     * Runs work on the main server thread.
     *
     * <p>Runs it immediately and inline when the caller is already on the main thread. That matters:
     * an engine method that always deferred would turn {@code npc.spawn()} into something that has
     * not happened yet when it returns, which no caller expects.
     *
     * @param work what to run
     * @throws NullPointerException if {@code work} is {@code null}
     */
    void runOnMainThread(Runnable work);

    /**
     * Runs work on the main server thread after a delay.
     *
     * @param delayTicks how many ticks to wait, at least one
     * @param work       what to run
     * @throws NullPointerException     if {@code work} is {@code null}
     * @throws IllegalArgumentException if {@code delayTicks} is below one
     */
    void runLater(long delayTicks, Runnable work);

    /**
     * Runs work on the main server thread repeatedly.
     *
     * @param delayTicks    how many ticks before the first run
     * @param intervalTicks how many ticks between runs, at least one
     * @param work          what to run
     * @return a handle for cancelling it
     * @throws NullPointerException     if {@code work} is {@code null}
     * @throws IllegalArgumentException if {@code intervalTicks} is below one
     */
    Task runTimer(long delayTicks, long intervalTicks, Runnable work);

    /**
     * Runs work off the main server thread.
     *
     * @param work what to run
     * @throws NullPointerException if {@code work} is {@code null}
     */
    void runAsync(Runnable work);

    /**
     * Returns whether the calling thread is the main server thread.
     *
     * <p>Used by the engine to fail loudly when API methods documented as main-thread-only are
     * called from somewhere else, rather than letting the resulting corruption surface as an
     * unrelated crash later.
     *
     * @return {@code true} if called from the main thread
     */
    boolean isMainThread();
}
