package com.hkust.marketplace.services;

/** The state of a deal on the All Deals Record screen (Task 3.5). Only accepted offers are deals. */
public enum DealState {
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    RETURNED("Returned");

    private final String displayName;

    DealState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}