package com.hkust.marketplace.services;

import java.util.OptionalDouble;

/**
 * One row of the Seller Ratings tab (Task 3.8, step 5): a seller with the rating data.
 * Hidden reviews are not counted in the average or in the total.
 *
 * @param averageRating  the average of the shown ratings, empty if there are none
 * @param totalReviews   the number of shown reviews
 * @param completedDeals the seller's completed deals
 */
public record SellerRating(String username, String fullName, OptionalDouble averageRating,
                           int totalReviews, int completedDeals) { }