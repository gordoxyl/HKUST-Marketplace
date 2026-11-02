package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.AccountValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that the demo data follows the project's own rules. */
class SeedDataTest {

    private final DataStore store = seeded();

    private static DataStore seeded() {
        DataStore store = new DataStore();
        SeedData.populate(store);
        return store;
    }

    @Test
    void seedPasswords_meetThePasswordRules() {
        // UC1 Special Requirement 2 applies to every account, including the demo ones
        assertNull(AccountValidator.validatePassword(SeedData.ADMIN_PASSWORD));
        assertNull(AccountValidator.validatePassword(SeedData.DEMO_PASSWORD));
    }

    @Test
    void seededAccounts_haveTheRightTypes() {
        assertInstanceOf(Admin.class, store.findUser("admin"));
        assertInstanceOf(Buyer.class, store.findUser("alice"));
        assertInstanceOf(Buyer.class, store.findUser("bob"));
        assertInstanceOf(Seller.class, store.findUser("charlie"));
        assertInstanceOf(Seller.class, store.findUser("diana"));
    }

    @Test
    void seededDeals_matchTheirListingStatuses() {
        for (Offer offer : store.getAllOffers()) {
            Listing listing = store.getListingOf(offer);
            assertTrue(offer.canBeMadeOn(listing));
            if (offer.getStatus() == OfferStatus.ACCEPTED && !offer.isCompleted()) {
                assertEquals(ListingStatus.RESERVED, listing.getStatus(), listing.getTitle());
            }
            if (offer.isCompleted() && offer instanceof PriceOffer) {
                assertEquals(ListingStatus.SOLD, listing.getStatus(), listing.getTitle());
            }
        }
    }

    @Test
    void seededReview_belongsToACompletedDealAndCountsInTheRating() {
        assertEquals(1, store.getRatingCount("charlie"));
        assertEquals(5.0, store.getAverageRating("charlie").getAsDouble());
        assertTrue(store.getAverageRating("diana").isEmpty());
    }
}
