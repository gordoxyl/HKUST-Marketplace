package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import com.hkust.marketplace.ui.NotificationDot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The admin's listing screens, through the real screens off screen (regression log R16, R17). */
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
        app.press("Save");
        assertEquals(Duration.ofHours(36), loan.getLendingDuration());        // R14 still holds
    }

    private static final String DOT = "#" + NotificationDot.ID;

    /** The number on the dot; 0 when it is hidden. */
    private static int dot() {
        String shown = app.text(DOT);
        return shown.isEmpty() ? 0 : Integer.parseInt(shown);
    }

    @Test
    void theDot_risesAfterTheAdminsOwnEdit_whenSaveReturnsToManageUsers() {
        data.seller("dotseller", "Dot Seller");
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        int before = dot();
        app.press("Manage Users");
        app.press("Edit", "Dot Seller");
        app.type("Full name (required)", "Dot Seller Renamed");
        app.press("Save");

        assertEquals("Manage Users", app.pageTitle());
        assertEquals(before + 1, dot()); // F4: every admin is told, the one who acted included
    }
}