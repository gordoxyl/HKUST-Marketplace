package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Review;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How a seller's rating and reviews read on every screen that shows them: the listing detail
 * screen and the seller's profile (UC8 step 7 and A3), and the seller's and admin's own review
 * screens. Kept apart from the JavaFX code so it can be unit-tested.
 */
public final class ReviewText {

    private ReviewText() {
        // Utility class
    }

    /** The heading above a seller's reviews: "Seller rating: 4.5 / 5 (2 ratings)" or "Seller rating: No ratings yet". */
    public static String ratingHeading(OptionalDouble average, int count) {
        return "Seller rating: " + Formats.averageRating(average, count);
    }

    /** A review's rating and date: "★★★★☆ · 09 Nov 2026". */
    public static String header(Review review) {
        return Formats.stars(review.getRating()) + " · " + Formats.date(review.getReviewDate());
    }

    /** The buyer's comment, or "No comment." for a rating without one. */
    public static String comment(Review review) {
        return review.getComment() == null ? "No comment." : review.getComment();
    }

    /** "Seller's reply: Thank you!", or empty if the seller has not replied. */
    public static Optional<String> reply(Review review) {
        return Optional.ofNullable(review.getSellerReply()).map(reply -> "Seller's reply: " + reply);
    }
}
