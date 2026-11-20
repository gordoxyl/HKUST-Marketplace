package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.User;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Everything the admin sees for VIEW USER (Manage All User Accounts, Task 3.3 step 6.1):
 * personal info, active listings, completed deals, pending offers and the seller's rating.
 * It never holds the password hash. Admin-only.
 *
 * @param user                the account (the screen must not show its password hash)
 * @param residenceHall       a buyer's residence hall, or null
 * @param bio                 a seller's bio, or null
 * @param preferredLocations  a seller's preferred meet-up locations, empty for a buyer
 * @param activeListings      a seller's Live, Reserved or Lent listings, empty for a buyer
 * @param completedDeals      completed deals of the user, as buyer or as seller
 * @param pendingOffers       open (Pending or Countered) offers of the user, as buyer or as seller
 * @param averageRating       a seller's average rating (hidden reviews not counted), empty if none
 * @param ratingCount         a seller's number of shown ratings, 0 for a buyer
 */
public record UserDetails(User user, String residenceHall, String bio,
                          List<MeetupLocation> preferredLocations,
                          List<Listing> activeListings,
                          List<Offer> completedDeals,
                          List<Offer> pendingOffers,
                          OptionalDouble averageRating, int ratingCount) { }