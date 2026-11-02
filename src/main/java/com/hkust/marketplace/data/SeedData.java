package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.PasswordHasher;
import com.hkust.marketplace.utils.DevLogin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo accounts and data loaded when the app starts. The README lists the demo logins.
 * Passwords are stored hashed, like real accounts, and meet the password rules
 * (UC1 Special Requirement 2).
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
        LocalDateTime now = LocalDateTime.now();
        String demoHash = PasswordHasher.hash(DEMO_PASSWORD);

        // ---------- Accounts ----------
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

        // ---------- Listings ----------
        Listing calculus = add(store, new SaleListing("charlie", "Calculus Textbook (Stewart)", Category.TEXTBOOKS,
                ItemCondition.LIKE_NEW, "Barely used, 8th edition. Perfect for MATH 1013.",
                MeetupLocation.LIBRARY, 180), ListingStatus.LIVE, now.minusDays(6));
        add(store, new SaleListing("charlie", "MacBook Pro Charger 61W", Category.ELECTRONICS,
                ItemCondition.USED, "Original Apple charger, works perfectly.",
                MeetupLocation.ACADEMIC_BUILDING_LG1, 250), ListingStatus.LIVE, now.minusDays(4));
        add(store, new GiveawayListing("diana", "IKEA Desk Lamp", Category.FURNITURE,
                ItemCondition.USED, "Good condition, LED bulb included.",
                MeetupLocation.HALL5_LOBBY), ListingStatus.LIVE, now.minusDays(3));
        Listing calculator = add(store, new LendListing("diana", "Graphing Calculator TI-84", Category.ELECTRONICS,
                ItemCondition.LIKE_NEW, "Can lend for one semester.",
                MeetupLocation.STUDENT_CENTRE, Duration.ofDays(90)), ListingStatus.LIVE, now.minusDays(2));
        add(store, new SaleListing("diana", "Mini Fridge", Category.FURNITURE,
                ItemCondition.USED, "Works well, cleaning required.",
                MeetupLocation.HALL2_LOBBY, 400), ListingStatus.PENDING_REVIEW, now.minusDays(1));
        Listing chemistry = add(store, new SaleListing("charlie", "Organic Chemistry Textbook", Category.TEXTBOOKS,
                ItemCondition.USED, "Some highlighting, otherwise clean.",
                MeetupLocation.LIBRARY, 120), ListingStatus.SOLD, now.minusDays(20));

        // ---------- Deals ----------
        // Alice's offer on the calculus textbook was accepted, so the listing is Reserved
        Offer aliceOffer = store.addOffer(new PriceOffer(calculus.getId(), "alice", 160));
        aliceOffer.accept(now.minusDays(1));
        calculus.setStatus(ListingStatus.RESERVED);

        ChatThread aliceChat = store.findThread("alice", calculus.getId());
        aliceChat.addMessage(new Message("alice", "Hi, is the textbook still available?"));
        aliceChat.addMessage(new Message("charlie", "Yes! Happy to meet at the Library."));

        // Bob bought the chemistry textbook, completed the deal and reviewed Charlie
        Offer bobPurchase = store.addOffer(new PriceOffer(chemistry.getId(), "bob", 120));
        bobPurchase.accept(now.minusDays(19));
        bobPurchase.complete(now.minusDays(18));
        store.addReview(new Review(bobPurchase.getId(), 5, "Very smooth transaction, book in excellent condition!"));

        // Bob's borrow request on the calculator is waiting for Diana
        store.addOffer(new BorrowRequest(calculator.getId(), "bob", now.plusDays(1), now.plusDays(8)));

        // ---------- Notifications ----------
        store.addNotification(new Notification("alice", NotificationCategory.OFFER_UPDATE,
                "Charlie Lee accepted your offer of HK$ 160 for Calculus Textbook (Stewart)."));
        store.addNotification(new Notification("charlie", NotificationCategory.OFFER_RECEIVED,
                "Alice Chan offered HK$ 160 for Calculus Textbook (Stewart)."));
        store.addNotification(new Notification("charlie", NotificationCategory.REVIEW_RECEIVED,
                "Bob Wong rated you 5 stars for Organic Chemistry Textbook."));
        store.addNotification(new Notification("diana", NotificationCategory.OFFER_RECEIVED,
                "Bob Wong sent a borrow request for Graphing Calculator TI-84."));
        store.addNotification(new Notification("diana", NotificationCategory.LISTING_SUBMITTED,
                "Your listing 'Mini Fridge' is pending review."));
        store.addNotification(new Notification("admin", NotificationCategory.LISTING_SUBMITTED,
                "Diana Ng submitted 'Mini Fridge' for review."));

        DevLogin.seedAccounts(store); // DEV-ONLY: test accounts for dev runs; remove before release
    }

    private static Listing add(DataStore store, Listing listing, ListingStatus status, LocalDateTime posted) {
        listing.setStatus(status);
        listing.setPostedDate(posted);
        return store.addListing(listing);
    }
}
