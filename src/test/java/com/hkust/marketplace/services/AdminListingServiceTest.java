package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.AdminTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.hkust.marketplace.testsupport.Fixtures;

/**
 * Tests for Manage Marketplace Listings: 3.2 (approve), 3.2 (reject), 3.7 (edit),
 * 3.7 (remove / take down), 3.7 (add), and A1 input validation.
 * Access-denied tests for all methods are in {@link AdminAccessControlTest}.
 */
class AdminListingServiceTest {

    private AdminTestSupport support;
    private AdminListingService service;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();
        service = new AdminListingService(support.store);
        support.loginAsAdmin();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    // ---------- 3.2 Approve ----------

    @Test
    void manageMarketplaceListings__basicFlow_approveMakesListingLive_recordsAction_notifiesSeller() {
        SaleListing book = support.pendingSale();
        support.clock.advance(Duration.ofHours(3));

        service.approveListing(book.getId());

        assertEquals(ListingStatus.LIVE, book.getStatus());
        List<ListingActionRecord> actions = support.store.getListingActions(book.getId());
        assertEquals(1, actions.size());
        assertEquals(ListingAction.APPROVE_LISTING, actions.get(0).getTypeOfAction());
        assertEquals("ada", actions.get(0).getAdminUsername());
        assertEquals(support.clock.now(), actions.get(0).getActionDate()); // store clock, not real time
        Notification toSeller = support.store.getNotificationsFor("sam").get(0);
        assertEquals(NotificationCategory.LISTING_APPROVED, toSeller.getCategory());
        assertTrue(toSeller.getContent().contains("'Book' is now live"));
    }

    @Test
    void manageMarketplaceListings_5_2_approveRefusedWhenListingIsNotPendingReview() {
        SaleListing live = support.liveSale();

        ServiceException e = assertThrows(ServiceException.class, () -> service.approveListing(live.getId()));

        assertEquals("Only a listing that is pending review can be approved.", e.getMessage());
        assertTrue(support.store.getListingActions(live.getId()).isEmpty());
        assertTrue(support.store.getNotificationsFor("sam").isEmpty());
    }

