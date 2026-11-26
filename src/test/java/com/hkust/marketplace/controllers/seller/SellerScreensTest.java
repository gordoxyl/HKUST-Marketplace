package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import com.hkust.marketplace.ui.NotificationDot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The seller's screens, through the real screens off screen. */
class SellerScreensTest {

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

    /** The number on the dot; 0 when it is hidden. */
    private static int dot() {
        String shown = app.text(DOT);
        return shown.isEmpty() ? 0 : Integer.parseInt(shown);
    }

    @Test
    void theDot_showsAtLogin_onEveryPage_andMarkAllAsReadRemovesIt() {
        data.seller("dotseller", "Dot Seller");
        data.notification("dotseller", NotificationCategory.ANNOUNCEMENT, "Welcome, seller.");
        app.logIn("Enter as Seller", "dotseller", SeedData.DEMO_PASSWORD);
        assertEquals(1, dot());
        app.press("Offers & Requests");
        assertEquals(1, dot());
        app.press("Notifications");
        app.press("Mark all as read");
        assertEquals(0, dot());
        app.press("My Listings");
        assertEquals(0, dot());
    }

    @Test
    void counterABorrowRequest_theEmptiedTimeBoxesShowTheirWholeHint() {
        data.seller("r23seller", "R23 Seller");
        data.buyer("r23buyer", "R23 Buyer");
        LendListing kayak = data.lend("r23seller", "R23 Kayak", Duration.ofDays(2));
        LocalDateTime start = app.store().now().plusDays(1);
        data.borrowRequest("r23buyer", kayak, start, start.plusDays(1));
        List<List<String>> cut = new ArrayList<>();
        app.answer("Counter borrow request", form -> {
            form.type("HH:mm or HH:mm:ss", 0, "").type("HH:mm or HH:mm:ss", 1, ""); // the hint shows once a time is cleared
            cut.add(form.cutPrompts());
            form.press("Cancel");
        });
        app.logIn("Enter as Seller", "r23seller", SeedData.DEMO_PASSWORD);
        app.press("Offers & Requests");

        app.press("Counter");

        assertEquals(List.of(List.of()), cut); // R23: "HH:mm or HH:mm:ss" in full
    }

    @Test
    void openChatOnAnOffer_opensTheChatScreen_withTheListBeside_andBack() {
        data.seller("chatseller", "Chat Seller");
        data.buyer("chatbuyer", "Chat Buyer");
        Listing lamp = data.sale("chatseller", "Chat Lamp", 80);
        data.priceOffer("chatbuyer", lamp, 70);
        data.message("chatbuyer", lamp, "chatbuyer", "Is it still available?");
        app.logIn("Enter as Seller", "chatseller", SeedData.DEMO_PASSWORD);
        app.press("Offers & Requests");
        app.press("Open Chat");

        assertTrue(app.shows("← Back to Offers & Requests"));
        assertTrue(app.shows("Is it still available?"));
        assertTrue(app.shows("Hide chats"), "the list of chats is beside the chat");
        assertEquals(List.of(), app.cutTexts());
        app.press("← Back to Offers & Requests");
        assertEquals("Offers & Requests", app.pageTitle());
    }

    @Test
    void r18_aListingPriceIsAPlainAmount_likeEveryOtherPrice() {
        data.seller("r18seller", "R18 Seller");
        SaleListing lamp = data.sale("r18seller", "R18 Lamp", 80);
        app.logIn("Enter as Seller", "r18seller", SeedData.DEMO_PASSWORD);
        app.press("Edit", "R18 Lamp");
        app.type("Price (HK$), required for Sell", "99.999");
        app.press("Save Changes");

        assertEquals(80, lamp.getPrice());             // refused: three decimals are not an amount
        assertEquals("Edit Listing", app.pageTitle()); // the form stays, as typed
        assertEquals(List.of("Not possible: The price must be a positive number, such as 180 or 99.50."), app.dialogs());
    }
}
