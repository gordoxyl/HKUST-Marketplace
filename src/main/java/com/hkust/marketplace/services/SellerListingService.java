package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Task 2.4 My Listings: the seller's own listings and the two things the seller can do with
 * them, edit (Manage Listings 3.1) and delete (3.2). The screen only shows the cards and the
 * form; every rule below is checked here and covered by {@code SellerListingServiceTest}.
 *
 * <ul>
 *   <li><b>A1</b> - the form's required fields are checked before anything changes, with the
 *       same messages as Create Listings ({@link ListingFormRules}).</li>
 *   <li><b>A2</b> - a Reserved, Sold, Lent, Given or Removed listing is locked: it cannot be
 *       edited or deleted ({@link Listing#isLocked()}).</li>
 *   <li><b>A3</b> - saving an edit or deleting a listing declines its open offers and tells each
 *       buyer why ({@link DealRules#declineOpenOffers}).</li>
 * </ul>
 *
 * <p>Saving an edit normally sends the listing back to Pending Review ({@link
 * Listing#resubmit()}) and tells the admin, exactly like a newly posted listing. The exception
 * is the client's answer #5 of 16 Nov: an edit of a <b>Live</b> listing with <b>no open
 * offers</b> keeps it Live, so the seller's small corrections do not wait for review again. A
 * Live listing whose edit declines open offers is resubmitted, as are Rejected and Pending
 * Review listings. Deleting keeps the record with status Removed ({@link Listing#withdraw()}),
 * as Manage Listings 3.2.4 requires. The type of a listing never changes after it is posted,
 * so each update method only accepts its own type.
 */
public class SellerListingService {

    private final DataStore store;
    private final DealRules rules;
    private final PhotoService photos;
    private final NotificationService notifications;

    public SellerListingService() {
        this(DataStore.getInstance());
    }

    public SellerListingService(DataStore store) {
        this.store = store;
        this.rules = new DealRules(store);
        this.photos = new PhotoService(store);
        this.notifications = new NotificationService(store);
    }

    /**
     * My Listings step 1: every listing the logged-in seller has posted, in any status, so the
     * screen can show the sold and removed ones too.
     * @throws AccessDeniedException if the logged-in user is not a seller
     */
    public List<Listing> myListings() {
        Seller seller = AccessControl.requireSeller();
        return store.getListingsBySeller(seller.getUsername());
    }

    /**
     * Manage Listings 3.1: saves the seller's changes to a Sell listing and sends it back for
     * review (3.1.6). Photos are optional; new ones replace the current photos, and passing
     * none keeps them.
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if the fields are invalid, the listing is not the seller's,
     *                               or it is locked; nothing changes then
     */
    public Listing updateSale(String listingId, String title, Category category,
                              ItemCondition condition, String description,
                              MeetupLocation meetupLocation, double price,
                              List<Path> newPhotoFiles) {
        Seller seller = AccessControl.requireSeller();
        ListingFormRules.requireCommonFields(title, category, condition, description, meetupLocation);
        ListingFormRules.requirePrice(price);
        return saveEdit(listingId, seller, ListingType.SELL, newPhotoFiles, listing -> {
            applyCommonFields(listing, title, category, condition, description, meetupLocation);
            ((SaleListing) listing).setPrice(price);
        });
    }

    /**
     * Manage Listings 3.1: saves the seller's changes to a Lend listing and sends it back for
     * review (3.1.6).
     * @param lendingDuration how long the item may be borrowed; at least the minimum borrow time
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if the fields are invalid, the listing is not the seller's,
     *                               or it is locked; nothing changes then
     */
    public Listing updateLend(String listingId, String title, Category category,
                              ItemCondition condition, String description,
                              MeetupLocation meetupLocation, Duration lendingDuration,
                              List<Path> newPhotoFiles) {
        Seller seller = AccessControl.requireSeller();
        ListingFormRules.requireCommonFields(title, category, condition, description, meetupLocation);
        ListingFormRules.requireLendingDuration(lendingDuration);
        return saveEdit(listingId, seller, ListingType.LEND, newPhotoFiles, listing -> {
            applyCommonFields(listing, title, category, condition, description, meetupLocation);
            ((LendListing) listing).setLendingDuration(lendingDuration);
        });
    }

    /**
     * Manage Listings 3.1: saves the seller's changes to a Give Away listing and sends it back
     * for review (3.1.6). It has no price or duration to update.
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if the fields are invalid, the listing is not the seller's,
     *                               or it is locked; nothing changes then
     */
    public Listing updateGiveaway(String listingId, String title, Category category,
                                  ItemCondition condition, String description,
                                  MeetupLocation meetupLocation, List<Path> newPhotoFiles) {
        Seller seller = AccessControl.requireSeller();
        ListingFormRules.requireCommonFields(title, category, condition, description, meetupLocation);
        return saveEdit(listingId, seller, ListingType.GIVEAWAY, newPhotoFiles,
                listing -> applyCommonFields(listing, title, category, condition, description, meetupLocation));
    }

    /**
     * Manage Listings 3.2: deletes the seller's own listing. The record stays with status
     * Removed (3.2.4), and the open offers on it are declined (A3).
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if the listing is not the seller's or it is locked
     */
    public void deleteListing(String listingId) {
        Seller seller = AccessControl.requireSeller();
        store.transaction(() -> {
            Listing listing = requireEditable(listingId, seller, null);
            rules.declineOpenOffers(listing, NotificationCategory.OFFER_UPDATE,
                    "the seller deleted the listing");
            listing.withdraw();
            notifications.send(seller.getUsername(), NotificationCategory.LISTING_REMOVED,
                    "Your listing '" + listing.getTitle() + "' was deleted.");
        });
    }

    /**
     * How many offers on the listing are still waiting for an answer. The screen warns with
     * this before an edit or delete (A3), because saving declines them.
     * @throws ServiceException if the listing is not the seller's
     */
    public int openOfferCount(String listingId) {
        Seller seller = AccessControl.requireSeller();
        Listing listing = store.findListing(listingId);
        if (listing == null || !listing.getSellerUsername().equals(seller.getUsername())) {
            throw new ServiceException("This listing is not yours.");
        }
        return (int) store.getOffersForListing(listingId).stream().filter(Offer::isOpen).count();
    }

    // ---------- Steps shared by the three update methods ----------

    private Listing saveEdit(String listingId, Seller seller, ListingType type,
                             List<Path> newPhotoFiles, Consumer<Listing> change) {
        requireEditable(listingId, seller, type); // refuse before importing photos
        List<String> photoIds = importPhotos(newPhotoFiles);
        return store.transaction(() -> {
            Listing listing = requireEditable(listingId, seller, type);
            boolean wasLive = listing.getStatus() == ListingStatus.LIVE;
            boolean hadOpenOffers = store.getOffersForListing(listingId).stream().anyMatch(Offer::isOpen);
            change.accept(listing);
            if (!photoIds.isEmpty()) {
                listing.setPhotos(photoIds);
            }
            if (hadOpenOffers) { // A3: the buyers whose offers the edit declines are told
                rules.declineOpenOffers(listing, NotificationCategory.OFFER_UPDATE,
                        "the seller changed the listing");
            }
            // Client answer #5: a Live listing with no open offers stays Live; anything else waits for review.
            if (!wasLive || hadOpenOffers) {
                listing.resubmit();
                notifications.sendToAdmins(NotificationCategory.LISTING_SUBMITTED,
                        seller.getFullName() + " updated '" + listing.getTitle()
                                + "' and submitted it for review.");
                notifications.send(seller.getUsername(), NotificationCategory.LISTING_SUBMITTED,
                        "Your changes to '" + listing.getTitle() + "' are pending review.");
            }
            return listing;
        });
    }

    /**
     * The listing must exist, belong to the seller, be of the expected type, and not be locked.
     * @param expected the type the form updates, or null when the type does not matter
     */
    private Listing requireEditable(String listingId, Seller seller, ListingType expected) {
        Listing listing = store.findListing(listingId);
        if (listing == null || !listing.getSellerUsername().equals(seller.getUsername())) {
            throw new ServiceException("This listing is not yours.");
        }
        if (expected != null && listing.getType() != expected) {
            throw new IllegalArgumentException("A " + expected + " listing was expected.");
        }
        if (listing.isLocked()) { // Manage Listings A2
            throw new ServiceException("This listing can no longer be edited or deleted.");
        }
        return listing;
    }

    private static void applyCommonFields(Listing listing, String title, Category category,
                                          ItemCondition condition, String description,
                                          MeetupLocation meetupLocation) {
        listing.setTitle(title.trim());
        listing.setCategory(category);
        listing.setCondition(condition);
        listing.setDescription(description.trim());
        listing.setMeetupLocation(meetupLocation);
    }

    /** Imports the chosen photos; an empty list means the listing keeps its current photos. */
    private List<String> importPhotos(List<Path> newPhotoFiles) {
        List<Path> files = newPhotoFiles == null ? List.of() : newPhotoFiles;
        ListingFormRules.requirePhotoCount(files.size());
        List<String> photoIds = new ArrayList<>();
        for (Path file : files) {
            photoIds.add(photos.importPhoto(file));
        }
        return photoIds;
    }
}
