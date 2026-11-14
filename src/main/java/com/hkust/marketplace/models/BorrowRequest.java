package com.hkust.marketplace.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** A request to borrow a lend listing for a period (BorrowRequest in the domain class diagram). */
public class BorrowRequest extends Offer {

    /** Shortest allowed borrow period: 5 seconds, so auto-return can be tested (Task 1.5). */
    public static final Duration MIN_BORROW_TIME = Duration.ofSeconds(5);

    /** How long before the due date the buyer is reminded (UC12 Special Requirement 2). */
    public static final Duration REMINDER_LEAD_TIME = Duration.ofHours(24);

    private static final DateTimeFormatter BORROW_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH);

    private final LocalDateTime proposedStartDate;
    private final LocalDateTime proposedEndDate;
    private LocalDateTime counterStartDate;
    private LocalDateTime counterEndDate;
    private boolean autoReturned;
    private boolean reminderSent;

    /** @throws IllegalArgumentException if the period is shorter than {@link #MIN_BORROW_TIME} */
    public BorrowRequest(String listingId, String buyerUsername,
                         LocalDateTime proposedStartDate, LocalDateTime proposedEndDate) {
        super(listingId, buyerUsername);
        requireValidPeriod(proposedStartDate, proposedEndDate);
        this.proposedStartDate = proposedStartDate;
        this.proposedEndDate = proposedEndDate;
    }

    @Override
    public boolean canBeMadeOn(Listing listing) {
        return listing instanceof LendListing;
    }

    @Override
    public String getKindName() {
        return "borrow request";
    }

    public LocalDateTime getProposedStartDate() { return proposedStartDate; }
    public LocalDateTime getProposedEndDate() { return proposedEndDate; }

    /** Null unless the seller countered. */
    public LocalDateTime getCounterStartDate() { return counterStartDate; }
    public LocalDateTime getCounterEndDate() { return counterEndDate; }

    /** The end of the requested period (UC7 Special Requirement 2). */
    public LocalDateTime getDueDate() { return proposedEndDate; }

    /**
     * When the buyer is reminded that the item is nearly due (UC12 Special Requirement 2):
     * {@link #REMINDER_LEAD_TIME 24 hours} before the due date, or halfway through the period
     * if the period is shorter than 48 hours.
     */
    public LocalDateTime getReminderTime() {
        Duration period = Duration.between(proposedStartDate, proposedEndDate);
        if (period.compareTo(REMINDER_LEAD_TIME.multipliedBy(2)) < 0) {
            return proposedStartDate.plus(period.dividedBy(2));
        }
        return proposedEndDate.minus(REMINDER_LEAD_TIME);
    }

    /**
     * A borrow time to the second, as screens and notifications show it: "14 Nov 2026 14:06:44".
     * Borrowing works to the second (the shortest loan is {@link #MIN_BORROW_TIME}), so a time
     * shown to the minute would make an item that is returned on time look late (regression log R9).
     */
    public static String formatBorrowTime(LocalDateTime time) {
        return time.format(BORROW_TIME);
    }

    /** True if the item was returned automatically when the period ended (UC12). */
    public boolean isAutoReturned() { return autoReturned; }

    /** True once the due-date reminder was sent; each request gets one (UC12 Special Requirement 3). */
    public boolean isReminderSent() { return reminderSent; }
    public void markReminderSent() { reminderSent = true; }

    /** The seller counters with other dates (Manage Offers &amp; Requests step 3.5). */
    public void counter(LocalDateTime start, LocalDateTime end) {
        requireValidPeriod(start, end);
        markCountered();
        counterStartDate = start;
        counterEndDate = end;
    }

    /** Returns the item automatically, with the due date as the completion date (UC12 step 3). */
    public void autoReturn() {
        complete(getDueDate());
        autoReturned = true;
    }

    /** @throws IllegalArgumentException if a date is missing or the period is too short */
    private static void requireValidPeriod(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (Duration.between(start, end).compareTo(MIN_BORROW_TIME) < 0) {
            throw new IllegalArgumentException("The borrow period must end at least "
                    + LendListing.formatDuration(MIN_BORROW_TIME) + " after it starts.");
        }
    }
}
