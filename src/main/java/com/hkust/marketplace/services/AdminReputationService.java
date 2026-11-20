package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Control class for Task 3.8 Seller Ratings Overview: the seller summary (step 5), a seller's
 * reviews (6.5) and hiding a review (6.6). Every method starts with the admin role check.
 * This class does not use the ui layer.
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
     * Lists the sellers with their rating data for the first view of the Seller Ratings tab,
     * ordered by username. Hidden reviews are not counted (Special Requirement 3).
     *
     * @param usernameText part of a seller's username, ignoring case; null or blank for all sellers
     * @return the matching sellers; empty if none match, never null
     */
    public List<SellerRating> findSellerRatings(String usernameText) {
        AccessControl.requireAdmin();
        String text = normalise(usernameText);

        return store.read(() -> store.getUsersByRole(UserRole.SELLER).stream()
                .filter(u -> text.isEmpty() || u.getUsername().toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing(User::getUsername))
                .map(u -> new SellerRating(u.getUsername(), u.getFullName(),
                        store.getAverageRating(u.getUsername()),
                        store.getRatingCount(u.getUsername()),
                        store.getCompletedDealCount(u.getUsername())))
                .toList());
    }

    /**
     * Lists reviews for the admin (Task 3.8, 6.5), including hidden ones. Every chosen filter
     * must match; a null filter field means "no filter". Results are sorted newest first.
     * The screen can mark a review the seller flagged with {@code Review.isFlagged()}.
     *
     * @param filter the filter options, or null for no filter
     * @return matching reviews, never null; empty if none match
     * @throws ServiceException if the rating filter is outside 1 to 5
     */
    public List<Review> findReviews(ReviewFilter filter) {
        AccessControl.requireAdmin();
        ReviewFilter f = filter == null ? ReviewFilter.none() : filter;

        if (f.rating() != null && (f.rating() < Review.MIN_RATING || f.rating() > Review.MAX_RATING)) {
            throw new ServiceException("The rating filter must be between 1 and 5.");
        }
        String sellerText = normalise(f.seller());

        return store.read(() -> store.getAllReviews().stream()
                .filter(r -> sellerText.isEmpty()
                        || store.getSellerUsername(r).toLowerCase(Locale.ROOT).contains(sellerText))
                .filter(r -> f.rating() == null || r.getRating() == f.rating())
                .filter(r -> f.fromDate() == null || !r.getReviewDate().isBefore(f.fromDate()))
                .filter(r -> f.toDate() == null || !r.getReviewDate().isAfter(f.toDate()))
                .sorted(Comparator.comparing(Review::getReviewDate).reversed())
                .toList());
    }

    /**
     * Hides a review (Task 3.8, 6.6.4): sets it hidden, records an Audit Log entry with the
     * reason and the admin's username, and notifies the buyer who wrote it and the seller it
     * is about, each with its own wording. The seller's average rating is recalculated
     * automatically, because hidden reviews are not counted.
     *
     * @throws ServiceException if the reason is blank (A1), the review does not exist,
     *         or it is already hidden
     */
    public void hideReview(String reviewId, String reason) {
        Admin admin = AccessControl.requireAdmin();
        String cleanReason = requireReason(reason, "A reason is required to hide a review.");

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
            String reviewer = store.getReviewerName(review); // 已刪除買家會顯示 "Deleted User"
            String itemTitle = store.getListingOf(store.findOffer(review.getOfferId())).getTitle();

            store.addAuditLogEntry(new AuditLogEntry(
                    admin.getUsername(), sellerUsername, "Hide review", cleanReason, null));

            // 只有買家還在才發通知
            if (store.findUser(buyerUsername) != null) {
                notifications.send(buyerUsername, NotificationCategory.ANNOUNCEMENT,
                        "Your review of " + store.getFullName(sellerUsername) + " for '" + itemTitle
                                + "' was hidden by an administrator. Reason: " + cleanReason);
            }
            // 賣家的通知使用 reviewer 變數
            notifications.send(sellerUsername, NotificationCategory.ANNOUNCEMENT,
                    "A review of you by " + reviewer + " for '" + itemTitle
                            + "' was hidden by an administrator. Reason: " + cleanReason);
        });
    }

    // ---------- helpers ----------

    private static String normalise(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    /** The reason without surrounding spaces; refuses a missing or blank reason. */
    private static String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(message);
        }
        return reason.strip();
    }
}