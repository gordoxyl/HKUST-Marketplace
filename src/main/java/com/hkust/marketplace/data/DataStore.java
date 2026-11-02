package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The single store for every user, listing, offer, chat thread, review, report,
 * notification and admin record (Technical Requirement: data structures for users,
 * listings, offers, chats, ratings and notifications).
 *
 * <p><b>Thread safety.</b> Every method runs under one lock. To make several reads and
 * writes one atomic step, wrap them in {@link #transaction}; for example, the final
 * availability check and the recording of an offer (UC5 Special Requirement 2):
 * <pre>{@code
 * boolean claimed = store.transaction(() -> {
 *     if (!store.isAvailable(listing)) return false;
 *     store.addOffer(new Claim(listing.getId(), buyer.getUsername()));
 *     return true;
 * });
 * }</pre>
 * Change model objects you got from the store inside a transaction too, because the
 * borrowing-deadline timer (UC12) changes them from another thread.
 *
 * <p><b>Storage.</b> Data is kept in memory and lost when the app closes. Saving to files is
 * planned; all data access goes through this class, so only this class will need to change.
 *
 * <p><b>Access control.</b> This class does not check roles. Service methods check the
 * logged-in user with {@code AccessControl} before calling it (UC2 Special Requirement 3).
 *
 * <p>Lists returned by this class are copies: changing a list does not change the store.
 */
public class DataStore {

    private static final DataStore INSTANCE = createSeeded();

    private final ReentrantLock lock = new ReentrantLock();

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<String, Offer> offers = new LinkedHashMap<>();
    private final Map<String, ChatThread> threads = new LinkedHashMap<>();
    private final Map<String, Review> reviews = new LinkedHashMap<>();
    private final Map<String, Report> reports = new LinkedHashMap<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final List<ListingActionRecord> listingActions = new ArrayList<>();
    private final List<AuditLogEntry> auditLog = new ArrayList<>();

    private int nextId = 1;

    /** An empty store, for tests. The app uses {@link #getInstance()}, which holds the demo data. */
    public DataStore() {
    }

    /** The app's store, filled with the demo data from {@link SeedData}. */
    public static DataStore getInstance() {
        return INSTANCE;
    }

    private static DataStore createSeeded() {
        DataStore store = new DataStore();
        SeedData.populate(store);
        return store;
    }

    // ========== Transactions ==========

    /** Runs work as one atomic step and returns its result. Transactions may be nested. */
    public <T> T transaction(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    /** Runs work as one atomic step. Transactions may be nested. */
    public void transaction(Runnable work) {
        lock.lock();
        try {
            work.run();
        } finally {
            lock.unlock();
        }
    }

    /** A new id such as "L12". Ids are unique across all kinds of record. Call under the lock. */
    private String newId(String prefix) {
        return prefix + nextId++;
    }

    private <T> List<T> select(Collection<T> items, Predicate<T> filter) {
        return transaction(() -> items.stream().filter(filter).toList());
    }

    // ========== Users ==========

    /**
     * Adds the account if its username is free, checking and adding in one step so two
     * registrations cannot take the same username (UC1 A1).
     * @return false if the username is already used by any account
     */
    public boolean addUser(User user) {
        return transaction(() -> users.putIfAbsent(user.getUsername(), user) == null);
    }

    /** Usernames are matched ignoring case and surrounding spaces. Returns null if there is no such account. */
    public User findUser(String username) {
        if (username == null) {
            return null;
        }
        String key = username.trim().toLowerCase(Locale.ROOT);
        return transaction(() -> users.get(key));
    }

    public boolean usernameExists(String username) {
        return findUser(username) != null;
    }

    public List<User> getAllUsers() {
        return select(users.values(), u -> true);
    }

    public List<User> getUsersByRole(UserRole role) {
        return select(users.values(), u -> u.getRole() == role);
    }

    /** The user's full name for display, or the username if the account no longer exists. */
    public String getFullName(String username) {
        User user = findUser(username);
        return user != null ? user.getFullName() : username;
    }

    // ========== Listings ==========

    /** Gives the listing an id and adds it. */
    public Listing addListing(Listing listing) {
        return transaction(() -> {
            listing.assignId(newId("L"));
            listings.put(listing.getId(), listing);
            return listing;
        });
    }

    /** Returns null if there is no such listing. */
    public Listing findListing(String id) {
        return transaction(() -> listings.get(id));
    }

    public List<Listing> getAllListings() {
        return select(listings.values(), l -> true);
    }

    public List<Listing> getListingsBySeller(String sellerUsername) {
        return select(listings.values(), l -> l.getSellerUsername().equalsIgnoreCase(sellerUsername));
    }

    public List<Listing> getListingsByStatus(ListingStatus status) {
        return select(listings.values(), l -> l.getStatus() == status);
    }

    /**
     * The live listings shown on Browse Marketplace (glossary; UC8 Special Requirement 1):
     * status Live, Reserved or Lent, posted by a seller whose account is active.
     */
    public List<Listing> getLiveListings() {
        return select(listings.values(), this::isLive);
    }

    private boolean isLive(Listing listing) {
        ListingStatus s = listing.getStatus();
        boolean liveStatus = s == ListingStatus.LIVE || s == ListingStatus.RESERVED || s == ListingStatus.LENT;
        return liveStatus && isSellerActive(listing);
    }

    /**
     * True if the listing accepts offers, borrow requests and claims (glossary;
     * UC5 Special Requirement 1): its status is Live, it is not a giveaway with a pending
     * claim, and its seller is active (a deactivated seller's listings are hidden).
     */
    public boolean isAvailable(Listing listing) {
        return transaction(() -> listing.getStatus() == ListingStatus.LIVE
                && isSellerActive(listing)
                && !(listing instanceof GiveawayListing && hasPendingClaim(listing.getId())));
    }

    private boolean isSellerActive(Listing listing) {
        User seller = users.get(listing.getSellerUsername());
        return seller != null && seller.isActive();
    }

    private boolean hasPendingClaim(String listingId) {
        return offers.values().stream().anyMatch(o -> o instanceof Claim
                && o.getListingId().equals(listingId) && o.getStatus() == OfferStatus.PENDING);
    }

    /** Adds one view when a listing's detail screen is opened (UC8 Special Requirement 2). */
    public void incrementViewCount(String listingId) {
        transaction(() -> {
            Listing listing = listings.get(listingId);
            if (listing != null) {
                listing.incrementViewCount();
            }
        });
    }

    // ========== Offers ==========

    /**
     * Gives the offer an id and records it in the chat thread between its buyer and its
     * listing, creating the thread if it does not exist yet (UC5 step 7).
     * This does not check availability; check {@link #isAvailable} in the same transaction.
     * @throws IllegalArgumentException if the listing or buyer does not exist, or the offer
     *         type does not match the listing type
     */
    public Offer addOffer(Offer offer) {
        return transaction(() -> {
            Listing listing = listings.get(offer.getListingId());
            if (listing == null) {
                throw new IllegalArgumentException("No listing with id " + offer.getListingId());
            }
            if (!offer.canBeMadeOn(listing)) {
                throw new IllegalArgumentException("A price offer can only be made on a sale listing, "
                        + "a borrow request on a lend listing, and a claim on a giveaway listing.");
            }
            getOrCreateThread(offer.getBuyerUsername(), listing.getId());
            offer.assignId(newId("O"));
            offers.put(offer.getId(), offer);
            return offer;
        });
    }

    /** Returns null if there is no such offer. */
    public Offer findOffer(String id) {
        return transaction(() -> offers.get(id));
    }

    public List<Offer> getAllOffers() {
        return select(offers.values(), o -> true);
    }

    public List<Offer> getOffersForListing(String listingId) {
        return select(offers.values(), o -> o.getListingId().equals(listingId));
    }

    public List<Offer> getOffersByBuyer(String buyerUsername) {
        return select(offers.values(), o -> o.getBuyerUsername().equalsIgnoreCase(buyerUsername));
    }

    /** Every offer on any of the seller's listings. */
    public List<Offer> getOffersForSeller(String sellerUsername) {
        return select(offers.values(), o -> sellerUsername.equalsIgnoreCase(listings.get(o.getListingId()).getSellerUsername()));
    }

    /** The offers made in a chat thread, oldest first. */
    public List<Offer> getOffersInThread(ChatThread thread) {
        return select(offers.values(), o -> o.getListingId().equals(thread.getListingId())
                && o.getBuyerUsername().equalsIgnoreCase(thread.getBuyerUsername()));
    }

    /** The listing an offer was made on. */
    public Listing getListingOf(Offer offer) {
        return findListing(offer.getListingId());
    }

    // ========== Chat threads ==========

    /** The thread between a buyer and a listing, or null if the buyer has not started one. */
    public ChatThread findThread(String buyerUsername, String listingId) {
        return transaction(() -> threads.values().stream()
                .filter(t -> t.getListingId().equals(listingId) && t.getBuyerUsername().equalsIgnoreCase(buyerUsername))
                .findFirst().orElse(null));
    }

    /**
     * The thread between a buyer and a listing, created if it does not exist yet.
     * A thread links one buyer to one listing (UC6 Special Requirement 1).
     * @throws IllegalArgumentException if the listing does not exist or the user is not a buyer
     */
    public ChatThread getOrCreateThread(String buyerUsername, String listingId) {
        return transaction(() -> {
            ChatThread existing = findThread(buyerUsername, listingId);
            if (existing != null) {
                return existing;
            }
            Listing listing = listings.get(listingId);
            if (listing == null) {
                throw new IllegalArgumentException("No listing with id " + listingId);
            }
            if (!(findUser(buyerUsername) instanceof Buyer buyer)) {
                throw new IllegalArgumentException("Only a buyer can start a chat thread.");
            }
            ChatThread thread = new ChatThread(buyer.getUsername(), listing);
            thread.assignId(newId("T"));
            threads.put(thread.getId(), thread);
            return thread;
        });
    }

    /** Returns null if there is no such thread. */
    public ChatThread findThreadById(String id) {
        return transaction(() -> threads.get(id));
    }

    public List<ChatThread> getThreadsForBuyer(String buyerUsername) {
        return select(threads.values(), t -> t.getBuyerUsername().equalsIgnoreCase(buyerUsername));
    }

    public List<ChatThread> getThreadsForSeller(String sellerUsername) {
        return select(threads.values(), t -> t.getSellerUsername().equalsIgnoreCase(sellerUsername));
    }

    // ========== Reviews ==========

    /**
     * Gives the review an id and adds it.
     * @throws IllegalStateException if the offer is not completed or already has a review
     *         ({only an Offer with a completedDate can have a Review}; UC10 Special Requirement 1)
     */
    public Review addReview(Review review) {
        return transaction(() -> {
            Offer offer = offers.get(review.getOfferId());
            if (offer == null || !offer.isCompleted()) {
                throw new IllegalStateException("Only a completed deal can be reviewed.");
            }
            if (findReviewForOffer(offer.getId()) != null) {
                throw new IllegalStateException("This deal has already been reviewed.");
            }
            review.assignId(newId("R"));
            reviews.put(review.getId(), review);
            return review;
        });
    }

    /** The review of a deal, or null if it has none. */
    public Review findReviewForOffer(String offerId) {
        return transaction(() -> reviews.values().stream()
                .filter(r -> r.getOfferId().equals(offerId)).findFirst().orElse(null));
    }

    public List<Review> getAllReviews() {
        return select(reviews.values(), r -> true);
    }

    /** Every review of the seller, including hidden ones (for the seller and the admin). */
    public List<Review> getReviewsForSeller(String sellerUsername) {
        return select(reviews.values(), r -> sellerUsername.equalsIgnoreCase(getSellerUsername(r)));
    }

    /** The seller's reviews that are not hidden, as shown to buyers (UC8 Special Requirement 5). */
    public List<Review> getVisibleReviewsForSeller(String sellerUsername) {
        return select(reviews.values(), r -> !r.isHidden() && sellerUsername.equalsIgnoreCase(getSellerUsername(r)));
    }

    /** The average of the seller's visible ratings, or empty if there are none. */
    public OptionalDouble getAverageRating(String sellerUsername) {
        return getVisibleReviewsForSeller(sellerUsername).stream().mapToInt(Review::getRating).average();
    }

    /** The number of the seller's visible ratings. */
    public int getRatingCount(String sellerUsername) {
        return getVisibleReviewsForSeller(sellerUsername).size();
    }

    /** The seller who was reviewed: the seller of the reviewed deal's listing. */
    public String getSellerUsername(Review review) {
        return transaction(() -> listings.get(offers.get(review.getOfferId()).getListingId()).getSellerUsername());
    }

    /** The buyer who wrote the review. */
    public String getBuyerUsername(Review review) {
        return transaction(() -> offers.get(review.getOfferId()).getBuyerUsername());
    }

    // ========== Reports ==========

    /**
     * Gives the report an id and adds it, unless this buyer already reported this listing
     * (UC9 Special Requirement 3).
     * @return false if the buyer already reported the listing
     */
    public boolean addReport(Report report) {
        return transaction(() -> {
            if (hasReported(report.getBuyerUsername(), report.getListingId())) {
                return false;
            }
            report.assignId(newId("P"));
            reports.put(report.getId(), report);
            return true;
        });
    }

    public boolean hasReported(String buyerUsername, String listingId) {
        return transaction(() -> reports.values().stream().anyMatch(r -> r.getListingId().equals(listingId)
                && r.getBuyerUsername().equalsIgnoreCase(buyerUsername)));
    }

    public List<Report> getReportsForListing(String listingId) {
        return select(reports.values(), r -> r.getListingId().equals(listingId));
    }

    public List<Report> getAllReports() {
        return select(reports.values(), r -> true);
    }

    // ========== Notifications ==========

    /** Gives the notification an id and adds it to its recipient's board. */
    public Notification addNotification(Notification notification) {
        return transaction(() -> {
            notification.assignId(newId("N"));
            notifications.add(notification);
            return notification;
        });
    }

    /** The user's notifications, newest first (UC4 step 2). */
    public List<Notification> getNotificationsFor(String username) {
        return transaction(() -> notifications.stream()
                .filter(n -> n.getRecipientUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Notification::getTimestamp).reversed())
                .toList());
    }

    // ========== Admin records ==========

    public ListingActionRecord addListingAction(ListingActionRecord record) {
        return transaction(() -> {
            record.assignId(newId("A"));
            listingActions.add(record);
            return record;
        });
    }

    public List<ListingActionRecord> getListingActions(String listingId) {
        return select(listingActions, r -> r.getListingId().equals(listingId));
    }

    public List<ListingActionRecord> getAllListingActions() {
        return select(listingActions, r -> true);
    }

    public AuditLogEntry addAuditLogEntry(AuditLogEntry entry) {
        return transaction(() -> {
            entry.assignLogReference(newId("G"));
            auditLog.add(entry);
            return entry;
        });
    }

    public List<AuditLogEntry> getAuditLog() {
        return select(auditLog, e -> true);
    }
}
