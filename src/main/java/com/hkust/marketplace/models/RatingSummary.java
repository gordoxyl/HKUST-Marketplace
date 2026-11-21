package com.hkust.marketplace.models;

import java.util.List;

/**
 * A seller's rating as every screen shows it: how many ratings count, and their average. These
 * are the class diagram's derived attributes /ratingCount and /averageRating of Seller, worked
 * out from the reviews that are shown (hidden ones do not count; UC10 Special Requirement 4).
 *
 * <p>The two always go together and must agree, so they are one value that cannot hold an
 * impossible pair: there is no average without ratings, and an average is from 1 to 5 stars.
 * A screen asks {@link #isEmpty()} before it shows the average ("No ratings yet").
 *
 * @param count   how many ratings count; 0 if there are none
 * @param average their average, from 1 to 5; 0 if there are none
 */
public record RatingSummary(int count, double average) {

    /** A seller with no ratings yet. */
    public static final RatingSummary NONE = new RatingSummary(0, 0);

    /**
     * @throws IllegalArgumentException for an impossible pair: a negative count, an average
     *         without ratings, or an average outside 1 to 5 stars
     */
    public RatingSummary {
        if (count < 0) {
            throw new IllegalArgumentException("The number of ratings cannot be negative.");
        }
        if (count == 0 && average != 0) {
            throw new IllegalArgumentException("There is no average without ratings.");
        }
        if (count > 0 && (average < Review.MIN_RATING || average > Review.MAX_RATING)) {
            throw new IllegalArgumentException("An average rating is from " + Review.MIN_RATING + " to "
                    + Review.MAX_RATING + " stars.");
        }
    }

    /** The count and the average of these reviews' ratings; {@link #NONE} for no reviews. */
    public static RatingSummary of(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return NONE;
        }
        return new RatingSummary(reviews.size(), reviews.stream().mapToInt(Review::getRating).average().orElseThrow());
    }

    /** True if there are no ratings yet. */
    public boolean isEmpty() {
        return count == 0;
    }
}
