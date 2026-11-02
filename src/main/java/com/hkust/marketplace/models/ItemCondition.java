package com.hkust.marketplace.models;

/** Condition of a listed item (ItemCondition in the domain class diagram; Task 2.3). */
public enum ItemCondition {
    NEW("New"),
    LIKE_NEW("Like New"),
    USED("Used"),
    FOR_PARTS("For Parts");

    private final String displayName;

    ItemCondition(String displayName) {
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
