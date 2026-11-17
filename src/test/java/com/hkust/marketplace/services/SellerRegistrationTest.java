package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2.1 Seller Registration. Exercises the seller-specific parts of the shared
 * registration service: the optional bio, the optional (and multiple) preferred meet-up
 * locations, and the same username/password rules as buyer registration.
 *
 * <p>The screen itself is not tested here (controllers have no automated tests in this
 * project; see the README coverage policy). It only calls the methods checked below.
 */
class SellerRegistrationTest {

    private static final String PASSWORD = "Secure#123";

    private final DataStore store = seededStore();
    private final AuthService auth = new AuthService(store);

    /** A fresh store with the demo data, so each test is independent. */
    private static DataStore seededStore() {
        DataStore store = new DataStore();
        SeedData.populate(store);
        return store;
    }

    private String register(String username, String fullName, String bio, List<MeetupLocation> locations) {
        return auth.registerSeller(username, fullName, PASSWORD, PASSWORD, bio, locations);
    }

    // ---------- Optional profile details (Task 2.1) ----------

    @Test
    void registersWithEveryChosenMeetupLocationInOrder() {
        assertNull(register("sam", "Sam Seller", "Books and gadgets",
                List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE, MeetupLocation.STUDENT_CENTRE)));

        Seller seller = assertInstanceOf(Seller.class, store.findUser("sam"));
        assertEquals(List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE, MeetupLocation.STUDENT_CENTRE),
                seller.getPreferredMeetupLocations());
    }

    @Test
    void duplicateMeetupLocationsAreStoredOnlyOnce() {
        assertNull(register("sam", "Sam Seller", null,
                List.of(MeetupLocation.LIBRARY, MeetupLocation.LIBRARY)));

        assertEquals(List.of(MeetupLocation.LIBRARY),
                ((Seller) store.findUser("sam")).getPreferredMeetupLocations());
    }

    @Test
    void meetupLocationsAndBioAreOptional() {
        assertNull(register("sam", "Sam Seller", null, null));
        assertNull(register("sally", "Sally Seller", "", List.of()));

        for (String username : List.of("sam", "sally")) {
            Seller seller = (Seller) store.findUser(username);
            assertTrue(seller.getPreferredMeetupLocations().isEmpty());
            assertNull(seller.getBio());
        }
    }

    @Test
    void bioIsTrimmedAndBlankBioMeansNoBio() {
        assertNull(register("sam", "Sam Seller", "  Selling books  ", null));
        assertNull(register("sally", "Sally Seller", "   ", null));

        assertEquals("Selling books", ((Seller) store.findUser("sam")).getBio());
        assertNull(((Seller) store.findUser("sally")).getBio());
    }

    // ---------- The account that gets created ----------

    @Test
    void newSellerIsActiveWithASellerRoleAndALowerCaseUsername() {
        assertNull(register("Sam", "Sam Seller", null, null));

        Seller seller = assertInstanceOf(Seller.class, store.findUser("sam"));
        assertEquals("sam", seller.getUsername());
        assertEquals(UserRole.SELLER, seller.getRole());
        assertTrue(seller.isActive());
        assertNotNull(seller.getCreatedAt());
    }

    @Test
    void passwordIsStoredHashedAndVerifies() {
        assertNull(register("sam", "Sam Seller", null, null));

        String stored = store.findUser("sam").getPasswordHash();
        assertNotEquals(PASSWORD, stored);
        assertTrue(PasswordHasher.verify(PASSWORD, stored));
    }

    // ---------- Same rules as buyer registration ----------

    @Test
    void usernameMustBeUniqueAcrossBuyersSellersAndAdmins() {
        assertEquals("This username is already in use.", register("alice", "Other Alice", null, null));
        assertEquals("This username is already in use.", register("charlie", "Other Charlie", null, null));
        assertEquals("This username is already in use.", register("ADMIN", "Other Admin", null, null));

        assertInstanceOf(Buyer.class, store.findUser("alice")); // existing accounts untouched
        assertEquals(UserRole.SELLER, store.findUser("charlie").getRole());
    }

    @Test
    void weakPasswordIsRejectedAndNothingIsStored() {
        String error = auth.registerSeller("sam", "Sam Seller", "weakpass", "weakpass", null, null);

        assertNotNull(error);
        assertNull(store.findUser("sam"));
    }

    @Test
    void mismatchedConfirmationIsRejectedAndNothingIsStored() {
        assertEquals("The two passwords do not match.",
                auth.registerSeller("sam", "Sam Seller", PASSWORD, "Secure#124", null, null));

        assertNull(store.findUser("sam"));
    }

    @Test
    void emptyFullNameIsRejectedAndNothingIsStored() {
        assertEquals("Full name cannot be empty.", register("sam", "   ", null, null));
        assertNull(store.findUser("sam"));
    }
}
