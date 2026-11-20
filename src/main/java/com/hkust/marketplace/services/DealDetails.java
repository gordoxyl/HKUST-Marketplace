package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Offer;

import java.time.LocalDateTime;

/**
 * Everything the admin sees for VIEW DETAILS on one deal (Task 3.5, step 4.1). It holds no
 * password hashes. Admin-only.
 *
 * @param residenceHall  the buyer's residence hall, or null if none or the account was deleted
 * @param sellerBio      the seller's bio, or null
 * @param amount         the offered price for a sale, otherwise null
 * @param borrowStart    the borrow period start for a loan, otherwise null
 * @param borrowEnd      the borrow period end for a loan, otherwise null
 * @param reviewed       true if the buyer reviewed this deal
 * @param reviewHidden   true if that review was hidden by the admin
 */
public record DealDetails(Offer offer, Listing listing,
                          String buyerUsername, String buyerFullName, String residenceHall,
                          String sellerUsername, String sellerFullName, String sellerBio,
                          ListingType dealType, Double amount,
                          LocalDateTime borrowStart, LocalDateTime borrowEnd,
                          LocalDateTime startDate, LocalDateTime completedDate, DealState state,
                          boolean reviewed, boolean reviewHidden) { }