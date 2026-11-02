package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A buyer's action on a listing (Offer in the domain class diagram), called a "deal" in the
 * use cases. Every offer is exactly one of {@link PriceOffer}, {@link BorrowRequest} or
 * {@link Claim} ({complete, disjoint}).
 *
 * An offer belongs to the chat thread between its buyer and its listing (UC5 step 7).
 * The status changes only through the methods below, which enforce:
 * <ul>
 *   <li>only a Pending offer can be accepted or countered (Manage Offers &amp; Requests);</li>
 *   <li>a Pending or Countered offer can be declined (UC7 Special Requirement 5);</li>
 *   <li>only an Accepted offer can be completed, and only once (UC7 Special Requirement 1);</li>
 *   <li>{acceptedDate and completedDate can only be set if status = Accepted}.</li>
 * </ul>
 */
public abstract class Offer {

    private String id;
    private final String listingId;
    private final String buyerUsername;
    private OfferStatus status = OfferStatus.PENDING;
    private final LocalDateTime offerDate = LocalDateTime.now();
    private LocalDateTime acceptedDate;
    private LocalDateTime completedDate;

    protected Offer(String listingId, String buyerUsername) {
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.buyerUsername = Objects.requireNonNull(buyerUsername, "buyerUsername");
    }

    /**
     * True if this kind of offer can be made on the listing: a price offer only on a sale
     * listing, a borrow request only on a lend listing, and a claim only on a giveaway listing.
     */
    public abstract boolean canBeMadeOn(Listing listing);

    /** Null until the offer is added to DataStore. */
    public String getId() { return id; }

    /** Called once by DataStore when the offer is added. */
    public void assignId(String id) {
        if (this.id != null) {
            throw new IllegalStateException("Offer already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
    }

    public String getListingId() { return listingId; }
    public String getBuyerUsername() { return buyerUsername; }
    public OfferStatus getStatus() { return status; }
    public LocalDateTime getOfferDate() { return offerDate; }

    /** Null unless the offer was accepted. */
    public LocalDateTime getAcceptedDate() { return acceptedDate; }

    /** Null until the deal is completed (marked complete, or the item returned). */
    public LocalDateTime getCompletedDate() { return completedDate; }

    /** The seller accepts this offer. */
    public void accept(LocalDateTime when) {
        requireStatus(OfferStatus.PENDING, "accepted");
        status = OfferStatus.ACCEPTED;
        acceptedDate = Objects.requireNonNull(when, "when");
    }

    /** The seller, or the system, declines this offer. */
    public void decline() {
        if (!isOpen()) {
            throw new IllegalStateException("Only a pending or countered offer can be declined.");
        }
        status = OfferStatus.DECLINED;
    }

    /** Used by the subclasses' counter methods, after they check the counter terms. */
    protected void markCountered() {
        requireStatus(OfferStatus.PENDING, "countered");
        status = OfferStatus.COUNTERED;
    }

    /** Records when an accepted deal was completed. */
    public void complete(LocalDateTime when) {
        if (status != OfferStatus.ACCEPTED || completedDate != null) {
            throw new IllegalStateException("Only an accepted deal that is not yet completed can be completed.");
        }
        completedDate = Objects.requireNonNull(when, "when");
    }

    /** Pending or Countered: still waiting for an answer. */
    public boolean isOpen() {
        return status == OfferStatus.PENDING || status == OfferStatus.COUNTERED;
    }

    /** Has a completion date (glossary: "Completed deal"). */
    public boolean isCompleted() {
        return completedDate != null;
    }

    /** A current deal on My Deals: open, or accepted and not yet completed (UC7 step 2). */
    public boolean isCurrent() {
        return isOpen() || (status == OfferStatus.ACCEPTED && !isCompleted());
    }

    private void requireStatus(OfferStatus required, String action) {
        if (status != required) {
            throw new IllegalStateException("Only a " + required.getDisplayName().toLowerCase()
                    + " offer can be " + action + ".");
        }
    }
}
