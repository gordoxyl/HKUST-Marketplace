package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of UC9 Report Listing (Task 1.4), from the listing detail screen. The test named
 * "...WhileTheFormIsOpen" removes the listing between two of the buyer's steps, as the admin
 * would (technical requirement: concurrent actions).
 */
class ReportScreensTest {

    private static final String DETAILS = "What is wrong with this listing?";

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void openTheApp() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
        data.seller("rpseller", "Rae Seller");
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    private static void openAsNewBuyer(String buyer, String title) {
        data.buyer(buyer, "Buyer " + buyer);
        app.logIn("Enter as Buyer", buyer, SeedData.DEMO_PASSWORD);
        app.type("Search by title or keyword", title);
        app.press("Search");
        app.press("View Details", title);
    }

    @Test
    void report_withAReasonAndDetails_isSubmitted_andASecondReportIsRefused() {
        data.sale("rpseller", "Report Fake Phone", 900);
        app.answer("Report Listing", form -> form.choose("Scam or fraud")
                .type(DETAILS, "Photos are copied from a shop website.").press("Submit Report"));
        openAsNewBuyer("rpbuyer1", "Report Fake Phone");

        app.press("Report Listing");
        app.press("Report Listing"); // A1: already reported

        List<String> dialogs = app.dialogs();
        assertEquals(3, dialogs.size(), dialogs::toString);
        assertTrue(dialogs.get(1).startsWith("Report submitted: "), dialogs::toString);
        assertEquals("Not possible: You have already reported this listing.", dialogs.get(2));
    }

    @Test
    void report_withoutDetails_isRefused_andTheFormComesBackToBeCorrectedOrCancelled() {
        SaleListing watch = data.sale("rpseller", "Report Odd Watch", 300);
        app.answer("Report Listing", form -> form.choose("Misleading description").press("Submit Report")); // A2
        app.answer("Report Listing", form -> {
            assertTrue(form.shows("Misleading description")); // the reason chosen before is still chosen
            form.press("Cancel"); // A3
        });
        openAsNewBuyer("rpbuyer2", "Report Odd Watch");

        app.press("Report Listing");

        List<String> dialogs = app.dialogs();
        assertEquals(3, dialogs.size(), dialogs::toString);
        assertEquals("Not possible: Both a reason and details are required.", dialogs.get(1));
        assertTrue(app.store().getReportsForListing(watch.getId()).isEmpty());
    }

    @Test
    void aListingRemovedWhileTheFormIsOpen_endsTheReport() {
        SaleListing ring = data.sale("rpseller", "Report Gone Ring", 500);
        app.answer("Report Listing", form -> {
            data.removeByAdmin(ring);
            form.choose("Prohibited item").type(DETAILS, "Looks stolen.").press("Submit Report");
        });
        openAsNewBuyer("rpbuyer3", "Report Gone Ring");

        app.press("Report Listing");

        List<String> dialogs = app.dialogs(); // A1 at {Check Report}, done again on submit: nothing is recorded
        assertEquals(List.of("Not possible: This listing is no longer on the marketplace."), dialogs.subList(1, dialogs.size()));
        assertTrue(app.store().getReportsForListing(ring.getId()).isEmpty());
    }
}
