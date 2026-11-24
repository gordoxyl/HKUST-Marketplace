package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;

import java.util.ArrayList;
import java.util.List;
import com.hkust.marketplace.data.DeletedAccount;

import java.util.Objects;
import java.util.Locale;
/**
 * Control class for Manage All User Accounts (Task 3.3): view the user list, view one user,
 * edit, deactivate and activate. Every method starts with the admin role check, and every
 * data change runs in one transaction. This class does not use the ui layer.
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

    // ==========================================
    // Task 3.3: user list and filters
    // ==========================================

    /**
     * Lists the buyer and seller accounts for the admin. Admin accounts are never listed, and
     * deactivated accounts stay in the list so the admin can activate them again. Every chosen
     * filter must match; a null filter field means "no filter". The registration range is
     * inclusive on both ends.
     *
     * @param filter the filter options, or null for no filter
     * @return the matching users, never null; empty if none match or if the start of the
     *         registration range is after its end
     */
    public List<User> findUsers(UserFilter filter) {
        AccessControl.requireAdmin();
        UserFilter f = filter == null ? UserFilter.none() : filter;

        if (f.fromDate() != null && f.toDate() != null && f.fromDate().isAfter(f.toDate())) {
            return List.of();
        }


        String query = f.text() == null ? "" : f.text().strip().toLowerCase(Locale.ROOT);

        return store.read(() -> store.getAllUsers().stream()
                .filter(u -> !(u instanceof Admin))
                .filter(u -> f.role() == null || u.getRole() == f.role())
                .filter(u -> f.active() == null || u.isActive() == f.active())


                .filter(u -> query.isEmpty()
                        || u.getUsername().toLowerCase(Locale.ROOT).contains(query)
                        || u.getFullName().toLowerCase(Locale.ROOT).contains(query))

                .filter(u -> f.fromDate() == null || !u.getCreatedAt().isBefore(f.fromDate()))
                .filter(u -> f.toDate() == null || !u.getCreatedAt().isAfter(f.toDate()))
                .toList());
    }

    // ==========================================
    // Task 3.3: view user
    // ==========================================

    /**
     * The details of one buyer or seller account for VIEW USER (step 6.1): personal info,
     * active listings, completed deals, pending offers and rating. The password hash is
     * never part of the result.
     *
     * @throws ServiceException if the account does not exist or is an admin account
     */
    public UserDetails getUserDetails(String username) {
        AccessControl.requireAdmin();
        return store.read(() -> {
            User user = store.findUser(username);
            if (user == null || user instanceof Admin) {
                throw new ServiceException("This account no longer exists.");
            }

            List<Offer> related = new ArrayList<>(store.getOffersByBuyer(user.getUsername()));
            List<Listing> active = List.of();
            RatingSummary rating = RatingSummary.NONE;
            String residenceHall = null;
            String bio = null;
            List<MeetupLocation> locations = List.of();

            if (user instanceof Buyer buyer) {
                residenceHall = buyer.getResidenceHall();
            }
            if (user instanceof Seller seller) {
                bio = seller.getBio();
                locations = seller.getPreferredMeetupLocations();
                active = store.getListingsBySeller(user.getUsername()).stream()
                        .filter(l -> l.getStatus() == ListingStatus.LIVE
                                || l.getStatus() == ListingStatus.RESERVED
                                || l.getStatus() == ListingStatus.LENT)
                        .toList();
                related.addAll(store.getOffersForSeller(user.getUsername()));
                rating = store.getRating(user.getUsername());
            }

            List<Offer> completed = related.stream().filter(Offer::isCompleted).toList();
            List<Offer> pending = related.stream().filter(Offer::isOpen).toList();
            return new UserDetails(user, residenceHall, bio, locations, active,
                    completed, pending, rating);
        });
    }

    // ==========================================
    // Task 3.3: edit user
    // ==========================================

    /**
     * Edits a buyer or seller account with the same rules as the user's own Manage Profile,
     * except the password. The username and the role cannot be changed. A seller's bio (a
     * blank bio is stored as none) and preferred meet-up locations (order kept, duplicates
     * dropped) can be changed too. The action is recorded in the Audit Log and the user is
     * notified.
     *
     * @param bio       the seller's new bio, or null; must be null or blank for a buyer
     * @param locations the seller's new meet-up locations, or null; must be null or empty for a buyer
     * @throws ServiceException if the full name is blank, the account does not exist or is an
     *         admin account, or a bio or locations are given for a buyer
     */
    public void editUser(String username, String fullName, String bio, List<MeetupLocation> locations) {
        Admin admin = AccessControl.requireAdmin();
        String nameError = AccountValidator.validateFullName(fullName);
        if (nameError != null) {
            throw new ServiceException(nameError);
        }
        if (locations != null && locations.stream().anyMatch(Objects::isNull)) {
            throw new ServiceException("A meet-up location is not valid.");
        }

        store.transaction(() -> {
            User user = store.findUser(username);
            if (user == null) {
                throw new ServiceException("This account no longer exists.");
            }
            if (user instanceof Admin) {
                throw new ServiceException("An admin account cannot be edited here.");
            }
            boolean hasBio = bio != null && !bio.isBlank();
            boolean hasLocations = locations != null && !locations.isEmpty();
            if (!(user instanceof Seller) && (hasBio || hasLocations)) {
                throw new ServiceException("Only a seller has a bio and meet-up locations.");
            }

            user.setFullName(fullName.strip());
            if (user instanceof Seller seller) {
                seller.setBio(bio);
                seller.setPreferredMeetupLocations(locations);
            }

            store.addAuditLogEntry(new AuditLogEntry(
                    admin.getUsername(), user.getUsername(), "Edit account", null, null));
            notifications.send(user.getUsername(), NotificationCategory.ACCOUNT_CHANGE,
                    "An administrator updated your account details.");

            //notifications.sendToAdmins(NotificationCategory.ACCOUNT_CHANGE,
            //        AccountNotices.edited(user, admin));
        });
    }

    // ==========================================
    // Task 3.3: deactivate and activate
    // ==========================================

    /**
     * Deactivates a user account (step 6.2.4): sets the account inactive, declines the open
     * offers on a seller's listings, declines a buyer's own open offers and notifies the
     * affected sellers, records an audit log entry and notifies the user.
     *
     * <p>Listings of a deactivated seller disappear from Browse automatically because the
     * store checks seller.isActive(); their status is never changed.
     *
     * @throws ServiceException if the reason is blank (A1), the account does not exist, is
     *         already deactivated, or is an admin account
     */
    public void deactivateUser(String username, String reason) {
        Admin admin = AccessControl.requireAdmin();
        String cleanReason = requireReason(reason, "A reason is required to deactivate an account.");

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

            if (user instanceof Seller) {
                for (Listing listing : store.getListingsBySeller(username)) {
                    dealRules.declineOpenOffers(listing, NotificationCategory.OFFER_UPDATE,
                            "the seller's account was deactivated");
                }
            }

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
            //notifications.sendToAdmins(NotificationCategory.ACCOUNT_CHANGE,
              //      AccountNotices.deactivated(user, admin, cleanReason));
        });
    }

    /**
     * Activates a deactivated user account (step 6.3.1): sets the account active, records an
     * audit log entry and notifies the user. A seller's listings become visible again
     * automatically; declined offers are not restored.
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
            //notifications.sendToAdmins(NotificationCategory.ACCOUNT_CHANGE,
              //      AccountNotices.activated(user, admin));
        });
    }

    // ==========================================
    // Task 3.3: delete user (6.4)
    // ==========================================

    public void deleteUser(String username, String reason) {
        Admin admin = AccessControl.requireAdmin();
        String cleanReason = requireReason(reason, "A reason is required to delete an account.");

        store.transaction(() -> {
            User user = store.findUser(username);
            if (user == null) {
                throw new ServiceException("This account no longer exists.");
            }
            if (user instanceof Admin) {
                throw new ServiceException("An admin account cannot be deleted.");
            }

            DeletedAccount deleted = store.deleteAccount(username);

            store.addAuditLogEntry(new AuditLogEntry(admin.getUsername(), username,
                    "Delete account", cleanReason, deleted.snapshot()));

            //notifications.sendToAdmins(NotificationCategory.ACCOUNT_CHANGE,
             //       AccountNotices.deleted(user, admin, cleanReason));

            for (Offer offer : deleted.declinedOffers()) {
                Listing listing = store.getListingOf(offer);
                String otherUser = user instanceof Buyer ? listing.getSellerUsername() : offer.getBuyerUsername();

                notifications.send(otherUser, NotificationCategory.OFFER_UPDATE,
                        "An offer on '" + listing.getTitle() + "' was cancelled because an account was deleted.");
            }
        });
    }
    // ==========================================
    // helpers
    // ==========================================

    /** The reason without surrounding spaces; refuses a missing or blank reason. */
    private static String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(message);
        }
        return reason.strip();
    }
}