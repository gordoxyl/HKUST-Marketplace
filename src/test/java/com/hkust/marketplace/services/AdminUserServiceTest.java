package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.AdminTestSupport;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests for Manage All User Accounts (Task 3.3): view and filter users (6.1),
 * edit user, deactivate, activate, and delete user (6.4). Every method starts with the admin
 * role check, and every data change runs in one transaction. This class does not use the ui layer.
 *
 * <p>Techniques: equivalence classes (role, active state, blank/non-blank reason) and
 * state-based testing (active vs inactive, existing vs deleted account).
 */
class AdminUserServiceTest {

    private AdminTestSupport support;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();               // admin ada, seller sam, buyer bea
        Fixtures.seller(support.store, "sue");
        Fixtures.buyer(support.store, "ben");
        service = new AdminUserService(support.store);
        support.loginAsAdmin();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    private List<Notification> notificationsOf(String username, NotificationCategory category) {
        return support.store.getNotificationsFor(username).stream()
                .filter(n -> n.getCategory() == category).toList();
    }

    private Offer offer(Listing listing, String buyer, double price) {
        return support.store.addOffer(new PriceOffer(listing.getId(), buyer, price));
    }

    // ---------- 6.2 Deactivate: basic flow ----------

    @Test
    void manageUserAccounts_6_2_basicFlow_deactivateSetsInactive_logsAudit_notifiesUser() {
        support.clock.advance(Duration.ofHours(2));

        service.deactivateUser("sam", "  Repeated policy violations ");

        assertFalse(support.store.findUser("sam").isActive());

        List<AuditLogEntry> log = support.store.getAuditLog();
        assertEquals(1, log.size());
        AuditLogEntry entry = log.get(0);
        assertEquals("ada", entry.getAdminUsername());
        assertEquals("sam", entry.getUsername());
        assertEquals("Deactivate account", entry.getActionTaken());
        assertEquals("Repeated policy violations", entry.getReason());   // trimmed
        assertNull(entry.getDataSnapshot());
        assertNotNull(entry.getLogReference());
        assertEquals(support.clock.now(), entry.getActionDate());        // store clock, not real time

        List<Notification> toUser = notificationsOf("sam", NotificationCategory.ACCOUNT_CHANGE);
        assertEquals(1, toUser.size());
        assertTrue(toUser.get(0).getContent().contains("deactivated"));
        assertTrue(toUser.get(0).getContent().contains("Reason: Repeated policy violations"));
    }

    @Test
    void manageUserAccounts_6_2_deactivatedUser_cannotPassTheAdminSessionCheck_orLogIn() {
        service.deactivateUser("bea", "Spam");

        support.loginAsBuyer();
        assertThrows(AccessDeniedException.class, AccessControl::requireBuyer);
    }

    // ---------- 6.2 Deactivate a seller ----------

    @Test
    void manageUserAccounts_6_2_deactivateSeller_declinesOpenOffersOnAllTheirListings_notifiesBuyers() {
        SaleListing book = support.liveSale();
        SaleListing book2 = support.liveSale();
        Offer beaOffer = offer(book, "bea", 90);
        PriceOffer benOffer = (PriceOffer) offer(book2, "ben", 80);
        benOffer.counter(95);                                            // countered counts as open

        service.deactivateUser("sam", "Fraud");

        assertEquals(OfferStatus.DECLINED, beaOffer.getStatus());
        assertEquals(OfferStatus.DECLINED, benOffer.getStatus());
        Notification toBea = notificationsOf("bea", NotificationCategory.OFFER_UPDATE).get(0);
        assertTrue(toBea.getContent().contains("the seller's account was deactivated"));
        assertEquals(1, notificationsOf("ben", NotificationCategory.OFFER_UPDATE).size());
    }

    @Test
    void manageUserAccounts_6_2_deactivateSeller_hidesListings_withoutChangingTheirStatus() {
        SaleListing live = support.liveSale();
        SaleListing pending = support.pendingSale();
        assertTrue(support.store.getLiveListings().contains(live));

        service.deactivateUser("sam", "Fraud");

        assertFalse(support.store.getLiveListings().contains(live));     // hidden from the marketplace
        assertFalse(support.store.isAvailable(live));
        assertEquals(ListingStatus.LIVE, live.getStatus());              // status is not touched
        assertEquals(ListingStatus.PENDING_REVIEW, pending.getStatus());
    }

