package com.hkust.marketplace.models;

/** The three kinds of listing (Task 2.3). Each matches one Listing subclass. */
public enum ListingType {
    SELL("Sell"),
    LEND("Lend"),
    GIVEAWAY("Giveaway");

    private final String displayName;

    ListingType(String displayName) {
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
