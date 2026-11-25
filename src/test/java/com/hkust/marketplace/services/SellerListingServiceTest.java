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
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.PriceOffer;
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
 * Task 2.4 My Listings: the rules behind the seller's Edit and Delete buttons
 * (Manage Listings 3.1 and 3.2). Each test starts from an empty in-memory store and a
 * logged-in seller, so nothing here touches the app's save file.
 */
class SellerListingServiceTest {

    private static final Category CATEGORY = Category.TEXTBOOKS;
    private static final ItemCondition CONDITION = ItemCondition.USED;
    private static final MeetupLocation LOCATION = MeetupLocation.LIBRARY;
    private static final String DESCRIPTION = "A useful item";

    @TempDir
    Path folder;

    private TestClock clock;
    private DataStore store;
    private SellerListingService service;
    private Seller sam;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        service = new SellerListingService(store);
        sam = Fixtures.seller(store, "sam");
        SessionManager.login(sam);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    private SaleListing liveSale() {
        return Fixtures.live(Fixtures.sale(store, "sam", 100));
    }

    private Path image(String name) throws IOException {
        // The photo service checks the file's first bytes, so this is enough to be a PNG.
        Path file = folder.resolve(name);
        Files.write(file, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});
        return file;
    }

    private Listing editSale(Listing listing, String title, double price) {
        return service.updateSale(listing.getId(), title, Category.ELECTRONICS, ItemCondition.NEW,
                "An updated description", MeetupLocation.SPORTS_HALL, price, List.of());
    }

    // ---------- My Listings step 1 ----------

    @Test
    void myListings_showsEveryListingOfTheSeller_andNoOneElses() {
        SaleListing mine = liveSale();
        Fixtures.seller(store, "sue");
        Fixtures.live(Fixtures.sale(store, "sue", 50));

        List<Listing> listings = service.myListings();

        assertEquals(List.of(mine), listings);
    }

    @Test
    void aSellerWithoutListings_getsAnEmptyList() {
        assertTrue(service.myListings().isEmpty());
    }

    @Test
    void onlyALoggedInSellerCanManageListings() {
        SaleListing listing = liveSale();
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> service.myListings());
        assertThrows(AccessDeniedException.class, () -> editSale(listing, "Book", 10));
        assertThrows(AccessDeniedException.class, () -> service.deleteListing(listing.getId()));

        Buyer bea = Fixtures.buyer(store, "bea");
        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class, () -> service.myListings());
        assertThrows(AccessDeniedException.class, () -> service.deleteListing(listing.getId()));

        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    // ---------- Manage Listings 3.1: edit ----------

    @Test
    void editingALiveListingWithNoOffers_changesTheFields_andKeepsItLive() {
        SaleListing listing = liveSale();

        Listing edited = editSale(listing, "  Advanced Calculus  ", 250);

        assertSame(listing, edited);
        assertEquals("Advanced Calculus", listing.getTitle());
        assertEquals(Category.ELECTRONICS, listing.getCategory());
        assertEquals(ItemCondition.NEW, listing.getCondition());
        assertEquals("An updated description", listing.getDescription());
        assertEquals(MeetupLocation.SPORTS_HALL, listing.getMeetupLocation());
        assertEquals(250, ((SaleListing) listing).getPrice());
        assertEquals(ListingStatus.LIVE, listing.getStatus()); // client answer #5: no review needed
        assertEquals(Fixtures.START, listing.getPostedDate()); // the posting date does not change
        assertTrue(store.getNotificationsFor("sam").isEmpty()); // and nobody is told it waits for review
    }

    @Test
    void editingARejectedListing_sendsItBackForReview() {
        SaleListing listing = Fixtures.moveTo(Fixtures.sale(store, "sam", 100), ListingStatus.REJECTED);

        editSale(listing, "Book", 120);

        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    @Test
    void editingALendListing_updatesTheDurationAndTheCommonFields() {
        LendListing calculator = Fixtures.live(
                Fixtures.lend(store, "sam", Duration.ofDays(7)));

        service.updateLend(calculator.getId(), "Graphing Calculator", Category.ELECTRONICS,
                ItemCondition.NEW, "Barely used", MeetupLocation.SPORTS_HALL, Duration.ofDays(3), List.of());

        assertEquals("Graphing Calculator", calculator.getTitle());
        assertEquals(Duration.ofDays(3), calculator.getLendingDuration());
        assertEquals(ListingStatus.LIVE, calculator.getStatus()); // client answer #5
    }

    @Test
    void editingAGiveawayListing_updatesTheCommonFields() {
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));

        service.updateGiveaway(lamp.getId(), "Desk Lamp", Category.FURNITURE, ItemCondition.NEW,
                "Works", MeetupLocation.SPORTS_HALL, List.of());

        assertEquals("Desk Lamp", lamp.getTitle());
        assertEquals(ListingStatus.LIVE, lamp.getStatus()); // client answer #5
    }

    @Test
    void editing_alockedListing_isRefused() {
        SaleListing reserved = Fixtures.moveTo(Fixtures.sale(store, "sam", 100), ListingStatus.RESERVED);

        assertEquals("This listing can no longer be edited or deleted.",
                assertThrows(ServiceException.class, () -> editSale(reserved, "Nope", 10)).getMessage());
        assertEquals("Book", reserved.getTitle());
        assertEquals(100, reserved.getPrice());
        assertEquals(ListingStatus.RESERVED, reserved.getStatus());
    }

    @Test
    void editingSomeoneElsesListing_isRefused() {
        Fixtures.seller(store, "sue");
        SaleListing hers = Fixtures.live(Fixtures.sale(store, "sue", 100));

        assertEquals("This listing is not yours.",
                assertThrows(ServiceException.class, () -> editSale(hers, "Nope", 10)).getMessage());
        assertEquals("Book", hers.getTitle());
        assertEquals(ListingStatus.LIVE, hers.getStatus());
    }

    @Test
    void editingAMissingListing_isRefused() {
        assertEquals("This listing is not yours.",
                assertThrows(ServiceException.class,
                        () -> service.updateSale("no-such-id", "Book", CATEGORY, CONDITION,
                                DESCRIPTION, LOCATION, 10, List.of())).getMessage());
    }

    @Test
    void editing_validatesTheFields_beforeChangingAnything() {
        SaleListing listing = liveSale();

        assertEquals("The item name cannot be empty.",
                assertThrows(ServiceException.class, () -> editSale(listing, "  ", 10)).getMessage());
        assertEquals("Please choose a category.",
                assertThrows(ServiceException.class, () -> service.updateSale(listing.getId(), "Book",
                        null, CONDITION, DESCRIPTION, LOCATION, 10, List.of())).getMessage());
        assertEquals("Please choose the item's condition.",
                assertThrows(ServiceException.class, () -> service.updateSale(listing.getId(), "Book",
                        CATEGORY, null, DESCRIPTION, LOCATION, 10, List.of())).getMessage());
        assertEquals("The description cannot be empty.",
                assertThrows(ServiceException.class, () -> service.updateSale(listing.getId(), "Book",
                        CATEGORY, CONDITION, "  ", LOCATION, 10, List.of())).getMessage());
        assertEquals("Please choose a meet-up location.",
                assertThrows(ServiceException.class, () -> service.updateSale(listing.getId(), "Book",
                        CATEGORY, CONDITION, DESCRIPTION, null, 10, List.of())).getMessage());
        assertEquals("The price must be a positive number, such as 180 or 99.50.",
                assertThrows(ServiceException.class, () -> editSale(listing, "Book", 0)).getMessage());
        assertEquals("The lending duration must be at least 5 seconds.",
                assertThrows(ServiceException.class, () -> service.updateLend("x", "Calculator",
                        Category.ELECTRONICS, CONDITION, DESCRIPTION, LOCATION, Duration.ofSeconds(4),
                        List.of())).getMessage());

        assertEquals("Book", listing.getTitle()); // the first failure changed nothing
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void anUpdateMustMatchTheTypeOfTheListing() {
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));

        assertThrows(IllegalArgumentException.class, () -> editSale(calculator, "Book", 10));
        assertEquals(ListingStatus.LIVE, calculator.getStatus());
    }

    // ---------- Manage Listings A3: open offers are declined ----------

    @Test
    void savingAnEdit_declinesTheOpenOffers_andTellsTheirBuyers() {
        SaleListing listing = liveSale();
        Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        PriceOffer bea = (PriceOffer) store.addOffer(new PriceOffer(listing.getId(), "bea", 80));
        PriceOffer ben = (PriceOffer) store.addOffer(new PriceOffer(listing.getId(), "ben", 90));
        clock.advance(Duration.ofHours(1));

        editSale(listing, "Book", 120);

        assertEquals(OfferStatus.DECLINED, bea.getStatus());
        assertEquals(OfferStatus.DECLINED, ben.getStatus());
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus()); // an edit that declines offers waits for review
        assertTrue(store.getNotificationsFor("bea").stream().anyMatch(n ->
                n.getCategory() == NotificationCategory.OFFER_UPDATE
                        && n.getContent().contains("declined because the seller changed the listing")));
        assertTrue(store.getNotificationsFor("ben").stream().anyMatch(n ->
                n.getContent().contains("declined because the seller changed the listing")));
    }

    @Test
    void aCounteredOffer_isOpen_andSoItIsDeclinedToo() {
        SaleListing listing = liveSale();
        Fixtures.buyer(store, "bea");
        PriceOffer offer = (PriceOffer) store.addOffer(new PriceOffer(listing.getId(), "bea", 80));
        offer.counter(95);

        editSale(listing, "Book", 120);

        assertEquals(OfferStatus.DECLINED, offer.getStatus());
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    // ---------- Photos ----------

    @Test
    void savingAnEditWithNoNewPhotos_keepsTheCurrentOnes() throws IOException {
        SaleListing listing = liveSale();
        listing.setPhotos(List.of(store.addPhoto(new byte[]{(byte) 0x89, 'P', 'N', 'G'})));
        List<String> before = listing.getPhotos();

        editSale(listing, "Book", 120);
        service.updateSale(listing.getId(), "Book", CATEGORY, CONDITION, DESCRIPTION, LOCATION,
                120, null);

        assertEquals(before, listing.getPhotos());
    }

    @Test
    void newPhotos_replaceTheCurrentOnes() throws IOException {
        SaleListing listing = liveSale();
        listing.setPhotos(List.of(store.addPhoto(new byte[]{(byte) 0x89, 'P', 'N', 'G'})));

        service.updateSale(listing.getId(), "Book", CATEGORY, CONDITION, DESCRIPTION, LOCATION,
                120, List.of(image("new.png")));

        assertEquals(1, listing.getPhotos().size());
        assertTrue(store.readPhoto(listing.getThumbnail()).isPresent());
    }

    @Test
    void moreThanSixNewPhotos_areRefused() throws IOException {
        SaleListing listing = liveSale();
        List<Path> seven = List.of(image("1.png"), image("2.png"), image("3.png"), image("4.png"),
                image("5.png"), image("6.png"), image("7.png"));

        assertEquals("A listing can have at most 6 photos.",
                assertThrows(ServiceException.class, () -> service.updateSale(listing.getId(), "Book",
                        CATEGORY, CONDITION, DESCRIPTION, LOCATION, 120, seven)).getMessage());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    // ---------- Steps 12-13: notifications ----------

    @Test
    void savingAnEdit_tellsTheAdminAndTheSellerThatItWaitsForReview() {
        Fixtures.admin(store, "ada");
        SaleListing listing = Fixtures.moveTo(Fixtures.sale(store, "sam", 100), ListingStatus.REJECTED);

        editSale(listing, "Advanced Calculus", 250);

        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());

        assertTrue(store.getNotificationsFor("ada").stream().anyMatch(n ->
                n.getCategory() == NotificationCategory.LISTING_SUBMITTED
                        && n.getContent().contains("updated 'Advanced Calculus' and submitted it for review")));
        assertTrue(store.getNotificationsFor("sam").stream().anyMatch(n ->
                n.getCategory() == NotificationCategory.LISTING_SUBMITTED
                        && n.getContent().contains("Your changes to 'Advanced Calculus' are pending review")));
    }

    // ---------- Manage Listings 3.2: delete ----------

    @Test
    void deletingAListing_marksItRemoved_andKeepsTheRecord() {
        SaleListing listing = liveSale();

        service.deleteListing(listing.getId());

        assertEquals(ListingStatus.REMOVED, listing.getStatus());
        assertSame(listing, store.findListing(listing.getId())); // 3.2.4: the record stays
        assertTrue(store.getAllListings().contains(listing));
    }

    @Test
    void deletingAListing_declinesTheOpenOffers_andTellsTheirBuyers() {
        SaleListing listing = liveSale();
        Fixtures.buyer(store, "bea");
        PriceOffer offer = (PriceOffer) store.addOffer(new PriceOffer(listing.getId(), "bea", 80));

        service.deleteListing(listing.getId());

        assertEquals(OfferStatus.DECLINED, offer.getStatus());
        assertTrue(store.getNotificationsFor("bea").stream().anyMatch(n ->
                n.getCategory() == NotificationCategory.OFFER_UPDATE
                        && n.getContent().contains("declined because the seller deleted the listing")));
    }

    @Test
    void deletingALockedListing_isRefused() {
        SaleListing sold = Fixtures.moveTo(Fixtures.sale(store, "sam", 100), ListingStatus.SOLD);

        assertEquals("This listing can no longer be edited or deleted.",
                assertThrows(ServiceException.class, () -> service.deleteListing(sold.getId())).getMessage());
        assertEquals(ListingStatus.SOLD, sold.getStatus());
    }

    @Test
    void deletingSomeoneElsesListing_isRefused() {
        Fixtures.seller(store, "sue");
        SaleListing hers = Fixtures.live(Fixtures.sale(store, "sue", 100));

        assertEquals("This listing is not yours.",
                assertThrows(ServiceException.class, () -> service.deleteListing(hers.getId())).getMessage());
        assertEquals(ListingStatus.LIVE, hers.getStatus());
        assertTrue(store.getAllListings().contains(hers));
    }

    @Test
    void aPendingOrRejectedListing_canBeDeletedToo() {
        SaleListing pending = Fixtures.sale(store, "sam", 100);
        SaleListing rejected = Fixtures.moveTo(Fixtures.sale(store, "sam", 50), ListingStatus.REJECTED);

        service.deleteListing(pending.getId());
        service.deleteListing(rejected.getId());

        assertEquals(ListingStatus.REMOVED, pending.getStatus());
        assertEquals(ListingStatus.REMOVED, rejected.getStatus());
    }
}
