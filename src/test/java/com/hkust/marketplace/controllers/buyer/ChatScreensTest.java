package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of UC6 Chat, as the buyer, through the real screens: the list of chats, writing
 * (A1), searching (A2), and agreeing to a counter from the chat. Tests named "...WhileLoggedIn"
 * or "...WhileTheFormIsOpen" change the data between two of the buyer's steps, as the admin or
 * the seller would (technical requirements: access control at the API layer, concurrent actions).
 */
class ChatScreensTest {

    private static final String COMPOSER = "Write a message. Enter sends it; Shift+Enter starts a new line.";
    private static final String SEARCH = "Search this chat";
    private static final String DEACTIVATED = "Not possible: This account has been deactivated. Please contact the admin.";

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void openTheApp() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
        data.seller("chseller", "Cher Seller");
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    /** A new buyer who has a chat about a new listing, with the given messages from the buyer and the seller in turn. */
    private static SaleListing buyerWithChat(String buyer, String title, String... messages) {
        data.buyer(buyer, "Buyer " + buyer);
        SaleListing listing = data.sale("chseller", title, 100);
        for (int i = 0; i < messages.length; i++) {
            data.message(buyer, listing, i % 2 == 0 ? buyer : "chseller", messages[i]);
        }
        return listing;
    }

    private static void openChat(String buyer, String title) {
        app.logIn("Enter as Buyer", buyer, SeedData.DEMO_PASSWORD);
        app.press("Chats");
        app.press("Open Chat", title);
    }

    // ---------- The list of chats ----------

    @Test
    void chats_ofABuyerWhoHasNotWrittenYet_sayHowAChatStarts() {
        data.buyer("chnone", "Buyer chnone");
        app.logIn("Enter as Buyer", "chnone", SeedData.DEMO_PASSWORD);

        app.press("Chats");

        assertTrue(app.shows("You have no chats yet."));
    }

    @Test
    void clickingAChatInTheList_opensIt() {
        buyerWithChat("chclick", "Chat Click Guitar", "Is it still available?");
        app.logIn("Enter as Buyer", "chclick", SeedData.DEMO_PASSWORD);
        app.press("Chats");

        app.click("Chat Click Guitar");

        assertEquals("Chat: Chat Click Guitar", app.pageTitle());
        assertTrue(app.shows("Is it still available?"));
    }

    @Test
    void chats_ofAnAccountDeactivatedWhileLoggedIn_areNotShown() {
        data.buyer("chdeact", "Buyer chdeact");
        app.logIn("Enter as Buyer", "chdeact", SeedData.DEMO_PASSWORD);
        data.deactivate("chdeact");

        app.press("Chats");

        assertEquals(List.of(DEACTIVATED), app.dialogs());
    }

    // ---------- Writing (steps 3-6, A1) ----------

    @Test
    void writing_sendsWithTheButtonOrEnter_shiftEnterStartsANewLine_andABlankMessageIsRefused() {
        buyerWithChat("chwrite", "Chat Write Desk", "Hello!");
        openChat("chwrite", "Chat Write Desk");

        app.type(COMPOSER, "Can we meet tomorrow?");
        app.press("Send");
        assertTrue(app.shows("Can we meet tomorrow?"));
        assertEquals("", app.valueOf(COMPOSER));

        app.press("Send"); // A1: nothing written
        assertEquals(List.of("Not possible: Please write a message before sending."), app.dialogs());

        app.type(COMPOSER, "At the Library");
        app.pressKey(COMPOSER, KeyCode.ENTER, true);
        assertTrue(app.valueOf(COMPOSER).contains("\n"), "Shift+Enter adds a line, and does not send");
        app.pressKey(COMPOSER, KeyCode.ESCAPE, false); // any other key is typed as usual
        app.pressKey(COMPOSER, KeyCode.ENTER, false);
        assertTrue(app.shows("At the Library"));
        assertEquals("", app.valueOf(COMPOSER));
    }

    // ---------- Searching (A2) ----------

    @Test
    void search_listsTheMatches_jumpsToEachChosenOne_andCloses() {
        buyerWithChat("chsearch", "Chat Search Bike",
                "Meet at the Library at noon?", "Library works, see you there.", "Bring the lock too.");
        openChat("chsearch", "Chat Search Bike");

        app.type(SEARCH, "library");
        app.press("Search");
        assertTrue(app.shows("2 messages contain 'library'."));

        app.click("Meet at the Library"); // the first result: the chat jumps to that message
        app.click("Library works");       // the second: the highlight moves to it
        app.press("Close search");        // and is taken away
        assertFalse(app.shows("messages contain"));

        app.press("Search");
        app.click("Meet at the Library");
        app.dataChanged(); // new data while looking at an older message: the chat reloads
        assertTrue(app.shows("Bring the lock too."));

        app.type(SEARCH, "zebra");
        app.press("Search");
        assertTrue(app.shows("No messages contain 'zebra'."));
        app.type(COMPOSER, "Thanks!");
        app.press("Send"); // sending closes the search
        assertFalse(app.shows("No messages contain"));

        app.type(SEARCH, " ");
        app.press("Search");
        assertEquals(List.of("Not possible: Please enter a keyword to search for."), app.dialogs());
    }

    @Test
    void chat_ofAnAccountDeactivatedWhileLoggedIn_cannotReload() {
        buyerWithChat("chreload", "Chat Reload Fan", "Hi");
        openChat("chreload", "Chat Reload Fan");
        data.deactivate("chreload");

        app.dataChanged(); // the timer's change: the open chat reloads, which the account may no longer do

        assertEquals(List.of(DEACTIVATED), app.dialogs());
    }

    // ---------- Agree to Counter in the chat: the cases where the choice ends early ----------

    @Test
    void agreeToCounter_whenTheSellerDeclinedItWhileTheFormIsOpen_endsTheChoice() {
        SaleListing desk = buyerWithChat("chcounter1", "Chat Counter Desk", "Would you take 70?");
        PriceOffer offer = data.priceOffer("chcounter1", desk, 70);
        data.counter(offer, 85);
        app.answer("Agree to Counter", form -> form.press("Agree"));
        app.answer("Make Offer", form -> {
            data.decline(offer);
            form.press("Cancel");
        });
        openChat("chcounter1", "Chat Counter Desk");

        app.press("Agree to Counter (1)");

        assertEquals(2, app.dialogs().size(), app.dialogs()::toString); // no countered offer is left to list again
        assertFalse(app.shows("Agree to Counter"));
    }

    @Test
    void agreeToCounter_whenTheAccountIsDeactivatedWhileTheFormIsOpen_endsTheChoice() {
        SaleListing chair = buyerWithChat("chcounter2", "Chat Counter Chair", "Would you take 40?");
        data.counter(data.priceOffer("chcounter2", chair, 40), 55);
        app.answer("Agree to Counter", form -> form.press("Agree"));
        app.answer("Make Offer", form -> {
            data.deactivate("chcounter2");
            form.press("Cancel");
        });
        openChat("chcounter2", "Chat Counter Chair");

        app.press("Agree to Counter (1)");

        List<String> dialogs = app.dialogs(); // the chat cannot reload, so the list does not open again
        assertEquals(List.of(DEACTIVATED), dialogs.subList(2, dialogs.size()));
    }
}
