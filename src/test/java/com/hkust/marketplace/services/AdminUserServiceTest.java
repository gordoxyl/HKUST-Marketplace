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

import static org.junit.jupiter.api.Assertions.*;

/** Tests for Manage All User Accounts 6.2 (deactivate) and 6.3 (activate). */
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
}