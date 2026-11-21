package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;

/**
 * The wording of the Deal History screen (UC11), kept apart from the JavaFX code so it can be
 * unit-tested. A deal's outcome reads as {@link OfferText#status} says (Special Requirement 4),
 * and its deal date as {@link Formats#date}.
 */
public final class HistoryText {

    /**
     * UC11 A1, both when the buyer has no completed deal yet ({Show History}) and when none
     * matches the filters ({Show Results}).
     */
    public static final String NO_DEALS = "No deals match. A deal appears here once it is completed.";

    private HistoryText() {
        // Utility class
    }

    /** The line above the table: "1 deal", "3 deals", or {@link #NO_DEALS} when there are none (A1). */
    public static String resultSummary(int count) {
        if (count <= 0) {
            return NO_DEALS;
        }
        return count == 1 ? "1 deal" : count + " deals";
    }

    /**
     * The deal type, as the buyer sees it (Special Requirement 3; Task 1.9): "Bought" for a price
     * offer, "Borrowed" for a borrow request and "Received" for a claim.
     * @throws IllegalArgumentException for any other kind of deal, so that a new kind is never
     *         shown under a wrong name (fail fast, Lecture 11)
     */
    public static String dealType(Offer deal) {
        if (deal instanceof PriceOffer) {
            return "Bought";
        }
        if (deal instanceof BorrowRequest) {
            return "Borrowed";
        }
        if (deal instanceof Claim) {
            return "Received";
        }
        throw new IllegalArgumentException("Unknown kind of deal: " + deal.getClass().getName());
    }
}
