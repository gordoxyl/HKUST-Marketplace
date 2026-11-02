package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.SessionManager;

import java.util.Arrays;

/**
 * Role-based access control for the service layer (UC2 Special Requirement 3).
 *
 * Every service method that belongs to one portal starts with one of these checks, so a
 * buyer session cannot run seller or admin functions even if the user interface is bypassed.
 * For example, a seller-only service method starts with:
 * <pre>{@code
 * Seller seller = AccessControl.requireSeller();
 * }</pre>
 * Each check returns the logged-in user, or throws {@link AccessDeniedException}.
 */
public final class AccessControl {

    private AccessControl() {
        // Utility class
    }

    /** The logged-in user, whose account must still be active. */
    public static User requireLoggedIn() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Please log in first.");
        }
        if (!user.isActive()) {
            throw new AccessDeniedException("This account has been deactivated. Please contact the admin.");
        }
        return user;
    }

    /** The logged-in user, who must have one of the allowed roles. */
    public static User requireRole(UserRole... allowed) {
        User user = requireLoggedIn();
        if (!Arrays.asList(allowed).contains(user.getRole())) {
            throw new AccessDeniedException("This function is not available to " + user.getRole().name().toLowerCase() + " accounts.");
        }
        return user;
    }

    public static Buyer requireBuyer() {
        return (Buyer) requireRole(UserRole.BUYER);
    }

    public static Seller requireSeller() {
        return (Seller) requireRole(UserRole.SELLER);
    }

    public static Admin requireAdmin() {
        return (Admin) requireRole(UserRole.ADMIN);
    }
}
