package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.RatingSummary;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;
import javafx.util.StringConverter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * One way to show dates, prices, ratings and periods on every screen, so all three portals
 * look and read the same (non-functional requirement: a consistent interface).
 */
public final class Formats {

    /** Shown when there is no value, such as a deal that is not completed yet. */
    public static final String NONE = "—";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
    /** A date as {@link #DATE} writes it, read back: one or two digits for the day, any case, real dates only. */
    private static final DateTimeFormatter TYPED_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM uuuu")
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    /** See {@link #datePickerConverter()}. */
    private static final StringConverter<LocalDate> PICKER_DATES = new StringConverter<>() {
        @Override
        public String toString(LocalDate date) {
            return date == null ? "" : date.format(DATE);
        }

        @Override
        public LocalDate fromString(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(text.strip(), TYPED_DATE);
            } catch (DateTimeParseException e) {
                return null; // not a date in this form: the picker is left without a date
            }
        }
    };

    private Formats() {
        // Utility class
    }

    /** "09 Nov 2026", or {@link #NONE} for no date. */
    public static String date(LocalDateTime time) {
        return time == null ? NONE : time.format(DATE);
    }

    /**
     * How a date picker shows and reads its date: "21 Nov 2026", as {@link #date} shows dates in
     * lists and tables, so a chosen date reads the same as the dates beside it (a consistent
     * interface). Text typed into an editable picker is read in the same form, in any case; text
     * that is not such a date, or no text, gives no date (null), which the form then reports.
     * <pre>{@code
     * DatePicker from = new DatePicker();
     * from.setConverter(Formats.datePickerConverter());
     * }</pre>
     */
    public static StringConverter<LocalDate> datePickerConverter() {
        return PICKER_DATES;
    }

    /**
     * "09 Nov 2026 14:20", or {@link #NONE} for no date: the time of a message, a notification
     * or a posting. Times of borrowing use {@link #borrowTime} instead.
     */
    public static String dateTime(LocalDateTime time) {
        return time == null ? NONE : time.format(DATE_TIME);
    }

    /**
     * A time of borrowing, to the second: "14 Nov 2026 14:06:44", or {@link #NONE} for no time.
     * Used for borrow periods and due dates, because borrowing works to the second; the
     * due-date reminder names the due time in the same form (regression log R9).
     */
    public static String borrowTime(LocalDateTime time) {
        return time == null ? NONE : BorrowRequest.formatBorrowTime(time);
    }

    /** The time of day with seconds, as it is typed into a time box: "14:05:30"; empty text for no time. */
    public static String timeOfDay(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_OF_DAY);
    }

    /** A borrow period, to the second: "10 Nov 2026 09:00:00 – 17 Nov 2026 09:00:00". */
    public static String period(LocalDateTime start, LocalDateTime end) {
        return borrowTime(start) + " – " + borrowTime(end);
    }

    /** "HK$ 180", or "HK$ 99.50" when there are cents. */
    public static String price(double amount) {
        return SaleListing.formatPrice(amount);
    }

    /** A length of time: "90 days", "1 day 2 hours", "5 seconds". */
    public static String duration(Duration duration) {
        return LendListing.formatDuration(duration);
    }

    /**
     * A 1-5 star rating as stars: 4 → "★★★★☆". 0 gives five empty stars.
     * @throws IllegalArgumentException outside 0 to 5
     */
    public static String stars(int rating) {
        if (rating < 0 || rating > Review.MAX_RATING) {
            throw new IllegalArgumentException("A rating is 0 to " + Review.MAX_RATING + " stars, not " + rating + ".");
        }
        return "★".repeat(rating) + "☆".repeat(Review.MAX_RATING - rating);
    }

    /**
     * A long text on one line, cut to at most {@code max} characters, for lists and previews:
     * line breaks become spaces, and a cut text ends in "…".
     * @throws IllegalArgumentException if max is less than 1
     */
    public static String preview(String text, int max) {
        if (max < 1) {
            throw new IllegalArgumentException("A preview needs at least 1 character, not " + max + ".");
        }
        String oneLine = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 1).stripTrailing() + "…";
    }

    /** The heading of a notification board: "No unread notifications", "1 unread notification", "3 unread notifications". */
    public static String unreadSummary(int unread) {
        if (unread <= 0) {
            return "No unread notifications";
        }
        return unread + " unread notification" + (unread == 1 ? "" : "s");
    }

    /**
     * A seller's average rating: "4.5 / 5 (2 ratings)", or "No ratings yet" when there are none
     * (View Seller Stats A2).
     */
    public static String averageRating(RatingSummary rating) {
        if (rating.isEmpty()) {
            return "No ratings yet";
        }
        int count = rating.count();
        return String.format(Locale.ENGLISH, "%.1f / 5 (%d rating%s)", rating.average(), count, count == 1 ? "" : "s");
    }
}
