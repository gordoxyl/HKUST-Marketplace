package com.hkust.marketplace.models;

/** Categories of notifications on the notification boards (UC4 Special Requirement 2). */
public enum NotificationCategory {
    OFFER_UPDATE("Offer Update"),
    NEW_MESSAGE("New Message"),
    DUE_DATE_REMINDER("Due-Date Reminder"),
    LISTING_REMOVED("Listing Removed"),
    REVIEW_REPLY("Review Reply"),
    LISTING_APPROVED("Listing Approved"),
    LISTING_REJECTED("Listing Rejected"),
    OFFER_RECEIVED("Offer Received"),
    REVIEW_RECEIVED("Review Received"),
    DEAL_COMPLETED("Deal Completed"),
    LISTING_SUBMITTED("Listing Submitted"),
    LISTING_REPORTED("Listing Reported"),
    ACCOUNT_CHANGE("Account Change"),
    ANNOUNCEMENT("Announcement");

    private final String displayName;

    NotificationCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The display name, so combo boxes and tables show readable text. */
    @Override
    public String toString() {
        return displayName;
    }
}
