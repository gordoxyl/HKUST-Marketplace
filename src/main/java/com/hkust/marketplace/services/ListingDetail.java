package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;

import java.util.Objects;

/**
 * What the listing detail screen shows (UC8 step 7; Task 1.4), as it was when the buyer
 * opened the listing: the listing with all its fields and photos, whether it is available,
 * and its seller with the average rating and reviews.
 *
 * @param listing   the listing; its view count already includes this opening
 * @param available true if the listing accepts an offer, borrow request or claim; the action
 *                  button is disabled when it does not (UC8 Special Requirement 1)
 * @param seller    the listing's seller, with rating and reviews
 */
public record ListingDetail(Listing listing, boolean available, SellerProfile seller) {

    public ListingDetail {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(seller, "seller");
    }
}
