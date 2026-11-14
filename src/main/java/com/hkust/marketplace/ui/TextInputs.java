package com.hkust.marketplace.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * Checks for text typed into dialogs and forms. Kept apart from the JavaFX dialogs so the
 * rules can be unit-tested.
 */
public final class TextInputs {

    /** A time of day as it is typed: hours, minutes and optionally seconds, such as "9:05" or "14:30:05". */
    private static final DateTimeFormatter TIME_OF_DAY =
            DateTimeFormatter.ofPattern("H:mm[:ss]").withResolverStyle(ResolverStyle.STRICT);

    private TextInputs() {
        // Utility class
    }

    /**
     * A date chosen in a date picker and a time typed next to it, as one date and time; empty
     * if the date is missing or the time is not a time of day. The time is hours and minutes,
     * with seconds if wanted ("14:30" or "14:30:05"), so that a borrow period of a few
     * seconds can be entered (UC5 Special Requirement 3).
     */
    public static Optional<LocalDateTime> dateTime(LocalDate date, String time) {
        if (date == null || time == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(date.atTime(LocalTime.parse(time.strip(), TIME_OF_DAY)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * The text without surrounding spaces, or empty if nothing was typed. Used for required
     * inputs such as a rejection or removal reason, which must not be blank (Manage
     * Marketplace Listings Special Requirement 1; Manage All User Accounts Special Requirement 1).
     */
    public static Optional<String> required(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text.trim());
    }
}
