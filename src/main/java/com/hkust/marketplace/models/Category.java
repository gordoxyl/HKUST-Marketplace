package com.hkust.marketplace.models;

/** Predefined item categories (Category in the domain class diagram; Task 2.3). */
public enum Category {
    TEXTBOOKS("Textbooks"),
    ELECTRONICS("Electronics"),
    FURNITURE("Furniture"),
    CLOTHING("Clothing"),
    STATIONERY("Stationery"),
    SPORTS_EQUIPMENT("Sports Equipment"),
    KITCHENWARE("Kitchenware"),
    OTHERS("Others");

    private final String displayName;

    Category(String displayName) {
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