    @Test
    void manageUserAccounts_6_2_deactivateSeller_keepsAcceptedDealsUntouched_andOtherSellersOffersOpen() {
        SaleListing sams = support.liveSale();
        Offer accepted = offer(sams, "bea", 90);
        accepted.accept(support.store.now());
        sams.acceptDeal();
        SaleListing sues = Fixtures.live(Fixtures.sale(support.store, "sue", 50));
        Offer otherSeller = offer(sues, "bea", 40);

        service.deactivateUser("sam", "Fraud");

        assertEquals(OfferStatus.ACCEPTED, accepted.getStatus());
        assertEquals(ListingStatus.RESERVED, sams.getStatus());
        assertEquals(OfferStatus.PENDING, otherSeller.getStatus());
    }

    // ---------- 6.2 Deactivate a buyer ----------

    @Test
    void manageUserAccounts_6_2_deactivateBuyer_declinesTheirOpenOffers_notifiesTheSellers() {
        SaleListing sams = support.liveSale();
        SaleListing sues = Fixtures.live(Fixtures.sale(support.store, "sue", 50));
        Offer onSam = offer(sams, "bea", 90);
        Offer onSue = offer(sues, "bea", 40);
        Offer bensOffer = offer(sams, "ben", 85);                        // another buyer: unaffected

        service.deactivateUser("bea", "Abusive chat");

        assertEquals(OfferStatus.DECLINED, onSam.getStatus());
        assertEquals(OfferStatus.DECLINED, onSue.getStatus());
        assertEquals(OfferStatus.PENDING, bensOffer.getStatus());

        List<Notification> toSam = notificationsOf("sam", NotificationCategory.OFFER_UPDATE);
        assertEquals(1, toSam.size());
        assertTrue(toSam.get(0).getContent().contains("'Book'"));
        assertTrue(toSam.get(0).getContent().contains("deactivated"));
        assertEquals(1, notificationsOf("sue", NotificationCategory.OFFER_UPDATE).size());
        assertEquals(1, notificationsOf("bea", NotificationCategory.ACCOUNT_CHANGE).size());
    }

