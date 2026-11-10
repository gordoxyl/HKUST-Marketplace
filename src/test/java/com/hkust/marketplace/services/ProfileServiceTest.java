package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Unit and integration tests for UC3 Manage Profile. */
class ProfileServiceTest {

    private static final String OLD_PASSWORD = "Old#Pass1";
    private static final String NEW_PASSWORD = "New#Pass2";

    private TestClock clock;
    private DataStore store;
    private ProfileService profiles;
    private Buyer bea;
    private Seller sam;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        profiles = new ProfileService(store);
        bea = new Buyer("bea", "Bea Buyer", PasswordHasher.hash(OLD_PASSWORD), "Hall 3");
        store.addUser(bea);
        sam = new Seller("sam", "Sam Seller", PasswordHasher.hash(OLD_PASSWORD));
        sam.setBio("Old bio");
        store.addUser(sam);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    // ---------- Basic flow ----------

    @Test
    void newNameAndPassword_areSaved_passwordHashedOnly() {
        SessionManager.login(bea);

        profiles.updateProfile("  Bea Chan ", NEW_PASSWORD, NEW_PASSWORD);

        assertEquals("Bea Chan", bea.getFullName());
        assertNotEquals(NEW_PASSWORD, bea.getPasswordHash()); // Special Requirement 4
        assertTrue(PasswordHasher.verify(NEW_PASSWORD, bea.getPasswordHash()));
        assertFalse(PasswordHasher.verify(OLD_PASSWORD, bea.getPasswordHash()));
    }

    @Test
    void blankNewPassword_keepsTheCurrentPassword() {
        SessionManager.login(bea);
        String before = bea.getPasswordHash();

        profiles.updateProfile("Bea Chan", "", "anything"); // Special Requirement 2: confirmation ignored
        profiles.updateProfile("Bea Chan", null, null);

        assertEquals(before, bea.getPasswordHash());
    }

    @Test
    void fullName_mayContainAnyCharacters() {
        SessionManager.login(bea);
        profiles.updateProfile("陳大文 Chan Tai-man", "", "");
        assertEquals("陳大文 Chan Tai-man", bea.getFullName()); // Special Requirement 3
    }

    @Test
    void usernameAndResidenceHall_stayTheSame() {
        SessionManager.login(bea);
        profiles.updateProfile("Someone Else", NEW_PASSWORD, NEW_PASSWORD);
        assertEquals("bea", bea.getUsername());          // Special Requirement 1
        assertEquals("Hall 3", bea.getResidenceHall());
    }

    @Test
    void seller_alsoUpdatesBioAndMeetupLocations() {
        SessionManager.login(sam);

        profiles.updateSellerProfile("Sam Lee", "", "", "  Selling books  ",
                List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE));

