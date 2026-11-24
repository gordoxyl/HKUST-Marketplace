package com.hkust.marketplace.services;

import java.time.LocalDateTime;

/**
 * The filter options of the admin's list of one seller's reviews (Task 3.8). A null field
 * means "no filter on this"; all chosen filters must match. The admin sees every review,
 * hidden ones included. Hiding is the only moderation action: there is no unhide.
 *
 * @param seller    part of the seller's username, ignoring case, or null for all sellers
 * @param rating    exact star rating (1-5), or null for all scores
 * @param fromDate  first review date, inclusive, or null
 * @param toDate    last review date, inclusive, or null
 */
public record ReviewFilter(String seller, Integer rating, LocalDateTime fromDate, LocalDateTime toDate) {

    /** No filter: every review. */
    public static ReviewFilter none() {
        return new ReviewFilter(null, null, null, null);
    }

    public ReviewFilter withSeller(String value) {
        return new ReviewFilter(value, rating, fromDate, toDate);
    }

    public ReviewFilter withRating(Integer value) {
        return new ReviewFilter(seller, value, fromDate, toDate);
    }

    public ReviewFilter withDateRange(LocalDateTime from, LocalDateTime to) {
        return new ReviewFilter(seller, rating, from, to);
    }
}