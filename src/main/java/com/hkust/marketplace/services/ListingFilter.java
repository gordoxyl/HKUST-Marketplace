package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;

import java.time.LocalDate;

/**
 * The filter options of the admin's listing list (Manage Marketplace Listings, step 2).
 * A null field (or false for reportedOnly) means "no filter on this". "Reported" is not a
 * listing status, so it is its own flag; with a status set, both must match.
 *
 * @param status       one listing status, or null for all
 * @param reportedOnly true to show only listings that have at least one report
 * @param category     one category, or null for all
 * @param type         one listing type, or null for all
 * @param seller       text matched against the seller's username or full name, ignoring case; null or blank for all
 * @param fromDate     first posted day, inclusive, or null
 * @param toDate       last posted day, inclusive, or null
 */
public record ListingFilter(ListingStatus status, boolean reportedOnly, Category category,
                            ListingType type, String seller, LocalDate fromDate, LocalDate toDate) {

    /** No filter: every listing. */
    public static ListingFilter none() {
        return new ListingFilter(null, false, null, null, null, null, null);
    }

    public ListingFilter withStatus(ListingStatus value) {
        return new ListingFilter(value, reportedOnly, category, type, seller, fromDate, toDate);
    }

    public ListingFilter withReportedOnly(boolean value) {
        return new ListingFilter(status, value, category, type, seller, fromDate, toDate);
    }

    public ListingFilter withCategory(Category value) {
        return new ListingFilter(status, reportedOnly, value, type, seller, fromDate, toDate);
    }

    public ListingFilter withType(ListingType value) {
        return new ListingFilter(status, reportedOnly, category, value, seller, fromDate, toDate);
    }

    public ListingFilter withSeller(String value) {
        return new ListingFilter(status, reportedOnly, category, type, value, fromDate, toDate);
    }

    public ListingFilter withDateRange(LocalDate from, LocalDate to) {
        return new ListingFilter(status, reportedOnly, category, type, seller, from, to);
    }
}