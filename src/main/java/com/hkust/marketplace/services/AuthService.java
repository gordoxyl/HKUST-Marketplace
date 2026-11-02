package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.SessionManager;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Registration and login shared by the buyer, seller and admin portals
 * (UC1 Register as Buyer, Register as Seller, UC2 Log In).
 * Admins cannot register (Task 3), so there is no method for it.
 *
 * Passwords are stored and checked only as hashes, via {@link PasswordHasher}.
 */
public class AuthService {

    private static final String USERNAME_TAKEN = "This username is already in use.";

    private final DataStore dataService;

    public AuthService() {
        this(DataStore.getInstance());
    }

    public AuthService(DataStore dataService) {
        this.dataService = dataService;
    }

    /**
     * Registers a new buyer account (UC1 basic flow steps 5-9).
     *
     * @param residenceHall optional; null or blank if not given
     * @return null if the account was created, otherwise the error message to show
     */
    public String registerBuyer(String username, String fullName, String password,
                                String confirmPassword, String residenceHall) {
        return register(username, fullName, password, confirmPassword,
                (cleanUsername, hash) -> new Buyer(cleanUsername, fullName.trim(), hash, residenceHall));
    }

    /**
     * Registers a new seller account (Register as Seller), with the same checks as buyers.
     *
     * @param bio       optional; null or blank if not given
     * @param locations optional preferred meet-up locations; null or empty if none
     * @return null if the account was created, otherwise the error message to show
     */
    public String registerSeller(String username, String fullName, String password, String confirmPassword,
                                 String bio, List<MeetupLocation> locations) {
        return register(username, fullName, password, confirmPassword, (cleanUsername, hash) -> {
            Seller seller = new Seller(cleanUsername, fullName.trim(), hash);
            seller.setBio(bio);
            seller.setPreferredMeetupLocations(locations);
            return seller;
        });
    }

    /**
     * The checks run in the order of the use case: username, full name, password, confirmation.
     * createAccount builds the account from the cleaned username and the password hash.
     */
    private String register(String username, String fullName, String password, String confirmPassword,
                            BiFunction<String, String, User> createAccount) {
        String error = AccountValidator.validateUsername(username);
        if (error != null) return error;
        String cleanUsername = username.trim().toLowerCase();
        // UC1 A1: unique across buyer, seller and admin, without saying which role holds it
        if (dataService.usernameExists(cleanUsername)) {
            return USERNAME_TAKEN;
        }

        error = AccountValidator.validateFullName(fullName);
        if (error != null) return error;

        error = AccountValidator.validatePassword(password);
        if (error != null) return error;

        error = AccountValidator.validateConfirmation(password, confirmPassword);
        if (error != null) return error;

        User user = createAccount.apply(cleanUsername, PasswordHasher.hash(password));
        // addUser checks the username again, in case someone took it while the password was hashed
        if (!dataService.addUser(user)) {
            return USERNAME_TAKEN;
        }
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
