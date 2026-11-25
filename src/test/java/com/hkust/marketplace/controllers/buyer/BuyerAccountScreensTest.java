package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.services.AccountValidator;
import com.hkust.marketplace.services.LoginResult;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System tests of the buyer's account screens (Lecture 14): UC1 Register as Buyer, UC2 Log In
 * and UC3 Manage Profile, through the real screens from the welcome screen on, with the
 * messages the user sees. Each test that changes data has accounts of its own.
 */
class BuyerAccountScreensTest {

    private static final String GOOD_PASSWORD = "Secure#123";
    private static final String NEW_PASSWORD_PROMPT = "Leave blank to keep your current password";
    private static final String CONFIRM_PROMPT = "Type the new password again";
    private static final String DEACTIVATED = "Not possible: This account has been deactivated. Please contact the admin.";

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void openTheApp() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
    }

    @AfterEach
    void backToTheWelcomeScreen() {
        app.reset();
    }

    private static void fillRegistration(String username, String fullName, String password, String confirm) {
        app.type("#usernameField", username);
        app.type("#fullNameField", fullName);
        app.type("#passwordField", password);
        app.type("#confirmPasswordField", confirm);
    }

    // ---------- UC1 Register as Buyer ----------

    @Test
    void register_validDetails_isConfirmed_clearsTheForm_andTheNewBuyerLogsIn() {
        app.press("Enter as Buyer");
        app.press("Register here");
        fillRegistration("regbuyer", "Rhea Gist", GOOD_PASSWORD, GOOD_PASSWORD);
        app.choose("UG Hall 1");

        app.press("Create Account");

        assertEquals("Registration successful! You can now login.", app.text("#successLabel"));
        assertEquals("", app.valueOf("#usernameField"));
        assertEquals("UG Hall 1", ((Buyer) app.store().findUser("regbuyer")).getResidenceHall());
        app.press("Login here");
        app.type("#usernameField", "regbuyer");
        app.type("#passwordField", GOOD_PASSWORD);
        app.press("Login");
        assertEquals("Hello, Rhea Gist", app.text("#welcomeLabel"));
    }

    @Test
    void register_eachInvalidDetail_isShownOnTheForm_andNothingIsStored() {
        app.press("Enter as Buyer");
        app.press("Register here");

        fillRegistration("alice", "Another Alice", GOOD_PASSWORD, GOOD_PASSWORD); // A1: username taken
        app.press("Create Account");
        assertEquals("This username is already in use.", app.text("#errorLabel"));

        fillRegistration("regerror", " ", GOOD_PASSWORD, GOOD_PASSWORD); // full name empty
        app.press("Create Account");
        assertEquals("Full name cannot be empty.", app.text("#errorLabel"));

        fillRegistration("regerror", "Rex Error", "password1", "password1"); // A3: weak password
        app.press("Create Account");
        assertEquals("Password must contain at least one uppercase letter. Password rules: "
                + AccountValidator.PASSWORD_RULES, app.text("#errorLabel"));

        fillRegistration("regerror", "Rex Error", GOOD_PASSWORD, "Secure#124"); // A4: passwords differ
        app.press("Create Account");
        assertEquals("The two passwords do not match.", app.text("#errorLabel"));
        assertNull(app.store().findUser("regerror"));
    }

    @Test
    void register_and_login_linksLeadBetweenTheScreens_andBackToTheWelcomeScreen() {
        app.press("Enter as Buyer");
        app.press("Register here");
        assertTrue(app.shows("Buyer Registration"));
        app.press("← Back to Welcome");
        assertTrue(app.shows("Choose your portal to continue"));

        app.press("Enter as Buyer");
        app.press("← Back to Welcome");
        assertTrue(app.shows("Choose your portal to continue"));
    }

    // ---------- UC2 Log In ----------

    @Test
    void login_withoutUsernameOrPassword_saysBothAreNeeded() {
        app.logIn("Enter as Buyer", "", "");

        assertEquals(LoginResult.MISSING_FIELDS.getMessage(), app.text("#errorLabel")); // A1
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void login_deactivatedAccount_isRefused() {
        data.buyer("logdeact", "Lou Deact");
        data.deactivate("logdeact");

        app.logIn("Enter as Buyer", "logdeact", SeedData.DEMO_PASSWORD);

        assertEquals(LoginResult.DEACTIVATED.getMessage(), app.text("#errorLabel")); // A4
        assertFalse(SessionManager.isLoggedIn());
    }

    // ---------- UC3 Manage Profile ----------

    @Test
    void profile_showsTheResidenceHall_orThatNoneWasGiven() {
        data.buyer("profnohall", "Paul Nohall");
        app.logIn("Enter as Buyer", "profnohall", SeedData.DEMO_PASSWORD);
        app.press("My Profile");
        assertTrue(app.shows("Not given"));

        app.press("Logout");
        app.logIn("Enter as Buyer", "alice", SeedData.DEMO_PASSWORD);
        app.press("My Profile");
        assertTrue(app.shows("Hall 3"));
        assertTrue(app.shows("@alice"));
    }

    @Test
    void profile_newName_isSaved_andTheGreetingShowsItAtOnce() {
        data.buyer("profname", "Pat Name");
        app.logIn("Enter as Buyer", "profname", SeedData.DEMO_PASSWORD);
        app.press("My Profile");

        app.type("Full name", "Patricia Name");
        app.press("Save Changes");

        assertEquals(List.of("Profile updated: Your profile was updated successfully."), app.dialogs());
        assertEquals("Hello, Patricia Name", app.text("#welcomeLabel"));
        assertEquals("Patricia Name", app.store().findUser("profname").getFullName());
    }

    @Test
    void profile_newPassword_isSaved_withANoteToUseIt_andWorksAtTheNextLogin() {
        data.buyer("profpass", "Pia Pass");
        app.logIn("Enter as Buyer", "profpass", SeedData.DEMO_PASSWORD);
        app.press("My Profile");

        app.type(NEW_PASSWORD_PROMPT, "Changed#456");
        app.type(CONFIRM_PROMPT, "Changed#456");
        app.press("Save Changes");

        assertEquals(List.of("Profile updated: Your profile was updated successfully. "
                + "Use your new password the next time you log in."), app.dialogs());
        app.press("Logout");
        app.logIn("Enter as Buyer", "profpass", "Changed#456");
        assertEquals("Hello, Pia Pass", app.text("#welcomeLabel"));
    }

    @Test
    void profile_invalidChanges_areRefused_andWhatWasTypedStays() {
        data.buyer("proferr", "Peg Err");
        app.logIn("Enter as Buyer", "proferr", SeedData.DEMO_PASSWORD);
        app.press("My Profile");

        app.type("Full name", " ");
        app.press("Save Changes"); // A1
        app.type("Full name", "Peg Err");
        app.type(NEW_PASSWORD_PROMPT, "weakpass");
        app.type(CONFIRM_PROMPT, "weakpass");
        app.press("Save Changes"); // A2
        app.type(NEW_PASSWORD_PROMPT, "Strong#789");
        app.type(CONFIRM_PROMPT, "Strong#780");
        app.press("Save Changes"); // A3

        List<String> dialogs = app.dialogs();
        assertEquals(3, dialogs.size(), dialogs::toString);
        assertEquals("Not possible: Full name cannot be empty.", dialogs.get(0));
        assertTrue(dialogs.get(1).startsWith("Not possible: Password must"), dialogs::toString);
        assertEquals("Not possible: The two passwords do not match.", dialogs.get(2));
        assertEquals("Strong#789", app.valueOf(NEW_PASSWORD_PROMPT));
        assertEquals("Strong#780", app.valueOf(CONFIRM_PROMPT));
        assertEquals("Peg Err", app.store().findUser("proferr").getFullName());
    }

    @Test
    void profile_cancel_putsBackTheSavedProfile() {
        data.buyer("profcancel", "Cy Cancel");
        app.logIn("Enter as Buyer", "profcancel", SeedData.DEMO_PASSWORD);
        app.press("My Profile");
        app.type("Full name", "Someone Else");

        app.press("Cancel"); // A4

        assertEquals("Cy Cancel", app.valueOf("Full name"));
        assertEquals("Cy Cancel", app.store().findUser("profcancel").getFullName());
    }

    @Test
    void profile_ofAnAccountDeactivatedWhileLoggedIn_isNotShown() {
        // Access control at the API layer (technical requirement; assumptions register 6)
        data.buyer("profdeact", "Dee Act");
        app.logIn("Enter as Buyer", "profdeact", SeedData.DEMO_PASSWORD);
        data.deactivate("profdeact");

        app.press("My Profile");

        assertEquals(List.of(DEACTIVATED), app.dialogs());
        assertFalse(app.shows("Save Changes"));
    }
}
