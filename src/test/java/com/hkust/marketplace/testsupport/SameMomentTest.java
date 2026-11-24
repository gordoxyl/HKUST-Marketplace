package com.hkust.marketplace.testsupport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link SameMoment}, the driver of the concurrency tests. */
class SameMomentTest {

    @Test
    void run_givesEachTasksResult_inTheOrderOfTheTasks() throws Exception {
        List<Callable<String>> tasks = List.of(() -> "first", () -> "second", () -> "third");

        List<String> results;
        try (SameMoment moment = new SameMoment(3)) {
            results = moment.run(tasks);
        }

        assertEquals(List.of("first", "second", "third"), results);
    }

    @Test
    void close_stopsATaskThatIsStillRunning_soNoThreadIsLeftBehind() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);

        try (SameMoment moment = new SameMoment(1)) {
            moment.submit(() -> {
                running.countDown();
                try {
                    new CountDownLatch(1).await(); // waits for ever: only stopping the thread ends it
                } catch (InterruptedException e) {
                    stopped.countDown();
                }
                return null;
            });
            moment.go();
            assertTrue(running.await(5, TimeUnit.SECONDS)); // the task is running when the block ends,
        }                                                    // as when a test fails before its tasks end

        assertTrue(stopped.await(5, TimeUnit.SECONDS));
    }
}
