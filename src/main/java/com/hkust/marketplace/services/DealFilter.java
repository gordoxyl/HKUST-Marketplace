package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ListingType;

import java.time.LocalDateTime;

/**
 * The filter options of the All Deals Record (Task 3.5, step 2). A null field means "no
 * filter on this"; all chosen filters must match. The date range applies to the Start Date
 * shown in the table, inclusive on both ends. Seller and buyer match part of the username,
 * ignoring case.
 */
public record DealFilter(DealState status, ListingType type, String seller, String buyer,
                         LocalDateTime fromDate, LocalDateTime toDate) {

    public static DealFilter none() {
        return new DealFilter(null, null, null, null, null, null);
    }

    public DealFilter withStatus(DealState value) {
        return new DealFilter(value, type, seller, buyer, fromDate, toDate);
    }

    public DealFilter withType(ListingType value) {
        return new DealFilter(status, value, seller, buyer, fromDate, toDate);
    }

    public DealFilter withSeller(String value) {
        return new DealFilter(status, type, value, buyer, fromDate, toDate);
    }

    public DealFilter withBuyer(String value) {
        return new DealFilter(status, type, seller, value, fromDate, toDate);
    }

    public DealFilter withDateRange(LocalDateTime from, LocalDateTime to) {
        return new DealFilter(status, type, seller, buyer, from, to);
    }
}