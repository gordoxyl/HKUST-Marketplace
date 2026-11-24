package com.hkust.marketplace.services;

import com.hkust.marketplace.models.RatingSummary;

/**
 * One row of the Seller Ratings tab (Task 3.8, step 5): a seller with the rating data.
 * Hidden reviews are not counted in the average or in the total.
 *
 * @param rating         the number and average of the shown ratings, as one value
 *                       ({@code RatingSummary.NONE} if there are none)
 * @param completedDeals the seller's completed deals
 */
public record SellerRating(String username, String fullName, RatingSummary rating, int completedDeals) { }