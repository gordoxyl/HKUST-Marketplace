package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.RatingSummary;

import java.util.List;
import java.util.Map;

/**
 * Task 2.8 View Seller Stats: the numbers and the chart data the seller's stats screen shows
 * (View Seller Stats steps 2-5). Built by {@link SellerStatsService}; this record only carries
 * them, so the screen has no rule or counting of its own.
 *
 * @param views             the views of all the seller's listings together
 * @param offersReceived    every price offer, borrow request and claim ever made on them
 * @param completedDeals    the deals completed among those offers (sold, given or returned)
 * @param rating            the seller's rating; hidden reviews are already left out (SR1)
 * @param perListing        one row per listing, for the bar chart
 * @param completedByType   completed deals per listing type, for the pie chart
 */
public record SellerStats(
        int views,
        int offersReceived,
        int completedDeals,
        RatingSummary rating,
        List<ListingStats> perListing,
        Map<ListingType, Integer> completedByType) {

    public SellerStats {
        perListing = List.copyOf(perListing);
        completedByType = Map.copyOf(completedByType);
    }

    /** How many listings the seller has posted, in any status. */
    public int listings() {
        return perListing.size();
    }

    /** One listing's bars: its views and how many offers it received. */
    public record ListingStats(String listingId, String title, int views, int offers) {
    }
}
