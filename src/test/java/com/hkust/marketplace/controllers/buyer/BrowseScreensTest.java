package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of UC8 Browse Marketplace and the listing detail screen (Lecture 14), through
 * the real screens as Alice, a demo buyer. Tests named "...WhileTheBuyer..." change the data
 * between two of the buyer's steps, as another user would (technical requirements: concurrent
 * actions, and access control at the API layer).
 */
class BrowseScreensTest {

    private static final String KEYWORD = "Search by title or keyword";
    private static final String NOT_ON_MARKETPLACE = "Not possible: This listing is no longer on the marketplace.";
    private static final String DEACTIVATED = "Not possible: This account has been deactivated. Please contact the admin.";

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void openTheAppWithListings() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
        data.seller("brseller", "Bree Seller");
        data.sale("brseller", "Browse Sortable Lamp A", 30);
        data.sale("brseller", "Browse Sortable Lamp B", 90);
        data.lend("brseller", "Browse Sortable Lamp C", Duration.ofDays(3));
        data.giveaway("brseller", "Browse Sortable Lamp D");
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    private static void logInAsAlice() {
        app.logIn("Enter as Buyer", "alice", SeedData.DEMO_PASSWORD);
    }

    private static void search(String keyword) {
        app.type(KEYWORD, keyword);
        app.press("Search");
    }

    private static byte[] samplePhoto() {
        try (InputStream logo = BrowseScreensTest.class.getResourceAsStream("/images/hkust_logo.png")) {
            return logo.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("The sample photo could not be read", e);
        }
    }

    // ---------- Search, filter and sort (steps 2-4, A1, A2) ----------

    @Test
    void filtersAndSorting_narrowAndOrderTheListings_andResetShowsAllAgain() {
        logInAsAlice();
        search("Sortable Lamp");
        assertEquals(4, app.cardTitles().size(), app.cardTitles()::toString);

        app.choose("Sell");
        app.choose("Price: low to high");
        assertEquals(List.of("Browse Sortable Lamp A", "Browse Sortable Lamp B"), app.cardTitles());
        app.choose("Price: high to low");
        assertEquals(List.of("Browse Sortable Lamp B", "Browse Sortable Lamp A"), app.cardTitles());

        app.type("Min", "50");
        app.type("Max", "100");
        app.press("Search");
        assertEquals(List.of("Browse Sortable Lamp B"), app.cardTitles());

        app.choose("Used");
        app.choose("Furniture"); // no sale listing of furniture: A1
        assertTrue(app.shows("No listings match. Try another keyword or fewer filters."));
        assertTrue(app.cardTitles().isEmpty());

        app.press("Reset");
        assertEquals("", app.valueOf(KEYWORD));
        assertTrue(app.shows("IKEA Desk Lamp"));
        assertTrue(app.shows("Browse Sortable Lamp D"));
    }

    @Test
    void invalidPriceRange_isRefused_andTheResultsShownStay() {
        logInAsAlice();
        search("Sortable Lamp");

        app.type("Min", "100");
        app.type("Max", "10");
        app.press("Search"); // A2

        assertEquals(List.of("Not possible: The price range is invalid. "
                + "Enter amounts of HK$0 or more, with the minimum no greater than the maximum."), app.dialogs());
        assertEquals(4, app.cardTitles().size(), app.cardTitles()::toString);
    }

    @Test
    void aGiveawayWithAPendingClaim_isShownAsClaimPending() {
        data.claim("bob", data.giveaway("brseller", "Browse Claimed Stool"));
        logInAsAlice();

        app.type(KEYWORD, "Claimed Stool");
        app.pressKey(KEYWORD, KeyCode.ENTER, false); // Enter in the search box searches too

        assertEquals(List.of("Browse Claimed Stool"), app.cardTitles());
        assertTrue(app.shows("Claim Pending"));
    }

    // ---------- Opening a listing (steps 5-8, A3) ----------

