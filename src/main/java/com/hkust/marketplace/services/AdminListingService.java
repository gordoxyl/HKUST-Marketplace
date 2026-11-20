package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Control class for Task 3.7 Manage Marketplace Listings and Task 3.2 Listing Review.
 * Every method starts with the admin role check. This class does not use the ui layer.
 */
public class AdminListingService {

    private final DataStore store;
    private final NotificationService notifications;
    private final DealRules dealRules;

    public AdminListingService() {
        this(DataStore.getInstance());
    }

    public AdminListingService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
        this.dealRules = new DealRules(store);
    }

    // ==========================================
    // Task 3.2: Approve & Reject
    // ==========================================

    /**
     * Approves a pending listing (Task 3.2 step 5.2.3): status Live, action recorded,
     * seller notified.
     *
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
     * Rejects a pending listing (Task 3.2 step 5.3.4): status Rejected, reason stored,
     * seller notified with the reason.
     *
     * @throws ServiceException if the reason is blank (A1), or the listing does not exist
     *         or is not Pending Review
     */
    public void rejectListing(String listingId, String reason) {
        Admin admin = AccessControl.requireAdmin();
        String cleanReason = requireReason(reason, "A reason is required to reject a listing.");

        store.transaction(() -> {
            Listing listing = requirePending(listingId, "rejected");
            listing.reject();
            store.addListingAction(new ListingActionRecord(
                    listing.getId(), admin.getUsername(), ListingAction.REJECT_LISTING, cleanReason));
            notifications.send(listing.getSellerUsername(), NotificationCategory.LISTING_REJECTED,
                    "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + cleanReason);
        });
    }

    // ==========================================
    // Task 3.7: Remove (Take Down)
    // ==========================================

    /**
     * Removes a listing (Task 3.7 step 5.5.4): status Removed, all open offers declined,
     * seller notified with the reason. Cannot be used on listings already Sold, Given or Removed.
     *
     * @throws ServiceException if the reason is blank (A1), the listing does not exist,
     *         or it is already Sold, Given or Removed
     */
    public void removeListing(String listingId, String reason) {
        Admin admin = AccessControl.requireAdmin();
        String cleanReason = requireReason(reason, "A reason is required to remove a listing.");

        store.transaction(() -> {
            Listing listing = store.findListing(listingId);
            if (listing == null) {
                throw new ServiceException("This listing no longer exists.");
            }
            if (!listing.canBeRemoved()) {
                throw new ServiceException(
                        "A listing that is already sold, given, or removed cannot be taken down.");
            }

            dealRules.declineOpenOffers(listing, NotificationCategory.LISTING_REMOVED,
                    "the admin removed the listing");
            listing.remove();

            store.addListingAction(new ListingActionRecord(
                    listing.getId(), admin.getUsername(), ListingAction.REMOVE_LISTING, cleanReason));

            notifications.send(listing.getSellerUsername(), NotificationCategory.LISTING_REMOVED,
                    "Your listing '" + listing.getTitle()
                            + "' has been taken down by an administrator. Reason: " + cleanReason);
        });
    }

    // ==========================================
    // Task 3.7: Edit Listing
    // ==========================================

    /**
     * Edits a listing (Task 3.7 step 5.4): title, description, category, meet-up location,
     * price (sale listings only), lending duration (lend listings only) and photos. The
     * status never changes (Special Requirement 2). The action is recorded and the seller
     * is notified. The seller's account may have been deleted; in that case the notification
     * is skipped silently and the edit is still saved.
     *
     * @param price     new price for a sale listing, or null to leave it unchanged
     * @param duration  new lending duration for a lend listing, or null to leave it unchanged
     * @param photoIds  new photo ids (at most 6), or null to keep the current photos
     * @throws ServiceException if a required field is missing or invalid (A1), the listing
     *         does not exist, or a price or duration is given for the wrong listing type
     */
    public void editListing(String listingId, String title, String description, Category category,
                            MeetupLocation meetupLocation, Double price, Duration duration,
                            List<String> photoIds) {
        Admin admin = AccessControl.requireAdmin();
        String cleanTitle = requireNonBlank(title, "The title is required.");
        if (category == null || meetupLocation == null) {
            throw new ServiceException("Category and meet-up location are required.");
        }
        if (price != null && (!(price > 0) || Double.isInfinite(price))) {
            throw new ServiceException("The price must be a positive number.");
        }
        if (duration != null) {
            requireValidLendingDuration(duration);
        }
        if (photoIds != null && photoIds.size() > Listing.MAX_PHOTOS) {
            throw new ServiceException("A listing can have at most " + Listing.MAX_PHOTOS + " photos.");
        }

        store.transaction(() -> {
            Listing listing = store.findListing(listingId);
            if (listing == null) {
                throw new ServiceException("This listing no longer exists.");
            }
            if (price != null && !(listing instanceof SaleListing)) {
                throw new ServiceException("Only a sale listing has a price.");
            }
            if (duration != null && !(listing instanceof LendListing)) {
                throw new ServiceException("Only a lend listing has a duration.");
            }

            listing.setTitle(cleanTitle);
            listing.setDescription(description);
            listing.setCategory(category);
            listing.setMeetupLocation(meetupLocation);
            if (price != null) {
                ((SaleListing) listing).setPrice(price);
            }
            if (duration != null) {
                ((LendListing) listing).setLendingDuration(duration);
            }
            if (photoIds != null) {
                listing.setPhotos(photoIds);
            }

            store.addListingAction(new ListingActionRecord(
                    listing.getId(), admin.getUsername(), ListingAction.EDIT_LISTING, null));
            // The seller's account may have been deleted; NotificationService skips a missing account
            if (store.findUser(listing.getSellerUsername()) != null) {
                notifications.send(listing.getSellerUsername(), NotificationCategory.ANNOUNCEMENT,
                        "An administrator edited your listing '" + listing.getTitle() + "'.");
            }
        });
    }

    // ==========================================
    // Task 3.7: Add Listing
    // ==========================================

    /**
     * Adds a listing on behalf of a seller (Task 3.7 step 5.6.4): creates the listing with
     * status Live (approved immediately), records the action and notifies the seller.
     *
     * @param price    required for SELL; must be null for LEND and GIVEAWAY
     * @param duration required for LEND; must be null for SELL and GIVEAWAY
     * @param photoIds optional, at most 6; null means no photos
     * @throws ServiceException if any required field is missing or invalid (A1, A4)
     */
    public void addListing(String sellerUsername, ListingType type, String title, String description,
                           Category category, ItemCondition condition, MeetupLocation meetupLocation,
                           Double price, Duration duration, List<String> photoIds) {
        Admin admin = AccessControl.requireAdmin();

        if (type == null) {
            throw new ServiceException("The listing type is required.");
        }
        String cleanTitle = requireNonBlank(title, "The title is required.");
        if (category == null || meetupLocation == null) {
            throw new ServiceException("Category and meet-up location are required.");
        }
        if (condition == null) {
            throw new ServiceException("The item condition is required.");
        }
        if (price != null && (!(price > 0) || Double.isInfinite(price))) {
            throw new ServiceException("The price must be a positive number.");
        }
        if (duration != null) {
            requireValidLendingDuration(duration);
        }
        if (photoIds != null && photoIds.size() > Listing.MAX_PHOTOS) {
            throw new ServiceException("A listing can have at most " + Listing.MAX_PHOTOS + " photos.");
        }

        switch (type) {
            case SELL -> {
                if (price == null) throw new ServiceException("A sale listing requires a price.");
                if (duration != null) throw new ServiceException("Only a lend listing has a duration.");
            }
            case LEND -> {
                if (duration == null) throw new ServiceException("A lend listing requires a lending duration.");
                if (price != null) throw new ServiceException("Only a sale listing has a price.");
            }
            case GIVEAWAY -> {
                if (price != null) throw new ServiceException("A giveaway listing has no price.");
                if (duration != null) throw new ServiceException("Only a lend listing has a duration.");
            }
        }

        store.transaction(() -> {
            User user = store.findUser(sellerUsername);
            if (!(user instanceof Seller seller)) {
                throw new ServiceException("Seller not found. Please enter a valid username.");
            }

            Listing listing = switch (type) {
                case SELL -> new SaleListing(seller.getUsername(), cleanTitle, category, condition,
                        description, meetupLocation, price);
                case LEND -> new LendListing(seller.getUsername(), cleanTitle, category, condition,
                        description, meetupLocation, duration);
                case GIVEAWAY -> new GiveawayListing(seller.getUsername(), cleanTitle, category, condition,
                        description, meetupLocation);
            };
            if (photoIds != null) {
                listing.setPhotos(photoIds);
            }

            store.addListing(listing);
            listing.approve();

            store.addListingAction(new ListingActionRecord(
                    listing.getId(), admin.getUsername(), ListingAction.ADD_LISTING, null));
            notifications.send(seller.getUsername(), NotificationCategory.ANNOUNCEMENT,
                    "An administrator added the listing '" + listing.getTitle() + "' on your behalf.");
        });
    }

    // ==========================================
    // Task 3.7: List and filters
    // ==========================================

    /**
     * Lists the marketplace listings for the admin (Task 3.7 steps 2-4), newest posted first.
     * Every chosen filter must match (AND); a null filter field means "no filter". A null
     * filter is treated as no filter.
     *
     * @param filter the filter options, or null for no filter
     * @return the matching listings; empty if none match (A2), never null
     * @throws ServiceException if the start of the date range is after its end
     */
    public List<Listing> findListings(ListingFilter filter) {
        AccessControl.requireAdmin();
        ListingFilter f = filter == null ? ListingFilter.none() : filter;
        if (f.fromDate() != null && f.toDate() != null && f.fromDate().isAfter(f.toDate())) {
            throw new ServiceException("The date range is invalid.");
        }

        Set<String> reportedIds = f.reportedOnly()
                ? store.getAllReports().stream().map(Report::getListingId).collect(Collectors.toSet())
                : Set.of();
        String sellerText = f.seller() == null ? "" : f.seller().strip().toLowerCase(Locale.ROOT);

        return store.read(() -> {
            List<Listing> result = new java.util.ArrayList<>(store.getAllListings().stream()
                    .filter(l -> f.status() == null || l.getStatus() == f.status())
                    .filter(l -> !f.reportedOnly() || reportedIds.contains(l.getId()))
                    .filter(l -> f.category() == null || l.getCategory() == f.category())
                    .filter(l -> f.type() == null || l.getType() == f.type())
                    .filter(l -> sellerText.isEmpty() || sellerMatches(l, sellerText))
                    .filter(l -> f.fromDate() == null || !l.getPostedDate().toLocalDate().isBefore(f.fromDate()))
                    .filter(l -> f.toDate() == null || !l.getPostedDate().toLocalDate().isAfter(f.toDate()))
                    .toList());
            result.sort(Comparator.comparing(Listing::getPostedDate).reversed());
            return result;
        });
    }

    private boolean sellerMatches(Listing listing, String lowerCaseText) {
        String username = listing.getSellerUsername().toLowerCase(Locale.ROOT);
        String fullName = store.getFullName(listing.getSellerUsername()).toLowerCase(Locale.ROOT);
        return username.contains(lowerCaseText) || fullName.contains(lowerCaseText);
    }

    /**
     * The details the admin sees when viewing one listing (Task 3.7 step 5.1): the listing,
     * its seller's full name and the reports filed on it. Only the admin may see who reported
     * (UC9 Special Requirement 4), which is why this is admin-only.
     *
     * @throws ServiceException if the listing does not exist
     */
    public ListingDetails getListingDetails(String listingId) {
        AccessControl.requireAdmin();
        return store.read(() -> {
            Listing listing = store.findListing(listingId);
            if (listing == null) {
                throw new ServiceException("This listing no longer exists.");
            }
            return new ListingDetails(listing,
                    store.getFullName(listing.getSellerUsername()),
                    store.getReportsForListing(listing.getId()));
        });
    }

    // ==========================================
    // helpers
    // ==========================================

    /** The listing, if it exists and is Pending Review; otherwise the user-facing error. */
    private Listing requirePending(String listingId, String action) {
        Listing listing = store.findListing(listingId);
        if (listing == null) {
            throw new ServiceException("This listing no longer exists.");
        }
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new ServiceException(
                    "Only a listing that is pending review can be " + action + ".");
        }
        return listing;
    }

    /** The reason without surrounding spaces; refuses a missing or blank reason. */
    private static String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(message);
        }
        return reason.strip();
    }

    /** The title without surrounding spaces; refuses blank. */
    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(message);
        }
        return value.strip();
    }

    /** Zero or negative, or shorter than the minimum borrow time (Create Listings A3). */
    private static void requireValidLendingDuration(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new ServiceException("The duration must be positive.");
        }
        if (duration.compareTo(BorrowRequest.MIN_BORROW_TIME) < 0) {
            throw new ServiceException("The lending duration must be at least "
                    + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME) + ".");
        }
    }
}