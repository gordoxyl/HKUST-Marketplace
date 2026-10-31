package com.hkust.marketplace.services;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for UC1 Register as Buyer and UC2 Log In. */
class AuthServiceTest {

    private static final String GOOD_PASSWORD = "Secure#123";

    private final DummyDataService data = DummyDataService.getInstance();
    private final AuthService auth = new AuthService(data);

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** DummyDataService is shared by all tests, so each test uses its own new username. */
    private static String newUsername() {
        return "test_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerBuyer() {
        String username = newUsername();
        assertNull(auth.register(username, "Test Buyer", GOOD_PASSWORD, GOOD_PASSWORD, UserRole.BUYER));
        return username;
    }

    // ---------- UC1 Register ----------

    @Test
    void register_validBuyer_createsAccountWithHashedPassword() {
        String username = newUsername();

        String error = auth.register(username, "  Gordon Lee ", GOOD_PASSWORD, GOOD_PASSWORD, UserRole.BUYER);

        assertNull(error);
        User user = data.findUser(username);
        assertNotNull(user);
        assertEquals("Gordon Lee", user.getFullName());
        assertEquals(UserRole.BUYER, user.getRole());
        assertTrue(user.isActive());
        assertNotEquals(GOOD_PASSWORD, user.getPasswordHash()); // never stored in plaintext
        assertTrue(PasswordHasher.verify(GOOD_PASSWORD, user.getPasswordHash()));
    }

    @Test
    void register_usernameTakenByAnotherRole_isRejected() {
        // "charlie" is a seeded seller; usernames are unique across all roles, ignoring case
        String error = auth.register("Charlie", "Another Charlie", GOOD_PASSWORD, GOOD_PASSWORD, UserRole.BUYER);

        assertEquals("This username is already in use.", error);
        assertEquals(UserRole.SELLER, data.findUser("charlie").getRole()); // seller account unchanged
    }

    @Test
    void register_invalidDetails_isRejectedAndNothingIsStored() {
        String u1 = newUsername();
        String u2 = newUsername();
        String u3 = newUsername();

        assertEquals("Full name cannot be empty.",
                auth.register(u1, " ", GOOD_PASSWORD, GOOD_PASSWORD, UserRole.BUYER));
        assertNotNull(auth.register(u2, "Weak Password", "weakpass", "weakpass", UserRole.BUYER));
        assertEquals("The two passwords do not match.",
                auth.register(u3, "Typo", GOOD_PASSWORD, "Secure#124", UserRole.SELLER));

        assertNull(data.findUser(u1));
        assertNull(data.findUser(u2));
        assertNull(data.findUser(u3));
    }

    @Test
    void register_adminRole_isNotAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> auth.register(newUsername(), "Fake Admin", GOOD_PASSWORD, GOOD_PASSWORD, UserRole.ADMIN));
    }

    // ---------- UC2 Log In ----------

    @Test
    void login_correctCredentialsAndPortal_succeedsAndStartsSession() {
        String username = registerBuyer();

        LoginResult result = auth.login(username, GOOD_PASSWORD, UserRole.BUYER);

        assertEquals(LoginResult.SUCCESS, result);
        assertTrue(SessionManager.isBuyer());
        assertEquals(username, SessionManager.getCurrentUser().getUsername());
    }

    @Test
    void login_missingUsernameOrPassword_returnsMissingFields() {
        assertEquals(LoginResult.MISSING_FIELDS, auth.login("", GOOD_PASSWORD, UserRole.BUYER));
        assertEquals(LoginResult.MISSING_FIELDS, auth.login("someone", "", UserRole.BUYER));
        assertEquals(LoginResult.MISSING_FIELDS, auth.login(null, null, UserRole.BUYER));
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void login_wrongPasswordAndUnknownUser_giveTheSameResult() {
        String username = registerBuyer();

        LoginResult wrongPassword = auth.login(username, "Wrong#123", UserRole.BUYER);
        LoginResult unknownUser = auth.login(newUsername(), GOOD_PASSWORD, UserRole.BUYER);

        // UC2 Special Requirement 2: never reveal whether the username exists
        assertEquals(LoginResult.INVALID_CREDENTIALS, wrongPassword);
        assertEquals(wrongPassword, unknownUser);
        assertEquals("Incorrect username or password.", unknownUser.getMessage());
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void login_buyerAccountOnSellerPortal_returnsWrongAccountType() {
        String username = registerBuyer();

        assertEquals(LoginResult.WRONG_ACCOUNT_TYPE, auth.login(username, GOOD_PASSWORD, UserRole.SELLER));
        assertEquals(LoginResult.WRONG_ACCOUNT_TYPE, auth.login(username, GOOD_PASSWORD, UserRole.ADMIN));
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void login_deactivatedAccount_returnsDeactivated() {
        String username = registerBuyer();
        data.findUser(username).setActive(false);

        assertEquals(LoginResult.DEACTIVATED, auth.login(username, GOOD_PASSWORD, UserRole.BUYER));
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void login_seededDemoAccounts_logInToTheirOwnPortals() {
        // Demo credentials from the README; the seed data stores them hashed
        assertEquals(LoginResult.SUCCESS, auth.login("alice", "password1", UserRole.BUYER));
        assertEquals(LoginResult.SUCCESS, auth.login("charlie", "password1", UserRole.SELLER));
        assertEquals(LoginResult.SUCCESS, auth.login("admin", "admin123", UserRole.ADMIN));
        assertEquals(LoginResult.WRONG_ACCOUNT_TYPE, auth.login("admin", "admin123", UserRole.BUYER));
        assertNotEquals("admin123", data.findUser("admin").getPasswordHash());
    }

    @Test
    void login_usernameIsCaseInsensitive() {
        String username = registerBuyer();

        assertEquals(LoginResult.SUCCESS, auth.login(username.toUpperCase(), GOOD_PASSWORD, UserRole.BUYER));
    }
}
