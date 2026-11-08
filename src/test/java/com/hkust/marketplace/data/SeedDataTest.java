package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.AccountValidator;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that the demo data follows the project's own rules. */
class SeedDataTest {

    private final TestClock clock = Fixtures.clock();
    private final DataStore store = seeded(clock);

    private static DataStore seeded(TestClock clock) {
        DataStore store = new DataStore(clock);
        SeedData.populate(store);
        return store;
    }

    private Listing listing(String title) {
        return store.getAllListings().stream().filter(l -> l.getTitle().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void seedDates_happenInOrder() {
        // Regression test: offers were once dated "now" although they were accepted days earlier
        LocalDateTime now = clock.now();
        for (Offer offer : store.getAllOffers()) {
            Listing listing = store.getListingOf(offer);
            assertFalse(offer.getOfferDate().isBefore(listing.getPostedDate()), listing.getTitle());
            if (offer.getAcceptedDate() != null) {
                assertTrue(offer.getOfferDate().isBefore(offer.getAcceptedDate()), listing.getTitle());
            }
            if (offer.getCompletedDate() != null) {
                assertTrue(offer.getAcceptedDate().isBefore(offer.getCompletedDate()), listing.getTitle());
            }
            assertTrue(offer.getOfferDate().isBefore(now), listing.getTitle());
        }
        for (Review review : store.getAllReviews()) {
            assertTrue(store.findOffer(review.getOfferId()).getCompletedDate().isBefore(review.getReviewDate()));
        }
    }

    @Test
    void seeding_leavesTheStoreOnItsOwnClock() {
        assertSame(clock, store.getClock());
        assertEquals(Fixtures.START, store.now());
    }

    @Test
    void seededListings_haveTheDocumentedStatuses() {
        // The README and the assumptions register describe these states
        assertEquals(ListingStatus.RESERVED, listing("Calculus Textbook (Stewart)").getStatus());
        assertEquals(ListingStatus.SOLD, listing("Organic Chemistry Textbook").getStatus());
        assertEquals(ListingStatus.PENDING_REVIEW, listing("Mini Fridge").getStatus());
        assertEquals(ListingStatus.LIVE, listing("Graphing Calculator TI-84").getStatus());
        assertEquals(ListingStatus.LIVE, listing("IKEA Desk Lamp").getStatus());
        assertEquals(OfferStatus.PENDING,
                store.getOffersForListing(listing("Graphing Calculator TI-84").getId()).get(0).getStatus());
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
