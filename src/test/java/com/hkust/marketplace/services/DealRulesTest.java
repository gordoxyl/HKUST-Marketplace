package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests of the shared deal lifecycle with the real DataStore (bottom-up; the
 * TestClock is the only stub). Most are thread tests of a use case's basic flow, including
 * flows that cross roles: a buyer's offer, the seller's acceptance and the buyer's completion.
 */
class DealRulesTest {

    private TestClock clock;
    private DataStore store;
    private DealRules rules;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        rules = new DealRules(store);
        Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        Fixtures.buyer(store, "bo");
    }

    private List<Notification> board(String username) {
        return store.getNotificationsFor(username);
    }

    private BorrowRequest acceptedBorrow(Duration period) {
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        BorrowRequest request = (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), "bea", clock.now(), clock.now().plus(period)));
        rules.accept(request);
        return request;
    }

    // ---------- Accept (Manage Offers & Requests 3.3; UC7 Special Requirement 5) ----------

    @Test
    void acceptPriceOffer_reservesListing_declinesOtherOpenOffers_andNotifiesEveryone() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        PriceOffer bea = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        PriceOffer ben = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "ben", 90));
        PriceOffer bo = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "bo", 70));
        bo.counter(95);
        clock.advance(Duration.ofHours(1));

        rules.accept(bea);

        assertEquals(OfferStatus.ACCEPTED, bea.getStatus());
        assertEquals(clock.now(), bea.getAcceptedDate());
        assertEquals(ListingStatus.RESERVED, book.getStatus());
        assertEquals(OfferStatus.DECLINED, ben.getStatus());
        assertEquals(OfferStatus.DECLINED, bo.getStatus()); // countered offers are declined too
        assertTrue(board("bea").get(0).getContent().contains("accepted your price offer"));
        assertTrue(board("ben").get(0).getContent().contains("declined because the seller accepted another offer"));
        assertEquals(NotificationCategory.OFFER_UPDATE, board("bo").get(0).getCategory());
    }

    @Test
    void acceptClaim_worksAlthoughThePendingClaimMakesTheGiveawayUnavailable() {
        // Regression guard: a giveaway with a pending claim is "unavailable" to other buyers,
        // but the seller must still be able to accept that claim
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        Offer claim = store.addOffer(new Claim(lamp.getId(), "bea"));
        assertFalse(store.isAvailable(lamp));

        rules.accept(claim);

        assertEquals(ListingStatus.RESERVED, lamp.getStatus());
    }

    @Test
    void acceptBorrowRequest_lendsTheListing() {
        BorrowRequest request = acceptedBorrow(Duration.ofDays(2));
        assertEquals(ListingStatus.LENT, store.getListingOf(request).getStatus());
    }

    @Test
    void accept_refusedWhenTheListingIsNoLongerLive() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer first = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        rules.accept(first);
        Offer late = store.addOffer(new PriceOffer(book.getId(), "ben", 90));

        ServiceException e = assertThrows(ServiceException.class, () -> rules.accept(late));
        assertEquals("This listing is no longer available.", e.getMessage());
        assertEquals(OfferStatus.PENDING, late.getStatus()); // nothing changed
    }

    @Test
    void accept_refusedForACounteredOffer() {
        // Assumption 3 (confirmed): only a pending offer can be accepted
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        PriceOffer offer = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        offer.counter(90);

        assertThrows(ServiceException.class, () -> rules.accept(offer));
        assertEquals(ListingStatus.LIVE, book.getStatus());
    }

    // ---------- Complete (UC7 steps 6-8) ----------

    @Test
    void completePurchase_sellsTheListingAndNotifiesTheSeller() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        rules.accept(offer);
        clock.advance(Duration.ofDays(1));

        rules.complete(offer);

        assertEquals(clock.now(), offer.getCompletedDate());
        assertEquals(ListingStatus.SOLD, book.getStatus());
        Notification toSeller = board("sam").get(0);
        assertEquals(NotificationCategory.DEAL_COMPLETED, toSeller.getCategory());
        assertTrue(toSeller.getContent().contains("marked the deal for 'Book' complete"));
    }

    @Test
    void completeClaim_givesTheListing() {
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        Offer claim = store.addOffer(new Claim(lamp.getId(), "bea"));
        rules.accept(claim);

        rules.complete(claim);

        assertEquals(ListingStatus.GIVEN, lamp.getStatus());
    }

    @Test
    void complete_refusedForBorrowRequestsAndUnacceptedOrCompletedDeals() {
        BorrowRequest request = acceptedBorrow(Duration.ofDays(2));
        assertThrows(IllegalArgumentException.class, () -> rules.complete(request));

        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer pending = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        assertThrows(ServiceException.class, () -> rules.complete(pending));

        rules.accept(pending);
        rules.complete(pending);
        assertThrows(ServiceException.class, () -> rules.complete(pending)); // only once
    }

    @Test
    void completeAfterAdminRemovedTheReservedListing_dealCompletes_listingStaysRemoved() {
        // Assumption 1 (to confirm with the client)
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        rules.accept(offer);
        book.remove();

        rules.complete(offer);

        assertTrue(offer.isCompleted());
        assertEquals(ListingStatus.REMOVED, book.getStatus());
    }

    // ---------- Return (UC7 A2) and auto-return (UC12 step 3) ----------

    @Test
    void selfReturn_makesTheListingLiveAgain_andNotifiesTheSeller() {
        BorrowRequest request = acceptedBorrow(Duration.ofDays(2));
        clock.advance(Duration.ofDays(1));

        rules.returnItem(request, false);

        assertEquals(clock.now(), request.getCompletedDate());
        assertFalse(request.isAutoReturned());
        assertEquals(ListingStatus.LIVE, store.getListingOf(request).getStatus());
        assertTrue(board("sam").get(0).getContent().contains("returned 'Calculator'"));
    }

    @Test
    void autoReturn_completesOnTheDueDate_andNotifiesBuyerAndSeller() {
        BorrowRequest request = acceptedBorrow(Duration.ofSeconds(5)); // the minimum borrow time
        LocalDateTime due = request.getDueDate();
        clock.advance(Duration.ofSeconds(6));

        rules.returnItem(request, true);

        assertEquals(due, request.getCompletedDate());
        assertTrue(request.isAutoReturned());
        assertEquals(ListingStatus.LIVE, store.getListingOf(request).getStatus());
        assertTrue(board("bea").get(0).getContent().contains("returned automatically"));
        assertTrue(board("sam").get(0).getContent().contains("returned automatically"));
    }

    @Test
    void returnAfterAdminRemovedTheLentListing_listingStaysRemoved() {
        // UC12 A1
        BorrowRequest request = acceptedBorrow(Duration.ofDays(2));
        store.getListingOf(request).remove();

        rules.returnItem(request, true);

        assertTrue(request.isCompleted());
        assertEquals(ListingStatus.REMOVED, store.getListingOf(request).getStatus());
    }

    @Test
    void return_countsOnlyOnce() {
        BorrowRequest request = acceptedBorrow(Duration.ofDays(2));
        rules.returnItem(request, false);

        ServiceException e = assertThrows(ServiceException.class, () -> rules.returnItem(request, true));
        assertEquals("This item has already been returned.", e.getMessage());
        assertFalse(request.isAutoReturned());
    }

    @Test
    void selfReturnAndAutoReturnAtTheSameMoment_onlyTheFirstCounts() throws Exception {
        // UC12 Special Requirement 5, a critical subsystem: run both returns at once, many times
        for (int round = 0; round < 50; round++) {
            BorrowRequest request = acceptedBorrow(Duration.ofSeconds(5));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Boolean>> results = new ArrayList<>();
            for (boolean automatic : new boolean[]{false, true}) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        rules.returnItem(request, automatic);
                        return true;
                    } catch (ServiceException alreadyReturned) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            pool.shutdown();
            assertEquals(1, successes, "round " + round);
            assertEquals(ListingStatus.LIVE, store.getListingOf(request).getStatus());
        }
    }

    // ---------- Decline open offers (Manage Listings A3, admin removal, deactivation) ----------

    @Test
    void declineOpenOffers_declinesOnlyOpenOnes_andUsesTheGivenCategory() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer bea = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        PriceOffer ben = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "ben", 90));
        ben.counter(95);

        List<Offer> declined = rules.declineOpenOffers(book, NotificationCategory.LISTING_REMOVED,
                "the admin removed the listing");

        assertEquals(List.of(bea, ben), declined);
        assertEquals(OfferStatus.DECLINED, bea.getStatus());
        Notification toBen = board("ben").get(0);
        assertEquals(NotificationCategory.LISTING_REMOVED, toBen.getCategory());
        assertEquals("Your price offer for 'Book' was declined because the admin removed the listing.", toBen.getContent());
        assertTrue(rules.declineOpenOffers(book, NotificationCategory.OFFER_UPDATE, "again").isEmpty());
    }

    // ---------- A whole deal across roles (thread test) ----------

    @Test
    void wholeDeal_offerAcceptCompleteReview_countsInTheSellersRating() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100)); // seller posts, admin approves
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 90));  // buyer offers (UC5)
        clock.advance(Duration.ofHours(2));
        rules.accept(offer);                                                    // seller accepts
        clock.advance(Duration.ofDays(1));
        rules.complete(offer);                                                  // buyer completes (UC7)
        clock.advance(Duration.ofHours(1));
        Review review = store.addReview(new Review(offer.getId(), 4, "Good"));  // buyer reviews (UC10)

        assertTrue(offer.getOfferDate().isBefore(offer.getAcceptedDate()));
        assertTrue(offer.getAcceptedDate().isBefore(offer.getCompletedDate()));
        assertTrue(offer.getCompletedDate().isBefore(review.getReviewDate()));
        assertEquals(ListingStatus.SOLD, book.getStatus());
        assertEquals(4.0, store.getAverageRating("sam").getAsDouble());
    }
}
