package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DataStore#deleteAccount}, the data-store step behind the admin's Delete
 * User (Manage All User Accounts and Reputation 6.4.4; UC6 Special Requirement 2): what is
 * deleted, what is cancelled, what is kept as a record, and that the username is retired.
 */
class AccountDeletionTest {

    private TestClock clock;
    private DataStore store;
    private Seller sam;
    private Buyer bea;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        sam = Fixtures.seller(store, "sam");
        Fixtures.seller(store, "sue");
        bea = new Buyer("bea", "Bea Buyer", "secret-hash", "Hall 3");
        store.addUser(bea);
        Fixtures.buyer(store, "ben");
    }

    /** Bea buys Sam's book: an offer, two chat messages, a completed deal, a review and a notification. */
    private Offer beaBuysSamsBook() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer purchase = store.addOffer(new PriceOffer(book.getId(), "bea", 90));
        ChatThread thread = store.findThread("bea", book.getId());
        thread.addMessage(new Message("bea", "Is it still available?", store.now()));
        thread.addMessage(new Message("sam", "Yes.\nMeet at the Library?", store.now()));
        purchase.accept(store.now());
        book.acceptDeal();
        purchase.complete(store.now());
        book.completeDeal();
        store.addReview(new Review(purchase.getId(), 5, "Smooth deal"));
        store.addNotification(new Notification("bea", NotificationCategory.OFFER_UPDATE, "Sam accepted your price offer."));
        return purchase;
    }

    // ---------- What is deleted ----------

    @Test
    void deletingABuyer_deletesTheAccount_theirChats_theirReviews_andTheirBoard() {
        Offer purchase = beaBuysSamsBook();
        SaleListing pen = Fixtures.live(Fixtures.sale(store, "sam", 10));
        store.addOffer(new PriceOffer(pen.getId(), "ben", 8)); // Ben's chat with Sam is not Bea's

        DeletedAccount deleted = store.deleteAccount(" Bea ");

        assertNull(store.findUser("bea"));
        assertTrue(store.getThreadsForBuyer("bea").isEmpty());                 // UC6 Special Requirement 2
        assertEquals(1, store.getThreadsForSeller("sam").size());              // only Ben's is left
        assertTrue(store.getAllReviews().isEmpty());                           // 6.4.4: reviews written by the user
        assertEquals(0, store.getRatingCount("sam"));                          // so Sam's rating is recalculated
        assertTrue(store.getAverageRating("sam").isEmpty());
        assertTrue(store.getNotificationsFor("bea").isEmpty());

        assertSame(bea, deleted.user());
        assertEquals(1, deleted.chatThreads().size());
        assertEquals(2, deleted.chatThreads().get(0).getMessages().size());
        assertEquals(1, deleted.reviews().size());
        assertEquals(1, deleted.notifications().size());
        assertTrue(deleted.declinedOffers().isEmpty());
        assertTrue(deleted.removedListings().isEmpty());

        // Kept as the archived transaction history
        assertEquals(List.of(purchase), store.getOffersByBuyer("bea"));
        assertTrue(purchase.isCompleted());
        assertEquals(ListingStatus.SOLD, store.getListingOf(purchase).getStatus());
        assertEquals("bea", store.getFullName("bea")); // screens show the username of an account that is gone
    }

    @Test
    void deletedAccount_isMarkedInactive_soASessionStillHoldingItIsRefused() {
        store.deleteAccount("bea");

        assertFalse(bea.isActive());
    }

    // ---------- What is cancelled ----------

    @Test
    void deletingABuyer_declinesTheirOpenOffers_soNothingWaitsOnThem() {
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        Offer claim = store.addOffer(new Claim(lamp.getId(), "bea"));
        PriceOffer countered = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "bea", 60));
        countered.counter(80);
        Offer benOffer = store.addOffer(new PriceOffer(book.getId(), "ben", 70));
        Offer borrowing = store.addOffer(new BorrowRequest(calculator.getId(), "bea", store.now(), store.now().plusDays(7)));
        borrowing.accept(store.now());
        calculator.acceptDeal();
        assertFalse(store.isAvailable(lamp)); // Bea's pending claim blocks the giveaway

        DeletedAccount deleted = store.deleteAccount("bea");

        assertEquals(OfferStatus.DECLINED, claim.getStatus());
        assertEquals(OfferStatus.DECLINED, countered.getStatus());
        assertEquals(List.of(claim, countered), deleted.declinedOffers());
        assertTrue(store.isAvailable(lamp));                        // other buyers can claim it again
        assertEquals(OfferStatus.PENDING, benOffer.getStatus());    // someone else's offer is untouched
        assertEquals(OfferStatus.ACCEPTED, borrowing.getStatus());  // a deal in progress stays as it is
        assertEquals(ListingStatus.LENT, calculator.getStatus());
    }

    @Test
    void deletingASeller_removesTheirListings_andDeclinesTheOffersOnThem() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer beaOffer = store.addOffer(new PriceOffer(book.getId(), "bea", 90));
        Offer benOffer = store.addOffer(new PriceOffer(book.getId(), "ben", 95));
        SaleListing desk = Fixtures.live(Fixtures.sale(store, "sam", 300));
        Offer deskDeal = store.addOffer(new PriceOffer(desk.getId(), "bea", 300));
        deskDeal.accept(store.now());
        desk.acceptDeal();
        SaleListing fridge = Fixtures.sale(store, "sam", 400); // Pending Review
        SaleListing sold = Fixtures.moveTo(Fixtures.sale(store, "sam", 20), ListingStatus.SOLD);
        SaleListing suesChair = Fixtures.live(Fixtures.sale(store, "sue", 50));
        Offer chairOffer = store.addOffer(new PriceOffer(suesChair.getId(), "bea", 40));

        DeletedAccount deleted = store.deleteAccount("sam");

        assertEquals(ListingStatus.REMOVED, book.getStatus());   // 6.4.4: removes all user listings
        assertEquals(ListingStatus.REMOVED, desk.getStatus());
        assertEquals(ListingStatus.REMOVED, fridge.getStatus());
        assertEquals(ListingStatus.SOLD, sold.getStatus());      // a sold listing stays as a record
        assertEquals(List.of(book, desk, fridge), deleted.removedListings());
        assertEquals(OfferStatus.DECLINED, beaOffer.getStatus()); // 6.4.4: cancels pending offers
        assertEquals(OfferStatus.DECLINED, benOffer.getStatus());
        assertEquals(List.of(beaOffer, benOffer), deleted.declinedOffers());
        assertEquals(OfferStatus.ACCEPTED, deskDeal.getStatus());

        assertEquals(4, store.getListingsBySeller("sam").size()); // the listings are kept, under the old username
        assertTrue(store.getLiveListings().stream().noneMatch(l -> l.getSellerUsername().equals("sam")));
        assertEquals(3, deleted.chatThreads().size());            // Bea-book, Ben-book and Bea-desk
        assertTrue(store.getThreadsForSeller("sam").isEmpty());

        // Another seller's listing, offer and chat are untouched
        assertEquals(ListingStatus.LIVE, suesChair.getStatus());
        assertEquals(OfferStatus.PENDING, chairOffer.getStatus());
        assertEquals(1, store.getThreadsForSeller("sue").size());
    }

    @Test
    void reviewsAboutADeletedSeller_areKept() {
        beaBuysSamsBook();

        store.deleteAccount("sam");

        assertEquals(1, store.getReviewsForSeller("sam").size());
    }

    // ---------- The username is retired ----------

    @Test
    void deletedUsername_isNeverGivenOutAgain() {
        beaBuysSamsBook();
        store.deleteAccount("bea");

        assertTrue(store.isRetiredUsername(" BEA "));
        assertTrue(store.usernameExists("bea"));
        assertFalse(store.addUser(new Buyer("bea", "Another Bea", "hash", null)));
        assertFalse(store.addUser(new Seller("Bea", "Seller Bea", "hash")));
        assertNull(store.findUser("bea"));

        assertFalse(store.isRetiredUsername("sam")); // an existing account is not retired
        assertFalse(store.isRetiredUsername("nobody"));
        assertFalse(store.isRetiredUsername(null));
        assertTrue(store.addUser(new Buyer("bella", "Bella", "hash", null)));
    }

    @Test
    void deletingAnUnknownAccount_isRefused_andChangesNothing() {
        beaBuysSamsBook();

        assertThrows(IllegalArgumentException.class, () -> store.deleteAccount("nobody"));

        assertEquals(4, store.getAllUsers().size());
        assertEquals(1, store.getAllReviews().size());
        assertFalse(store.isRetiredUsername("nobody"));
    }

    // ---------- The snapshot for the Audit Log (Special Requirement 4) ----------

    @Test
    void snapshot_describesEverythingThatWasDeleted_asItWasBefore_withoutThePasswordHash() {
        beaBuysSamsBook();
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        store.addOffer(new Claim(lamp.getId(), "bea"));

        String snapshot = store.deleteAccount("bea").snapshot();

        assertTrue(snapshot.contains("Username: bea"), snapshot);
        assertTrue(snapshot.contains("Full name: Bea Buyer"), snapshot);
        assertTrue(snapshot.contains("Role: BUYER"), snapshot);
        assertTrue(snapshot.contains("Status: Active"), snapshot);
        assertTrue(snapshot.contains("Residence hall: Hall 3"), snapshot);
        assertTrue(snapshot.contains("CHAT THREADS DELETED: 2"), snapshot); // the book's and the lamp's
        assertTrue(snapshot.contains("@bea: Is it still available?"), snapshot);
        assertTrue(snapshot.contains("@sam: Yes.\n        Meet at the Library?"), snapshot); // a second line stays in its record
        assertTrue(snapshot.contains("REVIEWS WRITTEN BY THE USER, DELETED: 1"), snapshot);
        assertTrue(snapshot.contains("seller @sam | 5 stars"), snapshot);
        assertTrue(snapshot.contains("comment: Smooth deal | seller's reply: none"), snapshot);
        assertTrue(snapshot.contains("NOTIFICATIONS DELETED: 1"), snapshot);
        assertTrue(snapshot.contains("Offer Update | unread | Sam accepted your price offer."), snapshot);
        assertTrue(snapshot.contains("OPEN OFFERS DECLINED: 1"), snapshot);
        assertTrue(snapshot.contains("claim by @bea"), snapshot);
        assertTrue(snapshot.contains("was Pending"), snapshot); // its status before it was declined
        assertTrue(snapshot.contains("LISTINGS REMOVED: 0"), snapshot);
        assertFalse(snapshot.contains("secret-hash"), "the password hash is not kept");
    }

    @Test
    void snapshotOfASeller_showsTheProfile_andTheListingsAsTheyWere() {
        sam.setBio("Selling my textbooks");
        sam.setPreferredMeetupLocations(List.of(MeetupLocation.LIBRARY));
        Fixtures.live(Fixtures.sale(store, "sam", 100));
        store.transaction(() -> sam.setActive(false)); // deactivated before being deleted

        String snapshot = store.deleteAccount("sam").snapshot();

        assertTrue(snapshot.contains("Role: SELLER"), snapshot);
        assertTrue(snapshot.contains("Status: Deactivated"), snapshot);
        assertTrue(snapshot.contains("Bio: Selling my textbooks"), snapshot);
        assertTrue(snapshot.contains("Preferred meet-up locations: [Library]"), snapshot);
        assertTrue(snapshot.contains("LISTINGS REMOVED: 1"), snapshot);
        assertTrue(snapshot.contains("'Book' (seller @sam) | was Live"), snapshot);
        assertFalse(snapshot.contains("Residence hall"), snapshot);
    }

    // ---------- Saving ----------

    @Test
    void deletion_survivesARestart_andItsSnapshotIsKeptInTheAuditLog(@TempDir Path folder) {
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed();

        DeletedAccount deleted = saved.deleteAccount("alice");
        AuditLogEntry entry = saved.addAuditLogEntry(new AuditLogEntry("admin", "alice", "Delete account",
                "Requested by the user", deleted.snapshot())); // what the admin's Delete User does next

        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(file));
        restarted.loadOrSeed();
        assertNull(restarted.findUser("alice"));
        assertTrue(restarted.isRetiredUsername("alice"));
        assertFalse(restarted.addUser(new Buyer("alice", "New Alice", "hash", null)));
        assertTrue(restarted.getThreadsForBuyer("alice").isEmpty());
        assertEquals(1, restarted.getOffersByBuyer("alice").size()); // her accepted offer is kept as a record
        assertEquals(Optional.of(deleted.snapshot()), restarted.readAuditSnapshotFile(entry.getLogReference()));
        assertEquals(deleted.snapshot(), restarted.getAuditLog().get(0).getDataSnapshot());
    }

    @Test
    void saveFileFromBeforeAccountsCouldBeDeleted_stillLoads() throws IOException {
        // Such a file has no list of retired usernames
        StoreSnapshot old = JsonMapping.newMapper().readValue("{\"nextId\":7,\"users\":[]}", StoreSnapshot.class);

        assertEquals(7, old.nextId);
        assertTrue(old.retiredUsernames.isEmpty());
    }
}