    @Test
    void manageMarketplaceListings_5_2_approveRefusedWhenListingDoesNotExist() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.approveListing("L999"));
        assertEquals("This listing no longer exists.", e.getMessage());
    }

    // ---------- 3.2 Reject ----------

    @Test
    void manageMarketplaceListings_5_2_basicFlow_rejectStoresReason_notifiesSellerWithReason() {
        SaleListing book = support.pendingSale();

        service.rejectListing(book.getId(), "  Photo is blurry ");

        assertEquals(ListingStatus.REJECTED, book.getStatus());
        ListingActionRecord record = support.store.getListingActions(book.getId()).get(0);
        assertEquals(ListingAction.REJECT_LISTING, record.getTypeOfAction());
        assertEquals("Photo is blurry", record.getReason()); // trimmed
        assertEquals("ada", record.getAdminUsername());
        Notification toSeller = support.store.getNotificationsFor("sam").get(0);
        assertEquals(NotificationCategory.LISTING_REJECTED, toSeller.getCategory());
        assertTrue(toSeller.getContent().contains("Reason: Photo is blurry"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void manageMarketplaceListings_A1_reasonRequired_rejectWithoutReasonChangesNothing(String reason) {
        SaleListing book = support.pendingSale();

        ServiceException e = assertThrows(ServiceException.class, () -> service.rejectListing(book.getId(), reason));

        assertEquals("A reason is required to reject a listing.", e.getMessage());
        assertEquals(ListingStatus.PENDING_REVIEW, book.getStatus());
        assertTrue(support.store.getListingActions(book.getId()).isEmpty());
        assertTrue(support.store.getNotificationsFor("sam").isEmpty());
    }

    @Test
    void manageMarketplaceListings_5_2_rejectRefusedWhenListingIsNotPendingReview() {
        SaleListing live = support.liveSale();

        ServiceException e = assertThrows(ServiceException.class, () -> service.rejectListing(live.getId(), "Spam"));

        assertEquals("Only a listing that is pending review can be rejected.", e.getMessage());
        assertEquals(ListingStatus.LIVE, live.getStatus());
    }


    // ---------- 3.7 Remove (Take Down) ----------

    @Test
    void manageMarketplaceListings_5_5_basicFlow_removeListingChangesStatusDeclineOffersNotifiesSeller() {
        SaleListing book = support.liveSale();
        Offer offer1 = support.store.addOffer(new PriceOffer(book.getId(), "bea", 90));
        Offer offer2 = support.store.addOffer(new PriceOffer(book.getId(), "bea", 85));
        support.clock.advance(Duration.ofHours(1));

        service.removeListing(book.getId(), "Violates policy");

        assertEquals(ListingStatus.REMOVED, book.getStatus());
        assertEquals(OfferStatus.DECLINED, offer1.getStatus());
        assertEquals(OfferStatus.DECLINED, offer2.getStatus());

        ListingActionRecord record = support.store.getListingActions(book.getId()).get(0);
        assertEquals(ListingAction.REMOVE_LISTING, record.getTypeOfAction());
        assertEquals("Violates policy", record.getReason());
        assertEquals("ada", record.getAdminUsername());

        Notification toBuyer = support.store.getNotificationsFor("bea").get(0);
        assertEquals(NotificationCategory.LISTING_REMOVED, toBuyer.getCategory());
        assertTrue(toBuyer.getContent().contains("the admin removed the listing"));

        Notification toSeller = support.store.getNotificationsFor("sam").get(0);
        assertEquals(NotificationCategory.LISTING_REMOVED, toSeller.getCategory());
        assertTrue(toSeller.getContent().contains("taken down"));
        assertTrue(toSeller.getContent().contains("Reason: Violates policy"));
    }

    @Test
    void manageMarketplaceListings_5_5_removeRefusedForSoldListing() {
        SaleListing book = Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.SOLD);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.removeListing(book.getId(), "Test"));

        assertEquals("A listing that is already sold, given, or removed cannot be taken down.",
                e.getMessage());
        assertEquals(ListingStatus.SOLD, book.getStatus());
        assertTrue(support.store.getListingActions(book.getId()).isEmpty());
    }

    @Test
    void manageMarketplaceListings_5_5_removeRefusedForGivenListing() {
        GiveawayListing item = Fixtures.moveTo(Fixtures.giveaway(support.store, "sam"), ListingStatus.GIVEN);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.removeListing(item.getId(), "Test"));

        assertTrue(e.getMessage().contains("already sold, given, or removed"));
        assertEquals(ListingStatus.GIVEN, item.getStatus());
    }

    // ---------- 3.7 Edit ----------
    @Test
    void manageMarketplaceListings_5_4_basicFlow_editUpdatesFields_recordsAction() {
        SaleListing book = support.pendingSale();
        support.clock.advance(Duration.ofHours(2));

        service.editListing(book.getId(), "  Calculus Book ", "Cleaner copy", Category.TEXTBOOKS,
                MeetupLocation.NORTH_GATE, 150.0, null, List.of("p1", "p2"));

        assertEquals("Calculus Book", book.getTitle());
        assertEquals("Cleaner copy", book.getDescription());
        assertEquals(Category.TEXTBOOKS, book.getCategory());
        assertEquals(MeetupLocation.NORTH_GATE, book.getMeetupLocation());
        assertEquals(150.0, book.getPrice());
        assertEquals(List.of("p1", "p2"), book.getPhotos());

        List<ListingActionRecord> actions = support.store.getListingActions(book.getId());
        assertEquals(1, actions.size());
        assertEquals(ListingAction.EDIT_LISTING, actions.get(0).getTypeOfAction());
        assertNull(actions.get(0).getReason());
        assertEquals("ada", actions.get(0).getAdminUsername());
        assertEquals(support.clock.now(), actions.get(0).getActionDate());
    }

    @Test
    void manageMarketplaceListings_3_7_SR2_editDoesNotChangeStatus() {
        SaleListing pending = support.pendingSale();
        SaleListing live = support.liveSale();

        service.editListing(pending.getId(), "New A", "d", Category.TEXTBOOKS, MeetupLocation.LIBRARY, null, null, null);
        service.editListing(live.getId(), "New B", "d", Category.TEXTBOOKS, MeetupLocation.LIBRARY, null, null, null);

        assertEquals(ListingStatus.PENDING_REVIEW, pending.getStatus());
        assertEquals(ListingStatus.LIVE, live.getStatus());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -5, Double.NaN, Double.POSITIVE_INFINITY})
    void manageMarketplaceListings_A1_invalidPrice_editChangesNothing(double price) {
        SaleListing book = support.pendingSale();

        ServiceException e = assertThrows(ServiceException.class, () -> service.editListing(book.getId(),
                "New title", "d", Category.ELECTRONICS, MeetupLocation.NORTH_GATE, price, null, null));

        assertEquals("The price must be a positive number.", e.getMessage());
        assertEquals("Book", book.getTitle());
        assertEquals(100.0, book.getPrice());
        assertTrue(support.store.getListingActions(book.getId()).isEmpty());
        assertTrue(support.store.getNotificationsFor("sam").isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void manageMarketplaceListings_A1_blankTitle_editChangesNothing(String title) {
        SaleListing book = support.pendingSale();

        ServiceException e = assertThrows(ServiceException.class, () -> service.editListing(book.getId(),
                title, "d", Category.TEXTBOOKS, MeetupLocation.LIBRARY, null, null, null));

        assertEquals("The title is required.", e.getMessage());
        assertEquals("Book", book.getTitle());
        assertTrue(support.store.getListingActions(book.getId()).isEmpty());
    }

    @Test
    void manageMarketplaceListings_5_4_editNotifiesTheSeller() {
        SaleListing book = support.liveSale();

        service.editListing(book.getId(), "Calculus Book", "d", Category.TEXTBOOKS,
                MeetupLocation.LIBRARY, null, null, null);

        Notification toSeller = support.store.getNotificationsFor("sam").get(0);
        assertEquals(NotificationCategory.ANNOUNCEMENT, toSeller.getCategory());
        assertTrue(toSeller.getContent().contains("edited"));
        assertTrue(toSeller.getContent().contains("'Calculus Book'"));
    }

    @Test
    void manageMarketplaceListings_A1_priceOnNonSaleListing_isRefused() {
        LendListing calc = Fixtures.lend(support.store, "sam", Duration.ofDays(7));

        ServiceException e = assertThrows(ServiceException.class, () -> service.editListing(calc.getId(),
                "Calc", "d", Category.ELECTRONICS, MeetupLocation.LIBRARY, 50.0, null, null));

        assertEquals("Only a sale listing has a price.", e.getMessage());
        assertEquals("Calculator", calc.getTitle());
    }

    @Test
    void manageMarketplaceListings_A1_durationOnSaleListing_editIsRefused() {
        SaleListing book = support.pendingSale();

        ServiceException e = assertThrows(ServiceException.class, () -> service.editListing(book.getId(),
                "Book", "d", Category.TEXTBOOKS, MeetupLocation.LIBRARY, null, Duration.ofDays(7), null));

        assertEquals("Only a lend listing has a duration.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_moreThanSixPhotos_editIsRefused() {
        SaleListing book = support.pendingSale();
        List<String> seven = List.of("1", "2", "3", "4", "5", "6", "7");

        ServiceException e = assertThrows(ServiceException.class, () -> service.editListing(book.getId(),
                "Book", "d", Category.TEXTBOOKS, MeetupLocation.LIBRARY, null, null, seven));

        assertEquals("A listing can have at most 6 photos.", e.getMessage());
        assertTrue(book.getPhotos().isEmpty());
    }

    @Test
    void manageMarketplaceListings_3_7_editRefusedWhenListingDoesNotExist() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.editListing("L999",
                "x", "d", Category.TEXTBOOKS, MeetupLocation.LIBRARY, null, null, null));
        assertEquals("This listing no longer exists.", e.getMessage());
    }


    // ---------- 3.7 Add Listing (5.6) ----------


    @Test
    void manageMarketplaceListings_5_6_addSaleListing_createsLiveListing_notifiesSeller_recordsAction() {
        support.clock.advance(Duration.ofHours(1));

        service.addListing("sam", ListingType.SELL, "Calculus Book", "Cleaner copy",
                Category.TEXTBOOKS, ItemCondition.USED, MeetupLocation.NORTH_GATE,
                150.0, null, List.of("p1", "p2"));

        List<Listing> samsListings = support.store.getListingsBySeller("sam");
        assertEquals(1, samsListings.size());
        SaleListing added = (SaleListing) samsListings.get(0);
        assertEquals("Calculus Book", added.getTitle());
        assertEquals("Cleaner copy", added.getDescription());
        assertEquals(Category.TEXTBOOKS, added.getCategory());
        assertEquals(ItemCondition.USED, added.getCondition());
        assertEquals(MeetupLocation.NORTH_GATE, added.getMeetupLocation());
        assertEquals(150.0, added.getPrice());
        assertEquals(List.of("p1", "p2"), added.getPhotos());
        assertEquals(ListingStatus.LIVE, added.getStatus());
        assertEquals(support.clock.now(), added.getPostedDate());

        List<ListingActionRecord> actions = support.store.getListingActions(added.getId());
        assertEquals(1, actions.size());
        assertEquals(ListingAction.ADD_LISTING, actions.get(0).getTypeOfAction());
        assertNull(actions.get(0).getReason());
        assertEquals("ada", actions.get(0).getAdminUsername());
        assertEquals(support.clock.now(), actions.get(0).getActionDate());

        List<Notification> notifications = support.store.getNotificationsFor("sam");
        assertEquals(1, notifications.size());
        assertEquals(NotificationCategory.ANNOUNCEMENT, notifications.get(0).getCategory());
        assertTrue(notifications.get(0).getContent().contains("Calculus Book"));
    }

    @Test
    void manageMarketplaceListings_5_6_addLendListing_createsLiveListingWithDuration() {
        service.addListing("sam", ListingType.LEND, "Calculator", "Scientific calculator",
                Category.ELECTRONICS, ItemCondition.USED, MeetupLocation.LIBRARY,
                null, Duration.ofDays(7), null);

        List<Listing> samsListings = support.store.getListingsBySeller("sam");
        assertEquals(1, samsListings.size());
        LendListing added = (LendListing) samsListings.get(0);
        assertEquals("Calculator", added.getTitle());
        assertEquals(Duration.ofDays(7), added.getLendingDuration());
        assertEquals(ListingStatus.LIVE, added.getStatus());
    }

    @Test
    void manageMarketplaceListings_5_6_addGiveawayListing_createsLiveListing() {
        service.addListing("sam", ListingType.GIVEAWAY, "Lamp", "Free lamp",
                Category.FURNITURE, ItemCondition.USED, MeetupLocation.LIBRARY,
                null, null, null);

        List<Listing> samsListings = support.store.getListingsBySeller("sam");
        assertEquals(1, samsListings.size());
        GiveawayListing added = (GiveawayListing) samsListings.get(0);
        assertEquals("Lamp", added.getTitle());
        assertEquals(ListingStatus.LIVE, added.getStatus());
    }

    @Test
    void manageMarketplaceListings_A4_sellerNotFound_addChangesNothing() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "nobody", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, null));

        assertEquals("Seller not found. Please enter a valid username.", e.getMessage());
        assertTrue(support.store.getAllListings().isEmpty());
        assertTrue(support.store.getAllListingActions().isEmpty());
        assertTrue(support.store.getNotificationsFor("nobody").isEmpty());
    }

    @Test
    void manageMarketplaceListings_A4_usernameExistsButIsBuyer_addChangesNothing() {
        // bea 是 Buyer，不是 Seller
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "bea", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, null));

        assertEquals("Seller not found. Please enter a valid username.", e.getMessage());
        assertTrue(support.store.getAllListings().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -5, Double.NaN, Double.POSITIVE_INFINITY})
    void manageMarketplaceListings_A1_invalidPriceForSaleListing_addChangesNothing(double price) {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, price, null, null));

        assertEquals("The price must be a positive number.", e.getMessage());
        assertTrue(support.store.getAllListings().isEmpty());
    }

    @Test
    void manageMarketplaceListings_A1_nullPriceForSaleListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, null, null, null));

        assertEquals("A sale listing requires a price.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_durationOnSaleListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, Duration.ofDays(7), null));

        assertEquals("Only a lend listing has a duration.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_nullDurationForLendListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.LEND, "Calc", "d", Category.ELECTRONICS, ItemCondition.USED,
                MeetupLocation.LIBRARY, null, null, null));

        assertEquals("A lend listing requires a lending duration.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_negativeDurationForLendListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.LEND, "Calc", "d", Category.ELECTRONICS, ItemCondition.USED,
                MeetupLocation.LIBRARY, null, Duration.ofSeconds(-1), null));

        assertEquals("The duration must be positive.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_priceOnLendListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.LEND, "Calc", "d", Category.ELECTRONICS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, Duration.ofDays(7), null));

        assertEquals("Only a sale listing has a price.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_priceOnGiveawayListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.GIVEAWAY, "Lamp", "d", Category.FURNITURE, ItemCondition.USED,
                MeetupLocation.LIBRARY, 1.0, null, null));

        assertEquals("A giveaway listing has no price.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_moreThanSixPhotos_isRefused() {
        List<String> seven = List.of("1", "2", "3", "4", "5", "6", "7");

        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, seven));

        assertEquals("A listing can have at most 6 photos.", e.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void manageMarketplaceListings_A1_blankTitle_addChangesNothing(String title) {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, title, "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, null));

        assertEquals("The title is required.", e.getMessage());
        assertTrue(support.store.getAllListings().isEmpty());
    }

    @Test
    void manageMarketplaceListings_A1_nullCategory_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", null, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, null));

        assertEquals("Category and meet-up location are required.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_nullCondition_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, null,
                MeetupLocation.LIBRARY, 100.0, null, null));

        assertEquals("The item condition is required.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_addListingRefusedWhenNotAdmin() {
        support.loginAsSeller(); // 不是 admin
        assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, null));
    }


    // ---------- Bug fixes: minimum lending duration (Create Listings A3), null type, deleted seller ----------

    @Test
    void manageMarketplaceListings_A1_addLend_fourSecondsIsRefused_fiveSecondsIsAccepted() {
        // Boundary values around BorrowRequest.MIN_BORROW_TIME (5 s)
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.LEND, "Calc", "d", Category.ELECTRONICS, ItemCondition.USED,
                MeetupLocation.LIBRARY, null, Duration.ofSeconds(4), null));
        assertEquals("The lending duration must be at least 5 seconds.", e.getMessage());
        assertTrue(support.store.getAllListings().isEmpty());

        service.addListing("sam", ListingType.LEND, "Calc", "d", Category.ELECTRONICS, ItemCondition.USED,
                MeetupLocation.LIBRARY, null, Duration.ofSeconds(5), null);

        LendListing added = (LendListing) support.store.getListingsBySeller("sam").get(0);
        assertEquals(Duration.ofSeconds(5), added.getLendingDuration());
    }

    @Test
    void manageMarketplaceListings_A1_editLend_fourSecondsIsRefusedWithoutChangingAnything_fiveSecondsIsAccepted() {
        LendListing calc = Fixtures.lend(support.store, "sam", Duration.ofDays(7));

        ServiceException e = assertThrows(ServiceException.class, () -> service.editListing(calc.getId(),
                "New title", "d", Category.ELECTRONICS, MeetupLocation.NORTH_GATE, null, Duration.ofSeconds(4), null));

        assertEquals("The lending duration must be at least 5 seconds.", e.getMessage());
        assertEquals("Calculator", calc.getTitle());                     // nothing was changed first
        assertEquals(Duration.ofDays(7), calc.getLendingDuration());
        assertTrue(support.store.getListingActions(calc.getId()).isEmpty());

        service.editListing(calc.getId(), "New title", "d", Category.ELECTRONICS,
                MeetupLocation.NORTH_GATE, null, Duration.ofSeconds(5), null);

        assertEquals("New title", calc.getTitle());
        assertEquals(Duration.ofSeconds(5), calc.getLendingDuration());
    }

    @Test
    void manageMarketplaceListings_A1_zeroDurationForLendListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.LEND, "Calc", "d", Category.ELECTRONICS, ItemCondition.USED,
                MeetupLocation.LIBRARY, null, Duration.ZERO, null));
        assertEquals("The duration must be positive.", e.getMessage());
    }

    @Test
    void manageMarketplaceListings_A1_nullListingType_addIsRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", null, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, null));

        assertEquals("The listing type is required.", e.getMessage());
        assertTrue(support.store.getAllListings().isEmpty());
        assertTrue(support.store.getAllListingActions().isEmpty());
    }

    @Test
    void manageMarketplaceListings_3_7_editListingOfDeletedSeller_worksAndSkipsTheNotification() {
        // store.deleteAccount (Gordon, 6.4.4) keeps the listing as archive and removes the account
        SaleListing book = support.liveSale();
        support.store.deleteAccount("sam");
        assertNull(support.store.findUser("sam"));

        assertDoesNotThrow(() -> service.editListing(book.getId(), "Archived Book", "d",
                Category.TEXTBOOKS, MeetupLocation.LIBRARY, null, null, null));

        assertEquals("Archived Book", book.getTitle());
        assertEquals(ListingStatus.REMOVED, book.getStatus());           // SR2: edit never changes the status
        List<ListingActionRecord> actions = support.store.getListingActions(book.getId());
        assertEquals(1, actions.size());
        assertEquals(ListingAction.EDIT_LISTING, actions.get(0).getTypeOfAction());
        assertTrue(support.store.getNotificationsFor("sam").isEmpty());
    }

    @Test
    void manageMarketplaceListings_A4_addForDeletedSeller_isRefusedAndChangesNothing() {
        support.store.deleteAccount("sam");

        ServiceException e = assertThrows(ServiceException.class, () -> service.addListing(
                "sam", ListingType.SELL, "Book", "d", Category.TEXTBOOKS, ItemCondition.USED,
                MeetupLocation.LIBRARY, 100.0, null, null));

        assertEquals("Seller not found. Please enter a valid username.", e.getMessage());
        assertTrue(support.store.getAllListingActions().isEmpty());
        assertTrue(support.store.getNotificationsFor("sam").isEmpty());
    }
}