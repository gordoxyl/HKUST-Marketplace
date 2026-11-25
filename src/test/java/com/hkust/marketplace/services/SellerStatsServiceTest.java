package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2.8 View Seller Stats: the numbers behind the stats screen (views, offers received,
 * completed deals, rating) and the two charts' data (views and offers per listing, completed
 * deals by type). Each test starts from an empty in-memory store.
 *
 * <p><b>Team decision (recorded here):</b> "offers received" counts every price offer, borrow
 * request and claim ever made on the seller's listings, whatever its status, declined ones
 * included. That is {@code store.getOffersReceivedCount}.
 */
class SellerStatsServiceTest {

    private TestClock clock;
    private DataStore store;
    private SellerStatsService service;
    private Seller sam;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        service = new SellerStatsService(store);
        sam = Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        SessionManager.login(sam);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    private int count(SellerStats stats, ListingType type) {
        return stats.completedByType().getOrDefault(type, -1);
    }

    @Test
    void aSellerWithoutListings_hasZeroesAndNoRating() {
        SellerStats stats = service.getMyStats();

        assertEquals(0, stats.listings());
        assertEquals(0, stats.views());
        assertEquals(0, stats.offersReceived());
        assertEquals(0, stats.completedDeals());
        assertTrue(stats.rating().isEmpty());
        assertTrue(stats.perListing().isEmpty());
        assertEquals(0, count(stats, ListingType.SELL));
        assertEquals(0, count(stats, ListingType.LEND));
        assertEquals(0, count(stats, ListingType.GIVEAWAY));
    }

    @Test
    void theNumbersAndTheChartData_comeFromTheSellersListingsAndDeals() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        store.incrementViewCount(book.getId());
        store.incrementViewCount(book.getId());
        store.incrementViewCount(book.getId());
        store.incrementViewCount(calculator.getId());

        // Two offers on the book: one is accepted and completed, the other is declined by that.
        PriceOffer winner = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        PriceOffer loser = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "ben", 90));
        BorrowRequest borrow = (BorrowRequest) store.addOffer(new BorrowRequest(calculator.getId(), "ben",
                clock.now().plusHours(1), clock.now().plusHours(2)));
        Claim claim = (Claim) store.addOffer(new Claim(lamp.getId(), "bea"));

        DealRules rules = new DealRules(store);
        rules.accept(winner);
        rules.complete(winner);
        rules.accept(borrow);
        rules.returnItem(borrow, false);
        rules.accept(claim);
        rules.complete(claim);
        store.addReview(new Review(winner.getId(), 4, "Good book"));
        store.addReview(new Review(borrow.getId(), 5, "Great calculator"));

        SellerStats stats = service.getMyStats();

        assertEquals(3, stats.listings());
        assertEquals(4, stats.views());
        assertEquals(4, stats.offersReceived()); // two price offers, one borrow request, one claim
        assertEquals(3, stats.completedDeals()); // sold, returned, given
        assertEquals(2, stats.rating().count());
        assertEquals(4.5, stats.rating().average(), 0.0001);
        assertEquals(1, count(stats, ListingType.SELL));
        assertEquals(1, count(stats, ListingType.LEND));
        assertEquals(1, count(stats, ListingType.GIVEAWAY));

        SellerStats.ListingStats bookStat = stat(stats, book);
        assertEquals(3, bookStat.views());
        assertEquals(2, bookStat.offers());
        assertEquals(1, stat(stats, calculator).views());
        assertEquals(1, stat(stats, calculator).offers());
        assertEquals(0, stat(stats, lamp).views());
        assertEquals(1, stat(stats, lamp).offers());
        assertEquals(OfferStatus.DECLINED, loser.getStatus()); // a declined offer still counts as received
    }

    private static SellerStats.ListingStats stat(SellerStats stats, Listing listing) {
        return stats.perListing().stream()
                .filter(row -> row.listingId().equals(listing.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aHiddenReview_isLeftOutOfTheRating() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        PriceOffer offer = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "bea", 90));
        DealRules rules = new DealRules(store);
        rules.accept(offer);
        rules.complete(offer);
        Review review = store.addReview(new Review(offer.getId(), 5, "Fine"));
        review.setHidden(true);

        SellerStats stats = service.getMyStats();

        assertTrue(stats.rating().isEmpty());
    }

    @Test
    void aCompletedSaleDeal_countsForTheSellType() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        PriceOffer offer = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "bea", 90));
        DealRules rules = new DealRules(store);
        rules.accept(offer);
        rules.complete(offer);

        SellerStats stats = service.getMyStats();

        assertEquals(1, count(stats, ListingType.SELL));
        assertEquals(1, stats.completedDeals());
        assertEquals(0, count(stats, ListingType.LEND));
        assertEquals(0, count(stats, ListingType.GIVEAWAY));
    }

    @Test
    void anotherSellersListingsAndOffers_areNotCounted() {
        Fixtures.seller(store, "sue");
        SaleListing hers = Fixtures.live(Fixtures.sale(store, "sue", 50));
        store.incrementViewCount(hers.getId());
        PriceOffer offer = (PriceOffer) store.addOffer(new PriceOffer(hers.getId(), "bea", 40));
        DealRules rules = new DealRules(store);
        rules.accept(offer);
        rules.complete(offer);
        store.addReview(new Review(offer.getId(), 5, "Fine"));

        SellerStats stats = service.getMyStats();

        assertEquals(0, stats.listings());
        assertEquals(0, stats.views());
        assertEquals(0, stats.offersReceived());
        assertEquals(0, stats.completedDeals());
        assertTrue(stats.rating().isEmpty());
    }

    @Test
    void onlyALoggedInSellerCanSeeTheStats() {
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> service.getMyStats());

        Buyer bea = (Buyer) store.findUser("bea");
        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class, () -> service.getMyStats());
    }

    @Test
    void theStatRowsFollowTheSellersListingsInTheStoreOrder() {
        SaleListing first = Fixtures.live(Fixtures.sale(store, "sam", 10));
        SaleListing second = Fixtures.live(Fixtures.sale(store, "sam", 20));

        SellerStats stats = service.getMyStats();

        assertEquals(List.of(first.getId(), second.getId()),
                stats.perListing().stream().map(SellerStats.ListingStats::listingId).toList());
        assertEquals("Book", stats.perListing().get(0).title());
    }
}