        assertEquals("Sam Lee", sam.getFullName());
        assertEquals("Selling books", sam.getBio());
        assertEquals(List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE), sam.getPreferredMeetupLocations());

        profiles.updateSellerProfile("Sam Lee", "", "", " ", List.of()); // bio and locations are optional
        assertNull(sam.getBio());
        assertTrue(sam.getPreferredMeetupLocations().isEmpty());
    }

    // ---------- Alternative flows: a failed check changes nothing ----------

    @Test
    void a1_emptyFullName_isRefused() {
        SessionManager.login(bea);
        String hashBefore = bea.getPasswordHash();

        ServiceException e = assertThrows(ServiceException.class,
                () -> profiles.updateProfile("   ", NEW_PASSWORD, NEW_PASSWORD));

        assertEquals("Full name cannot be empty.", e.getMessage());
        assertEquals("Bea Buyer", bea.getFullName());
        assertEquals(hashBefore, bea.getPasswordHash()); // the valid new password was not saved either
    }

    @Test
    void a2_weakNewPassword_isRefused_andTheRulesAreShown() {
        SessionManager.login(bea);
        String hashBefore = bea.getPasswordHash();

        ServiceException e = assertThrows(ServiceException.class,
                () -> profiles.updateProfile("Bea Chan", "Short#1", "Short#1")); // 7 characters: boundary

        assertTrue(e.getMessage().contains("at least 8 characters"));
        assertTrue(e.getMessage().contains(AccountValidator.PASSWORD_RULES));
        assertEquals("Bea Buyer", bea.getFullName()); // the valid new name was not saved either
        assertEquals(hashBefore, bea.getPasswordHash());
    }

    @Test
    void a2_passwordOfOnlySpaces_isCheckedLikeAnyOther() {
        // Assumption 18: only an empty field keeps the password
        SessionManager.login(bea);
        assertThrows(ServiceException.class, () -> profiles.updateProfile("Bea", "        ", "        "));
    }

    @Test
    void a3_confirmationMustMatch() {
        SessionManager.login(bea);

        ServiceException e = assertThrows(ServiceException.class,
                () -> profiles.updateProfile("Bea Chan", NEW_PASSWORD, "New#Pass3"));

        assertEquals("The two passwords do not match.", e.getMessage());
        assertTrue(PasswordHasher.verify(OLD_PASSWORD, bea.getPasswordHash()));
    }

    @Test
    void checksRunInTheUseCaseOrder_nameBeforePassword() {
        SessionManager.login(bea);
        ServiceException e = assertThrows(ServiceException.class,
                () -> profiles.updateProfile("", "weak", "other"));
        assertEquals("Full name cannot be empty.", e.getMessage());
    }

    // ---------- Access ----------

    @Test
    void onlyASeller_canUpdateSellerFields() {
        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class,
                () -> profiles.updateSellerProfile("Bea", "", "", "bio", List.of()));
    }

    @Test
    void nobodyLoggedIn_orDeactivated_isRefused() {
        assertThrows(AccessDeniedException.class, () -> profiles.updateProfile("X", "", ""));
        assertThrows(AccessDeniedException.class, profiles::getMyProfile);

        SessionManager.login(bea);
        bea.setActive(false);
        assertThrows(AccessDeniedException.class, () -> profiles.updateProfile("X", "", ""));
    }

    @Test
    void getMyProfile_returnsTheLoggedInUser() {
        SessionManager.login(sam);
        assertSame(sam, profiles.getMyProfile());
    }

    // ---------- Integration: login and saving ----------

    @Test
    void changedPassword_appliesAtTheNextLogin() {
        AuthService auth = new AuthService(store);
        assertEquals(LoginResult.SUCCESS, auth.login("bea", OLD_PASSWORD, UserRole.BUYER));

        profiles.updateProfile("Bea Chan", NEW_PASSWORD, NEW_PASSWORD);
        SessionManager.logout();

        assertEquals(LoginResult.INVALID_CREDENTIALS, auth.login("bea", OLD_PASSWORD, UserRole.BUYER));
        assertEquals(LoginResult.SUCCESS, auth.login("bea", NEW_PASSWORD, UserRole.BUYER));
    }

    @Test
    void profileChanges_surviveARestart(@TempDir Path folder) {
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed();
        SessionManager.login(saved.findUser("charlie"));

        new ProfileService(saved).updateSellerProfile("Charlie Chan", NEW_PASSWORD, NEW_PASSWORD,
                "Now selling lamps", List.of(MeetupLocation.SPORTS_HALL));

        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(file));
        restarted.loadOrSeed();
        Seller charlie = (Seller) restarted.findUser("charlie");
        assertEquals("Charlie Chan", charlie.getFullName());
        assertEquals("Now selling lamps", charlie.getBio());
        assertEquals(List.of(MeetupLocation.SPORTS_HALL), charlie.getPreferredMeetupLocations());
        assertTrue(PasswordHasher.verify(NEW_PASSWORD, charlie.getPasswordHash()));
        assertFalse(PasswordHasher.verify(SeedData.DEMO_PASSWORD, charlie.getPasswordHash()));
    }
}
