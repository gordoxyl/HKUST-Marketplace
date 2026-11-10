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

/** Tests for Manage Marketplace Listings 5.2 (approve) and 5.3 (reject). */
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

    // ---------- 5.2 Approve ----------

    @Test
    void manageMarketplaceListings_5_2_basicFlow_approveMakesListingLive_recordsAction_notifiesSeller() {
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

    // ---------- 5.3 Reject ----------

    @Test
    void manageMarketplaceListings_5_3_basicFlow_rejectStoresReason_notifiesSellerWithReason() {
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
    void manageMarketplaceListings_5_3_rejectRefusedWhenListingIsNotPendingReview() {
        SaleListing live = support.liveSale();

        ServiceException e = assertThrows(ServiceException.class, () -> service.rejectListing(live.getId(), "Spam"));

        assertEquals("Only a listing that is pending review can be rejected.", e.getMessage());
        assertEquals(ListingStatus.LIVE, live.getStatus());
    }
}