package dev.shvquu.betternpcs.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Scheduler} that runs everything inline and lets a test drive time by hand.
 *
 * <p>Immediate work runs on the calling thread, so a test never has to sleep or poll. Delayed and
 * repeating work is queued and only runs when the test asks for it, which is what makes it possible
 * to assert that something is <em>not</em> done yet — that a tab list entry survives the tick after
 * a spawn, for instance.
 */
public final class DirectScheduler implements Scheduler {

    /** A queued piece of delayed work. */
    private record Delayed(long delayTicks, Runnable work) {
    }

    private static final class RepeatingTask implements Task {

        private final Runnable work;
        private boolean cancelled;

        private RepeatingTask(Runnable work) {
            this.work = work;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private final List<Delayed> delayed = new ArrayList<>();
    private final List<RepeatingTask> repeating = new ArrayList<>();
    private final List<Runnable> async = new ArrayList<>();

    private boolean pretendMainThread = true;

    @Override
    public void runOnMainThread(Runnable work) {
        Objects.requireNonNull(work, "work").run();
    }

    @Override
    public void runLater(long delayTicks, Runnable work) {
        Objects.requireNonNull(work, "work");
        if (delayTicks < 1) {
            throw new IllegalArgumentException("The delay must be at least one tick");
        }
        delayed.add(new Delayed(delayTicks, work));
    }

    @Override
    public Task runTimer(long delayTicks, long intervalTicks, Runnable work) {
        Objects.requireNonNull(work, "work");
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("The interval must be at least one tick");
        }
        RepeatingTask task = new RepeatingTask(work);
        repeating.add(task);
        return task;
    }

    @Override
    public void runAsync(Runnable work) {
        // Queued rather than run, so a test can assert that work was moved off the main thread and
        // then decide when it happens.
        async.add(Objects.requireNonNull(work, "work"));
    }

    @Override
    public boolean isMainThread() {
        return pretendMainThread;
    }

    /**
     * Runs and clears every piece of delayed work.
     *
     * @return how many ran
     */
    public int runDelayed() {
        List<Delayed> due = List.copyOf(delayed);
        delayed.clear();
        due.forEach(entry -> entry.work().run());
        return due.size();
    }

    /**
     * Runs every repeating task once.
     *
     * @return how many ran
     */
    public int runTimers() {
        int ran = 0;
        for (RepeatingTask task : List.copyOf(repeating)) {
            if (!task.isCancelled()) {
                task.work.run();
                ran++;
            }
        }
        return ran;
    }

    /**
     * Runs and clears every piece of queued asynchronous work.
     *
     * @return how many ran
     */
    public int runAsyncWork() {
        List<Runnable> due = List.copyOf(async);
        async.clear();
        due.forEach(Runnable::run);
        return due.size();
    }

    /**
     * Returns how many pieces of delayed work are waiting.
     *
     * @return the queued count
     */
    public int pendingDelayed() {
        return delayed.size();
    }

    /**
     * Returns how many repeating tasks are registered and not cancelled.
     *
     * @return the active timer count
     */
    public int activeTimers() {
        return (int) repeating.stream().filter(task -> !task.isCancelled()).count();
    }

    /**
     * Sets what {@link #isMainThread()} reports.
     *
     * @param value the value to report
     */
    public void pretendMainThread(boolean value) {
        this.pretendMainThread = value;
    }
}
