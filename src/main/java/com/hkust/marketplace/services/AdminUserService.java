package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.ui.TextInputs;
import java.util.List;
/**
 * Control class for Manage All User Accounts and Reputation (Task 3.3).
 * Every method starts with the admin role check and all data changes
 * run in one transaction.
 */
public class AdminUserService {

    private final DataStore store;
    private final NotificationService notifications;
    private final DealRules dealRules;

    public AdminUserService() {
        this(DataStore.getInstance());
    }

    public AdminUserService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
        this.dealRules = new DealRules(store);
    }

    /**
     * Deactivates a user account (Task 3.3 - Deactivate): sets the account inactive,
     * hides listings for sellers and declines their open offers, declines the buyer's
     * own open offers and notifies affected sellers, records an audit log entry and
     * notifies the user.
     *
     * Listings of a deactivated seller disappear from Browse automatically because
     * store.isLive and isAvailable check seller.isActive(); their status is never changed.
     *
     * @throws ServiceException if the reason is blank (A1), the account does not exist,
     *         is already deactivated, or is an admin account
     */
    public void deactivateUser(String username, String reason) {
        Admin admin = AccessControl.requireAdmin();
        String cleanReason = TextInputs.required(reason)
                .orElseThrow(() -> new ServiceException("A reason is required to deactivate an account."));

        store.transaction(() -> {
            User user = store.findUser(username);
            if (user == null) {
                throw new ServiceException("This account no longer exists.");
            }
            if (user instanceof Admin) {
                throw new ServiceException("An admin account cannot be deactivated.");
            }
            if (!user.isActive()) {
                throw new ServiceException("User is already deactivated.");
            }

            user.setActive(false);

            // Seller: decline every open offer on each of their listings
            if (user instanceof Seller) {
                for (Listing listing : store.getListingsBySeller(username)) {
                    dealRules.declineOpenOffers(listing, NotificationCategory.OFFER_UPDATE,
                            "the seller's account was deactivated");
                }
            }

            // Buyer: decline their own open offers and notify each affected seller
            for (Offer offer : store.getOffersByBuyer(username)) {
                if (offer.isOpen()) {
                    offer.decline();
                    Listing listing = store.getListingOf(offer);
                    notifications.send(listing.getSellerUsername(), NotificationCategory.OFFER_UPDATE,
                            "An offer on your listing '" + listing.getTitle()
                                    + "' was declined because the buyer's account was deactivated.");
                }
            }

            store.addAuditLogEntry(new AuditLogEntry(
                    admin.getUsername(), username, "Deactivate account", cleanReason, null));

            notifications.send(username, NotificationCategory.ACCOUNT_CHANGE,
                    "Your account has been deactivated. Reason: " + cleanReason);
        });
    }

    /**
     * Activates a deactivated user account (Task 3.3 - Activate): sets the account
     * active, records an audit log entry and notifies the user. For sellers, their
     * listings become visible again automatically; declined offers are not restored.
     *
     * @throws ServiceException if the account does not exist or is already active (A2)
     */
    public void activateUser(String username) {
        Admin admin = AccessControl.requireAdmin();

        store.transaction(() -> {
            User user = store.findUser(username);
            if (user == null) {
                throw new ServiceException("This account no longer exists.");
            }
            if (user.isActive()) {
                throw new ServiceException("User is already active.");
            }

            user.setActive(true);

            store.addAuditLogEntry(new AuditLogEntry(
                    admin.getUsername(), username, "Activate account", null, null));

            notifications.send(username, NotificationCategory.ACCOUNT_CHANGE,
                    "Your account has been activated.");
        });
    }


    /**
     * Lists the buyer and seller accounts for the admin (Task 3.3 Manage All Users). Admin
     * accounts are never listed, and deactivated accounts stay in the list so the admin can
     * activate them again. Every chosen filter must match; a null filter field means "no filter".
     * The registration range is inclusive on both ends.
     *
     * @param filter the filter options, or null for no filter
     * @return the matching users, never null; empty if none match or if the start of the
     *         registration range is after its end (Task 3.3 no results)
     */
    public List<User> findUsers(UserFilter filter) {
        AccessControl.requireAdmin();
        UserFilter f = filter == null ? UserFilter.none() : filter;

        if (f.fromDate() != null && f.toDate() != null && f.fromDate().isAfter(f.toDate())) {
            return List.of();
        }

        return store.read(() -> store.getAllUsers().stream()
                .filter(u -> !(u instanceof Admin))
                .filter(u -> f.role() == null || u.getRole() == f.role())
                .filter(u -> f.active() == null || u.isActive() == f.active())
                .filter(u -> f.fromDate() == null || !u.getCreatedAt().isBefore(f.fromDate()))
                .filter(u -> f.toDate() == null || !u.getCreatedAt().isAfter(f.toDate()))
                .toList());
    }
}