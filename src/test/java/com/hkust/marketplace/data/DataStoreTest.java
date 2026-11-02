package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the shared rules and thread safety of DataStore. */
class DataStoreTest {

    private DataStore store;
    private Seller sam;

    @BeforeEach
    void setUp() {
        store = new DataStore();
        sam = new Seller("sam", "Sam Seller", "hash");
        store.addUser(sam);
        store.addUser(new Buyer("bea", "Bea Buyer", "hash", null));
        store.addUser(new Buyer("ben", "Ben Buyer", "hash", null));
    }

    private Listing live(Listing listing) {
        listing.setStatus(ListingStatus.LIVE);
        return store.addListing(listing);
    }

    private Listing sale() {
        return live(new SaleListing("sam", "Book", Category.TEXTBOOKS, ItemCondition.USED, "desc", MeetupLocation.LIBRARY, 100));
    }

    private Listing giveaway() {
        return live(new GiveawayListing("sam", "Lamp", Category.FURNITURE, ItemCondition.USED, "desc", MeetupLocation.LIBRARY));
    }

    // ---------- Users ----------

    @Test
    void addUser_refusesATakenUsernameIgnoringCase() {
        assertFalse(store.addUser(new Seller("SAM", "Other Sam", "hash")));
        assertEquals("Sam Seller", store.findUser(" Sam ").getFullName());
        assertEquals(1, store.getUsersByRole(UserRole.SELLER).size());
    }

    // ---------- Listings ----------

    @Test
    void addListing_assignsUniqueIds() {
        Listing a = sale();
        Listing b = sale();
        assertNotNull(a.getId());
        assertNotEquals(a.getId(), b.getId());
        assertSame(a, store.findListing(a.getId()));
    }

    @Test
    void liveListings_includeReservedAndLentButNotOtherStatuses() {
        Listing live = sale();
        Listing reserved = sale();
        reserved.setStatus(ListingStatus.RESERVED);
        Listing lent = sale();
        lent.setStatus(ListingStatus.LENT);
        for (ListingStatus hidden : List.of(ListingStatus.PENDING_REVIEW, ListingStatus.REJECTED,
                ListingStatus.SOLD, ListingStatus.GIVEN, ListingStatus.REMOVED)) {
            sale().setStatus(hidden);
        }

        assertEquals(List.of(live, reserved, lent), store.getLiveListings());
    }

    @Test
    void deactivatedSeller_listingsAreNotLiveOrAvailable() {
        Listing listing = sale();
        sam.setActive(false);

        assertTrue(store.getLiveListings().isEmpty());
        assertFalse(store.isAvailable(listing));
    }

    @Test
    void availability_followsStatusAndPendingClaims() {
        Listing book = sale();
        assertTrue(store.isAvailable(book));
        book.setStatus(ListingStatus.RESERVED);
        assertFalse(store.isAvailable(book));

        Listing lamp = giveaway();
        store.addOffer(new Claim(lamp.getId(), "bea"));
        assertFalse(store.isAvailable(lamp)); // a giveaway with a pending claim is unavailable
    }

    @Test
    void incrementViewCount_addsOneView() {
        Listing listing = sale();
        store.incrementViewCount(listing.getId());
        store.incrementViewCount(listing.getId());
        assertEquals(2, listing.getViewCount());
    }

    // ---------- Offers and chat ----------

    @Test
    void addOffer_createsTheChatThreadOnce() {
        Listing listing = sale();

        Offer first = store.addOffer(new PriceOffer(listing.getId(), "bea", 80));
        Offer second = store.addOffer(new PriceOffer(listing.getId(), "bea", 90));

        ChatThread thread = store.findThread("bea", listing.getId());
        assertNotNull(thread);
        assertEquals("sam", thread.getSellerUsername());
        assertEquals(List.of(first, second), store.getOffersInThread(thread));
        assertEquals(1, store.getThreadsForSeller("sam").size());
    }

    @Test
    void addOffer_refusesTheWrongOfferType() {
        Listing listing = sale();
        assertThrows(IllegalArgumentException.class, () -> store.addOffer(new Claim(listing.getId(), "bea")));
        assertTrue(store.getAllOffers().isEmpty());
    }

    @Test
    void chatThread_canOnlyBeStartedByABuyer() {
        Listing listing = sale();
        assertThrows(IllegalArgumentException.class, () -> store.getOrCreateThread("sam", listing.getId()));
    }

