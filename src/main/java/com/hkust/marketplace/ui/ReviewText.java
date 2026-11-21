package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.RatingSummary;
import com.hkust.marketplace.services.ReviewForm;

import java.util.Optional;

/**
 * How a seller's rating and reviews read on every screen that shows them: the listing detail
 * screen and the seller's profile (UC8 step 7 and A3), and the seller's and admin's own review
 * screens; and the wording of the buyer's review form (UC10). Kept apart from the JavaFX code
 * so it can be unit-tested.
 */
public final class ReviewText {

    private ReviewText() {
        // Utility class
    }

    /** The heading above a seller's reviews: "Seller rating: 4.5 / 5 (2 ratings)" or "Seller rating: No ratings yet". */
    public static String ratingHeading(RatingSummary rating) {
        return "Seller rating: " + Formats.averageRating(rating);
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

    // ---------- Review Seller (UC10), the buyer's form ----------

    /** The review form's heading, with the seller and the listing (UC10 step 3). */
    public static String formHeader(ReviewForm form) {
        return "Rate and review " + form.sellerName() + "\nfor '" + form.listingTitle() + "'";
    }

    /** The rating chosen so far in the form: "★★★★☆ 4 of 5", or a reminder while none is chosen (step 4). */
    public static String chosenRating(Integer rating) {
        return rating == null ? "Choose 1 to 5 stars." : Formats.stars(rating) + " " + rating + " of " + Review.MAX_RATING;
    }

    /** What the buyer is told once the review is recorded (step 10). */
    public static String submitted(ReviewForm form) {
        return "Thank you. Your review of " + form.sellerName() + " was submitted. It now shows on the "
                + "seller's profile and on each of their listings.";
    }
}
