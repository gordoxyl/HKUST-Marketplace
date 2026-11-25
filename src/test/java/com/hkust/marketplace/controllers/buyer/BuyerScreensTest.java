package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.testsupport.OffScreenApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of the Buyer Portal (Lecture 14): demo buyers use its real screens as a user
 * would, from the login screen on. Every dialog is answered Cancel, or OK when it only informs,
 * so no offer, report or review is sent; the rules behind the buttons are unit-tested in the
 * services' tests. In the demo data Alice has a current deal (the Calculus Textbook, with a
 * chat) and Bob a completed one (the Organic Chemistry Textbook).
 */
class BuyerScreensTest {

    private static final String CHARGER = "MacBook Pro Charger 61W";
    private static final String CALCULUS = "Calculus Textbook (Stewart)";

    private static OffScreenApp app;

    @BeforeAll
    static void openTheApp() {
        app = OffScreenApp.start();
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    private static void logInAs(String username) {
        app.logIn("Enter as Buyer", username, SeedData.DEMO_PASSWORD);
    }

    @Test
    void sidebar_opensThePageOfEachUseCase() {
        logInAs("alice");

        assertEquals(Map.of(
                "Browse Marketplace", "Browse Marketplace", // UC8
                "My Deals", "My Deals",                     // UC7
                "Deal History", "Deal History",             // UC11
                "Chats", "Chats",                           // UC6
                "Notifications", "Notification Board",      // UC4
                "My Profile", "Manage Profile"),            // UC3
                app.visitEveryPage());
        assertEquals(List.of(), app.dialogs());
    }

    // ---------- UC8 Browse Marketplace ----------

    @Test
    void browse_keywordSearch_showsTheMatchingListings_andResetShowsAllAgain() {
        logInAs("alice");

        app.type("Search by title or keyword", "calculator");
        app.press("Search");
        assertTrue(app.shows("Graphing Calculator TI-84"));
        assertFalse(app.shows("IKEA Desk Lamp"));

        app.press("Reset");
        assertTrue(app.shows("IKEA Desk Lamp"));
    }

    @Test
    void listing_opensWithItsSellersProfile_andBackLeadsToTheListings() {
        logInAs("alice");

        app.press("View Details", CHARGER);
        assertEquals(CHARGER, app.pageTitle());

        app.press("Charlie Lee"); // A3: the seller's name opens the seller's profile
        assertEquals("Seller: Charlie Lee", app.pageTitle());

        app.press("← Back to listing");
        assertEquals(CHARGER, app.pageTitle());
        app.press("← Back to listings"); // step 8
        assertEquals("Browse Marketplace", app.pageTitle());
        assertEquals(List.of(), app.dialogs());
    }

    @Test
    void listing_makeOfferAndReportCancelled_sendNothing() {
        logInAs("alice");
        app.press("View Details", CHARGER);

        app.press("Make Offer");     // UC5, cancelled in its form
        app.press("Report Listing"); // UC9, cancelled in its form

        List<String> dialogs = app.dialogs();
        assertEquals(2, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(0).startsWith("Make Offer: "), dialogs::toString);
        assertTrue(dialogs.get(1).startsWith("Report Listing: "), dialogs::toString);
        app.press("My Deals");
        assertFalse(app.shows(CHARGER), "a cancelled offer must not become a deal");
    }

    @Test
    void listing_chatWithTheSeller_opensTheChat_andBackLeadsToTheListing() {
        logInAs("alice");
        app.press("View Details", "IKEA Desk Lamp");

        app.press("Chat with Seller"); // UC6 from the detail screen
        assertEquals("Chat: IKEA Desk Lamp", app.pageTitle());

        app.press("← Back to listing");
        assertEquals("IKEA Desk Lamp", app.pageTitle());
    }

    // ---------- UC7 Manage My Deals ----------

    @Test
    void myDeals_aCurrentDeal_opensItsListingAndItsChat() {
        logInAs("alice");
        app.press("My Deals");

        app.press("View Listing", CALCULUS); // A4
        assertEquals(CALCULUS, app.pageTitle());
        app.press("← Back to My Deals");

        app.press("Open Chat", CALCULUS); // A4
        assertEquals("Chat: " + CALCULUS, app.pageTitle());
        assertTrue(app.shows("Yes! Happy to meet at the Library."));
        app.press("← Back to My Deals");
        assertEquals("My Deals", app.pageTitle());
    }

    @Test
    void myDeals_markCompleteCancelled_keepsTheDealCurrent() {
        logInAs("alice");
        app.press("My Deals");

        app.press("Mark Complete", CALCULUS);

        List<String> dialogs = app.dialogs(); // the wording is tested in DealTextTest
        assertEquals(1, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(0).startsWith("Mark Complete: Mark your price offer for '" + CALCULUS + "' complete?"),
                dialogs::toString);
        app.press("Mark Complete", CALCULUS); // still current, so it can still be completed
    }

    // ---------- UC6 Chat, UC11 Deal History, UC4 Notifications, UC3 Manage Profile ----------

    @Test
    void chats_openAChat_andSearchIt() {
        logInAs("alice");
        app.press("Chats");

        app.press("Open Chat", CALCULUS);
        app.type("Search this chat", "Library");
        app.press("Search");
        assertTrue(app.shows("Close search"));

        app.press("Close search");
        assertFalse(app.shows("Close search"));
    }

    @Test
    void dealHistory_filterBySeller_thenReset() {
        logInAs("bob");
        app.press("Deal History");
        assertTrue(app.shows("Organic Chemistry Textbook"));

        app.type("Seller's name", "Diana");
        app.press("Search");
        assertFalse(app.shows("Organic Chemistry Textbook")); // A1: Charlie sold it

        app.press("Reset");
        assertTrue(app.shows("Organic Chemistry Textbook"));
    }

    @Test
    void notifications_markAsRead_removesTheButton() {
        logInAs("alice");
        app.press("Notifications");

        app.press("Mark as read");

        assertFalse(app.shows("Mark as read"));
    }

    @Test
    void profile_savedUnchanged_isConfirmed() {
        logInAs("alice");
        app.press("My Profile");

        app.press("Save Changes");

        assertEquals(1, app.dialogs().size(), app.dialogs()::toString);
        assertTrue(app.dialogs().get(0).startsWith("Profile updated: "), app.dialogs()::toString);
        assertEquals("Hello, Alice Chan", app.text("#welcomeLabel"));
    }
}
