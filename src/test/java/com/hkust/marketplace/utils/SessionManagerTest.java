package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for who is logged in (UC2): each role check is true only for a logged-in user of that role. */
class SessionManagerTest {

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    @Test
    void nobodyLoggedIn_hasNoRole() {
        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
        assertFalse(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());
    }

    @Test
    void aLoggedInUser_hasOnlyTheRoleOfTheirAccount() {
        SessionManager.login(new Buyer("bea", "Bea", "hash", null));
        assertTrue(SessionManager.isLoggedIn());
        assertTrue(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());

        SessionManager.login(new Seller("sam", "Sam", "hash"));
        assertTrue(SessionManager.isSeller());
        assertFalse(SessionManager.isBuyer());
        assertFalse(SessionManager.isAdmin());

        SessionManager.login(new Admin("ada", "Ada", "hash"));
        assertTrue(SessionManager.isAdmin());
        assertFalse(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
    }
}
