package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Review;

/**
 * UC10 Review Seller, for the Buyer (Task 1.10): rating the seller of a completed deal from 1
 * to 5 stars, with a written review, once per deal.
 *
 * <p><b>Design (Lecture 15).</b> This is the use case's control class. Its boundary is
 * {@code controllers.buyer.ReviewSellerFlow} with the form {@code ReviewDialog}, which starts
 * from the prompt after a deal is completed or returned, or from the Rate Seller button of a
 * completed deal on My Deals (step 1). The entities are {@link Review}, the reviewed
 * {@link Offer} and the store, whose rating queries include every review that is not hidden
 * (step 8).
 *
 * <p><b>The client's answers</b> ({@code docs/client-answers.md}) change the use case in two
 * places: the written review is required (#8; Special Requirement 2 said it was optional), and
 * the prompt also follows the return of a borrowed item, by the buyer or automatically (#3).
 *
 * <p><b>Defensive programming (Lecture 10).</b> Everything the form sends is untrusted: the
 * deal's id, a rating that may be missing or out of range, and the comment. Each is checked
 * here, in the order of the flow of events: the deal first, because a deal that cannot be
 * reviewed ends the use case whatever was typed (A1), then the rating (A2), then the comment.
 * The {@link Review} entity only receives clean values.
 */
public class ReviewService {

    /** UC10 A2. */
    static final String RATING_REQUIRED = "A rating from 1 to 5 stars is required.";
    /** Client answer #8: the written part of a review is not optional. */
    static final String COMMENT_REQUIRED = "A written review is required. Tell other buyers how the deal went.";
    /** UC10 A1, for each reason a deal cannot be reviewed. */
    static final String NOT_YOURS = "This is not one of your deals.";
    static final String NOT_COMPLETED = "This deal cannot be reviewed: it is not completed yet.";
    static final String ALREADY_REVIEWED = "This deal cannot be reviewed: you have already reviewed it.";

    private final DataStore store;
    private final NotificationService notifications;

    public ReviewService() {
        this(DataStore.getInstance());
    }

    public ReviewService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    // ========== Check Deal and show the form (steps 1-3) ==========

    /**
     * Starts reviewing one of the logged-in buyer's deals: checks that it is completed and not
     * reviewed yet (step 2), and returns what the form shows (step 3).
     * @throws DealNotReviewableException if the deal is not the buyer's, is not completed, or
     *         has been reviewed already (A1)
     */
    public ReviewForm start(String offerId) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.read(() -> {
            Offer deal = requireReviewable(buyer, offerId);
            Listing listing = store.getListingOf(deal);
            return new ReviewForm(deal.getId(), listing.getTitle(), store.getFullName(listing.getSellerUsername()));
        });
    }

    // ========== Check Rating, record the review and notify the seller (steps 5-9) ==========

    /**
     * Records the buyer's review of a deal, dated today, and notifies the seller (steps 6-9). It
     * is one atomic step, so a deal gets one review even if it is submitted twice at the same
     * moment (Special Requirement 1). The review cannot be changed afterwards (Special
     * Requirement 3): this class has no way to edit or delete one.
     * @param rating  the stars the buyer selected, or null if none was selected
     * @param comment the written review as typed
     * @return the recorded review
     * @throws DealNotReviewableException if the deal can no longer be reviewed (A1): the use
     *         case ends
     * @throws ServiceException if the rating is missing or not 1 to 5 (A2), or the written review
     *         is empty (client answer #8): the buyer corrects the form
     */
    public Review submit(String offerId, Integer rating, String comment) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.transaction(() -> {
            Offer deal = requireReviewable(buyer, offerId); // {Check Deal} again: the form may be out of date
            if (rating == null || rating < Review.MIN_RATING || rating > Review.MAX_RATING) {
                throw new ServiceException(RATING_REQUIRED); // A2
            }
            if (comment == null || comment.isBlank()) {
                throw new ServiceException(COMMENT_REQUIRED);
            }
            Review review = store.addReview(new Review(deal.getId(), rating, comment)); // step 7, trimmed
            Listing listing = store.getListingOf(deal);
            notifications.send(listing.getSellerUsername(), NotificationCategory.REVIEW_RECEIVED,
                    news(buyer.getFullName(), review, listing)); // step 9

            // Postconditions of the use case. Assertions document and verify what must hold
            // here; they are not input checks (Lecture 10).
            assert store.findReviewForOffer(deal.getId()) == review : "the deal has its review";
            assert store.getReviewsForSeller(listing.getSellerUsername()).contains(review)
                    : "the seller's rating includes the review"; // step 8
            return review;
        });
    }

    // ========== Rules ==========

    /** {Check Deal}: step 2, A1 and Special Requirement 1. */
    private Offer requireReviewable(Buyer buyer, String offerId) {
        Offer deal = store.findOffer(offerId);
        if (deal == null || !deal.getBuyerUsername().equalsIgnoreCase(buyer.getUsername())) {
            throw new DealNotReviewableException(NOT_YOURS);
        }
        if (!deal.isCompleted()) {
            throw new DealNotReviewableException(NOT_COMPLETED);
        }
        if (store.findReviewForOffer(deal.getId()) != null) {
            throw new DealNotReviewableException(ALREADY_REVIEWED);
        }
        return deal;
    }

    /** What the seller's notification says (step 9): "Bea Lee rated you 4 out of 5 stars for 'Book': "Smooth deal"". */
    private static String news(String buyerName, Review review, Listing listing) {
        return buyerName + " rated you " + review.getRating() + " out of " + Review.MAX_RATING + " stars for '"
                + listing.getTitle() + "': \"" + review.getComment() + "\"";
    }
}
