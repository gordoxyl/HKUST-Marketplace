package com.hkust.marketplace.controllers;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.services.LoginResult;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of the three portals through their real screens (Lecture 14), with the demo
 * data: a demo user of each portal logs in on the welcome screen (UC2) and opens every page of
 * the sidebar. A page that fails to open, or opens with a dialog such as an error, fails the
 * test, and so does a page that cuts a button or label short ("…") at the app's window size
 * (regression log R15). What each page's own buttons do is tested per portal, as in
 * {@code BuyerScreensTest}.
 */
class PortalScreensTest {

    private static OffScreenApp app;

    @BeforeAll
    static void openTheApp() {
        app = OffScreenApp.start();
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    /** Every page opened with a title of its own, and no dialog opened. */
    private static void assertEveryPageOpens(Map<String, String> titles) {
        assertFalse(titles.isEmpty(), "the sidebar has no pages");
        assertTrue(titles.values().stream().noneMatch(String::isBlank), () -> "a page has no title: " + titles);
        assertEquals(titles.size(), Set.copyOf(titles.values()).size(), () -> "two pages have the same title: " + titles);
        assertEquals(List.of(), app.dialogs());
    }

    @Test
    void buyerPortal_everyPageOpens() {
        app.logIn("Enter as Buyer", "alice", SeedData.DEMO_PASSWORD);

        assertEquals("Hello, Alice Chan", app.text("#welcomeLabel"));
        assertEveryPageOpens(app.visitEveryPage());
        assertEquals(Map.of(), app.cutTextsOnEveryPage());
    }

    @Test
    void sellerPortal_everyPageOpens() {
        app.logIn("Enter as Seller", "charlie", SeedData.DEMO_PASSWORD);

        assertTrue(SessionManager.isSeller());
        assertEveryPageOpens(app.visitEveryPage());
        assertEquals(Map.of(), app.cutTextsOnEveryPage());
    }

    @Test
    void adminPortal_everyPageOpens() {
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);

        assertTrue(SessionManager.isAdmin());
        assertEveryPageOpens(app.visitEveryPage());
        assertEquals(Map.of(), app.cutTextsOnEveryPage());
    }

    @Test
    void wrongPassword_staysOnTheLoginScreen_andSaysWhy() {
        app.logIn("Enter as Buyer", "alice", "Wrong#123");

        assertEquals(LoginResult.INVALID_CREDENTIALS.getMessage(), app.text("#errorLabel")); // UC2 A2
        assertTrue(app.shows("Buyer Login"));
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void buyerAccountOnTheAdminPortal_isRefused() {
        app.logIn("Enter as Admin", "alice", SeedData.DEMO_PASSWORD);

        assertEquals(LoginResult.WRONG_ACCOUNT_TYPE.getMessage(), app.text("#errorLabel")); // UC2 A3
        assertFalse(SessionManager.isLoggedIn());
    }

    /**
     * The card of every login and registration screen is in the middle of the window
     * (regression log R19: the registration cards sat at the left edge).
     */
    @Test
    void loginAndRegistrationCards_areCentred() {
        Map<String, Double> offCentre = new LinkedHashMap<>();
        for (String portal : List.of("Buyer", "Seller")) {
            app.press("Enter as " + portal);
            offCentre.put(portal + " login", app.offCentre("auth-pane"));
            app.press("Register here");
            offCentre.put(portal + " registration", app.offCentre("auth-pane"));
            app.reset();
        }
        app.press("Enter as Admin");
        offCentre.put("Admin login", app.offCentre("auth-pane"));

        Map<String, Double> notCentred = new LinkedHashMap<>(offCentre);
        notCentred.values().removeIf(pixels -> pixels <= 1);
        assertEquals(Map.of(), notCentred, "pixels off centre");
    }

    /** No login screen shows a password: the demo accounts are listed in the README, not on the screens. */
    @Test
    void loginScreens_showNoPassword() {
        for (String portal : List.of("Enter as Buyer", "Enter as Seller", "Enter as Admin")) {
            app.press(portal);
            assertFalse(app.shows(SeedData.DEMO_PASSWORD), portal);
            assertFalse(app.shows(SeedData.ADMIN_PASSWORD), portal);
            app.reset();
        }
    }

    @Test
    void logout_endsTheSession_andShowsTheWelcomeScreen() {
        app.logIn("Enter as Seller", "charlie", SeedData.DEMO_PASSWORD);

        app.press("Logout");

        assertFalse(SessionManager.isLoggedIn());
        assertTrue(app.shows("Choose your portal to continue"));
    }
}
