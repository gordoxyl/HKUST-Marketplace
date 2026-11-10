package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingAction;
import com.hkust.marketplace.models.ListingActionRecord;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.NotificationCategory;

/**
 * Control class for the admin's listing moderation (use case Manage Marketplace Listings,
 * Tasks 3.2 and 3.7). Every method starts with the admin role check, and every data change
 * runs in one transaction.
 */
public class AdminListingService {

    private final DataStore store;
    private final NotificationService notifications;

    public AdminListingService() {
        this(DataStore.getInstance());
    }

    public AdminListingService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    /**
     * Approves a pending listing (5.2.3): status Live, action recorded, seller notified.
     * @throws ServiceException if the listing does not exist or is not Pending Review
     */
    public void approveListing(String listingId) {
        Admin admin = AccessControl.requireAdmin();
        store.transaction(() -> {
            Listing listing = requirePending(listingId, "approved");
            listing.approve();
            store.addListingAction(new ListingActionRecord(
                    listing.getId(), admin.getUsername(), ListingAction.APPROVE_LISTING, null));
            notifications.send(listing.getSellerUsername(), NotificationCategory.LISTING_APPROVED,
                    "Your listing '" + listing.getTitle() + "' is now live!");
        });
    }

    /**
     * Rejects a pending listing (5.3.4): status Rejected, reason stored in the action record,
     * seller notified with the reason.
     * @throws ServiceException if the reason is blank (A1), or the listing does not exist or is not Pending Review
     */
    public void rejectListing(String listingId, String reason) {
        Admin admin = AccessControl.requireAdmin();
        if (reason == null || reason.isBlank()) {
            throw new ServiceException("A reason is required to reject a listing.");
        }
        String cleanReason = reason.trim();
        store.transaction(() -> {
            Listing listing = requirePending(listingId, "rejected");
            listing.reject();
            store.addListingAction(new ListingActionRecord(
                    listing.getId(), admin.getUsername(), ListingAction.REJECT_LISTING, cleanReason));
            notifications.send(listing.getSellerUsername(), NotificationCategory.LISTING_REJECTED,
                    "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + cleanReason);
        });
    }

    /** The listing, if it exists and waits for review; otherwise the user-facing error. */
    private Listing requirePending(String listingId, String action) {
        Listing listing = store.findListing(listingId);
        if (listing == null) {
            throw new ServiceException("This listing no longer exists.");
        }
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new ServiceException("Only a listing that is pending review can be " + action + ".");
        }
        return listing;
    }
}