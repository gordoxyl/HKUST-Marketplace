package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An item a seller posts (Listing in the domain class diagram).
 * Every listing is exactly one of {@link SaleListing}, {@link LendListing} or
 * {@link GiveawayListing} ({complete, disjoint}), so the type cannot change after posting.
 *
 * <p><b>Status.</b> The status changes only through the event methods in the state machine
 * section below (approve, reject, resubmit, acceptDeal, completeDeal, withdraw, remove).
 * Each one refuses a change the state machine does not allow, so an impossible status, such
 * as a sale listing that is "Lent", cannot happen. The diagram and the use case behind each
 * event are in docs/state-machines.md.
 *
 * The seller is stored by username, so listings can later be saved to files
 * without object cycles.
 */
public abstract class Listing {

    /** A listing has at most 6 photos (Task 2.3). */
    public static final int MAX_PHOTOS = 6;

    private String id;
    private final String sellerUsername;
    private String title;
    private Category category;
    private ItemCondition condition;
    private String description;
    private List<String> photos = new ArrayList<>();
    private MeetupLocation meetupLocation;
    private LocalDateTime postedDate;
    private ListingStatus status = ListingStatus.PENDING_REVIEW;
    private int viewCount;

    /** A new listing is Pending Review until the admin approves it (Create Listings step 11). */
    protected Listing(String sellerUsername, String title, Category category, ItemCondition condition,
                      String description, MeetupLocation meetupLocation) {
        this.sellerUsername = Objects.requireNonNull(sellerUsername, "sellerUsername");
        this.title = Objects.requireNonNull(title, "title");
        this.category = Objects.requireNonNull(category, "category");
        this.condition = Objects.requireNonNull(condition, "condition");
        this.description = description;
        this.meetupLocation = Objects.requireNonNull(meetupLocation, "meetupLocation");
    }

    /** Sell, Lend or Giveaway, matching the subclass. */
    public abstract ListingType getType();

    /** Short price text for listing cards, such as "HK$ 180", "Borrow" or "Free". */
    public abstract String getPriceDisplay();

    /** Null until the listing is added to DataStore. */
    public String getId() { return id; }

