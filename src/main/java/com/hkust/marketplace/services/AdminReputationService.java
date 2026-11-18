package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.ui.TextInputs;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Control class for Task 3.8 Seller Ratings Overview: view with filters (6.5) and
 * hide review (6.6). Every method starts with the admin role check.
 */
public class AdminReputationService {

    private final DataStore store;
    private final NotificationService notifications;

    public AdminReputationService() {
        this(DataStore.getInstance());
    }

    public AdminReputationService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    /**
     * Lists reviews for the admin (Task 3.8 6.5), including hidden ones. Every chosen filter
     * must match; a null filter field means "no filter". Results are sorted newest first.
     *
     * @param filter the filter options, or null for no filter
     * @return matching reviews, never null; empty if none match
     * @throws ServiceException if the rating filter is outside 1–5
     */
    public List<Review> findReviews(ReviewFilter filter) {
        AccessControl.requireAdmin();
        ReviewFilter f = filter == null ? ReviewFilter.none() : filter;

        if (f.rating() != null && (f.rating() < Review.MIN_RATING || f.rating() > Review.MAX_RATING)) {
            throw new ServiceException("The rating filter must be between 1 and 5.");
        }

        return store.getAllReviews().stream()
                .filter(r -> f.seller() == null
                        || store.getSellerUsername(r).toLowerCase(Locale.ROOT)
                        .equals(f.seller().trim().toLowerCase(Locale.ROOT)))
                .filter(r -> f.rating() == null || r.getRating() == f.rating())
                .filter(r -> f.fromDate() == null || !r.getReviewDate().isBefore(f.fromDate()))
                .filter(r -> f.toDate() == null || !r.getReviewDate().isAfter(f.toDate()))
                .sorted(Comparator.comparing(Review::getReviewDate).reversed())
                .toList();
    }

    /**
     * Hides a review (Task 3.8 6.6.4): sets it hidden, records an audit log entry with the
     * reason and admin username, and notifies both the buyer who wrote the review and the
     * seller it concerns. The average rating is recalculated automatically.
     *
     * @throws ServiceException if the reason is blank (A1), the review does not exist,
     *         or it is already hidden
     */
    public void hideReview(String reviewId, String reason) {
        Admin admin = AccessControl.requireAdmin();
        String cleanReason = TextInputs.required(reason)
                .orElseThrow(() -> new ServiceException("A reason is required to hide a review."));

        store.transaction(() -> {
            Review review = store.getAllReviews().stream()
                    .filter(r -> r.getId().equals(reviewId))
                    .findFirst()
                    .orElseThrow(() -> new ServiceException("This review no longer exists."));

            if (review.isHidden()) {
                throw new ServiceException("This review is already hidden.");
            }

            review.setHidden(true);

            String buyerUsername = store.getBuyerUsername(review);
            String sellerUsername = store.getSellerUsername(review);

            store.addAuditLogEntry(new AuditLogEntry(
                    admin.getUsername(), sellerUsername, "Hide review", cleanReason, null));

            String message = "The review for your listing has been hidden by an administrator. Reason: " + cleanReason;
            notifications.send(buyerUsername, NotificationCategory.ANNOUNCEMENT, message);
            notifications.send(sellerUsername, NotificationCategory.ANNOUNCEMENT, message);
        });
    }
}