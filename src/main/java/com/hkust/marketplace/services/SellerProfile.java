package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Review;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A seller as buyers see them (UC8 A3; Task 1.10: "display the seller's average rating and
 * reviews on their profile and on each of their listing detail pages"). Reviews the admin
 * hid are neither listed nor counted (UC8 Special Requirement 5).
 *
 * @param username      the seller's username
 * @param fullName      the seller's full name, or the username if the account was deleted
 * @param bio           the seller's bio, or null if there is none
 * @param averageRating the average of the shown ratings; empty if there are none
 * @param ratingCount   how many ratings the average is made of
 * @param reviews       the shown reviews, newest first, each with the seller's reply, if any
 */
public record SellerProfile(String username, String fullName, String bio, OptionalDouble averageRating,
                            int ratingCount, List<Review> reviews) {

    public SellerProfile {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(averageRating, "averageRating");
        reviews = List.copyOf(reviews);
    }
}
