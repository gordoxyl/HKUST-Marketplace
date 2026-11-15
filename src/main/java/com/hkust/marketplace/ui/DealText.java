package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.Deal;

import java.util.Optional;

/**
 * The wording of the My Deals screen (UC7), kept apart from the JavaFX code so it can be
 * unit-tested. How the deal itself reads (type, terms, counter, status) is in {@link OfferText}.
 */
public final class DealText {

    /** UC7 A1: shown when the buyer has no current or completed deals. */
    public static final String NO_DEALS = "You have no deals yet. Open a listing in Browse Marketplace "
            + "to make an offer, send a borrow request or claim an item.";

    private DealText() {
        // Utility class
    }

    /** The seller and the deal type: "Seller: Charlie Lee · Price offer". */
    public static String about(Deal deal) {
        return "Seller: " + deal.sellerName() + " · " + OfferText.kind(deal.offer());
    }

    /**
     * When the deal was made, accepted and completed, as far as it got:
     * "Made 09 Nov 2026 · Accepted 10 Nov 2026 · Completed 12 Nov 2026". A borrowed item
     * says "Returned" instead of "Completed", or "Returned automatically" when the borrowing
     * period ended (UC12).
     */
    public static String dates(Deal deal) {
        Offer offer = deal.offer();
        StringBuilder text = new StringBuilder("Made " + Formats.date(offer.getOfferDate()));
        if (offer.getAcceptedDate() != null) {
            text.append(" · Accepted ").append(Formats.date(offer.getAcceptedDate()));
        }
        if (offer.isCompleted()) {
            text.append(" · ").append(completedWord(offer)).append(' ').append(Formats.date(offer.getCompletedDate()));
        }
        return text.toString();
    }

    private static String completedWord(Offer offer) {
        if (offer instanceof BorrowRequest request) {
            return request.isAutoReturned() ? "Returned automatically" : "Returned";
        }
        return "Completed";
    }

    /**
     * The due date of a borrowed item that has not been returned yet (UC7 step 2), to the
     * second: "Due 17 Nov 2026 09:00:00". Empty for every other deal.
     */
    public static Optional<String> due(Deal deal) {
        if (deal.offer().isCompleted()) {
            return Optional.empty();
        }
        return deal.dueDate().map(date -> "Due " + Formats.borrowTime(date));
    }

    /** The question before a deal is marked complete (UC7 step 4). */
    public static String confirmComplete(Deal deal) {
        return "Mark your " + deal.offer().getKindName() + " for '" + deal.listing().getTitle()
                + "' complete? Do this once you have received the item. It cannot be undone.";
    }

    /** The question before a borrowed item is returned (UC7 A2 step 1). */
    public static String confirmReturn(Deal deal) {
        return "Return '" + deal.listing().getTitle() + "' to " + deal.sellerName()
                + " now? This ends the borrowing and cannot be undone.";
    }

    /** What the buyer is told after returning an item (UC7 A2). */
    public static String returned(Deal deal) {
        return "'" + deal.listing().getTitle() + "' was returned. " + deal.sellerName() + " has been notified.";
    }

    /** The prompt after a deal is marked complete (UC7 step 9). */
    public static String ratePrompt(Deal deal) {
        return "The deal for '" + deal.listing().getTitle() + "' is complete, and " + deal.sellerName()
                + " has been notified. Would you like to rate and review the seller now?";
    }
}
