package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
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
        if (offer instanceof PriceOffer price) {
            return kind(offer) + ": " + Formats.price(price.getOfferedPrice());
        }
        if (offer instanceof BorrowRequest borrow) {
            return kind(offer) + ": " + Formats.period(borrow.getProposedStartDate(), borrow.getProposedEndDate());
        }
        return kind(offer);
    }

    /**
     * The seller's counter, if there is one: "Seller's counter: HK$ 170" or
     * "Seller's counter: 11 Nov 2026 09:00 – 18 Nov 2026 09:00". Empty for an offer that was
     * not countered; a claim cannot be countered.
     */
    public static Optional<String> counter(Offer offer) {
        if (offer instanceof PriceOffer price && price.getCounterPrice() != null) {
            return Optional.of("Seller's counter: " + Formats.price(price.getCounterPrice()));
        }
        if (offer instanceof BorrowRequest borrow && borrow.getCounterStartDate() != null) {
            return Optional.of("Seller's counter: "
                    + Formats.period(borrow.getCounterStartDate(), borrow.getCounterEndDate()));
        }
        return Optional.empty();
    }

    /** "Pending", "Accepted", "Declined" or "Countered"; "Completed" once an accepted deal is completed or returned. */
    public static String status(Offer offer) {
        return offer.isCompleted() ? "Completed" : offer.getStatus().getDisplayName();
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
