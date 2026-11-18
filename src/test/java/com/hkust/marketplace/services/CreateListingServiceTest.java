package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2.3 Create New Listing: the rules the seller form relies on. Each test starts from an
 * empty in-memory store and a logged-in seller, so nothing here touches the app's save file.
 */
class CreateListingServiceTest {

    private static final Category CATEGORY = Category.TEXTBOOKS;
    private static final ItemCondition CONDITION = ItemCondition.USED;
    private static final MeetupLocation LOCATION = MeetupLocation.LIBRARY;
    private static final String DESCRIPTION = "A useful item";

    @TempDir
    Path folder;

    private TestClock clock;
    private DataStore store;
    private CreateListingService service;
    private Seller sam;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        service = new CreateListingService(store);
        sam = Fixtures.seller(store, "sam");
        SessionManager.login(sam);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    private Listing sale(double price) {
        return service.createSale("Book", CATEGORY, CONDITION, DESCRIPTION, LOCATION, price, List.of());
    }

    private Path image(String name) throws IOException {
        // The photo service checks the file's first bytes, so this is enough to be a PNG.
        Path file = folder.resolve(name);
        Files.write(file, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});
        return file;
    }

    // ---------- The three types ----------

    @Test
    void saleListing_isCreatedAsPendingReviewWithAPostedDate() {
        Listing listing = sale(120);

        assertInstanceOf(SaleListing.class, listing);
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertEquals("sam", listing.getSellerUsername());
        assertEquals(120, ((SaleListing) listing).getPrice());
        assertEquals(clock.now(), listing.getPostedDate());
        assertNotNull(listing.getId());
        assertTrue(listing.getPhotos().isEmpty());
        assertEquals(List.of(listing), store.getListingsBySeller("sam"));
    }

    @Test
    void lendListing_keepsItsDuration_andGiveawayHasNoExtraFields() {
        Listing lend = service.createLend("Calculator", Category.ELECTRONICS, CONDITION, DESCRIPTION,
                LOCATION, Duration.ofDays(7), List.of());
        Listing giveaway = service.createGiveaway("Lamp", Category.FURNITURE, CONDITION, DESCRIPTION,
                LOCATION, List.of());

        assertInstanceOf(LendListing.class, lend);
        assertEquals(Duration.ofDays(7), ((LendListing) lend).getLendingDuration());
        assertInstanceOf(GiveawayListing.class, giveaway);
        assertEquals(2, store.getListingsBySeller("sam").size());
    }

    @Test
    void titleAndDescription_areTrimmed() {
        Listing listing = service.createSale("  Book  ", CATEGORY, CONDITION, "  A book  ",
                LOCATION, 10, List.of());

        assertEquals("Book", listing.getTitle());
        assertEquals("A book", listing.getDescription());
    }

    // ---------- A1: required fields ----------

    @Test
    void everyRequiredField_isChecked() {
        assertEquals("The item name cannot be empty.",
                assertThrows(ServiceException.class, () -> service.createSale("  ", CATEGORY, CONDITION,
                        DESCRIPTION, LOCATION, 10, List.of())).getMessage());
        assertEquals("Please choose a category.",
                assertThrows(ServiceException.class, () -> service.createSale("Book", null, CONDITION,
                        DESCRIPTION, LOCATION, 10, List.of())).getMessage());
        assertEquals("Please choose the item's condition.",
                assertThrows(ServiceException.class, () -> service.createSale("Book", CATEGORY, null,
                        DESCRIPTION, LOCATION, 10, List.of())).getMessage());
        assertEquals("The description cannot be empty.",
                assertThrows(ServiceException.class, () -> service.createSale("Book", CATEGORY, CONDITION,
                        "  ", LOCATION, 10, List.of())).getMessage());
        assertEquals("Please choose a meet-up location.",
                assertThrows(ServiceException.class, () -> service.createSale("Book", CATEGORY, CONDITION,
                        DESCRIPTION, null, 10, List.of())).getMessage());

        assertTrue(store.getAllListings().isEmpty());
    }

    // ---------- A2: price ----------

    @Test
    void priceMustBeAPositiveNumber() {
        for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals("The price must be a positive number, such as 180 or 99.50.",
                    assertThrows(ServiceException.class, () -> sale(bad)).getMessage());
        }
        assertTrue(store.getAllListings().isEmpty());
        assertEquals(0.01, ((SaleListing) sale(0.01)).getPrice()); // boundary: the smallest positive
    }

    // ---------- A3: lending duration ----------

    @Test
    void lendingDurationMustBeAtLeastFiveSeconds() {
        assertEquals("The lending duration must be at least 5 seconds.",
                assertThrows(ServiceException.class, () -> service.createLend("Calculator",
                        Category.ELECTRONICS, CONDITION, DESCRIPTION, LOCATION,
                        Duration.ofSeconds(4), List.of())).getMessage());
        assertEquals("The lending duration must be at least 5 seconds.",
                assertThrows(ServiceException.class, () -> service.createLend("Calculator",
                        Category.ELECTRONICS, CONDITION, DESCRIPTION, LOCATION, null, List.of())).getMessage());

        Listing boundary = service.createLend("Calculator", Category.ELECTRONICS, CONDITION,
                DESCRIPTION, LOCATION, Duration.ofSeconds(5), List.of());
        assertEquals(Duration.ofSeconds(5), ((LendListing) boundary).getLendingDuration());
    }

    // ---------- A4: photos ----------

    @Test
    void photosAreStoredInOrder_andTheFirstIsTheThumbnail() throws IOException {
        List<Path> files = List.of(image("first.png"), image("second.png"));

        Listing listing = service.createSale("Book", CATEGORY, CONDITION, DESCRIPTION, LOCATION, 10, files);

        assertEquals(2, listing.getPhotos().size());
        assertEquals(listing.getPhotos().get(0), listing.getThumbnail());
        assertTrue(store.readPhoto(listing.getThumbnail()).isPresent());
    }

    @Test
    void moreThanSixPhotosAreRefused() throws IOException {
        List<Path> seven = List.of(image("1.png"), image("2.png"), image("3.png"), image("4.png"),
                image("5.png"), image("6.png"), image("7.png"));

        assertEquals("A listing can have at most 6 photos.",
                assertThrows(ServiceException.class, () -> service.createSale("Book", CATEGORY,
                        CONDITION, DESCRIPTION, LOCATION, 10, seven)).getMessage());
        assertTrue(store.getAllListings().isEmpty());
    }

    @Test
    void aFileThatIsNotAnImageIsRefused() throws IOException {
        Path text = Files.writeString(folder.resolve("notes.txt"), "not a photo");

        assertThrows(ServiceException.class, () -> service.createSale("Book", CATEGORY, CONDITION,
                DESCRIPTION, LOCATION, 10, List.of(text)));
        assertTrue(store.getAllListings().isEmpty());
    }

    // ---------- Steps 12-13: notifications ----------

    @Test
    void theAdminIsToldAndTheSellerGetsAConfirmation() {
        Fixtures.admin(store, "ada");

        Listing listing = sale(50);

        assertTrue(store.getNotificationsFor("ada").stream().anyMatch(n ->
                n.getCategory() == NotificationCategory.LISTING_SUBMITTED
                        && n.getContent().contains("submitted 'Book' for review")));
        assertTrue(store.getNotificationsFor("sam").stream().anyMatch(n ->
                n.getCategory() == NotificationCategory.LISTING_SUBMITTED
                        && n.getContent().contains("pending review")));
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    // ---------- Access control ----------

    @Test
    void onlyALoggedInSellerCanCreateAListing() {
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> sale(10));

        Buyer bea = Fixtures.buyer(store, "bea");
        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class, () -> sale(10));

        assertTrue(store.getAllListings().isEmpty());
    }
}
