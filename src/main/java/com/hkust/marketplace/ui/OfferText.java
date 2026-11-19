package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;

import java.util.Optional;

/**
 * How a price offer, borrow request or claim reads on every screen that shows one: the chat
 * history (UC6), My Deals (UC7), the seller's offers and the admin's deal records. One place,
 * so a deal is worded the same in all three portals.
 */
public final class OfferText {

    private OfferText() {
        // Utility class
    }

    /**
     * What the buyer's action on a listing is called, by listing type (Task 1.4): "Make Offer"
     * for a sale, "Request Borrow" for a lend listing, "Claim Item" for a giveaway. The same
     * words are on the detail screen's button and on the Make Offer option in a chat (UC5 step 1).
     */
    public static String actionText(ListingType type) {
        return switch (type) {
            case SELL -> "Make Offer";
            case LEND -> "Request Borrow";
            case GIVEAWAY -> "Claim Item";
        };
    }

    /**
     * The confirmation after Make Offer (UC5 step 9):
     * "Your price offer for 'Desk Lamp' was submitted: HK$ 160. The seller has been notified."
     * For a claim, which has no terms: "Your claim for 'Desk Lamp' was submitted. The seller has been notified."
     */
    public static String submitted(Offer offer, String listingTitle) {
        String terms = terms(offer);
        return "Your " + offer.getKindName() + " for '" + listingTitle + "' was submitted"
                + (terms.isEmpty() ? "." : ": " + terms + ".") + " The seller has been notified.";
    }

    /** "Price offer", "Borrow request" or "Claim". */
    public static String kind(Offer offer) {
        String name = offer.getKindName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * What the buyer proposed: "Price offer: HK$ 160",
     * "Borrow request: 10 Nov 2026 09:00 – 17 Nov 2026 09:00", or "Claim" (a claim has no terms).
     */
    public static String summary(Offer offer) {
        String terms = terms(offer);
        return terms.isEmpty() ? kind(offer) : kind(offer) + ": " + terms;
    }

    /**
     * What the buyer proposed, without the kind of offer: "HK$ 160" or
     * "10 Nov 2026 09:00 – 17 Nov 2026 09:00". Empty for a claim, which has no terms.
     */
    public static String terms(Offer offer) {
        if (offer instanceof PriceOffer price) {
            return Formats.price(price.getOfferedPrice());
        }
        if (offer instanceof BorrowRequest borrow) {
            return Formats.period(borrow.getProposedStartDate(), borrow.getProposedEndDate());
        }
        return "";
    }

    /**
     * The seller's counter, if there is one: "Seller's counter: HK$ 170" or
     * "Seller's counter: 11 Nov 2026 09:00 – 18 Nov 2026 09:00". Empty for an offer that was
     * not countered; a claim cannot be countered.
     */
    public static Optional<String> counter(Offer offer) {
        return counterTerms(offer).map(terms -> "Seller's counter: " + terms);
    }

    /** The terms of the seller's counter, without a label: "HK$ 170" or a period; empty if there is none. */
    public static Optional<String> counterTerms(Offer offer) {
        if (offer instanceof PriceOffer price && price.getCounterPrice() != null) {
            return Optional.of(Formats.price(price.getCounterPrice()));
        }
        if (offer instanceof BorrowRequest borrow && borrow.getCounterStartDate() != null) {
            return Optional.of(Formats.period(borrow.getCounterStartDate(), borrow.getCounterEndDate()));
        }
        return Optional.empty();
    }

    /**
     * "Pending", "Accepted", "Declined" or "Countered". Once the deal is completed: "Completed"
     * for a purchase or claim, and for a borrowed item "Returned" if the buyer returned it or
     * "Auto-returned" if it was returned when its period ended (UC12 step 4; UC11).
     */
    public static String status(Offer offer) {
        if (!offer.isCompleted()) {
            return offer.getStatus().getDisplayName();
        }
        if (offer instanceof BorrowRequest request) {
            return request.isAutoReturned() ? "Auto-returned" : "Returned";
        }
        return "Completed";
    }

    /** The status badge's style class in styles.css: orange while waiting, green, red, or blue. */
    public static String badgeStyle(Offer offer) {
        if (offer.isCompleted()) {
            return "badge-sold";
        }
        return switch (offer.getStatus()) {
            case PENDING -> "badge-pending";
            case ACCEPTED -> "badge-live";
            case DECLINED -> "badge-rejected";
            case COUNTERED -> "badge-sold";
        };
    }
}
