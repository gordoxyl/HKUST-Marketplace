package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.PasswordHasher;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Demo accounts and data loaded when the app starts. The README lists the demo logins.
 * Passwords are stored hashed, like real accounts, and meet the password rules
 * (UC1 Special Requirement 2).
 *
 * The demo data is created as a short story in time order: the store's clock is set back
 * before each step, so every record gets a believable date (an offer is made before it is
 * accepted, a deal is completed before it is reviewed), and listings reach their status
 * through the same state machine events the portals use.
 */
public final class SeedData {

    /** Password of the seeded admin account (Task 3). */
    public static final String ADMIN_PASSWORD = "Admin@1234";

    /** Password of every seeded demo buyer and seller. */
    public static final String DEMO_PASSWORD = "Demo@1234";

    private SeedData() {
        // Utility class
    }

    /** Fills an empty store with the demo data. */
    public static void populate(DataStore store) {
        Clock realClock = store.getClock();
        LocalDateTime now = store.now();
        String demoHash = PasswordHasher.hash(DEMO_PASSWORD);

        // ---------- Accounts (registered a month ago) ----------
        at(store, now.minusDays(30));
        store.addUser(new Admin("admin", "HKUST Marketplace Admin", PasswordHasher.hash(ADMIN_PASSWORD)));
        store.addUser(new Buyer("alice", "Alice Chan", demoHash, "Hall 3"));
        store.addUser(new Buyer("bob", "Bob Wong", demoHash, "Off-campus"));

        Seller charlie = new Seller("charlie", "Charlie Lee", demoHash);
        charlie.setBio("Final year CSE student selling textbooks and gadgets.");
        charlie.setPreferredMeetupLocations(List.of(MeetupLocation.LIBRARY, MeetupLocation.ACADEMIC_BUILDING_LG1));
        store.addUser(charlie);

        Seller diana = new Seller("diana", "Diana Ng", demoHash);
        diana.setBio("Selling furniture and kitchen items before graduation.");
        diana.setPreferredMeetupLocations(List.of(MeetupLocation.HALL5_LOBBY));
        store.addUser(diana);

        // ---------- Bob bought the chemistry textbook and reviewed Charlie: Sold ----------
        at(store, now.minusDays(21));
        Listing chemistry = approved(store, new SaleListing("charlie", "Organic Chemistry Textbook", Category.TEXTBOOKS,
                ItemCondition.USED, "Some highlighting, otherwise clean.", MeetupLocation.LIBRARY, 120));
        at(store, now.minusDays(20));
        Offer bobPurchase = store.addOffer(new PriceOffer(chemistry.getId(), "bob", 120));
        at(store, now.minusDays(19));
        bobPurchase.accept(store.now());
        chemistry.acceptDeal();
        at(store, now.minusDays(18));
        bobPurchase.complete(store.now());
        chemistry.completeDeal();
        at(store, now.minusDays(17));
        store.addReview(new Review(bobPurchase.getId(), 5, "Very smooth transaction, book in excellent condition!"));
        store.addNotification(new Notification("charlie", NotificationCategory.REVIEW_RECEIVED,
                "Bob Wong rated you 5 stars for Organic Chemistry Textbook."));

        // ---------- Listings that are live now ----------
        at(store, now.minusDays(6));
        Listing calculus = approved(store, new SaleListing("charlie", "Calculus Textbook (Stewart)", Category.TEXTBOOKS,
                ItemCondition.LIKE_NEW, "Barely used, 8th edition. Perfect for MATH 1013.", MeetupLocation.LIBRARY, 180));
        at(store, now.minusDays(4));
        approved(store, new SaleListing("charlie", "MacBook Pro Charger 61W", Category.ELECTRONICS,
                ItemCondition.USED, "Original Apple charger, works perfectly.", MeetupLocation.ACADEMIC_BUILDING_LG1, 250));
        at(store, now.minusDays(3));
        approved(store, new GiveawayListing("diana", "IKEA Desk Lamp", Category.FURNITURE,
                ItemCondition.USED, "Good condition, LED bulb included.", MeetupLocation.HALL5_LOBBY));
        at(store, now.minusDays(2));
        Listing calculator = approved(store, new LendListing("diana", "Graphing Calculator TI-84", Category.ELECTRONICS,
                ItemCondition.LIKE_NEW, "Can lend for one semester.", MeetupLocation.STUDENT_CENTRE, Duration.ofDays(90)));

        // ---------- Alice's offer on the calculus textbook was accepted: Reserved ----------
        at(store, now.minusDays(3));
        Offer aliceOffer = store.addOffer(new PriceOffer(calculus.getId(), "alice", 160));
        store.addNotification(new Notification("charlie", NotificationCategory.OFFER_RECEIVED,
                "Alice Chan offered HK$ 160 for Calculus Textbook (Stewart)."));
        ChatThread aliceChat = store.findThread("alice", calculus.getId());
        aliceChat.addMessage(new Message("alice", "Hi, is the textbook still available?", store.now()));
        at(store, now.minusDays(2));
        aliceChat.addMessage(new Message("charlie", "Yes! Happy to meet at the Library.", store.now()));
        at(store, now.minusDays(1));
        aliceOffer.accept(store.now());
        calculus.acceptDeal();
        store.addNotification(new Notification("alice", NotificationCategory.OFFER_UPDATE,
                "Charlie Lee accepted your price offer for 'Calculus Textbook (Stewart)'."));

        // ---------- Diana's mini fridge waits for review: Pending Review ----------
        store.addListing(new SaleListing("diana", "Mini Fridge", Category.FURNITURE,
                ItemCondition.USED, "Works well, cleaning required.", MeetupLocation.HALL2_LOBBY, 400));
        store.addNotification(new Notification("diana", NotificationCategory.LISTING_SUBMITTED,
                "Your listing 'Mini Fridge' is pending review."));
        store.addNotification(new Notification("admin", NotificationCategory.LISTING_SUBMITTED,
                "Diana Ng submitted 'Mini Fridge' for review."));

        // ---------- Bob's borrow request on the calculator waits for Diana: Pending ----------
        at(store, now.minusHours(1));
        store.addOffer(new BorrowRequest(calculator.getId(), "bob", now.plusDays(1), now.plusDays(8)));
        store.addNotification(new Notification("diana", NotificationCategory.OFFER_RECEIVED,
                "Bob Wong sent a borrow request for Graphing Calculator TI-84."));

        store.setClock(realClock);
    }

    /** Adds the listing and approves it, as the admin would. */
    private static Listing approved(DataStore store, Listing listing) {
        store.addListing(listing);
        listing.approve();
        return listing;
    }

    /** Sets the store's clock to a fixed moment, so the next records are dated then. */
    private static void at(DataStore store, LocalDateTime when) {
        ZoneId zone = store.getClock().getZone();
        store.setClock(Clock.fixed(when.atZone(zone).toInstant(), zone));
    }
}