    @Test
    void manageUserAccounts_6_2_deactivateBuyer_leavesAcceptedAndCompletedDealsUntouched() {
        SaleListing sams = support.liveSale();
        Offer accepted = offer(sams, "bea", 90);
        accepted.accept(support.store.now());
        sams.acceptDeal();

        service.deactivateUser("bea", "Abusive chat");

        assertEquals(OfferStatus.ACCEPTED, accepted.getStatus());
        assertEquals(ListingStatus.RESERVED, sams.getStatus());
        assertTrue(notificationsOf("sam", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    // ---------- 6.2 Alternative flows ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void manageUserAccounts_A1_reasonRequired_deactivateWithoutReasonChangesNothing(String reason) {
        SaleListing book = support.liveSale();
        Offer open = offer(book, "bea", 90);

        ServiceException e = assertThrows(ServiceException.class, () -> service.deactivateUser("sam", reason));

        assertEquals("A reason is required to deactivate an account.", e.getMessage());
        assertTrue(support.store.findUser("sam").isActive());
        assertEquals(OfferStatus.PENDING, open.getStatus());
        assertTrue(support.store.getAuditLog().isEmpty());
        assertTrue(notificationsOf("sam", NotificationCategory.ACCOUNT_CHANGE).isEmpty());
        assertTrue(notificationsOf("bea", NotificationCategory.OFFER_UPDATE).isEmpty());
    }

    @Test
    void manageUserAccounts_6_2_deactivateUnknownUser_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.deactivateUser("nobody", "Spam"));

        assertEquals("This account no longer exists.", e.getMessage());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUserAccounts_6_2_deactivateAlreadyDeactivatedUser_isRefusedAndLogsNothing() {
        service.deactivateUser("sam", "Fraud");

        ServiceException e = assertThrows(ServiceException.class, () -> service.deactivateUser("sam", "Again"));

        assertEquals("User is already deactivated.", e.getMessage());
        assertEquals(1, support.store.getAuditLog().size());
    }

    @Test
    void manageUserAccounts_6_2_adminAccountsCannotBeDeactivated_notEvenYourOwn() {
        Fixtures.admin(support.store, "alan");

        ServiceException other = assertThrows(ServiceException.class, () -> service.deactivateUser("alan", "Test"));
        ServiceException self = assertThrows(ServiceException.class, () -> service.deactivateUser("ada", "Test"));

        assertEquals("An admin account cannot be deactivated.", other.getMessage());
        assertEquals("An admin account cannot be deactivated.", self.getMessage());
        assertTrue(support.store.findUser("alan").isActive());
        assertTrue(support.store.findUser("ada").isActive());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    // ---------- 6.3 Activate ----------

    @Test
    void manageUserAccounts_6_3_basicFlow_activateSetsActive_logsAudit_notifiesUser() {
        service.deactivateUser("sam", "Fraud");
        support.clock.advance(Duration.ofDays(1));

        service.activateUser("sam");

        assertTrue(support.store.findUser("sam").isActive());

        List<AuditLogEntry> log = support.store.getAuditLog();
        assertEquals(2, log.size());
        AuditLogEntry entry = log.get(1);
        assertEquals("ada", entry.getAdminUsername());
        assertEquals("sam", entry.getUsername());
        assertEquals("Activate account", entry.getActionTaken());
        assertNull(entry.getReason());
        assertNull(entry.getDataSnapshot());
        assertEquals(support.clock.now(), entry.getActionDate());

        List<Notification> toUser = notificationsOf("sam", NotificationCategory.ACCOUNT_CHANGE);
        assertEquals(2, toUser.size());                                  // deactivated + activated
        assertTrue(toUser.get(0).getContent().contains("activated"));    // newest first
    }

    @Test
    void manageUserAccounts_6_3_activateSeller_makesTheirListingsVisibleAgain() {
        SaleListing live = support.liveSale();
        service.deactivateUser("sam", "Fraud");
        assertFalse(support.store.getLiveListings().contains(live));

        service.activateUser("sam");

        assertTrue(support.store.getLiveListings().contains(live));
        assertTrue(support.store.isAvailable(live));
    }

    @Test
    void manageUserAccounts_6_3_activateDoesNotRestoreOffersDeclinedByTheDeactivation() {
        SaleListing book = support.liveSale();
        Offer open = offer(book, "bea", 90);
        service.deactivateUser("sam", "Fraud");

        service.activateUser("sam");

        assertEquals(OfferStatus.DECLINED, open.getStatus());
    }

    @Test
    void manageUserAccounts_A2_activateAlreadyActiveUser_isRefusedAndLogsNothing() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.activateUser("sam"));

        assertEquals("User is already active.", e.getMessage());
        assertTrue(support.store.getAuditLog().isEmpty());
        assertTrue(notificationsOf("sam", NotificationCategory.ACCOUNT_CHANGE).isEmpty());
    }

