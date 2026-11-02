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
    private LocalDateTime postedDate = LocalDateTime.now();
    private ListingStatus status = ListingStatus.PENDING_REVIEW;
    private int viewCount;

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

    /** Called once by DataStore when the listing is added. */
    public void assignId(String id) {
        if (this.id != null) {
            throw new IllegalStateException("Listing already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
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

    public LocalDateTime getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDateTime postedDate) { this.postedDate = Objects.requireNonNull(postedDate, "postedDate"); }

    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = Objects.requireNonNull(status, "status"); }

    public int getViewCount() { return viewCount; }

    /** Opening the listing detail screen adds one view (UC8 Special Requirement 2). */
    public void incrementViewCount() { viewCount++; }
}
