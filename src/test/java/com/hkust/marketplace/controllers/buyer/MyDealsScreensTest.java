package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.BorrowingDeadlineService;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of UC7 Manage My Deals, UC10 Review Seller and the rate-seller prompts of UC12
 * (client answer 3), through the real screens. Loans that must end get a real period of a few
 * seconds, and the test waits for it, as the app's timer would; the timer's check is then run
 * as the app runs it ({@link BorrowingDeadlineService#process()}). Tests named "...WhileLoggedIn"
 * deactivate the account between two of the buyer's steps (technical requirement: access
 * control at the API layer; assumptions register 6).
 */
class MyDealsScreensTest {

    private static final String COMMENT = "How did the deal go? A written review is required.";
    private static final String DEACTIVATED = "Not possible: This account has been deactivated. Please contact the admin.";
    private static final String RETURNED_AUTOMATICALLY = "Item returned automatically: ";

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void openTheApp() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
        data.seller("mdseller", "Md Seller");
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    private static void logInAndOpenMyDeals(String buyer) {
        app.logIn("Enter as Buyer", buyer, SeedData.DEMO_PASSWORD);
        app.press("My Deals");
    }

    /** A new buyer whose price offer on a new sale listing the seller has accepted. */
    private static Offer acceptedPurchase(String buyer, String title) {
        data.buyer(buyer, "Buyer " + buyer);
        Offer offer = data.priceOffer(buyer, data.sale("mdseller", title, 100), 90);
        data.accept(offer);
        return offer;
    }

    /** A loan accepted by the seller that ends at the given moment. */
    private static void acceptedLoan(String buyer, String title, LocalDateTime end) {
        LendListing listing = data.lend("mdseller", title, Duration.ofDays(1));
        BorrowRequest request = data.borrowRequest(buyer, listing, app.store().now().minusHours(1), end);
        data.accept(request);
    }

    private static long count(List<String> dialogs, String start) {
        return dialogs.stream().filter(dialog -> dialog.startsWith(start)).count();
    }

    // ---------- Mark Complete, then the rate-seller prompt (UC7 steps 3-9, UC10) ----------

    @Test
    void markComplete_completesTheDeal_andTheReviewIsCheckedUntilItIsComplete() {
        acceptedPurchase("mdbuyer1", "Deal Complete Kettle");
        app.answer("Mark Complete", form -> form.press("OK"));
        app.answer("Deal completed", form -> form.press("OK")); // step 9: rate the seller now
        app.answer("Review Seller", form -> form.type(COMMENT, "Fine").press("Submit Review")); // A2: no stars
        app.answer("Review Seller", form -> {
            assertEquals("Fine", form.valueOf(COMMENT)); // the form comes back with what was entered
            form.press("4 ★").press("4 ★").press("Submit Review"); // a chosen star clicked again is cleared
        });
        app.answer("Review Seller", form -> form.press("5 ★").type(COMMENT, " ").press("Submit Review")); // no review
        app.answer("Review Seller", form -> form.type(COMMENT, "Smooth deal, kettle as described.").press("Submit Review"));
        logInAndOpenMyDeals("mdbuyer1");

        app.press("Mark Complete", "Deal Complete Kettle");

        List<String> dialogs = app.dialogs();
        assertEquals(10, dialogs.size(), dialogs::toString);
        assertEquals(3, count(dialogs, "Not possible: "), dialogs::toString);
        assertEquals("Not possible: A rating from 1 to 5 stars is required.", dialogs.get(3));
        assertEquals("Not possible: A written review is required. Tell other buyers how the deal went.", dialogs.get(7));
        assertTrue(dialogs.get(9).startsWith("Review submitted: "), dialogs::toString);
        assertTrue(app.shows("Completed deals"));
        assertFalse(app.shows("Rate Seller")); // a reviewed deal has no Rate Seller button
    }

    @Test
    void markComplete_withThePromptDeclined_leavesRateSeller_whichCanBeSkipped() {
        acceptedPurchase("mdbuyer2", "Deal Later Toaster");
        app.answer("Mark Complete", form -> form.press("OK"));
        logInAndOpenMyDeals("mdbuyer2");

        app.press("Mark Complete", "Deal Later Toaster"); // the prompt is answered Cancel: not now
        app.press("Rate Seller", "Deal Later Toaster");   // the form is answered Skip (A3)

        List<String> dialogs = app.dialogs();
        assertEquals(3, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(1).startsWith("Deal completed: "), dialogs::toString);
        assertTrue(dialogs.get(2).startsWith("Review Seller: "), dialogs::toString);
        assertTrue(app.shows("Rate Seller"));
    }

    @Test
    void markComplete_ofAnAccountDeactivatedWhileLoggedIn_isRefused() {
        acceptedPurchase("mdbuyer3", "Deal Refused Blender");
        app.answer("Mark Complete", form -> {
            data.deactivate("mdbuyer3");
            form.press("OK");
        });
        logInAndOpenMyDeals("mdbuyer3");

        app.press("Mark Complete", "Deal Refused Blender");

        List<String> dialogs = app.dialogs(); // the refusal, then the list cannot be shown again
        assertEquals(List.of(DEACTIVATED, DEACTIVATED), dialogs.subList(1, dialogs.size()));
    }

    @Test
    void aDealWhoseSellerAccountWasDeleted_stillOpensItsListing() {
        // Client answer 6: the admin's Delete User removes the seller's listings; the buyer's deal is kept
        data.seller("mddeleted", "Del Seller");
        data.buyer("mdbuyer8", "Buyer mdbuyer8");
        Offer offer = data.priceOffer("mdbuyer8", data.sale("mddeleted", "Deal Orphan Clock", 60), 50);
        data.accept(offer);
        data.deleteAccount("mddeleted");
        logInAndOpenMyDeals("mdbuyer8");

        app.press("View Listing", "Deal Orphan Clock");

        assertEquals("Deal Orphan Clock", app.pageTitle());
        assertTrue(app.shows("Removed"));
        assertEquals(List.of(), app.dialogs());
    }

    @Test
    void rateSeller_ofAnAccountDeactivatedWhileLoggedIn_isRefused() {
        Offer offer = acceptedPurchase("mdbuyer9", "Deal Refused Iron");
        data.complete(offer);
        logInAndOpenMyDeals("mdbuyer9");
        data.deactivate("mdbuyer9");

        app.press("Rate Seller", "Deal Refused Iron");

        assertEquals(List.of(DEACTIVATED, DEACTIVATED), app.dialogs()); // UC10 A1, then My Deals cannot reload
    }

    @Test
    void listingAndChatOfADeal_ofAnAccountDeactivatedWhileLoggedIn_areRefused() {
        acceptedPurchase("mdbuyer4", "Deal Refused Mixer");
        logInAndOpenMyDeals("mdbuyer4");
        data.deactivate("mdbuyer4");

        app.press("View Listing", "Deal Refused Mixer");
        app.press("Open Chat", "Deal Refused Mixer");

        assertEquals(List.of(DEACTIVATED, DEACTIVATED), app.dialogs());
        assertEquals("My Deals", app.pageTitle());
    }

    // ---------- Return Item, then the prompt (UC7 A2, client answer 3) ----------

    @Test
    void returnItem_returnsTheLoan_andThePromptOpensTheReview() {
        data.buyer("mdbuyer5", "Buyer mdbuyer5");
        acceptedLoan("mdbuyer5", "Deal Return Drill", app.store().now().plusDays(2));
        app.answer("Return Item", form -> form.press("OK"));
        app.answer("Item returned", form -> form.press("OK"));
        app.answer("Review Seller", form -> form.press("3 ★").type(COMMENT, "Worked, but late to meet.").press("Submit Review"));
        logInAndOpenMyDeals("mdbuyer5");

        app.press("Return Item", "Deal Return Drill");

        List<String> dialogs = app.dialogs();
        assertEquals(4, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(3).startsWith("Review submitted: "), dialogs::toString);
        assertFalse(app.shows("Return Item"));
    }

    @Test
    void returnItem_cancelled_keepsTheLoan() {
        data.buyer("mdbuyer10", "Buyer mdbuyer10");
        acceptedLoan("mdbuyer10", "Deal Kept Ladder", app.store().now().plusDays(2));
        logInAndOpenMyDeals("mdbuyer10");

        app.press("Return Item", "Deal Kept Ladder"); // A3: the confirmation is answered Cancel

        assertEquals(1, app.dialogs().size(), app.dialogs()::toString);
        assertTrue(app.shows("Return Item"));
    }

    @Test
    void returnItem_withThePromptDeclined_leavesRateSeller() {
        data.buyer("mdbuyer6", "Buyer mdbuyer6");
        acceptedLoan("mdbuyer6", "Deal Return Saw", app.store().now().plusDays(2));
        app.answer("Return Item", form -> form.press("OK"));
        logInAndOpenMyDeals("mdbuyer6");

        app.press("Return Item", "Deal Return Saw"); // the prompt is answered Cancel

        assertTrue(app.dialogs().get(1).startsWith("Item returned: "), app.dialogs()::toString);
        assertTrue(app.shows("Rate Seller"));
    }

    @Test
    void returnItem_confirmedAfterTheDueTime_isRefused_asTheReturnIsAutomatic() {
        // Special Requirement 3: the due time passes while My Deals still shows the button
        data.buyer("mdbuyer7", "Buyer mdbuyer7");
        LocalDateTime end = app.store().now().plusSeconds(3);
        acceptedLoan("mdbuyer7", "Deal Late Sander", end);
        app.answer("Return Item", form -> form.press("OK"));
        logInAndOpenMyDeals("mdbuyer7");
        data.waitUntilAfter(end);

        app.press("Return Item", "Deal Late Sander");

        assertEquals("Not possible: The borrowing period has ended. The item is returned automatically.",
                app.dialogs().get(1));
    }

    // ---------- Automatic returns and the rate-seller prompt (UC12, client answer 3) ----------

    @Test
    void itemsReturnedAutomaticallyWhileTheDashboardIsOpen_areEachPromptedOnce() {
        data.buyer("mdauto1", "Buyer mdauto1");
        LocalDateTime end = app.store().now().plusSeconds(3);
        acceptedLoan("mdauto1", "Deal Auto Tent", end);
        acceptedLoan("mdauto1", "Deal Auto Stove", end);
        app.answer("Item returned automatically", form -> form.press("OK"));
        app.answer("Review Seller", form -> form.press("5 ★").type(COMMENT, "Tent was clean.").press("Submit Review"));
        logInAndOpenMyDeals("mdauto1");
        data.waitUntilAfter(end);

        new BorrowingDeadlineService(app.store()).process(); // the timer's check
        app.dataChanged();
        app.dataChanged(); // the timer reports again: nothing new to ask

        List<String> dialogs = app.dialogs(); // the first prompt is accepted, the second answered Cancel
        assertEquals(2, count(dialogs, RETURNED_AUTOMATICALLY), dialogs::toString);
        assertEquals(1, count(dialogs, "Review submitted: "), dialogs::toString);
        assertTrue(app.shows("Rate Seller")); // the deal not reviewed yet
    }

    @Test
    void anItemReturnedAutomaticallyBeforeTheBuyerLoggedIn_isNotPrompted() {
        // Its notification already invites the rating (client answer 3)
        data.buyer("mdauto2", "Buyer mdauto2");
        LocalDateTime end = app.store().now().plusSeconds(3);
        acceptedLoan("mdauto2", "Deal Auto Lantern", end);
        data.waitUntilAfter(end);
        new BorrowingDeadlineService(app.store()).process();

        logInAndOpenMyDeals("mdauto2");
        app.dataChanged();

        assertEquals(List.of(), app.dialogs());
        assertTrue(app.shows("Rate Seller"));
    }

    @Test
    void theTimerReportingAfterTheBuyerLoggedOut_reachesNoDashboard() {
        data.buyer("mdauto5", "Buyer mdauto5");
        app.logIn("Enter as Buyer", "mdauto5", SeedData.DEMO_PASSWORD);
        app.press("Logout");

        app.dataChanged();

        assertEquals(List.of(), app.dialogs());
        assertTrue(app.shows("Choose your portal to continue"));
    }

    @Test
    void theTimerReportingAsTheBuyerLogsOut_promptsNothing() {
        data.buyer("mdauto3", "Buyer mdauto3");
        app.logIn("Enter as Buyer", "mdauto3", SeedData.DEMO_PASSWORD);

        app.dataChangedAsUserLogsOut();

        assertEquals(List.of(), app.dialogs());
        assertTrue(app.shows("Choose your portal to continue"));
    }

    @Test
    void theTimerReportingAfterTheAccountWasDeactivatedWhileLoggedIn_promptsNothing() {
        data.buyer("mdauto4", "Buyer mdauto4");
        app.logIn("Enter as Buyer", "mdauto4", SeedData.DEMO_PASSWORD);
        data.deactivate("mdauto4");

        app.dataChanged();

        assertEquals(List.of(DEACTIVATED), app.dialogs()); // the open Browse screen cannot reload
    }
}
