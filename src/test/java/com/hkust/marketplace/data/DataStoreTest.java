package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
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

    private TestClock clock;
    private DataStore store;
    private Seller sam;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        sam = new Seller("sam", "Sam Seller", "hash");
        store.addUser(sam);
        store.addUser(new Buyer("bea", "Bea Buyer", "hash", null));
        store.addUser(new Buyer("ben", "Ben Buyer", "hash", null));
    }

    private Listing live(Listing listing) {
        store.addListing(listing);
        listing.approve();
        return listing;
    }

    /** A sale listing added to the store and moved to the status (not live). */
    private Listing saleIn(ListingStatus status) {
        Listing listing = new SaleListing("sam", "Book", Category.TEXTBOOKS, ItemCondition.USED, "desc", MeetupLocation.LIBRARY, 100);
        store.addListing(listing);
        return Fixtures.moveTo(listing, status);
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
        Listing reserved = saleIn(ListingStatus.RESERVED);
        Listing lent = new LendListing("sam", "Calc", Category.ELECTRONICS, ItemCondition.USED, "desc",
                MeetupLocation.LIBRARY, Duration.ofDays(7));
        store.addListing(lent);
        Fixtures.moveTo(lent, ListingStatus.LENT);
        for (ListingStatus hidden : List.of(ListingStatus.PENDING_REVIEW, ListingStatus.REJECTED,
                ListingStatus.SOLD, ListingStatus.REMOVED)) {
            saleIn(hidden);
        }
        Listing given = new GiveawayListing("sam", "Lamp", Category.FURNITURE, ItemCondition.USED, "desc", MeetupLocation.LIBRARY);
        store.addListing(given);
        Fixtures.moveTo(given, ListingStatus.GIVEN);

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
        book.acceptDeal(); // Reserved
        assertFalse(store.isAvailable(book));

        Listing lamp = giveaway();
        assertFalse(store.hasPendingClaim(lamp.getId()));
        store.addOffer(new Claim(lamp.getId(), "bea"));
        assertTrue(store.hasPendingClaim(lamp.getId()));
        assertFalse(store.isAvailable(lamp)); // a giveaway with a pending claim is unavailable
    }

    @Test
    void isLive_isWiderThanAvailable() {
        Listing reserved = saleIn(ListingStatus.RESERVED);
        assertTrue(store.isLive(reserved));       // shown on Browse (UC8 Special Requirement 1)
        assertFalse(store.isAvailable(reserved)); // but accepts no offers
        assertFalse(store.isLive(saleIn(ListingStatus.PENDING_REVIEW)));
        assertFalse(store.isLive(saleIn(ListingStatus.SOLD)));

        Listing book = sale();
        assertTrue(store.isLive(book));
        sam.setActive(false);
        assertFalse(store.isLive(book)); // a deactivated seller's listings are hidden
    }

    @Test
    void read_returnsItsResult_andSeesTheCurrentData() {
        Listing book = sale();
        store.addOffer(new PriceOffer(book.getId(), "bea", 80));

        String seen = store.read(() -> store.findListing(book.getId()).getTitle()
                + " has " + store.getOffersForListing(book.getId()).size() + " offer");

        assertEquals("Book has 1 offer", seen);
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
        assertEquals(1, store.getReviewsForSeller("sam").size());
        assertEquals(2, store.getReviewsForSellerIncludingHidden("sam").size()); // still stored
    }

    @Test
    void hiddenReview_isNotListedForTheSeller() {
        // Regression test for R5 (docs/regression-log.md): the seller's Reviews screen lists
        // getReviewsForSeller(...); a review the admin hid must not appear there (UC10 SR4)
        Review hidden = completedDealReview("bea", 1);
        Review shown = completedDealReview("ben", 4);

        hidden.setHidden(true);

        assertEquals(List.of(shown), store.getReviewsForSeller("sam"));
        assertEquals(List.of(hidden, shown), store.getReviewsForSellerIncludingHidden("sam")); // admin can still unhide it
    }

    private Review completedDealReview(String buyer, int rating) {
        Offer offer = store.addOffer(new PriceOffer(sale().getId(), buyer, 50));
        offer.accept(store.now());
        offer.complete(store.now());
        return store.addReview(new Review(offer.getId(), rating, null));
    }

    // ---------- Seller statistics ----------

    @Test
    void sellerStatistics_areZeroForASellerWithNoListings() {
        // View Seller Stats A1: all statistics are shown as zero
        assertEquals(0, store.getTotalViews("sam"));
        assertEquals(0, store.getOffersReceivedCount("sam"));
        assertEquals(0, store.getCompletedDealCount("sam"));
        assertTrue(store.getAverageRating("sam").isEmpty());
        assertEquals(0, store.getRatingCount("sam"));
    }

    @Test
    void totalViews_addsUpTheSellersListings_whateverTheirStatus() {
        store.addUser(new Seller("sue", "Sue Seller", "hash"));
        Listing book = sale();
        Listing lamp = giveaway();
        Listing other = live(new SaleListing("sue", "Desk", Category.FURNITURE, ItemCondition.USED, "desc", MeetupLocation.LIBRARY, 50));
        store.incrementViewCount(book.getId());
        store.incrementViewCount(book.getId());
        store.incrementViewCount(lamp.getId());
        store.incrementViewCount(other.getId());
        store.transaction(lamp::withdraw); // a removed listing keeps its views

        assertEquals(3, store.getTotalViews("sam"));
        assertEquals(3, store.getTotalViews("SAM")); // usernames ignore case
        assertEquals(1, store.getTotalViews("sue"));
    }

    @Test
    void offersReceived_countsEveryOffer_andCompletedDeals_onlyTheCompletedOnes() {
        Listing book = sale();
        Listing lamp = giveaway();
        Offer declined = store.addOffer(new PriceOffer(book.getId(), "ben", 60));
        declined.decline();
        Offer bought = store.addOffer(new PriceOffer(book.getId(), "bea", 90));
        Offer claimed = store.addOffer(new Claim(lamp.getId(), "bea"));
        assertEquals(3, store.getOffersReceivedCount("sam"));
        assertEquals(0, store.getCompletedDealCount("sam"));

        bought.accept(store.now());
        claimed.accept(store.now());
        assertEquals(0, store.getCompletedDealCount("sam")); // accepted, but not completed yet

        bought.complete(store.now());
        assertEquals(1, store.getCompletedDealCount("sam"));
        assertEquals(3, store.getOffersReceivedCount("sam")); // whatever happened to them afterwards
    }

    @Test
    void returnedBorrowedItem_countsAsACompletedDeal() {
        Listing calculator = live(new LendListing("sam", "Calculator", Category.ELECTRONICS, ItemCondition.USED,
                "desc", MeetupLocation.LIBRARY, Duration.ofDays(7)));
        BorrowRequest request = (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), "bea", store.now(), store.now().plusDays(7)));
        request.accept(store.now());

        request.autoReturn(); // UC12: returned when the period ended

        assertEquals(1, store.getCompletedDealCount("sam"));
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
    void notifications_areListedNewestFirstForTheirRecipient() {
        Notification older = store.addNotification(new Notification("bea", NotificationCategory.ANNOUNCEMENT, "first"));
        clock.advance(Duration.ofSeconds(1));
        Notification newer = store.addNotification(new Notification("bea", NotificationCategory.ANNOUNCEMENT, "second"));
        store.addNotification(new Notification("ben", NotificationCategory.ANNOUNCEMENT, "other"));

        assertEquals(List.of(newer, older), store.getNotificationsFor("bea"));
    }

    // ---------- Time ----------

    @Test
    void records_areDatedByTheStoreClockWhenAdded() {
        LocalDateTime start = clock.now();
        assertEquals(start, store.findUser("bea").getCreatedAt());

        clock.advance(Duration.ofMinutes(5));
        Listing listing = sale();
        Offer offer = store.addOffer(new PriceOffer(listing.getId(), "bea", 80));
        clock.advance(Duration.ofMinutes(5));
        Notification notification = store.addNotification(new Notification("bea", NotificationCategory.ANNOUNCEMENT, "hi"));

        assertEquals(start.plusMinutes(5), listing.getPostedDate());
        assertEquals(start.plusMinutes(5), offer.getOfferDate());
        assertEquals(start.plusMinutes(5), store.findThread("bea", listing.getId()).getCreatedAt());
        assertEquals(start.plusMinutes(10), notification.getTimestamp());
        assertEquals(start.plusMinutes(10), store.now());
    }

    @Test
    void addingARecordTwice_isRefused() {
        Listing listing = sale();
        assertThrows(IllegalStateException.class, () -> store.addListing(listing));
        assertEquals(1, store.getAllListings().size());
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
