package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.RatingSummary;
import javafx.util.StringConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the shared display formats (boundary values from Lecture 13). The date picker's
 * dates are also split into equivalence partitions of what can be typed: a date in the shown form
 * (in any case, with spaces around, with one- or two-digit days), and text that is no such date
 * (nothing, another form, a day that does not exist, no year).
 */
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

    /** The number on the sidebar's notification dot (assumptions register 92), at the edges of what it writes out. */
    @ParameterizedTest
    @CsvSource({"1, 1", "99, 99", "100, 99+"})
    void unreadCount_onTheDot_atTheBoundaries(int unread, String shown) {
        assertEquals(shown, Formats.unreadCount(unread));
    }

    @Test
    void unreadCount_belowOne_isRefused_becauseThenThereIsNoDot() {
        assertThrows(IllegalArgumentException.class, () -> Formats.unreadCount(0));
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
        assertEquals("No ratings yet", Formats.averageRating(RatingSummary.NONE)); // an average without ratings cannot be made: RatingSummaryTest count wins
        assertEquals("5.0 / 5 (1 rating)", Formats.averageRating(new RatingSummary(1, 5)));
        assertEquals("3.5 / 5 (2 ratings)", Formats.averageRating(new RatingSummary(2, 3.5)));
    }

    // ---------- Date pickers show and read dates as lists and tables show them ----------

    @Test
    void datePicker_showsADateAsTheTablesDo() {
        StringConverter<LocalDate> dates = Formats.datePickerConverter();

        assertEquals("21 Nov 2026", dates.toString(LocalDate.of(2026, 11, 21)));
        assertEquals(Formats.date(TIME), dates.toString(TIME.toLocalDate())); // one form for both
        assertEquals("01 Dec 2026", dates.toString(LocalDate.of(2026, 12, 1)));
        assertEquals("", dates.toString(null));
    }

    @ParameterizedTest(name = "[{0}] is read as {1}")
    @CsvSource({
            "21 Nov 2026,      2026-11-21",  // as shown
            "'  21 Nov 2026 ', 2026-11-21",  // spaces around
            "21 nov 2026,      2026-11-21",  // another case
            "01 Dec 2026,      2026-12-01",  // a day with two digits
            "1 Dec 2026,       2026-12-01",  // and with one
            "29 Feb 2028,      2028-02-29"}) // a leap day
    void datePicker_readsADateTypedInTheSameForm(String typed, LocalDate date) {
        assertEquals(date, Formats.datePickerConverter().fromString(typed));
    }

    @ParameterizedTest(name = "[{0}] is no date")
    @NullSource
    @ValueSource(strings = {"", "   ", "11/21/2026", "2026-11-21", "31 Feb 2026", "29 Feb 2027", "21 Nov"})
    void datePicker_readsNoDate_fromTextThatIsNotSuchADate(String typed) {
        assertNull(Formats.datePickerConverter().fromString(typed));
    }

    @Test
    void datePicker_readsBackEveryDateItShows() {
        StringConverter<LocalDate> dates = Formats.datePickerConverter();
        for (LocalDate day = LocalDate.of(2026, 1, 1); day.getYear() == 2026; day = day.plusDays(1)) {
            assertEquals(day, dates.fromString(dates.toString(day)));
        }
    }
}
