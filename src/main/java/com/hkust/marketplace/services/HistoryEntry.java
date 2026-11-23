package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One completed deal as Deal History shows it (UC11 step 2): the deal, its listing and the
 * seller's name. How the deal type and the outcome read is up to the screen
 * ({@code ui.HistoryText} and {@code ui.OfferText}), so this record only carries the facts.
 *
 * <p>A deal is in the history only once it has a completion date (Special Requirement 1; the
 * glossary's "Completed deal"), so the record refuses any other. That invariant holds for
 * every row, also while the borrowing timer completes deals on its own thread.
 *
 * @param deal       the completed price offer, borrow request or claim
 * @param listing    its listing, with the listing's current title and category: no copy is kept
 *                   when a deal is completed (decided 21 Nov 2026, see {@code docs/client-answers.md})
 * @param sellerName the seller's full name, or the username if the account was deleted
 */
public record HistoryEntry(Offer deal, Listing listing, String sellerName) {

    /** @throws IllegalArgumentException if the deal is not completed */
    public HistoryEntry {
        Objects.requireNonNull(deal, "deal");
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(sellerName, "sellerName");
        if (!deal.isCompleted()) {
            throw new IllegalArgumentException("Only a completed deal is in the history.");
        }
    }

    /**
     * The deal date (Special Requirement 2; client answer #4): the completion date. For a
     * borrowed item it is the return date, and for an automatic return the due date (UC12 step 3).
     */
    public LocalDateTime dealDate() {
        return deal.getCompletedDate();
    }
}
