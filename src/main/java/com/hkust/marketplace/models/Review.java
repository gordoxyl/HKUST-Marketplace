package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A buyer's rating and optional comment on the seller of one completed deal
 * (Review in the domain class diagram; UC10). The buyer cannot change it afterwards
 * (UC10 Special Requirement 3), so the rating and comment are final.
 * The buyer and seller are found through the reviewed offer.
 */
public class Review {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    private String id;
    private final String offerId;
    private final int rating;
    private final String comment;
    private LocalDateTime reviewDate;
    private String sellerReply;
    private boolean flagged;
    private boolean hidden;

    /** @throws IllegalArgumentException if the rating is not 1 to 5 */
    public Review(String offerId, int rating, String comment) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("A rating from " + MIN_RATING + " to " + MAX_RATING + " stars is required.");
        }
        this.offerId = Objects.requireNonNull(offerId, "offerId");
        this.rating = rating;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
    }

    /** Null until the review is added to DataStore. */
    public String getId() { return id; }

    /** Called once by DataStore when the review is added: gives it its id and review date. */
    public void markAdded(String id, LocalDateTime reviewDate) {
        if (this.id != null) {
            throw new IllegalStateException("Review already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
        this.reviewDate = Objects.requireNonNull(reviewDate, "reviewDate");
    }

    public String getOfferId() { return offerId; }
    public int getRating() { return rating; }

    /** Null if the buyer wrote no comment. */
    public String getComment() { return comment; }
    public LocalDateTime getReviewDate() { return reviewDate; }

    /** Null until the seller replies. */
    public String getSellerReply() { return sellerReply; }

    /**
     * Saves the seller's reply. A review has at most one reply (Manage Reviews Special Requirement 1).
     * @throws IllegalStateException if the review already has a reply
     * @throws IllegalArgumentException if the reply is empty
     */
    public void reply(String reply) {
        if (sellerReply != null) {
            throw new IllegalStateException("Only one reply is allowed per review.");
        }
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("The reply cannot be empty.");
        }
        sellerReply = reply.trim();
    }

    /** True once the seller flagged the review for the admin. */
    public boolean isFlagged() { return flagged; }
    public void flag() { flagged = true; }

    /** A hidden review stays stored but is neither shown nor counted in ratings (UC8 Special Requirement 5). */
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}
