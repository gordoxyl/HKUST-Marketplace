package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import com.hkust.marketplace.ui.Formats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of UC5 Make Offer (Task 1.5) through the real screens: a price offer, a borrow
 * request and a claim, each with its alternative flows, from the listing, the chat and My Deals.
 * Tests named "...WhileThe..." change the data between two of the buyer's steps, as another
 * buyer's accepted offer would (technical requirement: concurrent actions).
 */
class MakeOfferScreensTest {

    private static final String PRICE = "such as 150 or 99.50";
    private static final String DATE = "such as 21 Nov 2026";
    private static final String TIME = "HH:mm or HH:mm:ss";
    private static final String UNAVAILABLE = "Not possible: This listing is no longer available.";

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void openTheApp() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
        data.seller("moseller", "Mo Seller");
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    /** A new buyer logs in and opens the listing from Browse. */
    private static void openAsNewBuyer(String buyer, String title) {
        data.buyer(buyer, "Buyer " + buyer);
        app.logIn("Enter as Buyer", buyer, SeedData.DEMO_PASSWORD);
        app.type("Search by title or keyword", title);
        app.press("Search");
        app.press("View Details", title);
    }

    private static String day(int daysFromToday) {
        return Formats.datePickerConverter().toString(LocalDate.now().plusDays(daysFromToday));
    }

    /** Fills the borrow form: start and end, each a day from today and a time of day. */
    private static void period(OffScreenApp.Form form, int startDay, String startTime, int endDay, String endTime) {
        form.type(DATE, 0, day(startDay)).type(TIME, 0, startTime).type(DATE, 1, day(endDay)).type(TIME, 1, endTime);
    }

    // ---------- A price offer (Sell) ----------

    @Test
    void priceOffer_aWrongPriceIsRefused_theFormComesBackWithIt_andTheCorrectedPriceIsSubmitted() {
        data.sale("moseller", "Offer Desk Chair", 80);
        app.answer("Make Offer", form -> {
            assertEquals("80", form.valueOf(PRICE)); // step 4: the asking price is suggested
            form.type(PRICE, "eighty").press("Submit");
        });
        app.answer("Make Offer", form -> {
            assertEquals("eighty", form.valueOf(PRICE)); // A2: back to {Enter Terms} with what was typed
            form.type(PRICE, "75").press("Submit");
        });
        openAsNewBuyer("mobuyer1", "Offer Desk Chair");

        app.press("Make Offer");

        List<String> dialogs = app.dialogs();
        assertEquals(4, dialogs.size(), dialogs::toString);
        assertEquals("Not possible: The price must be a positive amount, such as 150 or 99.50.", dialogs.get(1));
        assertTrue(dialogs.get(3).startsWith("Submitted: "), dialogs::toString);
        assertEquals("Offer Desk Chair", app.pageTitle()); // the listing is shown again
        app.press("My Deals");
        assertTrue(app.shows(Formats.price(75)));
    }

    // ---------- A borrow request (Lend) ----------

    @Test
    void borrowRequest_eachWrongPeriodIsRefused_untilACorrectOneIsSubmitted() {
        data.lend("moseller", "Offer Tripod", Duration.ofDays(2));
        app.answer("Request Borrow", form -> {
            assertFalse(form.valueOf(DATE).isEmpty()); // step 4: a period is suggested
            period(form, -1, "10:00", 1, "10:00");
            form.press("Submit");
        });
        app.answer("Request Borrow", form -> {
            assertEquals(day(-1), form.valueOf(DATE)); // A3: back to {Enter Terms} with what was entered
            period(form, 1, "10:00:00", 1, "10:00:02");
            form.press("Submit");
        });
        app.answer("Request Borrow", form -> {
            period(form, 1, "10:00", 2, "");
            form.press("Submit");
        });
        app.answer("Request Borrow", form -> {
            period(form, 1, "10:00", 2, "10:00");
            form.press("Submit");
        });
        openAsNewBuyer("mobuyer2", "Offer Tripod");

        app.press("Request Borrow");

        List<String> dialogs = app.dialogs();
        assertEquals(8, dialogs.size(), dialogs::toString);
        assertEquals("Not possible: The borrow period cannot start in the past.", dialogs.get(1));
        assertTrue(dialogs.get(3).startsWith("Not possible: The borrow period must end at least 5 seconds"), dialogs::toString);
        assertTrue(dialogs.get(5).startsWith("Not possible: Please enter both the start and the end"), dialogs::toString);
        assertTrue(dialogs.get(7).startsWith("Submitted: "), dialogs::toString);
    }

