package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ListingType;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Task 2.8 View Seller Stats: the numbers and the chart data the seller's stats screen shows
 * (View Seller Stats steps 3-5). Built by {@link SellerStatsService}; this record only carries
 * them, so the screen has no rule or counting of its own.
 *
 * @param listingCount      how many listings the seller has posted, in any status
 * @param totalViews        the views of all of them together
 * @param offersReceived    every price offer, borrow request and claim ever made on them
 * @param completedDeals    the deals completed among those offers (sold, given or returned)
 * @param averageRating     the average of the shown ratings; empty when there are none
 * @param ratingCount       how many shown ratings the average is over
 * @param listings          one row per listing, for the views chart
 * @param completedDealsByType completed deals per listing type, for the pie chart
 */
public record SellerStats(
        int listingCount,
        int totalViews,
        int offersReceived,
        int completedDeals,
        OptionalDouble averageRating,
        int ratingCount,
        List<ListingStat> listings,
        Map<ListingType, Integer> completedDealsByType) {

    public SellerStats {
        listings = List.copyOf(listings);
        completedDealsByType = Map.copyOf(completedDealsByType);
    }

    /** One listing's bar in the views chart: its views and how many offers it received. */
    public record ListingStat(String listingId, String title, int views, int offers) {
    }
}
