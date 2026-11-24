package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;

/**
 * Which listings a buyer can open: the detail screen of UC8 Browse Marketplace (steps 5-7),
 * which is also the precondition of UC9 Report Listing. One rule for both use cases, moved here
 * from {@link BrowseService} when Report Listing became its second user (refactoring on the
 * second use, Lecture 10).
 */
final class ListingAccess {

    /** UC8's message for a listing the buyer can no longer open; UC9 uses the same one. */
    static final String NOT_AVAILABLE = "This listing is no longer on the marketplace.";

    private ListingAccess() {
        // Utility class
    }

    /**
     * True if the buyer can open the listing's detail screen: it is live, available or not, or
     * the buyer has a deal on it, whatever its status, so it can be reopened from My Deals.
     * Call it inside one of the store's reads or transactions, so the answer stays true for
     * what follows.
     */
    static boolean canOpen(DataStore store, Buyer buyer, Listing listing) {
        return store.isLive(listing) || store.getOffersForListing(listing.getId()).stream()
                .anyMatch(offer -> offer.getBuyerUsername().equalsIgnoreCase(buyer.getUsername()));
    }
}
