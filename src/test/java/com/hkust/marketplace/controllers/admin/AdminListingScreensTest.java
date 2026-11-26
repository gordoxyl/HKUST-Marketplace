package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import com.hkust.marketplace.ui.NotificationDot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The admin's listing screens, through the real screens off screen (regression log R16, R17, R20). */
class AdminListingScreensTest {

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

    @Test
    void editingALendListing_namesItsDuration_cutsNoText_andKeepsIt() {
        data.seller("r16seller", "R16 Seller");
        LendListing loan = data.lend("r16seller", "R16 Long Loan", Duration.ofHours(36));
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("All Listings");
        app.press("Edit", "R16 Long Loan");

        assertEquals("", app.valueOf("Leave empty to keep 1 day 12 hours")); // R16: the hint names the duration
        assertEquals(List.of(), app.cutTexts());                              // R17: "days", not "…"
        assertEquals(List.of(), app.cutPrompts());                            // R20: the hint shows in full
        app.press("Save");
        assertEquals(Duration.ofHours(36), loan.getLendingDuration());        // R14 still holds
    }

    @Test
    void editingAListing_keepsItsCondition_orSavesTheOneChosen() {
        data.seller("condseller", "Condition Seller");
        SaleListing lamp = data.sale("condseller", "Condition Desk Lamp", 120); // listed as Used
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("All Listings");

        app.press("Edit", "Condition Desk Lamp");
        app.press("Save"); // the form shows the listing's own condition, which is required
        assertEquals(List.of("Listing updated: The seller has been notified."), app.dialogs());
        assertEquals(ItemCondition.USED, lamp.getCondition());

        app.press("Edit", "Condition Desk Lamp");
        app.choose("Like New");
        app.press("Save");
        assertEquals(ItemCondition.LIKE_NEW, lamp.getCondition()); // Task 3.7: edit any listing details
    }

    @Test
    void addingAListing_takesAPriceOnlyForSell_andADurationOnlyForLend() {
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("All Listings");
        app.press("+ Add Listing");
        assertFalse(app.canType("Price (Sell only)"));             // no type chosen yet
        assertFalse(app.canType("Lending duration (Lend only)"));

        app.choose("Sell");
        assertTrue(app.canType("Price (Sell only)"));
        assertFalse(app.canType("Lending duration (Lend only)"));
        assertThrows(AssertionError.class, () -> app.choose("hours")); // the unit box is disabled too
        app.type("Price (Sell only)", "120");

        app.choose("Lend");
        assertFalse(app.canType("Price (Sell only)"));
        assertEquals("", app.valueOf("Price (Sell only)"));        // the price typed for Sell is not kept
        assertTrue(app.canType("Lending duration (Lend only)"));
        app.choose("hours");                                        // the unit box can be used
        app.type("Lending duration (Lend only)", "7");

        app.choose("Giveaway");
        assertFalse(app.canType("Price (Sell only)"));
        assertFalse(app.canType("Lending duration (Lend only)"));
        assertEquals("", app.valueOf("Lending duration (Lend only)"));
    }

    @Test
    void aTimerEvent_keepsTheAddListingFormAsTyped() {
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("All Listings");
        app.press("+ Add Listing");
        app.type("Title *", "Half-typed Kettle");

        app.dataChanged(); // the borrowing-deadline timer changed the data (UC12 step 4)

        assertEquals("Add Listing", app.pageTitle());              // R24: the form is not replaced
        assertEquals("Half-typed Kettle", app.valueOf("Title *"));
    }

    @Test
    void aTimerEvent_bringsAllListingsUpToDate_andKeepsItsFilter() {
        data.seller("timerseller", "Timer Seller");
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("All Listings");
        app.type("Seller...", "timerseller");
        app.press("Search");
        assertEquals(List.of(), app.cardTitles());

        data.sale("timerseller", "Timer Kettle", 40);
        app.dataChanged();

        assertEquals("All Listings", app.pageTitle());
        assertEquals(List.of("Timer Kettle"), app.cardTitles()); // the new listing, and still only this seller's
    }

    @Test
    void manageUsers_eachFilterLabelStaysBesideItsBox() {
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("Manage Users");

        assertTrue(app.onOneLine("Reg. From:", "Reg. From")); // R25: the filter bar wraps a label with its box
        assertTrue(app.onOneLine("Reg. To:", "Reg. To"));
        assertEquals(List.of(), app.cutPrompts());              // R22 still holds
    }

    @Test
    void allDeals_withManyDealsAndLongTitles_cutsNoText() {
        data.seller("r26seller", "R26 Seller");
        for (int i = 1; i <= 15; i++) {
            data.buyer("r26buyer" + i, "R26 Buyer " + i);
            data.accept(data.priceOffer("r26buyer" + i,
                    data.sale("r26seller", "Vintage Mechanical Keyboard With Extra Keycaps " + i, 50), 40));
        }
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);

        app.press("All Deals");

        assertEquals(List.of(), app.cutTexts()); // R26: the columns keep their width, and the table scrolls sideways
    }

    private static final String DOT = "#" + NotificationDot.ID;

    /** The number on the dot; 0 when it is hidden. */
    private static int dot() {
        String shown = app.text(DOT);
        return shown.isEmpty() ? 0 : Integer.parseInt(shown);
    }

    @Test
    void theDot_risesAfterTheAdminsOwnEdit_whenSaveReturnsToManageUsers() {
        data.seller("admindotseller", "Admin Dot Seller");
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        int before = dot();
        app.press("Manage Users");
        app.press("Edit", "Admin Dot Seller");
        app.type("Full name (required)", "Dot Seller Renamed");
        app.press("Save");

        assertEquals("Manage Users", app.pageTitle());
        assertEquals(before + 1, dot()); // F4: every admin is told, the one who acted included
    }
}