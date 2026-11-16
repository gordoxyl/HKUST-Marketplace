package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 2.3 Create New Listing, shared by the seller form and the admin's "add a listing for a
 * seller" (Task 3.7, once it is wired). The use case (Create Listings) is implemented here:
 * required fields are checked for the chosen type (A1), then the price (A2) or lending
 * duration (A3), then the photos (A4, at most 6). The listing is saved as Pending Review, the
 * admin is told it is waiting, and the seller gets a confirmation (steps 11-13).
 *
 * <p>Photos are imported only when the listing is submitted (step 8), so a cancelled form
 * stores nothing. They are copied into the app's encrypted storage by {@link PhotoService};
 * the listing keeps their ids and the first one is the thumbnail.
 */
public class CreateListingService {

    private final DataStore store;
    private final PhotoService photos;
    private final NotificationService notifications;

    public CreateListingService() {
        this(DataStore.getInstance());
    }

    public CreateListingService(DataStore store) {
        this.store = store;
        this.photos = new PhotoService(store);
        this.notifications = new NotificationService(store);
    }

    /**
     * Posts a Sell listing (Create Listings steps 3-13).
     * @param price      the asking price, which must be a positive number (A2)
     * @param photoFiles optional; at most {@link Listing#MAX_PHOTOS}, the first is the thumbnail
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if a rule is broken; nothing is stored then
     */
    public Listing createSale(String title, Category category, ItemCondition condition,
                              String description, MeetupLocation meetupLocation,
                              double price, List<Path> photoFiles) {
        Seller seller = AccessControl.requireSeller();
        requireCommonFields(title, category, condition, description, meetupLocation);
        if (!(price > 0) || Double.isInfinite(price)) {
            throw new ServiceException("The price must be a positive number, such as 180 or 99.50.");
        }
        return publish(seller, new SaleListing(seller.getUsername(), title.trim(), category,
                condition, description.trim(), meetupLocation, price), photoFiles);
    }

    /**
     * Posts a Lend listing.
     * @param lendingDuration how long the item may be borrowed; at least the minimum borrow
     *                        time of 5 seconds (A3)
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if a rule is broken; nothing is stored then
     */
    public Listing createLend(String title, Category category, ItemCondition condition,
                              String description, MeetupLocation meetupLocation,
                              Duration lendingDuration, List<Path> photoFiles) {
        Seller seller = AccessControl.requireSeller();
        requireCommonFields(title, category, condition, description, meetupLocation);
        if (lendingDuration == null
                || lendingDuration.compareTo(BorrowRequest.MIN_BORROW_TIME) < 0) {
            throw new ServiceException("The lending duration must be at least "
                    + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME) + ".");
        }
        return publish(seller, new LendListing(seller.getUsername(), title.trim(), category,
                condition, description.trim(), meetupLocation, lendingDuration), photoFiles);
    }

    /** Posts a Give Away listing. It has no price or duration to check. */
    public Listing createGiveaway(String title, Category category, ItemCondition condition,
                                  String description, MeetupLocation meetupLocation,
                                  List<Path> photoFiles) {
        Seller seller = AccessControl.requireSeller();
        requireCommonFields(title, category, condition, description, meetupLocation);
        return publish(seller, new GiveawayListing(seller.getUsername(), title.trim(), category,
                condition, description.trim(), meetupLocation), photoFiles);
    }

    /** A1: every field the form asks for, whatever the type. */
    private static void requireCommonFields(String title, Category category, ItemCondition condition,
                                            String description, MeetupLocation meetupLocation) {
        if (title == null || title.isBlank()) {
            throw new ServiceException("The item name cannot be empty.");
        }
        if (category == null) {
            throw new ServiceException("Please choose a category.");
        }
        if (condition == null) {
            throw new ServiceException("Please choose the item's condition.");
        }
        if (description == null || description.isBlank()) {
            throw new ServiceException("The description cannot be empty.");
        }
        if (meetupLocation == null) {
            throw new ServiceException("Please choose a meet-up location.");
        }
    }

    /**
     * Imports the photos, then adds the listing and sends both notifications as one step.
     * The photo count is checked before anything is imported (A4).
     */
    private Listing publish(Seller seller, Listing listing, List<Path> photoFiles) {
        List<Path> files = photoFiles == null ? List.of() : photoFiles;
        if (files.size() > Listing.MAX_PHOTOS) {
            throw new ServiceException("A listing can have at most " + Listing.MAX_PHOTOS + " photos.");
        }
        List<String> photoIds = new ArrayList<>();
        for (Path file : files) {
            photoIds.add(photos.importPhoto(file));
        }
        return store.transaction(() -> {
            store.addListing(listing);
            if (!photoIds.isEmpty()) {
                listing.setPhotos(photoIds);
            }
            notifications.sendToAdmins(NotificationCategory.LISTING_SUBMITTED,
                    seller.getFullName() + " submitted '" + listing.getTitle() + "' for review.");
            notifications.send(seller.getUsername(), NotificationCategory.LISTING_SUBMITTED,
                    "Your listing '" + listing.getTitle() + "' is pending review.");
            return listing;
        });
    }
}
