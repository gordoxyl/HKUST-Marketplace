package com.hkust.marketplace.services;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The Timer actor of UC12 Process Borrowing Deadlines: it signals, again and again, that it
 * is time to check the borrowing deadlines (UC12 step 1).
 *
 * <p>This is an <b>active class</b> (Lecture 15): it has its own thread of control, apart
 * from the thread that draws the screens, so deadlines are met whatever the user is doing,
 * and on every screen, the welcome screen included. The thread is a daemon, so it never
 * keeps the app from closing. The app starts one timer when it opens and stops it when it
 * closes:
 * <pre>{@code
 * timer = new BorrowingDeadlineTimer(new BorrowingDeadlineService(),
 *         BorrowingDeadlineTimer.CHECK_EVERY, report -> Platform.runLater(LiveUpdates::dataChanged));
 * timer.start();
 * ...
 * timer.stop();
 * }</pre>
 */
public class BorrowingDeadlineTimer {

    /**
     * How often the app checks. Special Requirement 1 asks for at least once a second, so
     * that even a 5-second borrow is returned on time; twice a second leaves a margin.
     */
    public static final Duration CHECK_EVERY = Duration.ofMillis(500);

    /** How long {@link #stop()} waits for a running check to finish before it interrupts it. */
    static final Duration STOP_WAIT = Duration.ofSeconds(5);

    private final BorrowingDeadlineService service;
    private final Duration interval;
    private final Consumer<DeadlineReport> onChange;
    private final Duration stopWait;
    private ScheduledExecutorService thread;
    private String lastProblem;

    /**
     * @param interval the pause between two checks
     * @param onChange told, on the timer's thread, about every check that sent a reminder or
     *                 returned an item, so the open screen can show the new data (step 4);
     *                 a screen must move to its own thread before it touches anything
     */
    public BorrowingDeadlineTimer(BorrowingDeadlineService service, Duration interval, Consumer<DeadlineReport> onChange) {
        this(service, interval, onChange, STOP_WAIT);
    }

    /** @param stopWait how long {@link #stop()} waits for a running check; tests make it short */
    BorrowingDeadlineTimer(BorrowingDeadlineService service, Duration interval, Consumer<DeadlineReport> onChange,
                           Duration stopWait) {
        this.service = Objects.requireNonNull(service, "service");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.stopWait = Objects.requireNonNull(stopWait, "stopWait");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("The timer needs a pause between checks.");
        }
    }

    /** Starts checking, beginning at once. Starting a running timer changes nothing. */
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        thread = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread timerThread = new Thread(task, "borrowing-deadline-timer");
            timerThread.setDaemon(true);
            return timerThread;
        });
        thread.scheduleWithFixedDelay(this::check, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Stops checking. A check that is running may finish first, for up to {@link #STOP_WAIT}; one
     * that takes longer, for example because the save file is locked, is interrupted, so closing
     * the app never hangs. Nothing is lost then: the save file is replaced only by a complete new
     * one, and the next start of the app runs the check again. Stopping a stopped timer changes
     * nothing.
     */
    public synchronized void stop() {
        if (thread == null) {
            return;
        }
        thread.shutdown();
        try {
            if (!thread.awaitTermination(stopWait.toMillis(), TimeUnit.MILLISECONDS)) {
                thread.shutdownNow();
                System.err.println("[BorrowingDeadlineTimer] A deadline check did not finish in time and was interrupted.");
            }
        } catch (InterruptedException e) {
            thread.shutdownNow();
            Thread.currentThread().interrupt();
        }
        thread = null;
    }

    public synchronized boolean isRunning() {
        return thread != null;
    }

    /**
     * One signal of the Timer. A check that fails, for example because the data could not be
     * saved, must not end the timer: the problem is logged once, and the next check tries again.
     */
    private void check() {
        try {
            DeadlineReport report = service.process();
            lastProblem = null;
            if (report.hasChanges()) {
                onChange.accept(report);
            }
        } catch (RuntimeException e) {
            String problem = String.valueOf(e.getMessage());
            if (!problem.equals(lastProblem)) {
                System.err.println("[BorrowingDeadlineTimer] A deadline check failed and will be tried again: " + problem);
                lastProblem = problem;
            }
        }
    }
}
