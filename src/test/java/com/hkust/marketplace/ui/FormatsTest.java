package com.hkust.marketplace.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the shared display formats (boundary values from Lecture 14). */
class FormatsTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 9, 14, 5);

    @Test
    void dates_useOneFormat_andShowADashWhenMissing() {
        assertEquals("09 Nov 2026", Formats.date(TIME));
        assertEquals("09 Nov 2026 14:05", Formats.dateTime(TIME));
        assertEquals(Formats.NONE, Formats.date(null));
        assertEquals(Formats.NONE, Formats.dateTime(null));
        assertEquals("09 Nov 2026 14:05:00 – 10 Nov 2026 14:05:00", Formats.period(TIME, TIME.plusDays(1)));
    }

    @Test
    void borrowTimes_areToTheSecond_otherTimesToTheMinute() {
        LocalDateTime due = LocalDateTime.of(2026, 11, 14, 14, 6, 44);

        assertEquals("14 Nov 2026 14:06:44", Formats.borrowTime(due));
        assertEquals("14 Nov 2026 14:06", Formats.dateTime(due)); // messages, notifications, postings
        assertEquals(Formats.NONE, Formats.borrowTime(null));
    }

    /** Regression R9: the shortest loan, 5 seconds, no longer reads "14:01 – 14:01". */
    @Test
    void period_ofAFiveSecondLoan_showsBothEnds() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 14, 14, 1, 10);

        assertEquals("14 Nov 2026 14:01:10 – 14 Nov 2026 14:01:15", Formats.period(start, start.plusSeconds(5)));
    }

    @Test
    void timeOfDay_hasSeconds_andCanBeTypedBack() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 9, 14, 5, 30);

        assertEquals("14:05:30", Formats.timeOfDay(time));
        assertEquals("", Formats.timeOfDay(null));
        assertEquals(java.util.Optional.of(time), TextInputs.dateTime(time.toLocalDate(), Formats.timeOfDay(time)));
    }

    @Test
    void prices_showCentsOnlyWhenThereAreSome() {
        assertEquals("HK$ 180", Formats.price(180));
        assertEquals("HK$ 0.50", Formats.price(0.5));
        assertEquals("HK$ 99.99", Formats.price(99.99));
    }

    @Test
    void durations_readAsText() {
        assertEquals("5 seconds", Formats.duration(Duration.ofSeconds(5)));
        assertEquals("1 day 2 hours", Formats.duration(Duration.ofHours(26)));
    }

    @Test
    void stars_atTheBoundaries() {
        assertEquals("☆☆☆☆☆", Formats.stars(0));
        assertEquals("★☆☆☆☆", Formats.stars(1));
        assertEquals("★★★★☆", Formats.stars(4));
        assertEquals("★★★★★", Formats.stars(5));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6})
    void stars_outsideZeroToFive_areRefused(int rating) {
        assertThrows(IllegalArgumentException.class, () -> Formats.stars(rating));
    }

    @Test
    void unreadSummary_atTheBoundaries() {
        assertEquals("No unread notifications", Formats.unreadSummary(0));
        assertEquals("1 unread notification", Formats.unreadSummary(1));
        assertEquals("2 unread notifications", Formats.unreadSummary(2));
        assertEquals("No unread notifications", Formats.unreadSummary(-1)); // never negative on screen
    }

    @Test
    void preview_atTheBoundaries() {
        assertEquals("Hello", Formats.preview("Hello", 5));          // exactly max: unchanged
        assertEquals("Hell…", Formats.preview("Hello!", 5));         // one over: cut, still 5 characters
        assertEquals("Hi…", Formats.preview("Hi  there", 4));        // no space left before the "…"
        assertEquals("…", Formats.preview("Hello", 1));
        assertEquals("", Formats.preview(null, 5));
        assertEquals("", Formats.preview("   ", 5));
    }

    @Test
    void preview_putsALongTextOnOneLine() {
        assertEquals("Line one line two", Formats.preview("  Line one\n\n line two\t", 40));
    }

    @Test
    void preview_needsRoomForAtLeastOneCharacter() {
        assertThrows(IllegalArgumentException.class, () -> Formats.preview("Hello", 0));
    }

    @Test
    void averageRating_saysNoRatingsYet_whenThereAreNone() {
        assertEquals("No ratings yet", Formats.averageRating(OptionalDouble.empty(), 0));
        assertEquals("No ratings yet", Formats.averageRating(OptionalDouble.of(4), 0)); // inconsistent input: count wins
        assertEquals("5.0 / 5 (1 rating)", Formats.averageRating(OptionalDouble.of(5), 1));
        assertEquals("3.5 / 5 (2 ratings)", Formats.averageRating(OptionalDouble.of(3.5), 2));
    }
}
