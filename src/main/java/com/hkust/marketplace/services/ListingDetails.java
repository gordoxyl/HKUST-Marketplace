package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Report;

import java.util.List;

/**
 * Everything the admin sees when viewing one listing (Manage Marketplace Listings 3.7): the
 * listing, its seller's full name, and the reports filed on it. Reports show who reported
 * (UC9 Special Requirement 4: only the admin sees this), so never use this outside admin code.
 *
 * @param listing        the listing
 * @param sellerFullName the seller's full name, or the username if the account was deleted
 * @param reports        the reports on the listing, possibly empty
 */
public record ListingDetails(Listing listing, String sellerFullName, List<Report> reports) { }