    @Test
    void clickingACard_opensItsListing_butNotARightClick_norAClickOnItsButton() {
        data.sale("brseller", "Browse Click Kettle", 70);
        logInAsAlice();
        search("Click Kettle");

        app.click("Browse Click Kettle", MouseButton.SECONDARY);
        app.click("View Details"); // the button keeps its click, so the card does not also open
        assertEquals("Browse Marketplace", app.pageTitle());

        app.click("Browse Click Kettle");
        assertEquals("Browse Click Kettle", app.pageTitle());
    }

    @Test
    void photos_theThumbnailAndEveryPhotoAreShown_andADamagedOrUnreadablePhotoShowsNoPhoto() {
        SaleListing camera = data.sale("brseller", "Browse Photo Camera", 300);
        List<String> photos = data.photos(camera, samplePhoto(), samplePhoto(),
                "not an image".getBytes(StandardCharsets.UTF_8));
        data.damagePhotoFile(photos.get(1));
        logInAsAlice();

        search("Photo Camera");
        app.waitForAPhoto(); // the first photo is the card's thumbnail

        app.press("View Details", "Browse Photo Camera");
        app.waitForAPhoto(); // all three photos get a frame; only the first can be shown
        assertEquals(1, app.photosShown());
        assertTrue(app.shows("No photo"));
    }

    @Test
    void openScreens_reloadWhenTheTimerChangesTheData() {
        logInAsAlice();
        search("Sortable Lamp B");
        app.press("View Details", "Browse Sortable Lamp B");

        app.dataChanged(); // UC12 step 4: the listing reloads
        assertEquals("Browse Sortable Lamp B", app.pageTitle());
        app.press("Bree Seller");
        app.dataChanged(); // the seller's profile has nothing to reload
        assertEquals("Seller: Bree Seller", app.pageTitle());
        app.press("My Profile");
        app.dataChanged(); // nor has the profile
        assertEquals("Manage Profile", app.pageTitle());
        assertEquals(List.of(), app.dialogs());
    }

    @Test
    void aListingRemovedWhileTheBuyerViewsIt_takesTheBuyerBackToTheListings() {
        SaleListing radio = data.sale("brseller", "Browse Removed Radio", 45);
        logInAsAlice();
        search("Removed Radio");
        app.press("View Details", "Browse Removed Radio");
        app.press("Bree Seller");

        data.removeByAdmin(radio);
        app.press("← Back to listing");

        assertEquals(List.of(NOT_ON_MARKETPLACE), app.dialogs());
        assertEquals("Browse Marketplace", app.pageTitle());
        assertFalse(app.shows("Browse Removed Radio"));
    }

    @Test
    void aListingRemovedWhileTheBuyerBrowses_cannotBeOpened() {
        SaleListing tray = data.sale("brseller", "Browse Late Tray", 55);
        logInAsAlice();
        search("Late Tray");

        data.removeByAdmin(tray);
        app.press("View Details", "Browse Late Tray");

        assertEquals(List.of(NOT_ON_MARKETPLACE), app.dialogs());
        assertEquals("Browse Marketplace", app.pageTitle());
    }

    @Test
    void anAccountDeactivatedWhileTheBuyerViewsAListing_canNoLongerUseIt() {
        data.buyer("brdeact", "Bo Deact");
        app.logIn("Enter as Buyer", "brdeact", SeedData.DEMO_PASSWORD);
        search("Sortable Lamp A");
        app.press("View Details", "Browse Sortable Lamp A");

        data.deactivate("brdeact");
        app.press("Bree Seller");      // UC8 A3
        app.press("Chat with Seller"); // UC6
        app.press("Report Listing");   // UC9

        assertEquals(List.of(DEACTIVATED, DEACTIVATED, DEACTIVATED), app.dialogs());
        assertEquals("Browse Sortable Lamp A", app.pageTitle());
    }
}
