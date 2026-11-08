package com.hkust.marketplace.models;

import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;

/** A listing lent out for a period (LendListing in the domain class diagram). */
public class LendListing extends Listing {

    private Duration lendingDuration;

    public LendListing(String sellerUsername, String title, Category category, ItemCondition condition,
                       String description, MeetupLocation meetupLocation, Duration lendingDuration) {
        super(sellerUsername, title, category, condition, description, meetupLocation);
        setLendingDuration(lendingDuration);
    }

    @Override
    public ListingType getType() {
        return ListingType.LEND;
    }

    @Override
    public String getPriceDisplay() {
        return "Borrow";
    }

    @Override
    protected ListingStatus statusWhenDealAccepted() {
        return ListingStatus.LENT;
    }

    /** A returned item can be lent again. */
    @Override
    protected ListingStatus statusWhenDealCompleted() {
        return ListingStatus.LIVE;
    }

    public Duration getLendingDuration() { return lendingDuration; }

    /**
     * @throws IllegalArgumentException if the duration is shorter than
     *         {@link BorrowRequest#MIN_BORROW_TIME} (Create Listings A3)
     */
    public void setLendingDuration(Duration lendingDuration) {
        Objects.requireNonNull(lendingDuration, "lendingDuration");
        if (lendingDuration.compareTo(BorrowRequest.MIN_BORROW_TIME) < 0) {
            throw new IllegalArgumentException("The lending duration must be at least "
                    + formatDuration(BorrowRequest.MIN_BORROW_TIME) + ".");
        }
        this.lendingDuration = lendingDuration;
    }

    /** The lending duration as text, such as "90 days" or "5 seconds". */
    public String getDurationDisplay() {
        return formatDuration(lendingDuration);
    }

    /** A duration as text, such as "1 day 2 hours" or "5 seconds". */
    public static String formatDuration(Duration duration) {
        StringJoiner parts = new StringJoiner(" ");
        addUnit(parts, duration.toDays(), "day");
        addUnit(parts, duration.toHoursPart(), "hour");
        addUnit(parts, duration.toMinutesPart(), "minute");
        addUnit(parts, duration.toSecondsPart(), "second");
        return parts.length() == 0 ? "0 seconds" : parts.toString();
    }

    private static void addUnit(StringJoiner parts, long amount, String unit) {
        if (amount > 0) {
            parts.add(amount + " " + unit + (amount == 1 ? "" : "s"));
        }
    }
}
