package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of UC11 View Deal History and UC4 View Notifications, as the buyer, through the
 * real screens. Tests named "...WhileLoggedIn" deactivate the account between two of the
 * buyer's steps (technical requirement: access control at the API layer; assumptions register 6).
 */
class HistoryAndNotificationsScreensTest {

    private static final String RANGE_PROBLEM = "The date range is invalid. Choose a start date on or before the end date.";
    private static final String DEACTIVATED = "Not possible: This account has been deactivated. Please contact the admin.";

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void openTheAppWithCompletedDeals() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
        data.seller("dhseller", "Dee Seller");
        data.buyer("dhbuyer", "Buyer dhbuyer");
        Offer lamp = data.priceOffer("dhbuyer", data.sale("dhseller", "History Desk Lamp", 60), 55);
        data.accept(lamp);
        data.complete(lamp);
        Offer chair = data.claim("dhbuyer", data.giveaway("dhseller", "History Chair"));
        data.accept(chair);
        data.complete(chair);
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    // ---------- UC11 View Deal History ----------

    @Test
    void history_filtersByCategoryAndDates_saysWhenNothingMatches_andResetShowsAllAgain() {
        app.logIn("Enter as Buyer", "dhbuyer", SeedData.DEMO_PASSWORD);
        app.press("Deal History");
        assertTrue(app.shows("History Desk Lamp"));
        assertTrue(app.shows("History Chair"));

        app.choose("Furniture");
        assertFalse(app.shows("History Desk Lamp"));
        assertTrue(app.shows("History Chair"));

        app.pick("From", LocalDate.now().plusDays(1)); // completed today, so before this range: A1
        assertTrue(app.shows("No deals match. A deal appears here once it is completed."));

        app.press("Reset");
        assertTrue(app.shows("History Desk Lamp"));
        assertTrue(app.shows("History Chair"));
    }

    @Test
    void history_aRangeThatEndsBeforeItStarts_isReported_andTheMessageStaysUntilItIsCorrected() {
        app.logIn("Enter as Buyer", "dhbuyer", SeedData.DEMO_PASSWORD);
        app.press("Deal History");

        app.pick("From", LocalDate.now());
        app.pick("To", LocalDate.now().minusDays(1)); // A2
        assertTrue(app.shows(RANGE_PROBLEM));
        assertTrue(app.shows("History Chair")); // the earlier results stay

        app.dataChanged(); // the timer reloads the history (Special Requirement 1): the message stays
        assertTrue(app.shows(RANGE_PROBLEM));

        app.pick("To", LocalDate.now()); // {Refine}
        assertFalse(app.shows(RANGE_PROBLEM));
        assertTrue(app.shows("History Chair"));
    }

    @Test
    void history_ofAnAccountDeactivatedWhileLoggedIn_cannotBeSearched() {
        data.buyer("dhdeact", "Buyer dhdeact");
        app.logIn("Enter as Buyer", "dhdeact", SeedData.DEMO_PASSWORD);
        app.press("Deal History");
        data.deactivate("dhdeact");

        app.press("Search");

        assertEquals(List.of(DEACTIVATED), app.dialogs());
    }

    // ---------- UC4 View Notifications ----------

    @Test
    void notifications_ofABuyerWithNone_sayThereAreNone() {
        data.buyer("ntnone", "Buyer ntnone");
        app.logIn("Enter as Buyer", "ntnone", SeedData.DEMO_PASSWORD);

        app.press("Notifications");

        assertTrue(app.shows("There are no notifications yet.")); // A1
    }

    @Test
    void notifications_ofAnAccountDeactivatedWhileLoggedIn_areNotShown() {
        data.buyer("ntdeact", "Buyer ntdeact");
        app.logIn("Enter as Buyer", "ntdeact", SeedData.DEMO_PASSWORD);
        data.deactivate("ntdeact");

        app.press("Notifications");

        assertEquals(List.of(DEACTIVATED), app.dialogs());
    }

    @Test
    void markAsRead_ofAnAccountDeactivatedWhileLoggedIn_isRefused() {
        data.buyer("ntread", "Buyer ntread");
        data.notification("ntread", NotificationCategory.ANNOUNCEMENT, "The marketplace is open during the holidays.");
        app.logIn("Enter as Buyer", "ntread", SeedData.DEMO_PASSWORD);
        app.press("Notifications");
        data.deactivate("ntread");

        app.press("Mark as read");

        assertEquals(List.of(DEACTIVATED), app.dialogs());
        assertTrue(app.shows("Mark as read")); // still unread
    }
}
