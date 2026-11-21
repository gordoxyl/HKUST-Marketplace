package com.hkust.marketplace.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs tasks on threads of their own and lets them all go at the same moment: the driver for the
 * tests of the critical case, where several users, or a user and the timer, act at once
 * (Lecture 14). Use it with try-with-resources, so that its threads stop even when the test fails:
 * <pre>{@code
 * List<Boolean> claimed;
 * try (SameMoment moment = new SameMoment(8)) {
 *     claimed = moment.run(Collections.nCopies(8, () -> tryToClaim(lamp)));
 * }
 * assertEquals(1, Collections.frequency(claimed, true));
 * }</pre>
 * It owns its thread pool because an {@code ExecutorService} can be closed with
 * try-with-resources only from Java 19 on, and the project targets Java 17.
 */
public final class SameMoment implements AutoCloseable {

    /** How long a test waits for one task before it fails. */
    private static final long TIMEOUT_SECONDS = 10;

    private final ExecutorService pool;
    private final CountDownLatch go = new CountDownLatch(1);

    /** @param threads how many tasks can run at the same time */
    public SameMoment(int threads) {
        pool = Executors.newFixedThreadPool(threads);
    }

    /** Submits a task that waits for {@link #go()} before it runs. */
    public <T> Future<T> submit(Callable<T> task) {
        return pool.submit(() -> {
            go.await();
            return task.call();
        });
    }

    /** Lets every task submitted so far run, all at the same moment. */
    public void go() {
        go.countDown();
    }

    /** The task's result, waiting at most 10 seconds for it. */
    public static <T> T resultOf(Future<T> task) throws Exception {
        return task.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Runs the tasks all at the same moment, and returns their results in the same order. */
    public <T> List<T> run(List<Callable<T>> tasks) throws Exception {
        List<Future<T>> started = new ArrayList<>();
        for (Callable<T> task : tasks) {
            started.add(submit(task));
        }
        go();
        List<T> results = new ArrayList<>();
        for (Future<T> task : started) {
            results.add(resultOf(task));
        }
        return results;
    }

    /** Stops the threads, also those of tasks still waiting or still running. */
    @Override
    public void close() {
        pool.shutdownNow();
    }
}