    @Test
    void manageUserAccounts_6_3_activateUnknownUser_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.activateUser("nobody"));

        assertEquals("This account no longer exists.", e.getMessage());
    }

    // ---------- UC2 Special Requirement 3: access denied ----------

    @Test
    void manageUserAccounts_UC2_SR3_noSession_isDenied() {
        support.logout();

        assertThrows(AccessDeniedException.class, () -> service.deactivateUser("sam", "Fraud"));
        assertThrows(AccessDeniedException.class, () -> service.activateUser("sam"));
        assertTrue(support.store.findUser("sam").isActive());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUserAccounts_UC2_SR3_buyerSession_isDenied() {
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.deactivateUser("sam", "Fraud"));
        assertThrows(AccessDeniedException.class, () -> service.activateUser("sam"));
        assertTrue(support.store.findUser("sam").isActive());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUserAccounts_UC2_SR3_sellerSession_isDenied() {
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.deactivateUser("bea", "Spam"));
        assertThrows(AccessDeniedException.class, () -> service.activateUser("bea"));
        assertTrue(support.store.findUser("bea").isActive());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    // ---------- 6.4 Delete User (Item 3) ----------

    @Test
    void manageUsers_task33_deleteBuyer_keepsTheirReview_shownAsDeletedUser() {
        SaleListing book = support.liveSale();
        Offer deal = support.store.addOffer(new PriceOffer(book.getId(), "bea", 90));
        deal.accept(support.store.now());
        book.acceptDeal();
        new DealRules(support.store).complete(deal);
        Review review = support.store.addReview(new Review(deal.getId(), 4, "Good"));

        service.deleteUser("bea", "Spam");

        assertNull(support.store.findUser("bea"));
        assertTrue(support.store.getAllReviews().contains(review));            // still stored
        assertEquals("Deleted User", support.store.getReviewerName(review));   // shown as Deleted User
        assertEquals(1, support.store.getRatingCount("sam"));                  // still counted
        OptionalDouble avg = support.store.getAverageRating("sam");
        assertTrue(avg.isPresent());
        assertEquals(4.0, avg.getAsDouble());
        assertFalse(support.store.getAuditLog().get(0).getDataSnapshot().isBlank());
    }

    @Test
    void manageUsers_task33_deleteBuyer_declinesOpenOffers_andNotifiesSellers() {
        SaleListing book = support.liveSale();
        Offer openOffer = support.store.addOffer(new PriceOffer(book.getId(), "bea", 90));

        service.deleteUser("bea", "Spam");

        assertEquals(OfferStatus.DECLINED, openOffer.getStatus());
        List<Notification> toSam = notificationsOf("sam", NotificationCategory.OFFER_UPDATE);
        assertEquals(1, toSam.size());
        assertTrue(toSam.get(0).getContent().contains("cancelled because an account was deleted"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void manageUsers_A1_reasonRequired_deleteWithoutReasonChangesNothing(String reason) {
        ServiceException e = assertThrows(ServiceException.class, () -> service.deleteUser("bea", reason));

        assertEquals("A reason is required to delete an account.", e.getMessage());
        assertNotNull(support.store.findUser("bea"));
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUsers_task33_adminCannotBeDeleted() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.deleteUser("ada", "Test"));

        assertEquals("An admin account cannot be deleted.", e.getMessage());
        assertNotNull(support.store.findUser("ada"));
    }

    @Test
    void manageUsers_UC2_SR3_deleteUser_noSession_isDenied() {
        support.logout();
        assertThrows(AccessDeniedException.class, () -> service.deleteUser("bea", "Spam"));
    }


    // ---------- helpers for Task 3.3 edit and view ----------

    /**
     * A sale of the seller that the buyer bought and marked complete.
     */
    private Offer completedDeal(String buyer, double price) {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, "sam", price));
        Offer deal = support.store.addOffer(new PriceOffer(listing.getId(), buyer, price));
        DealRules rules = new DealRules(support.store);
        rules.accept(deal);
        rules.complete(deal);
        return deal;
    }

    private Review reviewed(String buyer, int rating) {
        Offer deal = completedDeal(buyer, 50);
        return support.store.addReview(new Review(deal.getId(), rating, "comment"));
    }

    // ---------- Task 3.3 edit user ----------

    @Test
    void manageUsers_task33_edit_buyer_savesTrimmedName_logsAudit_notifiesUser() {
        support.clock.advance(Duration.ofHours(1));

        service.editUser("bea", "  Bea Chan  ", "   ", List.of());

        assertEquals("Bea Chan", support.store.findUser("bea").getFullName());
        List<AuditLogEntry> log = support.store.getAuditLog();
        assertEquals(1, log.size());
        assertEquals("Edit account", log.get(0).getActionTaken());
        assertEquals("ada", log.get(0).getAdminUsername());
        assertEquals("bea", log.get(0).getUsername());
        assertNull(log.get(0).getReason());
        assertEquals(support.clock.now(), log.get(0).getActionDate());
        List<Notification> toUser = notificationsOf("bea", NotificationCategory.ACCOUNT_CHANGE);
        assertEquals(1, toUser.size());
        assertEquals("An administrator updated your account details.", toUser.get(0).getContent());
    }

    @Test
    void manageUsers_task33_edit_seller_savesBio_andLocationsInOrderWithoutDuplicates() {
        service.editUser("sam", "Sam Lee", "  Sells books ",
                List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE, MeetupLocation.LIBRARY));

        Seller sam = (Seller) support.store.findUser("sam");
        assertEquals("Sam Lee", sam.getFullName());
        assertEquals("Sells books", sam.getBio());
        assertEquals(List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE), sam.getPreferredMeetupLocations());
        assertEquals(1, support.store.getAuditLog().size());
        assertEquals(1, notificationsOf("sam", NotificationCategory.ACCOUNT_CHANGE).size());
    }

    @Test
    void manageUsers_task33_edit_seller_blankBioIsNoBio_andNullLocationsMeansNone() {
        service.editUser("sam", "Sam Seller", "Old bio", List.of(MeetupLocation.LIBRARY));

        service.editUser("sam", "Sam Seller", "   ", null);

        Seller sam = (Seller) support.store.findUser("sam");
        assertNull(sam.getBio());
        assertTrue(sam.getPreferredMeetupLocations().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void manageUsers_A1_edit_blankFullName_isRefusedAndChangesNothing(String name) {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.editUser("bea", name, null, null));

        assertEquals(AccountValidator.validateFullName(name), e.getMessage());
        assertEquals("Buyer bea", support.store.findUser("bea").getFullName());
        assertTrue(support.store.getAuditLog().isEmpty());
        assertTrue(notificationsOf("bea", NotificationCategory.ACCOUNT_CHANGE).isEmpty());
    }

    @Test
    void manageUsers_A1_edit_locationListWithNull_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.editUser("sam", "Sam",
                null, Arrays.asList(MeetupLocation.LIBRARY, null)));

        assertEquals("A meet-up location is not valid.", e.getMessage());
        assertEquals("Seller sam", support.store.findUser("sam").getFullName());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUsers_task33_edit_unknownUser_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.editUser("nobody", "Nobody", null, null));

        assertEquals("This account no longer exists.", e.getMessage());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUsers_task33_edit_adminAccount_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.editUser("ada", "New Name", null, null));

        assertEquals("An admin account cannot be edited here.", e.getMessage());
        assertEquals("Admin ada", support.store.findUser("ada").getFullName());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUsers_task33_edit_buyerWithBio_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.editUser("bea", "Bea", "Hi", null));

        assertEquals("Only a seller has a bio and meet-up locations.", e.getMessage());
        assertEquals("Buyer bea", support.store.findUser("bea").getFullName());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUsers_task33_edit_buyerWithLocations_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.editUser("bea", "Bea", null, List.of(MeetupLocation.LIBRARY)));

        assertEquals("Only a seller has a bio and meet-up locations.", e.getMessage());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUsers_UC2_SR3_edit_nonAdminSessions_areDenied() {
        support.logout();
        assertThrows(AccessDeniedException.class, () -> service.editUser("bea", "New", null, null));
        support.loginAsBuyer();
        assertThrows(AccessDeniedException.class, () -> service.editUser("bea", "New", null, null));
        support.loginAsSeller();
        assertThrows(AccessDeniedException.class, () -> service.editUser("bea", "New", null, null));

        assertEquals("Buyer bea", support.store.findUser("bea").getFullName());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    // ---------- Task 3.3 view user ----------

    @Test
    void manageUsers_task33_view_buyer_showsResidenceHall_noListingsOrRating_andSplitsDeals() {
        Buyer hal = new Buyer("hal", "Hal Hall", "hash", "Hall 3");
        support.store.addUser(hal);
        Offer completed = completedDeal("hal", 100);
        SaleListing other = support.liveSale();
        Offer open = offer(other, "hal", 40);

        UserDetails d = service.getUserDetails("hal");

        assertSame(hal, d.user());
        assertEquals("Hall 3", d.residenceHall());
        assertNull(d.bio());
        assertTrue(d.preferredLocations().isEmpty());
        assertTrue(d.activeListings().isEmpty());
        assertEquals(List.of(completed), d.completedDeals());
        assertEquals(List.of(open), d.pendingOffers());
        assertEquals(RatingSummary.NONE, d.rating());
    }

    @Test
    void manageUsers_task33_view_seller_activeListingsAreLiveReservedAndLentOnly() {
        SaleListing live = support.liveSale();
        SaleListing reserved = Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.RESERVED);
        LendListing lent = Fixtures.moveTo(Fixtures.lend(support.store, "sam", Duration.ofDays(7)), ListingStatus.LENT);
        Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.SOLD);
        Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.REMOVED);
        support.pendingSale();

        UserDetails d = service.getUserDetails("sam");

        assertEquals(Set.of(live, reserved, lent), Set.copyOf(d.activeListings()));
        assertEquals(3, d.activeListings().size());
        assertNull(d.residenceHall());
    }

    @Test
    void manageUsers_task33_view_seller_showsBioAndLocations() {
        service.editUser("sam", "Sam Seller", "Sells books", List.of(MeetupLocation.LIBRARY));

        UserDetails d = service.getUserDetails("sam");

        assertEquals("Sells books", d.bio());
        assertEquals(List.of(MeetupLocation.LIBRARY), d.preferredLocations());
    }

    @Test
    void manageUsers_task33_view_seller_ratingLeavesOutHiddenReviews() {
        reviewed("bea", 5);
        reviewed("ben", 3);
        Review hidden = reviewed("bea", 1);
        support.store.transaction(() -> hidden.setHidden(true));

        RatingSummary rating = service.getUserDetails("sam").rating();

        assertEquals(2, rating.count());
        assertEquals(4.0, rating.average());
    }

    @Test
    void manageUsers_task33_view_seller_includesDealsAndOffersOnTheirListings() {
        Offer completed = completedDeal("bea", 100);
        SaleListing book = support.liveSale();
        Offer open = offer(book, "ben", 80);

        UserDetails d = service.getUserDetails("sam");

        assertEquals(List.of(completed), d.completedDeals());
        assertEquals(List.of(open), d.pendingOffers());
    }

    @Test
    void manageUsers_task33_view_unknownUser_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.getUserDetails("nobody"));

        assertEquals("This account no longer exists.", e.getMessage());
    }

    @Test
    void manageUsers_task33_view_adminAccount_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.getUserDetails("ada"));

        assertEquals("This account no longer exists.", e.getMessage());
    }

    @Test
    void manageUsers_UC2_SR3_view_nonAdminSessions_areDenied() {
        support.logout();
        assertThrows(AccessDeniedException.class, () -> service.getUserDetails("sam"));
        support.loginAsBuyer();
        assertThrows(AccessDeniedException.class, () -> service.getUserDetails("sam"));
        support.loginAsSeller();
        assertThrows(AccessDeniedException.class, () -> service.getUserDetails("sam"));
    }

    @Test
    void manageUsers_task33_deleteUser_D1_unknownUsername_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.deleteUser("nobody", "Spam"));

        assertEquals("This account no longer exists.", e.getMessage());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void manageUsers_task33_deleteUser_D2_sellerWithOpenOffer_buyerGetsDeclinedNotice() {
        Listing book = support.store.addListing(
                new SaleListing("sam", "Book", Category.TEXTBOOKS, ItemCondition.USED,
                        null, MeetupLocation.LIBRARY, 100.0));
        book.approve();
        Offer offer = support.store.addOffer(new PriceOffer(book.getId(), "bea", 90));

        service.deleteUser("sam", "Policy violation");

        assertEquals(OfferStatus.DECLINED, offer.getStatus());
        assertTrue(support.store.getNotificationsFor("bea").stream()
                .anyMatch(n -> n.getCategory() == NotificationCategory.OFFER_UPDATE));
    }




    @Test
    void manageUsers_task36_editUser_refusedAction_sendsNoAdminNotice() {
        assertThrows(ServiceException.class, () -> service.editUser("bea", "", null, null));
        assertNoAdminNotices();
    }

    @Test
    void manageUsers_task36_deactivateUnknownUser_sendsNoAdminNotice() {
        assertThrows(ServiceException.class, () -> service.deactivateUser("nonexistent", "Not valid"));
        assertTrue(notificationsOf("ada", NotificationCategory.ACCOUNT_CHANGE).isEmpty());
        assertTrue(notificationsOf("ada2", NotificationCategory.ACCOUNT_CHANGE).isEmpty());
    }



    @Test
    void manageUsers_task36_editUser_sendsAdminNotice() {
        service.editUser("bea", "Beatrice New", null, null);

        String expected = "@ada edited the buyer account of Beatrice New (@bea).";
        assertAdminNoticeDeliveredOnce(expected);
    }

    @Test
    void manageUsers_task36_deactivateUser_sendsAdminNotice() {
        service.deactivateUser("bea", "Repeated policy violations");

        String expected = "@ada deactivated the buyer account of Buyer bea (@bea). "
                + "Reason: Repeated policy violations";
        assertAdminNoticeDeliveredOnce(expected);
    }

    @Test
    void manageUsers_task36_activateUser_sendsAdminNotice() {
        service.deactivateUser("bea", "temp");
        service.activateUser("bea");
        assertAdminNoticeDeliveredOnce("@ada reactivated the buyer account of Buyer bea (@bea).");
    }

    @Test
    void manageUsers_task36_deleteUser_sendsAdminNotice() {
        service.deleteUser("sam", "Policy violation");

        String expected = "@ada deleted the seller account of Seller sam (@sam). "
                + "Reason: Policy violation";
        assertAdminNoticeDeliveredOnce(expected);
    }

    @Test
    void manageUsers_task36_refusedDelete_sendsNoAdminNotice() {

        assertThrows(ServiceException.class,
                () -> service.deleteUser("nobody", "whatever"));

        assertNoAdminNotices();
    }

    @Test
    void manageUsers_task36_refusedActivate_sendsNoAdminNotice() {
        assertThrows(ServiceException.class, () -> service.activateUser("nobody"));
        assertNoAdminNotices();
    }


    // ---------- Task 3.8 N1: text search in findUsers ----------

    @Test
    void task3_8_findUsers_textMatchesUsername() {
        List<User> result = service.findUsers(UserFilter.none().withText("sam"));

        assertTrue(result.stream().anyMatch(u -> "sam".equals(u.getUsername())));
        assertTrue(result.stream().noneMatch(u -> "bea".equals(u.getUsername())));
    }

    @Test
    void task3_8_findUsers_textMatchesFullName() {
        service.editUser("bea", "Bea Chan", null, null);

        List<User> result = service.findUsers(UserFilter.none().withText("Chan"));

        assertTrue(result.stream().anyMatch(u -> "bea".equals(u.getUsername())));
    }

    @Test
    void task3_8_findUsers_textIsCaseInsensitiveAndTrimmed() {
        List<User> lower = service.findUsers(UserFilter.none().withText("sam"));
        List<User> upperSpaced = service.findUsers(UserFilter.none().withText("  SAM  "));

        assertEquals(
                lower.stream().map(User::getUsername).sorted().toList(),
                upperSpaced.stream().map(User::getUsername).sorted().toList());
        assertTrue(upperSpaced.stream().anyMatch(u -> "sam".equals(u.getUsername())));
    }

    @Test
    void task3_8_findUsers_nullFilterOrEmptyText_returnsEveryNonAdminUser() {
        List<User> noFilter = service.findUsers(null);
        List<User> blankFilter = service.findUsers(UserFilter.none().withText(""));
        List<User> spacedText = service.findUsers(UserFilter.none().withText("   "));

        for (List<User> rs : List.of(noFilter, blankFilter, spacedText)) {
            assertTrue(rs.stream().anyMatch(u -> "sam".equals(u.getUsername())));
            assertTrue(rs.stream().anyMatch(u -> "sue".equals(u.getUsername())));
            assertTrue(rs.stream().anyMatch(u -> "bea".equals(u.getUsername())));
            assertTrue(rs.stream().anyMatch(u -> "ben".equals(u.getUsername())));
            assertTrue(rs.stream().noneMatch(u -> u instanceof Admin),
                    "admin accounts must not be listed");
        }
    }

    private void assertAdminNoticeDeliveredOnce(String expectedText) {
        for (String admin : List.of("ada", "ada2")) {
            long count = notificationsOf(admin, NotificationCategory.ACCOUNT_CHANGE).stream()
                    .filter(n -> expectedText.equals(n.getContent()))
                    .count();
            assertEquals(1, count,
                    "admin " + admin + " should get exactly one notice: " + expectedText);
        }
    }

    private void assertNoAdminNotices() {
        for (String admin : List.of("ada", "ada2")) {
            assertTrue(notificationsOf(admin, NotificationCategory.ACCOUNT_CHANGE).isEmpty(),
                    "admin " + admin + " should get no ACCOUNT_CHANGE");
        }
    }
}

