package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Timer of UC12, an active class with its own thread (Lecture 15). Here the
 * timer really runs, checking every few milliseconds, while the test moves the clock; each
 * test waits for the timer's report instead of sleeping, so it is fast and does not depend
 * on timing.
 */
class BorrowingDeadlineTimerTest {

    private static final Duration FAST = Duration.ofMillis(5);

    private TestClock clock;
    private DataStore store;
    private BlockingQueue<DeadlineReport> reports;
    private BorrowingDeadlineTimer timer;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
        reports = new LinkedBlockingQueue<>();
        timer = new BorrowingDeadlineTimer(new BorrowingDeadlineService(store), FAST, reports::add);
    }

    @AfterEach
    void stopTimer() {
        timer.stop();
    }

    /** An item borrowed from now for the period. */
    private BorrowRequest borrowed(Duration period) {
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        BorrowRequest request = (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), "bea", clock.now(), clock.now().plus(period)));
        new DealRules(store).accept(request);
        return request;
    }

    /** The timer's next report of a change, or a failure if none comes within two seconds. */
    private DeadlineReport nextReport() throws InterruptedException {
        DeadlineReport report = reports.poll(2, TimeUnit.SECONDS);
        assertNotNull(report, "the timer reported nothing within 2 seconds");
        return report;
    }

    @Test
    void timerReturnsADueItem_withoutAnyoneAsking() throws InterruptedException {
        BorrowRequest request = borrowed(Duration.ofSeconds(5)); // the shortest borrow (Special Requirement 1)
        timer.start();
        assertTrue(timer.isRunning());

        clock.advance(Duration.ofSeconds(3));  // past the halfway reminder
        assertEquals(List.of(request), nextReport().reminded());
        assertFalse(request.isCompleted());

        clock.advance(Duration.ofSeconds(2));  // the due date
        assertEquals(List.of(request), nextReport().returned());
        assertTrue(request.isAutoReturned());
        assertEquals(ListingStatus.LIVE, store.getListingOf(request).getStatus());
    }

    @Test
    void firstCheck_runsAtOnce_soAnItemDueWhileTheAppWasClosedIsReturnedOnStart() throws InterruptedException {
        BorrowRequest request = borrowed(Duration.ofHours(1));
        clock.advance(Duration.ofDays(1));

        timer.start();

        assertEquals(List.of(request), nextReport().returned());
    }

    @Test
    void checksWithNothingToDo_reportNothing() throws InterruptedException {
        borrowed(Duration.ofDays(7));
        timer.start();

        assertNull(reports.poll(100, TimeUnit.MILLISECONDS)); // about 20 checks, none of them reported
    }

    @Test
    void timerRunsOnItsOwnDaemonThread() throws InterruptedException {
        AtomicReference<Thread> reportedOn = new AtomicReference<>();
        timer = new BorrowingDeadlineTimer(new BorrowingDeadlineService(store), FAST, report -> {
            reportedOn.set(Thread.currentThread());
            reports.add(report);
        });
        borrowed(Duration.ofHours(1));
        clock.advance(Duration.ofHours(1));

        timer.start();
        nextReport();

        assertNotSame(Thread.currentThread(), reportedOn.get());
        assertEquals("borrowing-deadline-timer", reportedOn.get().getName());
        assertTrue(reportedOn.get().isDaemon()); // it never keeps the app from closing
    }

    @Test
    void checkThatFails_doesNotStopTheTimer() throws InterruptedException {
        AtomicInteger checks = new AtomicInteger();
        BorrowingDeadlineService failingAtFirst = new BorrowingDeadlineService(store) {
            @Override
            public DeadlineReport process() {
                if (checks.incrementAndGet() <= 3) {
                    throw new IllegalStateException("the save file is locked");
                }
                return super.process();
            }
        };
        timer = new BorrowingDeadlineTimer(failingAtFirst, FAST, reports::add);
        BorrowRequest request = borrowed(Duration.ofHours(1));
        clock.advance(Duration.ofHours(1));

        timer.start();

        assertEquals(List.of(request), nextReport().returned()); // the fourth check got through
        assertTrue(checks.get() >= 4);
    }

    @Test
    void stop_letsARunningCheckFinish() throws InterruptedException {
        CountDownLatch inCheck = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        BorrowingDeadlineService slow = new BorrowingDeadlineService(store) {
            @Override
            public DeadlineReport process() {
                inCheck.countDown();
                sleepQuietly(Duration.ofMillis(200)); // a check that saves a large file
                finished.set(true);
                return DeadlineReport.NOTHING;
            }
        };
        timer = new BorrowingDeadlineTimer(slow, FAST, reports::add, Duration.ofSeconds(5));
        timer.start();
        assertTrue(inCheck.await(2, TimeUnit.SECONDS));

        timer.stop();

        assertTrue(finished.get()); // stop() waited for it
        assertFalse(timer.isRunning());
    }

    @Test
    void stop_interruptsACheckThatDoesNotFinish_soClosingTheAppNeverHangs() throws InterruptedException {
        CountDownLatch inCheck = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        BorrowingDeadlineService stuck = new BorrowingDeadlineService(store) {
            @Override
            public DeadlineReport process() {
                inCheck.countDown();
                try {
                    Thread.sleep(60_000); // for example, waiting for a locked save file
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
                return DeadlineReport.NOTHING;
            }
        };
        timer = new BorrowingDeadlineTimer(stuck, FAST, reports::add, Duration.ofMillis(50));
        timer.start();
        assertTrue(inCheck.await(2, TimeUnit.SECONDS));

        long started = System.nanoTime();
        timer.stop();
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(tookMs < 2000, "stop() took " + tookMs + " ms");
        assertFalse(timer.isRunning());
        assertTrue(interrupted.await(2, TimeUnit.SECONDS), "the stuck check was interrupted");
    }

    private static void sleepQuietly(Duration time) {
        try {
            Thread.sleep(time.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void stoppedTimer_checksNoMore() throws InterruptedException {
        BorrowRequest request = borrowed(Duration.ofHours(1));
        timer.start();
        timer.stop();
        assertFalse(timer.isRunning());

        clock.advance(Duration.ofHours(2));

        assertNull(reports.poll(100, TimeUnit.MILLISECONDS));
        assertFalse(request.isCompleted());
    }

    @Test
    void startingOrStoppingTwice_changesNothing_andAStoppedTimerCanStartAgain() throws InterruptedException {
        BorrowRequest request = borrowed(Duration.ofHours(1));
        timer.stop();           // never started
        timer.start();
        timer.start();          // already running
        timer.stop();
        timer.stop();           // already stopped
        clock.advance(Duration.ofHours(1));

        timer.start();

        assertEquals(List.of(request), nextReport().returned());
        assertNull(reports.poll(50, TimeUnit.MILLISECONDS)); // one report only: a single timer thread is running
    }

    @Test
    void timerNeedsAServiceAPauseAndAListener() {
        BorrowingDeadlineService service = new BorrowingDeadlineService(store);

        assertThrows(IllegalArgumentException.class, () -> new BorrowingDeadlineTimer(service, Duration.ZERO, reports::add));
        assertThrows(IllegalArgumentException.class,
                () -> new BorrowingDeadlineTimer(service, Duration.ofSeconds(-1), reports::add));
        assertThrows(NullPointerException.class, () -> new BorrowingDeadlineTimer(null, FAST, reports::add));
        assertThrows(NullPointerException.class, () -> new BorrowingDeadlineTimer(service, null, reports::add));
        assertThrows(NullPointerException.class, () -> new BorrowingDeadlineTimer(service, FAST, null));
        assertThrows(NullPointerException.class, () -> new BorrowingDeadlineTimer(service, FAST, reports::add, null));
    }

    @Test
    void appChecksAtLeastOnceASecond() {
        // Special Requirement 1
        assertTrue(BorrowingDeadlineTimer.CHECK_EVERY.compareTo(Duration.ofSeconds(1)) <= 0);
    }
}
