package com.hkust.marketplace.services;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

/**
 * Registration and login shared by the buyer, seller and admin portals
 * (UC1 Register as Buyer, Register as Seller, UC2 Log In).
 *
 * Passwords are stored and checked only as hashes, via {@link PasswordHasher}.
 */
public class AuthService {

    private final DummyDataService dataService;

    public AuthService() {
        this(DummyDataService.getInstance());
    }

    public AuthService(DummyDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Registers a new buyer or seller account (UC1 basic flow steps 5-9).
     * The checks run in the order of the use case: username, full name, password, confirmation.
     *
     * @return null if the account was created, otherwise the error message to show
     * @throws IllegalArgumentException if role is ADMIN, because admins cannot register (Task 3)
     */
    public String register(String username, String fullName, String password,
                           String confirmPassword, UserRole role) {
        if (role == null || role == UserRole.ADMIN) {
            throw new IllegalArgumentException("Only buyers and sellers can register.");
        }

        String error = AccountValidator.validateUsername(username);
        if (error != null) return error;
        String cleanUsername = username.trim().toLowerCase();
        // UC1 A1: unique across buyer, seller and admin, without saying which role holds it
        if (dataService.usernameExists(cleanUsername)) {
            return "This username is already in use.";
        }

        error = AccountValidator.validateFullName(fullName);
        if (error != null) return error;

        error = AccountValidator.validatePassword(password);
        if (error != null) return error;

        error = AccountValidator.validateConfirmation(password, confirmPassword);
        if (error != null) return error;

        User user = new User(cleanUsername, fullName.trim(), PasswordHasher.hash(password), role);
        dataService.registerUser(user);
        return null;
    }

    /**
     * Logs a user in to the portal for portalRole (UC2 basic flow steps 5-9).
     * On success the user becomes the current user in {@link SessionManager}.
     */
    public LoginResult login(String username, String password, UserRole portalRole) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return LoginResult.MISSING_FIELDS;
        }

        User user = dataService.findUser(username.trim());
        if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
            return LoginResult.INVALID_CREDENTIALS;
        }
        if (user.getRole() != portalRole) {
            return LoginResult.WRONG_ACCOUNT_TYPE;
        }
        if (!user.isActive()) {
            return LoginResult.DEACTIVATED;
        }

        SessionManager.login(user);
        return LoginResult.SUCCESS;
    }
}
