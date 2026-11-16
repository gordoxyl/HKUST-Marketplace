package com.hkust.marketplace.services;

/** The sort orders of Browse Marketplace (UC8 step 3; Task 1.3). */
public enum ListingSort {
    NEWEST("Newest"),
    PRICE_LOW_TO_HIGH("Price: low to high"),
    PRICE_HIGH_TO_LOW("Price: high to low");

    private final String displayName;

    ListingSort(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The display name, so a combo box shows readable text. */
    @Override
    public String toString() {
        return displayName;
    }
}