    @Test
    void offerQueries_findOffersByBuyerAndSeller() {
        Listing listing = sale();
        Offer offer = store.addOffer(new PriceOffer(listing.getId(), "bea", 80));

        assertEquals(List.of(offer), store.getOffersByBuyer("bea"));
        assertEquals(List.of(offer), store.getOffersForSeller("sam"));
        assertTrue(store.getOffersByBuyer("ben").isEmpty());
        assertSame(listing, store.getListingOf(offer));
    }

    // ---------- Reviews ----------

    @Test
    void addReview_onlyForACompletedDealAndOnlyOnce() {
        Listing listing = sale();
        Offer offer = store.addOffer(new PriceOffer(listing.getId(), "bea", 80));
        assertThrows(IllegalStateException.class, () -> store.addReview(new Review(offer.getId(), 5, null)));

        offer.accept(LocalDateTime.now());
        offer.complete(LocalDateTime.now());
        store.addReview(new Review(offer.getId(), 5, null));

        assertThrows(IllegalStateException.class, () -> store.addReview(new Review(offer.getId(), 4, null)));
        assertEquals("sam", store.getSellerUsername(store.findReviewForOffer(offer.getId())));
        assertEquals("bea", store.getBuyerUsername(store.findReviewForOffer(offer.getId())));
    }

    @Test
    void averageRating_leavesOutHiddenReviews() {
        assertTrue(store.getAverageRating("sam").isEmpty());

        Review five = completedDealReview("bea", 5);
        completedDealReview("ben", 2);
        assertEquals(3.5, store.getAverageRating("sam").getAsDouble());

        five.setHidden(true);
        assertEquals(2.0, store.getAverageRating("sam").getAsDouble());
        assertEquals(1, store.getRatingCount("sam"));
        assertEquals(2, store.getReviewsForSeller("sam").size()); // still stored
    }

    private Review completedDealReview(String buyer, int rating) {
        Offer offer = store.addOffer(new PriceOffer(sale().getId(), buyer, 50));
        offer.accept(LocalDateTime.now());
        offer.complete(LocalDateTime.now());
        return store.addReview(new Review(offer.getId(), rating, null));
    }

    // ---------- Reports and notifications ----------

    @Test
    void addReport_onlyOncePerBuyerAndListing() {
        Listing listing = sale();

        assertTrue(store.addReport(new Report(listing.getId(), "bea", ReportReason.SCAM_OR_FRAUD, "Fake")));
        assertFalse(store.addReport(new Report(listing.getId(), "bea", ReportReason.OTHER, "Again")));
        assertTrue(store.addReport(new Report(listing.getId(), "ben", ReportReason.OTHER, "Me too")));
        assertEquals(2, store.getReportsForListing(listing.getId()).size());
    }

    @Test
    void notifications_areListedNewestFirstForTheirRecipient() throws InterruptedException {
        Notification older = store.addNotification(new Notification("bea", NotificationCategory.ANNOUNCEMENT, "first"));
        Thread.sleep(5);
        Notification newer = store.addNotification(new Notification("bea", NotificationCategory.ANNOUNCEMENT, "second"));
        store.addNotification(new Notification("ben", NotificationCategory.ANNOUNCEMENT, "other"));

        assertEquals(List.of(newer, older), store.getNotificationsFor("bea"));
    }

    // ---------- Thread safety ----------

    @Test
    void concurrentClaims_onlyTheFirstSucceeds() throws Exception {
        Listing lamp = giveaway();
        int buyers = 20;
        for (int i = 0; i < buyers; i++) {
            store.addUser(new Buyer("racer" + i, "Racer " + i, "hash", null));
        }

        // The UC5 pattern: check availability and record the claim as one atomic step
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            String buyer = "racer" + i;
            results.add(pool.submit(() -> {
                start.await();
                return store.transaction(() -> {
                    if (!store.isAvailable(lamp)) {
                        return false;
                    }
                    store.addOffer(new Claim(lamp.getId(), buyer));
                    return true;
                });
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

        assertEquals(1, successes);
        assertEquals(1, store.getOffersForListing(lamp.getId()).size());
    }

    @Test
    void lendListing_keepsItsDuration() {
        LendListing lend = (LendListing) live(new LendListing("sam", "Calc", Category.ELECTRONICS,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY, Duration.ofSeconds(5)));
        assertEquals(Duration.ofSeconds(5), lend.getLendingDuration());
    }
}
