package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.OfferStatus;

import java.time.LocalDateTime;

/**
 * The filter options of the admin's All Deals Record view (Task 3.5). A null field means
 * "no filter on this"; all chosen filters must match. The date range applies to the deal's
 * offer date (the start date shown in the table), inclusive on both ends.
 *
 * @param status   one offer status, or null for all statuses
 * @param type     one listing type (SELL/LEND/GIVEAWAY), or null for all types
 * @param seller   text matched against the seller's username, case-insensitive; null or blank for all
 * @param buyer    text matched against the buyer's username, case-insensitive; null or blank for all
 * @param fromDate first offer date, inclusive, or null
 * @param toDate   last offer date, inclusive, or null
 */
public record DealFilter(OfferStatus status, ListingType type, String seller, String buyer,
                         LocalDateTime fromDate, LocalDateTime toDate) {

    /** No filter: every offer. */
    public static DealFilter none() {
        return new DealFilter(null, null, null, null, null, null);
    }

    public DealFilter withStatus(OfferStatus value) {
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