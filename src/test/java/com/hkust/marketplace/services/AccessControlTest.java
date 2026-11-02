package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for service-layer role checks (UC2 Special Requirement 3). */
class AccessControlTest {

    private final Buyer buyer = new Buyer("bea", "Bea", "hash", null);
    private final Seller seller = new Seller("sam", "Sam", "hash");
    private final Admin admin = new Admin("ada", "Ada", "hash");

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    @Test
    void noSession_isDenied() {
        assertThrows(AccessDeniedException.class, AccessControl::requireLoggedIn);
        assertThrows(AccessDeniedException.class, AccessControl::requireBuyer);
    }

    @Test
    void eachRole_passesOnlyItsOwnCheck() {
        SessionManager.login(buyer);
        assertSame(buyer, AccessControl.requireBuyer());
        assertThrows(AccessDeniedException.class, AccessControl::requireSeller);
        assertThrows(AccessDeniedException.class, AccessControl::requireAdmin);

        SessionManager.login(seller);
        assertSame(seller, AccessControl.requireSeller());
        assertThrows(AccessDeniedException.class, AccessControl::requireBuyer);

        SessionManager.login(admin);
        assertSame(admin, AccessControl.requireAdmin());
        assertThrows(AccessDeniedException.class, AccessControl::requireSeller);
    }

    @Test
    void requireRole_acceptsAnyListedRole() {
        SessionManager.login(seller);

        assertSame(seller, AccessControl.requireRole(UserRole.BUYER, UserRole.SELLER));
        assertThrows(AccessDeniedException.class, () -> AccessControl.requireRole(UserRole.ADMIN));
    }

    @Test
    void accountDeactivatedDuringSession_isDenied() {
        SessionManager.login(buyer);
        buyer.setActive(false);

        AccessDeniedException e = assertThrows(AccessDeniedException.class, AccessControl::requireBuyer);
        assertTrue(e.getMessage().contains("deactivated"));
    }
}
