package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Builders for test data, shared by everyone's tests. Each test makes its own store, so
 * tests never affect each other:
 * <pre>{@code
 * TestClock clock = Fixtures.clock();
 * DataStore store = Fixtures.store(clock);
 * Seller sam = Fixtures.seller(store, "sam");
 * Buyer bea = Fixtures.buyer(store, "bea");
 * SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
 * }</pre>
 * Listings reach a status only through the state machine's events, the same way the app
 * does it (Lecture 13, state-based testing: first put the object in the state you need).
 */
public final class Fixtures {

    /** The moment every test clock starts at. */
    public static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 12, 0);

    private Fixtures() {
        // Utility class
    }

    public static TestClock clock() {
        return new TestClock(START);
    }

    public static DataStore store(TestClock clock) {
        return new DataStore(clock);
    }

    // ---------- Accounts (password hashes are not needed outside AuthService tests) ----------

    public static Buyer buyer(DataStore store, String username) {
        Buyer buyer = new Buyer(username, "Buyer " + username, "hash", null);
        store.addUser(buyer);
        return buyer;
    }

    public static Seller seller(DataStore store, String username) {
        Seller seller = new Seller(username, "Seller " + username, "hash");
        store.addUser(seller);
        return seller;
    }

    public static Admin admin(DataStore store, String username) {
        Admin admin = new Admin(username, "Admin " + username, "hash");
        store.addUser(admin);
        return admin;
    }

    // ---------- Listings (added to the store, status Pending Review) ----------

    public static SaleListing sale(DataStore store, String seller, double price) {
        return add(store, new SaleListing(seller, "Book", Category.TEXTBOOKS, ItemCondition.USED,
                "A test book", MeetupLocation.LIBRARY, price));
    }

    public static LendListing lend(DataStore store, String seller, Duration lendingDuration) {
        return add(store, new LendListing(seller, "Calculator", Category.ELECTRONICS, ItemCondition.USED,
                "A test calculator", MeetupLocation.LIBRARY, lendingDuration));
    }

    public static GiveawayListing giveaway(DataStore store, String seller) {
        return add(store, new GiveawayListing(seller, "Lamp", Category.FURNITURE, ItemCondition.USED,
                "A test lamp", MeetupLocation.LIBRARY));
    }

    private static <L extends Listing> L add(DataStore store, L listing) {
        store.addListing(listing);
        return listing;
    }

    /** Approves a pending listing, as the admin would, and returns it. */
    public static <L extends Listing> L live(L listing) {
        listing.approve();
        return listing;
    }

    /**
     * Moves a Pending Review listing to the target status through the state machine's events.
     * Reserved needs a sale or giveaway listing, Lent a lend listing, Sold a sale listing and
     * Given a giveaway listing; any other combination is refused, as in the app.
     */
    public static <L extends Listing> L moveTo(L listing, ListingStatus target) {
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("moveTo starts from a Pending Review listing.");
        }
        switch (target) {
            case PENDING_REVIEW -> { }
            case LIVE -> listing.approve();
            case REJECTED -> listing.reject();
            case REMOVED -> listing.withdraw();
            case RESERVED, LENT -> {
                listing.approve();
                listing.acceptDeal();
            }
            case SOLD, GIVEN -> {
                listing.approve();
                listing.acceptDeal();
                listing.completeDeal();
            }
        }
        if (listing.getStatus() != target) {
            throw new IllegalArgumentException("A " + listing.getType() + " listing cannot become " + target + ".");
        }
        return listing;
    }
}
