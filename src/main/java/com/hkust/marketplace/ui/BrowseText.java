package com.hkust.marketplace.ui;

/**
 * The wording of the Browse Marketplace screen (UC8), kept apart from the JavaFX code so it
 * can be unit-tested.
 */
public final class BrowseText {

    /** UC8 A1: shown instead of the listings when none match. */
    public static final String NO_LISTINGS = "No listings match. Try another keyword or fewer filters.";

    private BrowseText() {
        // Utility class
    }

    /** The line above the results: "1 listing", "6 listings", or {@link #NO_LISTINGS} when there are none. */
    public static String resultSummary(int count) {
        if (count <= 0) {
            return NO_LISTINGS;
        }
        return count == 1 ? "1 listing" : count + " listings";
    }

    /** A seller's bio on the profile screen (UC8 A3), or a line saying that there is none. */
    public static String bio(String bio) {
        return bio == null || bio.isBlank() ? "This seller has not written a bio." : bio.strip();
    }
}
