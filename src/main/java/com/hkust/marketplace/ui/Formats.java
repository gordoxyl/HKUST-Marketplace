package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * One way to show dates, prices, ratings and periods on every screen, so all three portals
 * look and read the same (non-functional requirement: a consistent interface).
 */
public final class Formats {

    /** Shown when there is no value, such as a deal that is not completed yet. */
    public static final String NONE = "—";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);

    private Formats() {
        // Utility class
    }

    /** "09 Nov 2026", or {@link #NONE} for no date. */
    public static String date(LocalDateTime time) {
        return time == null ? NONE : time.format(DATE);
    }

    /** "09 Nov 2026 14:20", or {@link #NONE} for no date. */
    public static String dateTime(LocalDateTime time) {
        return time == null ? NONE : BorrowRequest.formatDateTime(time); // the same form as in notifications
    }

    /** The time of day with seconds, as it is typed into a time box: "14:05:30"; empty text for no time. */
    public static String timeOfDay(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_OF_DAY);
    }

    /** A borrow period: "10 Nov 2026 09:00 – 17 Nov 2026 09:00". */
    public static String period(LocalDateTime start, LocalDateTime end) {
        return dateTime(start) + " – " + dateTime(end);
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
    public static String averageRating(OptionalDouble average, int count) {
        if (average.isEmpty() || count == 0) {
            return "No ratings yet";
        }
        return String.format(Locale.ENGLISH, "%.1f / 5 (%d rating%s)", average.getAsDouble(), count, count == 1 ? "" : "s");
    }
}
