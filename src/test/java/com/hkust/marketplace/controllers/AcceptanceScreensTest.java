package com.hkust.marketplace.controllers;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.services.BorrowingDeadlineService;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import com.hkust.marketplace.ui.Formats;
import com.hkust.marketplace.ui.NotificationDot;
import com.hkust.marketplace.ui.Scrolling;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance scenarios of {@code docs/acceptance-tests.md} that can be judged on the screens,
 * run on the real screens off screen (system and acceptance testing, Lecture 14). Each test is
 * named after the scenarios it runs, follows their steps and checks their expected results. The
 * screen tests of a run share one store, so each test makes its own accounts and listings, named
 * after the scenario, where the manual steps use the demo data. What needs a person, such as a
 * restart of the app or real waiting, stays in the manual checklist.
 */
class AcceptanceScreensTest {

    private static final String KEYWORD = "Search by title or keyword";
    private static final String PRICE = "such as 150 or 99.50";
    private static final String DATE = "such as 21 Nov 2026";
    private static final String TIME = "HH:mm or HH:mm:ss";
    private static final String COMMENT = "How did the deal go? A written review is required.";
    private static final String DETAILS = "What is wrong with this listing?";
    private static final String COMPOSER = "Write a message. Enter sends it; Shift+Enter starts a new line.";
    private static final String DOT = "#" + NotificationDot.ID;

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void open() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
    }

    @AfterEach
    void reset() {
        app.reset();
    }

    // ---------- Helpers ----------

    private static void logInAs(String portal, String username) {
        app.logIn(portal, username, SeedData.DEMO_PASSWORD);
    }

    /** As the buyer logged in, finds the listing on Browse and opens it. */
    private static void openListing(String title) {
        app.press("Browse Marketplace");
        app.type(KEYWORD, title);
        app.press("Search");
        app.press("View Details", title);
    }

    private static String day(int daysFromToday) {
        return Formats.datePickerConverter().toString(LocalDate.now().plusDays(daysFromToday));
    }

    /** The number on the dot; 0 when it is hidden. */
    private static int dot() {
        String shown = app.text(DOT);
        return shown.isEmpty() ? 0 : Integer.parseInt(shown);
    }

    /** How many of the user's notifications contain this text. */
    private static long notices(String username, String text) {
        return app.store().getNotificationsFor(username).stream()
                .filter(notification -> notification.getContent().contains(text))
                .count();
    }

    /** A sale the buyer completed and reviewed with five stars (UC10). */
    private static void reviewedSale(String seller, String buyer, String title, String comment) {
        Offer deal = data.priceOffer(buyer, data.sale(seller, title, 50), 45);
        data.accept(deal);
        data.complete(deal);
        app.store().addReview(new Review(deal.getId(), 5, comment));
    }

    /** A loan the seller accepted. */
    private static BorrowRequest acceptedLoan(LendListing listing, String buyer, LocalDateTime start, LocalDateTime end) {
        BorrowRequest loan = data.borrowRequest(buyer, listing, start, end);
        data.accept(loan);
        return loan;
    }

    // ---------- UC8 Browse Marketplace ----------

    @Test
    void atUC8_04_05_aGiveawayCountsAsFree_andALoanHasNoPriceToSortBy() {
        data.seller("at804seller", "At804 Seller");
        data.giveaway("at804seller", "AT804 Lamp");
        data.sale("at804seller", "AT804 Book", 180);
        data.sale("at804seller", "AT804 Charger", 250);
        data.lend("at804seller", "AT804 Calculator", Duration.ofDays(90));
        data.buyer("at804buyer", "At804 Buyer");
        logInAs("Enter as Buyer", "at804buyer");

        app.type(KEYWORD, "AT804");
        app.type("Max", "200");
        app.choose("Price: low to high");
        app.press("Search");
        assertEquals(List.of("AT804 Lamp", "AT804 Book"), app.cardTitles()); // UC8-04

        app.press("Reset");
        app.type(KEYWORD, "AT804");
        app.press("Search");
        app.choose("Price: high to low");
        assertEquals(List.of("AT804 Charger", "AT804 Book", "AT804 Lamp", "AT804 Calculator"), app.cardTitles()); // UC8-05
    }

    @Test
    void atUC8_08_theDetailShowsEverything_andEachOpeningCountsOneView() {
        data.seller("at88seller", "At88 Seller");
        data.buyer("at88buyer", "At88 Buyer");
        reviewedSale("at88seller", "at88buyer", "AT88 Old Fan", "Great fan, friendly seller.");
        SaleListing charger = data.sale("at88seller", "AT88 Charger", 250);
        logInAs("Enter as Buyer", "at88buyer");
        int before = charger.getViewCount();

        openListing("AT88 Charger");
        assertEquals(before + 1, charger.getViewCount());
        for (String shown : List.of("No photo", "Electronics", "Used", "Library", "Views", "Test item: AT88 Charger",
                "At88 Seller", "Seller rating: 5.0 / 5 (1 rating)", "Great fan, friendly seller.",
                "Make Offer", "Chat with Seller", "Report Listing")) {
            assertTrue(app.shows(shown), shown);
        }
        app.press("← Back to listings");
        app.press("View Details", "AT88 Charger");
        assertEquals(before + 2, charger.getViewCount());
    }

    @Test
    void atUC8_12_aReviewTheAdminHid_isShownNeitherOnTheListingNorOnTheProfile() {
        data.seller("at812seller", "At812 Seller");
        data.buyer("at812buyer", "At812 Buyer");
        reviewedSale("at812seller", "at812buyer", "AT812 Old Desk", "Rude and late.");
        data.sale("at812seller", "AT812 Charger", 250);
        app.answer("Hide review", form -> form.type("Reason:", "Offensive").press("OK"));
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("Seller Ratings");
        app.press("View Reviews", "At812 Seller");
        app.press("Hide", "Rude and late.");
        app.reset();

        logInAs("Enter as Buyer", "at812buyer");
        openListing("AT812 Charger");
        assertTrue(app.shows("Seller rating: No ratings yet"));
        assertFalse(app.shows("Rude and late."));
        app.press("At812 Seller");
        assertTrue(app.shows("Seller rating: No ratings yet"));
        assertFalse(app.shows("Rude and late."));
    }

    // ---------- UC5 Make Offer ----------

    @Test
    void atUC5_04_makeOfferInTheChatOfAReservedListing_isRefused_andAddsNothing() {
        data.seller("at54seller", "At54 Seller");
        data.buyer("at54buyer", "At54 Buyer");
        data.buyer("at54other", "At54 Other");
        SaleListing book = data.sale("at54seller", "AT54 Textbook", 180);
        data.message("at54buyer", book, "at54buyer", "Is it still available?");
        data.accept(data.priceOffer("at54other", book, 170)); // Reserved for another buyer
        logInAs("Enter as Buyer", "at54buyer");
        app.press("Chats");
        app.press("Open Chat", "AT54 Textbook");

        app.press("Make Offer");

        assertEquals(List.of("Not possible: This listing is no longer available."), app.dialogs());
        assertTrue(app.store().getOffersForListing(book.getId()).stream()
                .noneMatch(offer -> offer.getBuyerUsername().equals("at54buyer")));
    }

    @Test
    void atUC5_06_08_theSmallestPrice_andTheShortestPeriod_areAccepted() {
        data.seller("at56seller", "At56 Seller");
        data.sale("at56seller", "AT56 Charger", 250);
        data.lend("at56seller", "AT56 Calculator", Duration.ofDays(90));
        data.buyer("at56buyer", "At56 Buyer");
        app.answer("Make Offer", form -> form.type(PRICE, "0.01").press("Submit"));
        app.answer("Request Borrow", form -> form.type(DATE, 0, day(1)).type(TIME, 0, "14:30:00")
                .type(DATE, 1, day(1)).type(TIME, 1, "14:30:05").press("Submit"));
        logInAs("Enter as Buyer", "at56buyer");

        openListing("AT56 Charger");
        app.press("Make Offer");
        openListing("AT56 Calculator");
        app.press("Request Borrow");

        List<String> dialogs = app.dialogs();
        assertEquals(4, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(1).contains("was submitted: HK$ 0.01."), dialogs::toString); // UC5-06
        assertTrue(dialogs.get(3).startsWith("Submitted: "), dialogs::toString);              // UC5-08
    }

    @Test
    void atUC5_14_theBorrowFormsDates_readAsEverywhere_aTypedDateCounts_andAnotherFormatIsRefused() {
        data.seller("at514seller", "At514 Seller");
        LendListing calculator = data.lend("at514seller", "AT514 Calculator", Duration.ofDays(90));
        data.lend("at514seller", "AT514 Tripod", Duration.ofDays(90));
        data.buyer("at514buyer", "At514 Buyer");
        List<String> suggested = new ArrayList<>();
        app.answer("Request Borrow", form -> {
            suggested.add(form.valueOf(DATE));
            form.type(DATE, 0, day(3)).type(DATE, 1, day(4)).press("Submit"); // (b) typed, Enter never pressed
        });
        app.answer("Request Borrow", form -> form.type(DATE, 0, "11/25/2026").press("Submit")); // (c)
        app.answer("Request Borrow", form -> {
            suggested.add(form.valueOf(DATE));
            form.press("Cancel");
        });
        logInAs("Enter as Buyer", "at514buyer");

        openListing("AT514 Calculator");
        app.press("Request Borrow");
        openListing("AT514 Tripod");
        app.press("Request Borrow");

        assertTrue(suggested.get(0).matches("\\d{1,2} [A-Z][a-z]{2} \\d{4}"), suggested::toString); // (a) like "21 Nov 2026"
        BorrowRequest request = (BorrowRequest) app.store().getOffersForListing(calculator.getId()).get(0);
        assertEquals(LocalDate.now().plusDays(3), request.getProposedStartDate().toLocalDate()); // (b)
        List<String> dialogs = app.dialogs();
        assertEquals("Not possible: Please enter both the start and the end of the borrow period, each with a date and a time.",
                dialogs.get(3), dialogs::toString);                                               // (c)
        assertEquals("", suggested.get(1)); // the form comes back with the start date empty, and its hint
    }

    // ---------- Chat deal actions (assumptions register 71) ----------

    @Test
    void atCHAT_02_05_and_UC5_12_agreeingToACounterFromTheChat() {
        data.seller("atc2seller", "Atc2 Seller");
        data.buyer("atc2buyer", "Atc2 Buyer");
        SaleListing speaker = data.sale("atc2seller", "ATC2 Speaker", 300);
        PriceOffer first = data.priceOffer("atc2buyer", speaker, 220);
        data.counter(first, 260);
        logInAs("Enter as Buyer", "atc2buyer");
        app.press("Chats");
        app.press("Open Chat", "ATC2 Speaker");
        assertTrue(app.shows("Make Offer"));                                  // CHAT-02
        assertTrue(app.shows("Agree to Counter (1)"));
        assertTrue(app.shows("Seller's counter: " + Formats.price(260)));

        List<List<String>> buttons = new ArrayList<>();
        app.answer("Agree to Counter", form -> {                              // CHAT-03
            assertTrue(form.shows("Agree to a counter from Atc2 Seller"));
            assertTrue(form.shows(Formats.price(220) + " → " + Formats.price(260)));
            buttons.add(form.buttons());
            form.press("Agree");
        });
        app.answer("Make Offer", form -> {                                    // CHAT-04
            assertEquals("260", form.valueOf(PRICE));
            form.press("Cancel");
        });
        app.answer("Agree to Counter", form -> form.press("Cancel"));
        app.press("Agree to Counter (1)");
        assertEquals(List.of(List.of("Agree", "Cancel")), buttons);
        assertEquals(3, app.dialogs().size(), app.dialogs()::toString);
        assertEquals(1, app.store().getOffersForListing(speaker.getId()).size()); // nothing was submitted

        app.answer("Agree to Counter", form -> form.press("Agree"));          // CHAT-05, UC5-12
        app.answer("Make Offer", form -> form.press("Submit"));
        app.press("Agree to Counter (1)");
        assertTrue(app.dialogs().get(5).contains("was submitted: " + Formats.price(260)), app.dialogs()::toString);
        assertEquals(2, app.store().getOffersForListing(speaker.getId()).size());
        assertEquals(OfferStatus.COUNTERED, first.getStatus());
        assertTrue(app.shows("Pending"));
        assertTrue(app.shows("Agree to Counter (1)"));
    }

    @Test
    void atCHAT_06_09_theSellersChatLinksToTheOffers_whereAcceptingOneEndsTheOthers() {
        data.seller("atc6seller", "Atc6 Seller");
        data.buyer("atc6buyer", "Atc6 Buyer");
        SaleListing bike = data.sale("atc6seller", "ATC6 Bike", 300);
        data.priceOffer("atc6buyer", bike, 250);
        PriceOffer newest = data.priceOffer("atc6buyer", bike, 270);
        app.answer("Accept price offer", form -> form.press("OK"));
        logInAs("Enter as Seller", "atc6seller");
        app.press("Chats");
        app.press("Open Chat", "ATC6 Bike");
        assertTrue(app.shows("Respond in Offers & Requests (2)"));            // CHAT-06
        for (String action : List.of("Accept", "Decline", "Counter")) {
            assertFalse(app.shows(action), action);
        }

        app.press("Respond in Offers & Requests (2)");
        app.press("Accept", Formats.price(270));                              // CHAT-09
        assertTrue(app.dialogs().contains("Offer accepted: The buyer has been notified."), app.dialogs()::toString);
        assertEquals(List.of(), app.styled(Scrolling.JUMP_HIGHLIGHT));        // the list again, unmarked
        app.press("Chats");
        app.press("Open Chat", "ATC6 Bike");
        assertTrue(app.shows("Accepted"));
        assertTrue(app.shows("Declined"));
        assertFalse(app.shows("Respond in Offers & Requests"));
        assertEquals(OfferStatus.ACCEPTED, newest.getStatus());
    }

    @Test
    void atCHAT_07_08_10_theLinkMarksTheChatsOpenOffers_inView_untilThePageIsLeft() {
        data.seller("atc7seller", "Atc7 Seller");
        data.buyer("atc7buyer", "Atc7 Buyer");
        data.buyer("atc7other", "Atc7 Other");
        SaleListing desk = data.sale("atc7seller", "ATC7 Desk", 300);
        data.counter(data.priceOffer("atc7buyer", desk, 200), 260);           // Countered
        data.priceOffer("atc7buyer", desk, 250);                              // Pending, the newest of hers
        data.priceOffer("atc7other", desk, 240);                              // another chat's offer
        logInAs("Enter as Seller", "atc7seller");
        app.press("Chats");
        app.press("Open Chat", "Atc7 Buyer");

        app.press("Respond in Offers & Requests (2)");

        assertEquals("Offers & Requests", app.activePage());                  // CHAT-07
        assertTrue(app.inView("ATC7 Desk"));
        List<String> marked = app.styled(Scrolling.JUMP_HIGHLIGHT);           // the chat search's yellow
        assertEquals(2, marked.size(), marked::toString);                     // CHAT-10: both of hers
        assertTrue(marked.stream().noneMatch(row -> row.contains("Atc7 Other")), marked::toString);
        assertTrue(app.inView(Formats.price(250)));                           // the newest in view
        assertFalse(app.canPress("Accept", Formats.price(200)));              // Countered: only Decline
        assertFalse(app.canPress("Counter", Formats.price(200)));
        assertTrue(app.canPress("Decline", Formats.price(200)));
        for (String action : List.of("Accept", "Decline", "Counter")) {       // Pending: all three
            assertTrue(app.canPress(action, Formats.price(250)), action);
        }

        data.waitUntilAfter(app.store().now().plusSeconds(3));               // CHAT-08
        assertEquals(2, app.styled(Scrolling.JUMP_HIGHLIGHT).size());
        app.press("My Listings");
        app.press("Offers & Requests");
        assertEquals(List.of(), app.styled(Scrolling.JUMP_HIGHLIGHT));
    }

    @Test
    void atCHAT_11_aLoanAndAGiveaway_haveTheSameLink_andOnlyTheLoanCanBeCountered() {
        data.seller("atc11seller", "Atc11 Seller");
        data.buyer("atc11buyer", "Atc11 Buyer");
        LendListing calculator = data.lend("atc11seller", "ATC11 Calculator", Duration.ofDays(90));
        GiveawayListing lamp = data.giveaway("atc11seller", "ATC11 Lamp");
        LocalDateTime start = app.store().now().plusDays(1);
        data.borrowRequest("atc11buyer", calculator, start, start.plusDays(2));
        data.claim("atc11buyer", lamp);
        List<Boolean> asksForDates = new ArrayList<>();
        app.answer("Counter borrow request", form -> {
            asksForDates.add(form.shows("Start") && form.shows("End"));
            form.press("Cancel");
        });
        logInAs("Enter as Seller", "atc11seller");

        app.press("Chats");
        app.press("Open Chat", "ATC11 Calculator");
        app.press("Respond in Offers & Requests (1)");
        assertEquals(1, app.styled(Scrolling.JUMP_HIGHLIGHT).size());
        app.press("Counter", "Borrow request");
        assertEquals(List.of(true), asksForDates);

        app.press("Chats");
        app.press("Open Chat", "ATC11 Lamp");
        app.press("Respond in Offers & Requests (1)");
        List<String> marked = app.styled(Scrolling.JUMP_HIGHLIGHT);
        assertEquals(1, marked.size(), marked::toString);
        assertTrue(marked.get(0).contains("Claim"), marked::toString);
        assertFalse(marked.get(0).contains("Counter"), marked::toString);     // a claim has Accept and Decline only
    }

    // ---------- UC7 Manage My Deals ----------

    @Test
    void atUC7_06_aSoldListing_opensFromMyDeals_withoutMakeOffer_andEachOpeningCountsOneView() {
        data.seller("at76seller", "At76 Seller");
        data.buyer("at76buyer", "At76 Buyer");
        SaleListing book = data.sale("at76seller", "AT76 Textbook", 180);
        Offer deal = data.priceOffer("at76buyer", book, 160);
        data.accept(deal);
        data.complete(deal);
        logInAs("Enter as Buyer", "at76buyer");
        app.press("My Deals");
        int before = book.getViewCount();

        app.press("View Listing", "AT76 Textbook");
        assertEquals("AT76 Textbook", app.pageTitle());
        assertFalse(app.canPress("Make Offer"));
        assertEquals(before + 1, book.getViewCount());
        app.press("← Back to My Deals");
        assertEquals("My Deals", app.pageTitle());
        app.press("View Listing", "AT76 Textbook");
        assertEquals(before + 2, book.getViewCount());
    }

    @Test
    void atUC7_10_UC10_07_returningALoan_tellsTheSeller_freesTheListing_andOffersTheReview() {
        data.seller("at710seller", "At710 Seller");
        data.buyer("at710buyer", "At710 Buyer");
        LendListing calculator = data.lend("at710seller", "AT710 Calculator", Duration.ofDays(90));
        acceptedLoan(calculator, "at710buyer", app.store().now().minusHours(1), app.store().now().plusDays(3));
        app.answer("Return Item", form -> form.press("OK"));
        app.answer("Item returned", form -> form.press("OK"));                // UC10-07: OK opens the form
        app.answer("Review Seller", form -> form.press("Skip"));
        logInAs("Enter as Buyer", "at710buyer");
        app.press("My Deals");
        assertTrue(app.shows("Accepted"));
        assertTrue(app.shows("Due "));

        app.press("Return Item", "AT710 Calculator");

        List<String> dialogs = app.dialogs();
        assertEquals("Item returned: 'AT710 Calculator' was returned, and At710 Seller has been notified. "
                + "Would you like to rate and review the seller now?", dialogs.get(1), dialogs::toString);
        assertTrue(dialogs.get(2).startsWith("Review Seller: "), dialogs::toString);
        assertTrue(app.shows("Returned"));
        assertTrue(app.shows(Formats.date(app.store().now())));
        assertEquals(1, notices("at710seller", "At710 Buyer returned 'AT710 Calculator'."));
        assertEquals(ListingStatus.LIVE, calculator.getStatus());
        openListing("AT710 Calculator");
        assertTrue(app.shows("Available"));
    }

    @Test
    void atUC7_12_agreeingToACounterInMyDeals_startsFromTheCounter_andListsTheNewOfferAboveIt() {
        data.seller("at712seller", "At712 Seller");
        data.buyer("at712buyer", "At712 Buyer");
        PriceOffer offer = data.priceOffer("at712buyer", data.sale("at712seller", "AT712 Charger", 250), 200);
        data.counter(offer, 230);
        List<String> startsWith = new ArrayList<>();
        app.answer("Make Offer", form -> {
            startsWith.add(form.valueOf(PRICE));
            form.press("Submit");
        });
        logInAs("Enter as Buyer", "at712buyer");
        app.press("My Deals");
        assertTrue(app.shows("Seller's counter: " + Formats.price(230)));

        app.press("Agree to Counter", "AT712 Charger");

        assertEquals(List.of("230"), startsWith);
        List<String> deals = app.styled("card").stream().filter(card -> card.contains("AT712 Charger")).toList();
        assertEquals(2, deals.size(), deals::toString);
        assertTrue(deals.get(0).contains("Pending"), deals::toString);   // the new offer, above
        assertTrue(deals.get(1).contains("Countered"), deals::toString);
        assertEquals(OfferStatus.COUNTERED, offer.getStatus());
    }

    // ---------- UC12 Process Borrowing Deadlines, with its effects on UC7, UC10 and the dot ----------

    @Test
    void atUC12_01_05_UC7_11_UC10_08_UC4_07_theTimerRemindsOnce_thenReturnsTheItemByItself() {
        data.seller("at12seller", "At12 Seller");
        data.buyer("at12buyer", "At12 Buyer");
        LendListing cable = data.lend("at12seller", "AT12 HDMI Cable", Duration.ofMinutes(5));
        LocalDateTime end = app.store().now().plusSeconds(4);
        acceptedLoan(cable, "at12buyer", app.store().now().minusSeconds(20), end); // past halfway: the reminder is due
        BorrowingDeadlineService timer = new BorrowingDeadlineService(app.store());
        logInAs("Enter as Buyer", "at12buyer");
        app.press("Notifications");
        int before = dot();

        timer.process();
        app.dataChanged();
        assertTrue(app.shows("Reminder: 'AT12 HDMI Cable' is due on"));        // UC12-01, without reopening the board
        assertEquals(before + 1, dot());                                       // UC4-07
        timer.process();
        app.dataChanged();
        app.press("Notifications");
        assertEquals(1, notices("at12buyer", "Reminder: 'AT12 HDMI Cable'"));   // UC12-02

        app.press("My Deals");
        app.answer("Item returned automatically", form -> form.press("OK"));    // UC10-08: OK opens the form
        app.answer("Review Seller", form -> form.press("Skip"));
        data.waitUntilAfter(end);
        timer.process();
        app.dataChanged();

        List<String> dialogs = app.dialogs();
        assertEquals("Item returned automatically: 'AT12 HDMI Cable' was returned automatically because the borrowing "
                + "period ended. Would you like to rate and review At12 Seller now?", dialogs.get(0), dialogs::toString);
        assertTrue(dialogs.get(1).startsWith("Review Seller: "), dialogs::toString);
        assertTrue(app.shows("Auto-returned"));                               // UC12-03
        assertTrue(app.shows("Returned automatically"));
        assertFalse(app.shows("Return Item"));                                // UC7-11
        assertTrue(app.shows("Rate Seller"));
        assertEquals(1, notices("at12buyer", "'AT12 HDMI Cable' was returned automatically because the borrowing "
                + "period ended. You can rate the seller from My Deals."));   // UC12-04
        assertEquals(1, notices("at12seller", "'AT12 HDMI Cable', borrowed by At12 Buyer, was returned automatically"));
        openListing("AT12 HDMI Cable");                                       // UC12-05
        assertTrue(app.shows("Available"));
        app.answer("Request Borrow", form -> form.press("Cancel"));
        app.press("Request Borrow");
    }

    @Test
    void atUC12_07_aListingRemovedWhileLent_isReturnedByTheTimer_butStaysRemoved() {
        data.seller("at127seller", "At127 Seller");
        data.buyer("at127buyer", "At127 Buyer");
        LendListing cable = data.lend("at127seller", "AT127 Cable", Duration.ofMinutes(5));
        LocalDateTime end = app.store().now().plusSeconds(3);
        acceptedLoan(cable, "at127buyer", app.store().now().minusMinutes(1), end);
        app.answer("Remove listing", form -> form.press("OK"));
        app.answer("Remove listing", form -> form.type("Reason:", "Prohibited item").press("OK"));
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("All Listings");
        app.press("Remove", "AT127 Cable");
        app.reset();
        assertEquals(ListingStatus.REMOVED, cable.getStatus());

        data.waitUntilAfter(end);
        new BorrowingDeadlineService(app.store()).process();

        logInAs("Enter as Buyer", "at127buyer");
        app.press("My Deals");
        assertTrue(app.shows("Auto-returned"));
        assertEquals(1, notices("at127buyer", "'AT127 Cable' was returned automatically"));
        assertEquals(1, notices("at127seller", "'AT127 Cable', borrowed by At127 Buyer, was returned automatically"));
        assertEquals(ListingStatus.REMOVED, cable.getStatus());
        app.press("Browse Marketplace");
        app.type(KEYWORD, "AT127");
        app.press("Search");
        assertEquals(List.of(), app.cardTitles());
    }

    @Test
    void atUC12_08_returningJustAfterTheDueTime_leavesTheReturnToTheTimer_whichIsNotifiedOnce() {
        data.seller("at128seller", "At128 Seller");
        data.buyer("at128buyer", "At128 Buyer");
        LendListing sander = data.lend("at128seller", "AT128 Sander", Duration.ofMinutes(5));
        LocalDateTime end = app.store().now().plusSeconds(3);
        acceptedLoan(sander, "at128buyer", app.store().now().minusMinutes(1), end);
        app.answer("Return Item", form -> form.press("OK"));
        logInAs("Enter as Buyer", "at128buyer");
        app.press("My Deals");
        data.waitUntilAfter(end);

        app.press("Return Item", "AT128 Sander");
        new BorrowingDeadlineService(app.store()).process();

        assertEquals("Not possible: The borrowing period has ended. The item is returned automatically.",
                app.dialogs().get(1), app.dialogs()::toString);
        assertEquals(0, notices("at128seller", "At128 Buyer returned 'AT128 Sander'."));
        assertEquals(1, notices("at128seller", "'AT128 Sander', borrowed by At128 Buyer, was returned automatically"));
    }

    @Test
    void atUC12_09_acceptingABorrowRequestWhosePeriodHasEnded_isRefused() {
        data.seller("at129seller", "At129 Seller");
        data.buyer("at129buyer", "At129 Buyer");
        LendListing calculator = data.lend("at129seller", "AT129 Calculator", Duration.ofDays(90));
        LocalDateTime start = app.store().now().minusSeconds(10);
        BorrowRequest late = data.borrowRequest("at129buyer", calculator, start, start.plusSeconds(5)); // ended 5 s ago
        app.answer("Accept borrow request", form -> form.press("OK"));
        logInAs("Enter as Seller", "at129seller");
        app.press("Offers & Requests");

        app.press("Accept", "AT129 Calculator");

        assertEquals("Not possible: The borrow period of this request has already ended. Decline it, or counter with "
                + "new dates.", app.dialogs().get(1), app.dialogs()::toString);
        assertEquals(OfferStatus.PENDING, late.getStatus());
    }

    // ---------- UC10 Review Seller ----------

    @Test
    void atUC10_10_twoLoansOfOneItem_areReviewedOnceEach() {
        data.seller("at1010seller", "At1010 Seller");
        data.buyer("at1010buyer", "At1010 Buyer");
        LendListing calculator = data.lend("at1010seller", "AT1010 Calculator", Duration.ofDays(90));
        BorrowRequest first = acceptedLoan(calculator, "at1010buyer", app.store().now().minusHours(2), app.store().now().plusDays(2));
        app.answer("Return Item", form -> form.press("OK"));
        app.answer("Item returned", form -> form.press("OK"));
        app.answer("Review Seller", form -> form.press("4 ★").type(COMMENT, "First loan, fine.").press("Submit Review"));
        logInAs("Enter as Buyer", "at1010buyer");
        app.press("My Deals");
        app.press("Return Item", "AT1010 Calculator");

        BorrowRequest second = acceptedLoan(calculator, "at1010buyer", app.store().now().minusHours(1), app.store().now().plusDays(2));
        app.answer("Return Item", form -> form.press("OK"));
        app.answer("Item returned", form -> form.press("OK"));
        app.answer("Review Seller", form -> form.press("5 ★").type(COMMENT, "Second loan, also fine.").press("Submit Review"));
        app.press("My Deals");
        app.press("Return Item", "AT1010 Calculator");

        List<Review> reviews = app.store().getReviewsForSeller("at1010seller");
        assertEquals(2, reviews.size(), reviews::toString);
        assertEquals(Set.of(first.getId(), second.getId()), Set.copyOf(reviews.stream().map(Review::getOfferId).toList()));
        assertFalse(app.shows("Rate Seller")); // each reviewed once, so neither offers it again
    }

    // ---------- UC11 View Deal History ----------

    @Test
    void atUC11_02_03_05_08_theHistory_listsEachDealOnce_newestFirst_andItsFiltersCombine() {
        data.seller("at11diana", "At11 Diana");
        data.seller("at11charlie", "At11 Charlie");
        data.buyer("at11buyer", "At11 Buyer");
        Offer hub = data.priceOffer("at11buyer", data.sale("at11diana", "AT11 USB Hub", 90), 80);
        data.accept(hub);
        data.complete(hub);
        LocalDateTime umbrellaEnd = app.store().now().plusSeconds(2);
        acceptedLoan(data.lend("at11charlie", "AT11 Umbrella", Duration.ofDays(1)), "at11buyer",
                app.store().now().minusMinutes(1), umbrellaEnd);
        data.waitUntilAfter(umbrellaEnd);
        new BorrowingDeadlineService(app.store()).process();
        String today = Formats.date(app.store().now());
        logInAs("Enter as Buyer", "at11buyer");
        app.press("Deal History");
        assertEquals(List.of(                                                  // UC11-02
                List.of("AT11 Umbrella", "At11 Charlie", today, "Borrowed", "Auto-returned"),
                List.of("AT11 USB Hub", "At11 Diana", today, "Bought", "Completed")), app.tableRows());

        data.accept(data.priceOffer("at11buyer", data.sale("at11charlie", "AT11 Desk Fan", 150), 140));   // UC11-03
        data.accept(data.claim("at11buyer", data.giveaway("at11diana", "AT11 Notebooks")));
        acceptedLoan(data.lend("at11diana", "AT11 Tripod", Duration.ofDays(3)), "at11buyer",
                app.store().now().minusHours(1), app.store().now().plusDays(2));
        app.answer("Mark Complete", form -> form.press("OK"));
        app.answer("Deal completed", form -> form.press("Cancel"));
        app.answer("Mark Complete", form -> form.press("OK"));
        app.answer("Deal completed", form -> form.press("Cancel"));
        app.answer("Return Item", form -> form.press("OK"));
        app.answer("Item returned", form -> form.press("Cancel"));
        app.press("My Deals");
        app.press("Mark Complete", "AT11 Desk Fan");
        app.press("Mark Complete", "AT11 Notebooks");
        app.press("Return Item", "AT11 Tripod");
        app.press("Deal History");
        List<List<String>> rows = app.tableRows();
        assertEquals(List.of(
                List.of("AT11 Tripod", "At11 Diana", today, "Borrowed", "Returned"),
                List.of("AT11 Notebooks", "At11 Diana", today, "Received", "Completed"),
                List.of("AT11 Desk Fan", "At11 Charlie", today, "Bought", "Completed")), rows.subList(0, 3));

        app.pick("From", LocalDate.now());                                    // UC11-05: a one-day range includes its day
        app.pick("To", LocalDate.now());
        assertEquals(5, app.tableRows().size());
        app.press("Reset");
        app.choose("Furniture");                                              // UC11-06
        assertEquals(List.of("AT11 Notebooks"), app.tableRows().stream().map(row -> row.get(0)).toList());
        app.choose("All categories");
        assertEquals(5, app.tableRows().size());
        app.type("Seller's name", "AT11 DIANA");                              // UC11-07: case does not matter
        app.pressKey("Seller's name", KeyCode.ENTER, false);
        assertEquals(List.of("AT11 Tripod", "AT11 Notebooks", "AT11 USB Hub"),
                app.tableRows().stream().map(row -> row.get(0)).toList());
        app.choose("Others");                                                 // UC11-08
        assertEquals(List.of(), app.tableRows());
        assertTrue(app.shows("No deals match. A deal appears here once it is completed."));
    }

    // ---------- UC9 Report Listing ----------

    @Test
    void atUC9_07_08_10_twoBuyersReportOneListing_onlyTheAdminLearnsOfIt_andASoldListingCanBeReported() {
        data.seller("at9seller", "At9 Seller");
        data.buyer("at9bob", "At9 Bob");
        data.buyer("at9alice", "At9 Alice");
        data.sale("at9seller", "AT9 Charger", 250);
        Offer sold = data.priceOffer("at9bob", data.sale("at9seller", "AT9 Chemistry Book", 120), 100);
        data.accept(sold);
        data.complete(sold);
        for (String buyer : List.of("at9bob", "at9alice")) {
            app.answer("Report Listing", form -> form.choose("Misleading description")
                    .type(DETAILS, "Says 61W but the label shows 30W.").press("Submit Report"));
            logInAs("Enter as Buyer", buyer);
            openListing("AT9 Charger");
            app.press("Report Listing");
            assertTrue(app.dialogs().get(1).startsWith("Report submitted: "), app.dialogs()::toString);
            app.reset();
        }
        assertEquals(2, notices("admin", "'AT9 Charger' by At9 Seller was reported"));   // UC9-07

        logInAs("Enter as Seller", "at9seller");                                       // UC9-08
        for (String page : app.visitEveryPage().keySet()) {
            app.press(page);
            assertFalse(app.shows("reported"), page);
            assertFalse(app.shows("At9 Alice") || app.shows("at9alice"), page);
        }
        assertEquals(0, notices("at9seller", "report"));
        app.reset();

        app.answer("Report Listing", form -> form.press("Cancel"));                    // UC9-10
        logInAs("Enter as Buyer", "at9bob");
        app.press("My Deals");
        app.press("View Listing", "AT9 Chemistry Book");
        app.press("Report Listing");
        assertTrue(app.dialogs().get(0).startsWith("Report Listing: Report 'AT9 Chemistry Book' to the admin"),
                app.dialogs()::toString);
    }

    // ---------- UC4 View Notifications ----------

    @Test
    void atUC4_03_06_aMessageRaisesTheDot_theFilterKeepsTheCount_andMarkAllClearsIt() {
        data.seller("at4seller", "At4 Seller");
        data.buyer("at4alice", "At4 Alice");
        SaleListing book = data.sale("at4seller", "AT4 Textbook", 180);
        data.accept(data.priceOffer("at4alice", book, 160));                  // her one unread: Offer Update
        data.message("at4alice", book, "at4alice", "When can we meet?");
        logInAs("Enter as Seller", "at4seller");
        app.press("Chats");
        app.press("Open Chat", "AT4 Textbook");
        app.type(COMPOSER, "The book is ready.");
        app.press("Send");
        app.reset();

        logInAs("Enter as Buyer", "at4alice");
        assertEquals(2, dot());                                               // UC4-03
        app.press("Notifications");
        List<String> board = app.styled("notif-item");
        assertTrue(board.get(0).contains("New Message"), board::toString);

        app.choose("New Message");                                            // UC4-04
        assertEquals(List.of("All categories", "Offer Update", "New Message"), app.choices("New Message"));
        assertEquals(1, app.styled("notif-item").size());
        assertTrue(app.shows("2 unread notifications"));

        app.press("Mark as read");                                            // UC4-05
        assertTrue(app.shows("1 unread notification"));
        assertEquals(1, dot());
        assertEquals("New Message", app.choices("New Message").get(2));       // the box still shows New Message
        assertFalse(app.shows("Mark as read"));

        app.choose("All categories");                                         // UC4-06
        app.press("Mark all as read");
        assertEquals(List.of(), app.dialogs());                               // no question asked
        assertTrue(app.shows("No unread notifications"));
        assertFalse(app.shows("Mark as read"));
        assertFalse(app.shows("Mark all as read"));
        assertEquals(0, dot());
        assertEquals(2, app.styled("notif-item").size());
    }

    @Test
    void atUC4_08_theSellersAndTheAdminsBoards_offerTheirOwnCategories_andClearTheDot() {
        data.seller("at48seller", "At48 Seller");
        data.notification("at48seller", NotificationCategory.OFFER_RECEIVED, "At48: a new offer.");
        data.notification("at48seller", NotificationCategory.REVIEW_RECEIVED, "At48: a new review.");
        logInAs("Enter as Seller", "at48seller");
        assertEquals(2, dot());
        app.press("Notifications");
        assertEquals(List.of("All categories", "Offer Received", "Review Received"), app.choices("All categories"));
        app.press("Mark all as read");
        assertEquals(0, dot());
        app.reset();

        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("Notifications");
        List<String> own = app.store().getNotificationsFor("admin").stream()
                .map(notification -> notification.getCategory())
                .distinct().sorted().map(NotificationCategory::getDisplayName).toList();
        List<String> offered = app.choices("All categories");
        assertEquals(own, offered.subList(1, offered.size()));
        if (dot() > 0) {
            app.press("Mark all as read");
        }
        assertEquals(0, dot());
        assertTrue(app.shows("No unread notifications"));
    }

    @Test
    void atUC4A_03_06_profileChanges_reachTheAdminsHistory_onlyWhenSomethingChanged() {
        data.buyer("at4abuyer", "At4a Buyer");
        data.seller("at4aseller", "At4a Seller");
        data.buyer("at4abob", "At4a Bob");
        int buyersBefore = app.store().getNotificationsFor("at4abuyer").size();
        logInAs("Enter as Buyer", "at4abuyer");                               // UC4A-03
        app.press("My Profile");
        app.type("Full name", "At4a Buyer Tai");
        app.type("Leave blank to keep your current password", "New#Pass2");
        app.type("Type the new password again", "New#Pass2");
        app.press("Save Changes");
        assertTrue(app.dialogs().get(0).startsWith("Profile updated: "), app.dialogs()::toString);
        assertEquals(buyersBefore, app.store().getNotificationsFor("at4abuyer").size());
        assertEquals(1, notices("admin", "At4a Buyer Tai (@at4abuyer) changed their profile: full name, password."));
        assertEquals(0, notices("admin", "New#Pass2"));
        app.reset();

        logInAs("Enter as Seller", "at4aseller");                             // UC4A-04
        app.press("My Profile");
        app.type("Bio (optional)", "Selling course books.");
        app.press("Library");
        app.press("Save Changes");
        assertEquals(1, notices("admin", "At4a Seller (@at4aseller) changed their profile: bio, meet-up locations."));
        app.reset();

        logInAs("Enter as Buyer", "at4abob");                                 // UC4A-05
        app.press("My Profile");
        app.press("Save Changes");
        assertTrue(app.dialogs().get(0).startsWith("Profile updated: "), app.dialogs()::toString);
        assertEquals(0, notices("admin", "(@at4abob)"));
        app.reset();

        int adminsBefore = app.store().getNotificationsFor("admin").size();
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);        // UC4A-06
        app.press("My Profile");
        app.type("Full name", "HKUST Marketplace Admin Renamed");
        app.press("Save Changes");
        app.type("Full name", "HKUST Marketplace Admin");                     // and back, for the other tests
        app.press("Save Changes");
        assertEquals(adminsBefore, app.store().getNotificationsFor("admin").size());
    }

    // ---------- Admin: Manage Marketplace (Task 3.7) ----------

    @Test
    void atUC3A_04_theConditionChosenOnEdit_isShownOnView_andTheSellerIsTold() {
        data.seller("at3aseller", "At3a Seller");
        data.sale("at3aseller", "AT3A Charger", 250); // Used
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("All Listings");
        app.press("Edit", "AT3A Charger");
        assertEquals(List.of("New", "Like New", "Used", "For Parts"), app.choices("Used")); // the listing's own, chosen

        app.choose("Like New");
        app.press("Save");

        assertEquals(List.of("Listing updated: The seller has been notified."), app.dialogs());
        app.press("View", "AT3A Charger");
        assertTrue(app.shows("Like New"));
        assertEquals(1, notices("at3aseller", "An administrator edited your listing 'AT3A Charger'."));
    }
}