    @Test
    void borrowRequest_theEmptiedTimeBoxesShowTheirWholeHint() {
        data.lend("moseller", "Offer Hint Kayak", Duration.ofDays(2));
        List<List<String>> cut = new ArrayList<>();
        app.answer("Request Borrow", form -> {
            form.type(TIME, 0, "").type(TIME, 1, ""); // the hint shows once the suggested time is cleared
            cut.add(form.cutPrompts());
            form.press("Cancel");
        });
        openAsNewBuyer("mobuyer12", "Offer Hint Kayak");

        app.press("Request Borrow");

        assertEquals(List.of(List.of()), cut); // R23: "HH:mm or HH:mm:ss" in full
    }

    @Test
    void borrowRequest_cancelled_sendsNothing() {
        data.lend("moseller", "Offer Ladder", Duration.ofDays(1));
        openAsNewBuyer("mobuyer3", "Offer Ladder");

        app.press("Request Borrow"); // A4: the form is answered Cancel

        assertEquals(1, app.dialogs().size(), app.dialogs()::toString);
        app.press("My Deals");
        assertTrue(app.shows("You have no deals yet."));
    }

    // ---------- A claim (Giveaway) ----------

    @Test
    void claim_isSubmittedAtOnce_andTheListingThenShowsTheClaimPending() {
        data.giveaway("moseller", "Offer Free Shelf");
        openAsNewBuyer("mobuyer4", "Offer Free Shelf");

        app.press("Claim Item"); // step 3: a claim needs no input

        assertEquals(1, app.dialogs().size(), app.dialogs()::toString);
        assertTrue(app.dialogs().get(0).startsWith("Submitted: "), app.dialogs()::toString);
        assertEquals("Offer Free Shelf", app.pageTitle());
        assertTrue(app.shows("Claim Pending"));
    }

    // ---------- The listing is taken by another buyer (A1) ----------

    @Test
    void aGiveawayTakenWhileTheBuyerViewsIt_cannotBeClaimed_andIsShownAsReserved() {
        GiveawayListing vase = data.giveaway("moseller", "Offer Taken Vase");
        openAsNewBuyer("mobuyer5", "Offer Taken Vase");

        data.accept(data.claim("bob", vase));
        app.press("Claim Item");

        assertEquals(List.of(UNAVAILABLE), app.dialogs());
        assertEquals("Offer Taken Vase", app.pageTitle()); // a reserved listing stays on the marketplace
        assertTrue(app.shows("Reserved"));
    }

    @Test
    void aSaleTakenWhileTheOfferFormIsOpen_endsTheOffer_andNothingIsRecorded() {
        SaleListing lamp = data.sale("moseller", "Offer Taken Lamp", 50);
        app.answer("Make Offer", form -> {
            data.accept(data.priceOffer("bob", lamp, 60));
            form.type(PRICE, "45").press("Submit");
        });
        openAsNewBuyer("mobuyer6", "Offer Taken Lamp");

        app.press("Make Offer");

        List<String> dialogs = app.dialogs();
        assertEquals(List.of(UNAVAILABLE), dialogs.subList(1, dialogs.size()));
        assertTrue(app.shows("Reserved"));
        assertTrue(app.store().getOffersForListing(lamp.getId()).stream()
                .noneMatch(offer -> offer.getBuyerUsername().equals("mobuyer6")));
    }

    @Test
    void aLoanTakenWhileTheBorrowFormIsOpen_endsTheRequest() {
        LendListing drill = data.lend("moseller", "Offer Taken Drill", Duration.ofDays(2));
        app.answer("Request Borrow", form -> {
            LocalDateTime start = app.store().now().plusHours(1);
            data.accept(data.borrowRequest("bob", drill, start, start.plusDays(1)));
            form.press("Submit"); // the suggested period
        });
        openAsNewBuyer("mobuyer7", "Offer Taken Drill");

        app.press("Request Borrow");

        List<String> dialogs = app.dialogs();
        assertEquals(List.of(UNAVAILABLE), dialogs.subList(1, dialogs.size()));
        assertTrue(app.shows("Lent"));
    }

