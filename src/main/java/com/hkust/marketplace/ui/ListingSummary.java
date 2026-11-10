package com.hkust.marketplace.ui;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What a listing card shows, worked out once from a listing and the store. The card looks
 * the same in all three portals (non-functional requirement: shared components for cards),
 * and only a few lines change with who is looking:
 * <ul>
 *   <li>{@link UserRole#BUYER} (Browse, UC8 step 2): the availability status, and the card is
 *       greyed out when the listing is not available (UC8 Special Requirement 1).</li>
 *   <li>{@link UserRole#SELLER} (My Listings, Task 2.4): the listing's status; no seller line,
 *       because the listings are the seller's own.</li>
 *   <li>{@link UserRole#ADMIN} (Listing Review, Task 3.2; Manage All Listings, Task 3.7): the
 *       status, the seller's username next to the full name, the submitted date and time, and
 *       the number of photos.</li>
 * </ul>
 * All of this is tested in ListingSummaryTest; {@link ListingCard} only displays it.
 *
 * @param listing      the listing shown
 * @param viewer       the role of the user looking at the card
 * @param sellerName   the seller's full name, or the username if the account no longer exists
 * @param sellerActive false if the admin deactivated the seller (their listings are hidden from Browse)
 * @param available    true if the listing accepts offers, borrow requests and claims (glossary)
 * @param claimPending true if the listing is a giveaway with a pending claim
 */
public record ListingSummary(Listing listing, UserRole viewer, String sellerName,
                             boolean sellerActive, boolean available, boolean claimPending) {

    /** Availability status of a listing that accepts offers, borrow requests and claims. */
    public static final String AVAILABLE = "Available";
    /** Availability status of a giveaway that already has a pending claim (UC5 Special Requirement 1). */
    public static final String CLAIM_PENDING = "Claim Pending";
    /** Availability status of a listing whose seller the admin deactivated. */
    public static final String SELLER_DEACTIVATED = "Seller Deactivated";

    public ListingSummary {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(viewer, "viewer");
    }

    /** Builds the summary from the store's current data. */
    public static ListingSummary of(Listing listing, UserRole viewer, DataStore store) {
        User seller = store.findUser(listing.getSellerUsername());
        boolean claimPending = listing instanceof GiveawayListing && store.hasPendingClaim(listing.getId());
        return new ListingSummary(listing, viewer, store.getFullName(listing.getSellerUsername()),
                seller != null && seller.isActive(), store.isAvailable(listing), claimPending);
    }

    public String title() {
        return listing.getTitle();
    }

    /** "Sell", "Lend" or "Giveaway". */
    public String typeText() {
        return listing.getType().getDisplayName();
    }

    /** "HK$ 180" for a sale, "Borrow" for a lend listing, "Free" for a giveaway. */
    public String priceText() {
        return listing.getPriceDisplay();
    }

    /** "Textbooks · Used". */
    public String detailsText() {
        return listing.getCategory().getDisplayName() + " · " + listing.getCondition().getDisplayName();
    }

    /**
     * "Seller: Charlie Lee" for a buyer, "Seller: Charlie Lee (@charlie)" for the admin, and empty
     * for the seller, who is looking at their own listings.
     */
    public Optional<String> sellerText() {
        return switch (viewer) {
            case BUYER -> Optional.of("Seller: " + sellerName);
            case ADMIN -> Optional.of("Seller: " + sellerName + " (@" + listing.getSellerUsername() + ")");
            case SELLER -> Optional.empty();
        };
    }

    /** "Meet-up: Library". */
    public String locationText() {
        return "Meet-up: " + listing.getMeetupLocation().getDisplayName();
    }

    /** "Posted: 09 Nov 2026", or "Submitted: 09 Nov 2026 14:20" for the admin (Task 3.2). */
    public String dateText() {
        return viewer == UserRole.ADMIN
                ? "Submitted: " + Formats.dateTime(listing.getPostedDate())
                : "Posted: " + Formats.date(listing.getPostedDate());
    }

    /** "Photos: 3" for the admin (Task 3.2), empty for everyone else. */
    public Optional<String> photosText() {
        if (viewer != UserRole.ADMIN) {
            return Optional.empty();
        }
        int count = listing.getPhotos().size();
        return Optional.of(count == 0 ? "No photos" : "Photos: " + count);
    }

    /**
     * The availability status a buyer sees (UC8 step 2): "Available", "Claim Pending",
     * "Seller Deactivated", or otherwise the status, such as "Reserved" or "Lent".
     */
    public String availabilityText() {
        if (available) {
            return AVAILABLE;
        }
        if (listing.getStatus() == ListingStatus.LIVE) {
            if (!sellerActive) {
                return SELLER_DEACTIVATED;
            }
            if (claimPending) {
                return CLAIM_PENDING;
            }
        }
        return listing.getStatus().getDisplayName();
    }

    /** The badge text: the availability status for a buyer, the listing's status for the seller and admin. */
    public String badgeText() {
        return viewer == UserRole.BUYER ? availabilityText() : listing.getStatus().getDisplayName();
    }

    /** The badge's style class in styles.css: green, orange, blue or red. */
    public String badgeStyle() {
        return switch (listing.getStatus()) {
            case LIVE -> {
                if (viewer != UserRole.BUYER || available) {
                    yield "badge-live";
                }
                yield sellerActive ? "badge-pending" : "badge-rejected";
            }
            case PENDING_REVIEW -> "badge-pending";
            case RESERVED, LENT, SOLD, GIVEN -> "badge-sold";
            case REJECTED, REMOVED -> "badge-rejected";
        };
    }

    /** True if the card is shown greyed out: a buyer looking at a listing that is not available (UC8 SR1). */
    public boolean greyedOut() {
        return viewer == UserRole.BUYER && !available;
    }

    /** The thumbnail (first photo) as a URL an image can load, or empty when there is none to show. */
    public Optional<String> thumbnailUrl() {
        return imageUrl(listing.getThumbnail());
    }

    /**
     * Turns a stored photo path into a URL an image can load: a URL with a scheme (such as
     * {@code file:} or {@code https:}) is kept, and a file path becomes a file URL if the file
     * exists. Empty for no path, a blank one, or a file that is missing.
     */
    static Optional<String> imageUrl(String photo) {
        if (photo == null || photo.isBlank()) {
            return Optional.empty();
        }
        String path = photo.trim();
        if (hasScheme(path)) {
            return Optional.of(path);
        }
        try {
            Path file = Path.of(path);
            return Files.isRegularFile(file) ? Optional.of(file.toUri().toString()) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** True for "https://…" or "file:/…", but not for a Windows path such as "C:\photos\a.jpg". */
    private static boolean hasScheme(String path) {
        try {
            String scheme = URI.create(path).getScheme();
            return scheme != null && scheme.length() > 1;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
