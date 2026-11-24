package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.RatingSummary;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Seller;

import java.util.List;

/**
 * Task 2.9 Review and Feedback Handling: the reviews buyers left for the seller's completed
 * deals, and the two things the seller can do with one (Manage Reviews).
 *
 * <ul>
 *   <li><b>Reply</b> - one reply per review (A2), never empty (A3); the buyer who wrote the
 *       review is told with a {@link NotificationCategory#REVIEW_REPLY} notification.</li>
 *   <li><b>Flag</b> - marks an inappropriate review for the admin ({@link Review#isFlagged()});
 *       a already flagged review is "already awaiting moderation" (A4), and an
 *       {@link NotificationCategory#ANNOUNCEMENT} notice puts it on the admin's board.</li>
 * </ul>
 *
 * <p>Only the seller of the reviewed deal's listing may act on a review, and every method
 * starts with the role check, like the other seller services. A hidden review is not in
 * {@link #getMyReviews()}: the admin hid it from the marketplace.
 */
public class SellerReviewService {

    private final DataStore store;
    private final NotificationService notifications;

    public SellerReviewService() {
        this(DataStore.getInstance());
    }

    public SellerReviewService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    /**
     * Step 1: the reviews buyers left for the logged-in seller's completed deals, newest first.
     * Hidden reviews are not included.
     * @throws AccessDeniedException if the logged-in user is not a seller
     */
    public List<Review> getMyReviews() {
        Seller seller = AccessControl.requireSeller();
        return store.getReviewsForSeller(seller.getUsername());
    }

    /**
     * The seller's rating, shown above the reviews: count and average, hidden reviews left out
     * (View Seller Stats A2 and SR1).
     * @throws AccessDeniedException if the logged-in user is not a seller
     */
    public RatingSummary getMyRating() {
        Seller seller = AccessControl.requireSeller();
        return store.getRating(seller.getUsername());
    }

    /**
     * Replies to one review and tells the buyer (Manage Reviews steps 3-5).
     * @param reply the seller's reply; required, and stored without surrounding spaces
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if the review is not on the seller's listings, already has a
     *                               reply, or the reply is empty; nothing is changed then
     */
    public void reply(String reviewId, String reply) {
        Seller seller = AccessControl.requireSeller();
        String text = requireReply(reply);
        store.transaction(() -> {
            Review review = requireOwnedReview(reviewId, seller);
            if (review.getSellerReply() != null) {
                throw new ServiceException("Only one reply is allowed per review.");
            }
            Listing listing = store.getListingOf(store.findOffer(review.getOfferId()));
            review.reply(text);
            notifications.send(store.getBuyerUsername(review), NotificationCategory.REVIEW_REPLY,
                    seller.getFullName() + " replied to your review for '" + listing.getTitle()
                            + "': " + text);
        });
    }

    /**
     * Flags one review for the admin as inappropriate (Manage Reviews step 6). The admin's
     * notification board gets an entry, so the flag is seen without the admin watching the
     * ratings screen.
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if the review is not on the seller's listings or is already flagged
     */
    public void flag(String reviewId) {
        Seller seller = AccessControl.requireSeller();
        store.transaction(() -> {
            Review review = requireOwnedReview(reviewId, seller);
            if (review.isFlagged()) {
                throw new ServiceException("This review is already awaiting moderation.");
            }
            Listing listing = store.getListingOf(store.findOffer(review.getOfferId()));
            review.flag();
            notifications.sendToAdmins(NotificationCategory.ANNOUNCEMENT,
                    "@" + seller.getUsername() + " flagged a review of '" + listing.getTitle()
                            + "' as inappropriate.");
        });
    }

    /** The reply as it will be stored: required, trimmed. */
    private static String requireReply(String reply) {
        if (reply == null || reply.isBlank()) {
            throw new ServiceException("The reply cannot be empty.");
        }
        return reply.trim();
    }

    /** The review must exist and be on one of the logged-in seller's listings. */
    private Review requireOwnedReview(String reviewId, Seller seller) {
        Review review = store.getAllReviews().stream()
                .filter(candidate -> candidate.getId().equals(reviewId))
                .findFirst()
                .orElse(null);
        if (review == null || !seller.getUsername().equals(store.getSellerUsername(review))) {
            throw new ServiceException("This review is not on one of your listings.");
        }
        return review;
    }
}
