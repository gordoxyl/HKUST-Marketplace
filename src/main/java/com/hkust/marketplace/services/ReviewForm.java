package com.hkust.marketplace.services;

import java.util.Objects;

/**
 * What the review form shows (UC10 step 3), after the deal was found to be completed and not
 * reviewed yet.
 *
 * @param offerId      the deal being reviewed
 * @param listingTitle the title of the deal's listing
 * @param sellerName   the seller's full name, or the username if the account was deleted
 */
public record ReviewForm(String offerId, String listingTitle, String sellerName) {

    public ReviewForm {
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(listingTitle, "listingTitle");
        Objects.requireNonNull(sellerName, "sellerName");
    }
}
