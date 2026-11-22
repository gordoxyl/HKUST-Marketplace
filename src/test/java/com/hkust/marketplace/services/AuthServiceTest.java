package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for UC1 Register as Buyer, Register as Seller and UC2 Log In. */
class AuthServiceTest {

    private static final String GOOD_PASSWORD = "Secure#123";

    private final DataStore data = seededStore();
    private final AuthService auth = new AuthService(data);

    /** Each test gets its own store with the demo data, so tests cannot affect each other. */
    private static DataStore seededStore() {
        DataStore store = new DataStore();
        SeedData.populate(store);
        return store;
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    private String registerBuyer() {
        assertNull(auth.registerBuyer("newbuyer", "Test Buyer", GOOD_PASSWORD, GOOD_PASSWORD, null));
        return "newbuyer";
    }

    // ---------- UC1 Register ----------

    @Test
    void registerBuyer_validDetails_createsBuyerWithHashedPassword() {
        String error = auth.registerBuyer("gordon", "  Gordon Lee ", GOOD_PASSWORD, GOOD_PASSWORD, "Hall 1");

        assertNull(error);
        User user = data.findUser("gordon");
        Buyer buyer = assertInstanceOf(Buyer.class, user);
        assertEquals("Gordon Lee", buyer.getFullName());
        assertEquals("Hall 1", buyer.getResidenceHall());
        assertEquals(UserRole.BUYER, buyer.getRole());
        assertTrue(buyer.isActive());
        assertNotEquals(GOOD_PASSWORD, buyer.getPasswordHash()); // never stored in plaintext
        assertTrue(PasswordHasher.verify(GOOD_PASSWORD, buyer.getPasswordHash()));
    }

    @Test
    void registerBuyer_blankResidence_isStoredAsNotGiven() {
        assertNull(auth.registerBuyer("gordon", "Gordon", GOOD_PASSWORD, GOOD_PASSWORD, " "));

        assertNull(((Buyer) data.findUser("gordon")).getResidenceHall());
    }

    @Test
    void registerSeller_validDetails_createsSellerWithBioAndLocations() {
        String error = auth.registerSeller("sam", "Sam Seller", GOOD_PASSWORD, GOOD_PASSWORD,
                " Selling books ", List.of(MeetupLocation.LIBRARY, MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE));

        assertNull(error);
        Seller seller = assertInstanceOf(Seller.class, data.findUser("sam"));
        assertEquals("Selling books", seller.getBio());
        assertEquals(List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE), seller.getPreferredMeetupLocations());
    }

    @Test
    void register_usernameTakenByAnotherRole_isRejected() {
        // "charlie" is a seeded seller; usernames are unique across all roles, ignoring case
        String error = auth.registerBuyer("Charlie", "Another Charlie", GOOD_PASSWORD, GOOD_PASSWORD, null);

        assertEquals("This username is already in use.", error);
        assertEquals(UserRole.SELLER, data.findUser("charlie").getRole()); // seller account unchanged
    }

    @Test
    void register_invalidDetails_isRejectedAndNothingIsStored() {
        assertEquals("Full name cannot be empty.",
                auth.registerBuyer("u1", " ", GOOD_PASSWORD, GOOD_PASSWORD, null));
        assertNotNull(auth.registerBuyer("u2", "Weak Password", "weakpass", "weakpass", null));
        assertEquals("The two passwords do not match.",
                auth.registerSeller("u3", "Typo", GOOD_PASSWORD, "Secure#124", null, null));

        assertNull(data.findUser("u1"));
        assertNull(data.findUser("u2"));
        assertNull(data.findUser("u3"));
    }

    /** Regression log R11: UC1 A3 says what is wrong with the password and shows the password rules. */
    @Test
    void register_weakPassword_saysWhatIsWrong_andShowsThePasswordRules() {
        String expected = "Password must contain at least one uppercase letter. Password rules: "
                + AccountValidator.PASSWORD_RULES;

        assertEquals(expected, auth.registerBuyer("u4", "Weak Password", "password1", "password1", null));
        assertEquals(expected, auth.registerSeller("u5", "Weak Password", "password1", "password1", null, null));
        assertNull(data.findUser("u4"));
        assertNull(data.findUser("u5"));
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
        LoginResult unknownUser = auth.login("nobody", GOOD_PASSWORD, UserRole.BUYER);

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
        assertEquals(LoginResult.SUCCESS, auth.login("alice", SeedData.DEMO_PASSWORD, UserRole.BUYER));
        assertEquals(LoginResult.SUCCESS, auth.login("charlie", SeedData.DEMO_PASSWORD, UserRole.SELLER));
        assertEquals(LoginResult.SUCCESS, auth.login("admin", SeedData.ADMIN_PASSWORD, UserRole.ADMIN));
        assertEquals(LoginResult.WRONG_ACCOUNT_TYPE, auth.login("admin", SeedData.ADMIN_PASSWORD, UserRole.BUYER));
        assertNotEquals(SeedData.ADMIN_PASSWORD, data.findUser("admin").getPasswordHash());
    }

    @Test
    void login_usernameIsCaseInsensitive() {
        String username = registerBuyer();

        assertEquals(LoginResult.SUCCESS, auth.login(username.toUpperCase(), GOOD_PASSWORD, UserRole.BUYER));
    }
}