    // ---------- From the chat, and agreeing to a counter (step 1, Special Requirement 4) ----------

    @Test
    void makeOffer_fromTheChat_showsTheOfferInTheChat() {
        data.sale("moseller", "Offer Chat Speaker", 200);
        app.answer("Make Offer", form -> form.type(PRICE, "180").press("Submit"));
        openAsNewBuyer("mobuyer8", "Offer Chat Speaker");
        app.press("Chat with Seller");

        app.press("Make Offer");

        assertTrue(app.dialogs().get(1).startsWith("Submitted: "), app.dialogs()::toString);
        assertEquals("Chat: Offer Chat Speaker", app.pageTitle());
        assertTrue(app.shows(Formats.price(180)));
    }

    @Test
    void agreeToCounter_inMyDeals_startsTheFormWithTheCounterPrice_andSubmitsANewOffer() {
        SaleListing desk = data.sale("moseller", "Offer Counter Desk", 300);
        data.buyer("mobuyer9", "Buyer mobuyer9");
        data.counter(data.priceOffer("mobuyer9", desk, 250), 280);
        app.answer("Make Offer", form -> {
            assertEquals("280", form.valueOf(PRICE));
            form.press("Submit");
        });
        app.logIn("Enter as Buyer", "mobuyer9", SeedData.DEMO_PASSWORD);
        app.press("My Deals");

        app.press("Agree to Counter", "Offer Counter Desk");

        assertTrue(app.dialogs().get(1).startsWith("Submitted: "), app.dialogs()::toString);
        assertEquals(2, app.cardTitles().stream().filter("Offer Counter Desk"::equals).count()); // the countered and the new offer
    }

    @Test
    void agreeToCounter_inTheChat_listsTheCounters_andACancelledFormOpensTheListAgain() {
        SaleListing bike = data.sale("moseller", "Offer Counter Bike", 500);
        data.buyer("mobuyer10", "Buyer mobuyer10");
        data.counter(data.priceOffer("mobuyer10", bike, 400), 480);
        data.counter(data.priceOffer("mobuyer10", bike, 420), 460);
        app.answer("Agree to Counter", form -> form.clickBeside("→ " + Formats.price(480)).press("Agree")); // the older counter
        app.answer("Make Offer", form -> {
            assertEquals("480", form.valueOf(PRICE));
            form.press("Cancel");
        });
        app.logIn("Enter as Buyer", "mobuyer10", SeedData.DEMO_PASSWORD);
        app.press("Chats");
        app.press("Open Chat", "Offer Counter Bike");

        app.press("Agree to Counter (2)");

        List<String> dialogs = app.dialogs(); // the list, the form, then the list again (answered Cancel)
        assertEquals(3, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(0).startsWith("Agree to Counter: Agree to a counter from Mo Seller"), dialogs::toString);
        assertTrue(dialogs.get(0).endsWith(", newest first"), dialogs::toString);
        assertTrue(dialogs.get(2).startsWith("Agree to Counter: "), dialogs::toString);
    }

    @Test
    void agreeToCounter_inTheChat_submitted_endsTheChoice() {
        SaleListing board = data.sale("moseller", "Offer Counter Board", 90);
        data.buyer("mobuyer11", "Buyer mobuyer11");
        data.counter(data.priceOffer("mobuyer11", board, 70), 85);
        app.answer("Agree to Counter", form -> form.press("Agree")); // the newest is chosen already
        app.answer("Make Offer", form -> form.press("Submit"));
        app.logIn("Enter as Buyer", "mobuyer11", SeedData.DEMO_PASSWORD);
        app.press("Chats");
        app.press("Open Chat", "Offer Counter Board");

        app.press("Agree to Counter (1)");

        List<String> dialogs = app.dialogs();
        assertEquals(3, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(2).startsWith("Submitted: "), dialogs::toString);
        assertTrue(app.shows(Formats.price(85)));
    }
}
