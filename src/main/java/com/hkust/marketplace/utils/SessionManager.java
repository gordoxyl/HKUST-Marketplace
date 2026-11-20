package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;

/**
 * Holds the user who is logged in to this copy of the app (a desktop app with no server, so
 * there is no session token). The services check this user's role and account status through
 * {@code AccessControl}.
 */
public class SessionManager {
    private static User currentUser;

    public static void login(User user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isBuyer() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.BUYER;
    }

    public static boolean isSeller() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.SELLER;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.ADMIN;
    }
}
