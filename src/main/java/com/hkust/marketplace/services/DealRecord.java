package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Offer;

import java.time.LocalDateTime;

/**
 * One row of the All Deals Record table (Task 3.5, step 3), already worked out by the service.
 *
 * @param startDate     a loan's agreed borrow start, or the date a sale or giveaway was accepted
 * @param completedDate the completion or return date; null while the deal is in progress
 */
public record DealRecord(Offer offer, Listing listing, String buyerUsername, String sellerUsername,
                         ListingType dealType, LocalDateTime startDate, LocalDateTime completedDate,
                         DealState state) { }