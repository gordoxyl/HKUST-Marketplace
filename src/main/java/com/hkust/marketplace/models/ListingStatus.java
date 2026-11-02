package com.hkust.marketplace.models;

/** Lifecycle status of a listing (ListingStatus in the domain class diagram). */
public enum ListingStatus {
    PENDING_REVIEW("Pending Review"),
    LIVE("Live"),
    REJECTED("Rejected"),
    RESERVED("Reserved"),
    SOLD("Sold"),
    LENT("Lent"),
    GIVEN("Given"),
    REMOVED("Removed");

    private final String displayName;

    ListingStatus(String displayName) {
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
