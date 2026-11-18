package com.hkust.marketplace.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A clock for tests: it stands still until the test moves it. This is the stub that
 * replaces real time (Lecture 14: stubs), so time rules such as the 5-second minimum
 * borrow or a due-date reminder can be tested instantly and repeatably.
 * <pre>{@code
 * TestClock clock = new TestClock(Fixtures.START);
 * DataStore store = new DataStore(clock);
 * ...
 * clock.advance(Duration.ofSeconds(5));
 * }</pre>
 */
public class TestClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public TestClock(LocalDateTime start) {
        this(start.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }

    private TestClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** The clock's current time as a local date-time. */
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant, zone);
    }

    /** Moves the clock forward (or back, with a negative duration). */
    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    /** Sets the clock to a moment. */
    public void set(LocalDateTime time) {
        instant = time.atZone(zone).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