    /** Called once by DataStore when the listing is added: gives it its id and posted date. */
    public void markAdded(String id, LocalDateTime postedDate) {
        if (this.id != null) {
            throw new IllegalStateException("Listing already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
        this.postedDate = Objects.requireNonNull(postedDate, "postedDate");
    }

    public String getSellerUsername() { return sellerUsername; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = Objects.requireNonNull(title, "title"); }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = Objects.requireNonNull(category, "category"); }

    public ItemCondition getCondition() { return condition; }
    public void setCondition(ItemCondition condition) { this.condition = Objects.requireNonNull(condition, "condition"); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Photo file paths in order ({ordered}); the first is the thumbnail. */
    public List<String> getPhotos() { return List.copyOf(photos); }

    /** @throws IllegalArgumentException if there are more than {@link #MAX_PHOTOS} photos */
    public void setPhotos(List<String> photos) {
        if (photos.size() > MAX_PHOTOS) {
            throw new IllegalArgumentException("A listing can have at most " + MAX_PHOTOS + " photos.");
        }
        this.photos = new ArrayList<>(photos);
    }

    /** The first photo, or null if there are none (Task 1.3). */
    public String getThumbnail() {
        return photos.isEmpty() ? null : photos.get(0);
    }

    public MeetupLocation getMeetupLocation() { return meetupLocation; }
    public void setMeetupLocation(MeetupLocation meetupLocation) {
        this.meetupLocation = Objects.requireNonNull(meetupLocation, "meetupLocation");
    }

    /** Null until the listing is added to DataStore. */
    public LocalDateTime getPostedDate() { return postedDate; }

    public ListingStatus getStatus() { return status; }

    public int getViewCount() { return viewCount; }

    /** Opening the listing detail screen adds one view (UC8 Special Requirement 2). */
    public void incrementViewCount() { viewCount++; }

    // ========== State machine (docs/state-machines.md) ==========

    /** The status while a deal on this listing is accepted but not completed: Reserved or Lent. */
    protected abstract ListingStatus statusWhenDealAccepted();

    /** The status once that deal is completed: Sold, Given, or Live again for a returned item. */
    protected abstract ListingStatus statusWhenDealCompleted();

    /**
     * True if the seller can no longer edit or delete the listing: it is Reserved, Sold, Lent,
     * Given or Removed (Manage Listings A2).
     */
    public boolean isLocked() {
        return switch (status) {
            case RESERVED, SOLD, LENT, GIVEN, REMOVED -> true;
            case PENDING_REVIEW, LIVE, REJECTED -> false;
        };
    }

    /**
     * The admin approves the listing, so it goes Live (Manage Marketplace Listings 5.2).
     * A listing the admin adds is approved right after it is created (5.6.4).
     */
    public void approve() {
        requireStatus(ListingStatus.PENDING_REVIEW, "approved");
        status = ListingStatus.LIVE;
    }

    /** The admin rejects the listing; the reason is kept in a ListingActionRecord (5.3). */
    public void reject() {
        requireStatus(ListingStatus.PENDING_REVIEW, "rejected");
        status = ListingStatus.REJECTED;
    }

    /** The seller saved changes, so the listing waits for review again (Manage Listings 3.1.6). */
    public void resubmit() {
        requireUnlocked("edited");
        status = ListingStatus.PENDING_REVIEW;
    }

    /** The seller deleted the listing. It stays stored with status Removed (Manage Listings 3.2.4). */
    public void withdraw() {
        requireUnlocked("deleted");
        status = ListingStatus.REMOVED;
    }

    /**
     * The admin removed the listing (Manage Marketplace Listings 5.5.4). This is allowed until the
     * listing is Sold, Given or already Removed. A deal accepted before the removal can still be
     * completed, and the listing then stays Removed (UC12 A1; assumption for Reserved listings).
     */
    public void remove() {
        if (!canBeRemoved()) {
            throw new IllegalStateException("A " + status.getDisplayName().toLowerCase() + " listing cannot be removed.");
        }
        status = ListingStatus.REMOVED;
    }

    /** True unless the listing is Sold, Given or already Removed: those stay as they are, as records. */
    public boolean canBeRemoved() {
        return status != ListingStatus.SOLD && status != ListingStatus.GIVEN && status != ListingStatus.REMOVED;
    }

    /**
     * The seller accepted an offer, borrow request or claim: the listing becomes Reserved for a
     * sale or giveaway, or Lent for a lend listing (UC7 Special Requirement 5).
     */
    public void acceptDeal() {
        requireStatus(ListingStatus.LIVE, "reserved or lent");
        status = statusWhenDealAccepted();
    }

    /**
     * The accepted deal was completed: a sale becomes Sold, a giveaway Given, and a returned
     * lend listing Live again (UC7 steps 6 and A2, UC12 step 3). If the admin removed the
     * listing in the meantime, it stays Removed (UC7 A2, UC12 A1).
     */
    public void completeDeal() {
        if (status == ListingStatus.REMOVED) {
            return;
        }
        requireStatus(statusWhenDealAccepted(), "completed");
        status = statusWhenDealCompleted();
    }

    private void requireStatus(ListingStatus required, String action) {
        if (status != required) {
            throw new IllegalStateException("Only a " + required.getDisplayName().toLowerCase()
                    + " listing can be " + action + ", but this one is " + status.getDisplayName().toLowerCase() + ".");
        }
    }

    private void requireUnlocked(String action) {
        if (isLocked()) {
            throw new IllegalStateException("A " + status.getDisplayName().toLowerCase()
                    + " listing can no longer be " + action + ".");
        }
    }
}
