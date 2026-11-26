package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.testsupport.AdminTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.MeetupLocation;

import static org.junit.jupiter.api.Assertions.*;

/** Access-denied tests for the admin listing service (UC2 Special Requirement 3). */
class AdminAccessControlTest {

    private AdminTestSupport support;
    private AdminListingService service;
    private SaleListing listing;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();
        service = new AdminListingService(support.store);
        listing = support.pendingSale();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    @Test
    void manageMarketplaceListings_UC2_SR3_noSession_approveIsDenied() {
        assertThrows(AccessDeniedException.class, () -> service.approveListing(listing.getId()));
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    @Test
    void manageMarketplaceListings_UC2_SR3_buyerSession_approveIsDenied() {
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.approveListing(listing.getId()));
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    @Test
    void manageMarketplaceListings_UC2_SR3_sellerSession_rejectIsDenied() {
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.rejectListing(listing.getId(), "Spam"));
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertTrue(support.store.getAllListingActions().isEmpty());
    }

// ---------- editListing ----------

    @Test
    void manageMarketplaceListings_UC2_SR3_noSession_editIsDenied() {
        assertThrows(AccessDeniedException.class, () -> service.editListing(listing.getId(),
                "New Title", "d", Category.TEXTBOOKS, ItemCondition.USED, MeetupLocation.LIBRARY, null, null, null));
        assertEquals("Book", listing.getTitle());                       // nothing changed
        assertTrue(support.store.getAllListingActions().isEmpty());
    }

    @Test
    void manageMarketplaceListings_UC2_SR3_buyerSession_editIsDenied() {
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.editListing(listing.getId(),
                "New Title", "d", Category.TEXTBOOKS, ItemCondition.USED, MeetupLocation.LIBRARY, null, null, null));
        assertEquals("Book", listing.getTitle());
        assertTrue(support.store.getAllListingActions().isEmpty());
    }

// ---------- removeListing ----------

    @Test
    void manageMarketplaceListings_UC2_SR3_noSession_removeIsDenied() {
        SaleListing live = support.liveSale();

        assertThrows(AccessDeniedException.class, () -> service.removeListing(live.getId(), "Spam"));
        assertEquals(ListingStatus.LIVE, live.getStatus());
        assertTrue(support.store.getAllListingActions().isEmpty());
    }

    @Test
    void manageMarketplaceListings_UC2_SR3_sellerSession_removeIsDenied() {
        SaleListing live = support.liveSale();
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.removeListing(live.getId(), "Spam"));
        assertEquals(ListingStatus.LIVE, live.getStatus());
        assertTrue(support.store.getAllListingActions().isEmpty());
    }




}
