package com.hkust.marketplace.services;

import com.hkust.marketplace.models.RatingSummary;
import com.hkust.marketplace.models.Review;

import java.util.List;
import java.util.Objects;

/**
 * A seller as buyers see them (UC8 A3; Task 1.10: "display the seller's average rating and
 * reviews on their profile and on each of their listing detail pages"). Reviews the admin
 * hid are neither listed nor counted (UC8 Special Requirement 5).
 *
 * @param username      the seller's username
 * @param fullName      the seller's full name, or the username if the account was deleted
 * @param bio           the seller's bio, or null if there is none
 * @param rating        the count and the average of the shown ratings
 * @param reviews       the shown reviews, newest first, each with the seller's reply, if any
 */
public record SellerProfile(String username, String fullName, String bio, RatingSummary rating,
                            List<Review> reviews) {

    /** @throws IllegalArgumentException if the rating does not count exactly the reviews shown */
    public SellerProfile {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(rating, "rating");
        reviews = List.copyOf(reviews);
        if (rating.count() != reviews.size()) {
            throw new IllegalArgumentException("The rating must count exactly the reviews shown.");
        }
    }
}
