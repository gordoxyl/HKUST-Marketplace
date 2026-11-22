package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DevLogin;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
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
 * <p><b>Time.</b> Every date the system records comes from {@link #now()}, which reads the
 * store's clock. Records are dated when they are added (a listing's posted date, an offer's
 * offer date, a notification's timestamp, ...). Tests give the store a clock they control,
 * so time-based rules such as the 5-second borrow (UC12) are tested without waiting.
 *
 * <p><b>Storage.</b> The app's store ({@link #getInstance()}) saves everything to an encrypted
 * file after every change and loads it at start-up ({@link EncryptedJsonStorage}). A change
 * is saved when the outermost {@link #transaction} or add method finishes; reads never save.
 * Stores made with {@code new DataStore(...)} and no storage, as in the tests, keep their
 * data in memory only.
 *
 * <p><b>Access control.</b> This class does not check roles. Service methods check the
 * logged-in user with {@code AccessControl} before calling it (UC2 Special Requirement 3).
 *
 * <p>Lists returned by this class are copies: changing a list does not change the store.
 */
public class DataStore {

    /** Where the app keeps its saved data, relative to the folder the app is started from. */
    public static final Path DATA_FOLDER = Path.of("marketplace-data");

    /**
     * How screens name the writer of a review whose account was deleted. The review is kept
     * and its writer is shown as "Deleted User" (client answer 6, 16 Nov 2026); see
     * {@link #getReviewerName}.
     */
    public static final String DELETED_USER = "Deleted User";

    private final ReentrantLock lock = new ReentrantLock();
    private final Storage storage;
    private String loadProblem;

    private final Map<String, User> users = new LinkedHashMap<>();
    /** Usernames of deleted accounts. They are never given out again; see {@link #deleteAccount}. */
    private final Set<String> retiredUsernames = new LinkedHashSet<>();
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<String, Offer> offers = new LinkedHashMap<>();
    private final Map<String, ChatThread> threads = new LinkedHashMap<>();
    private final Map<String, Review> reviews = new LinkedHashMap<>();
    private final Map<String, Report> reports = new LinkedHashMap<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final List<ListingActionRecord> listingActions = new ArrayList<>();
    private final List<AuditLogEntry> auditLog = new ArrayList<>();
    /** Listing photos of a store without storage (tests); with storage, each photo is a file. */
    private final Map<String, byte[]> photosInMemory = new HashMap<>();

    private int nextId = 1;
    private volatile Clock clock;

    /** An empty in-memory store using the computer's clock. The app uses {@link #getInstance()}. */
    public DataStore() {
        this(Clock.systemDefaultZone());
    }

    /** An empty in-memory store using the given clock; tests pass a clock they can move forward. */
    public DataStore(Clock clock) {
        this(clock, null);
    }

    /**
     * An empty store that saves every change to the storage. Call {@link #loadOrSeed()} to
     * load what was saved before.
     */
    public DataStore(Clock clock, Storage storage) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.storage = storage;
    }

    /**
     * The app's store: the saved data, or the demo data from {@link SeedData} on the first run.
     * This is the Singleton pattern (Lecture 18) with lazy creation, adapted for testing: the
     * app uses only this one instance, but the constructors stay public so that each test can
     * make a store of its own.
     */
    public static DataStore getInstance() {
        return AppStore.INSTANCE;
    }

    /**
     * Holds the app's store, created the first time {@link #getInstance()} is called. It is in
     * its own class so that making other stores (as tests do) never loads or writes the app's
     * save file: Java creates INSTANCE only when this class is first used.
     */
    private static final class AppStore {
        static final DataStore INSTANCE = createAppStore();
    }

    private static DataStore createAppStore() {
        String fileName = DevLogin.isEnabled() ? "marketplace-dev.dat" : "marketplace.dat"; // DEV-ONLY: dev runs use their own file; make it "marketplace.dat" before release
        DataStore store = new DataStore(Clock.systemDefaultZone(), new EncryptedJsonStorage(DATA_FOLDER.resolve(fileName)));
        store.loadOrSeed();
        if (store.getLoadProblem() != null) {
            // Only the app's store logs this; MarketplaceApp also shows it in a dialog
            System.err.println("[DataStore] " + store.getLoadProblem());
        }
        return store;
    }

    // ========== Time ==========

    /** The current date and time, from the store's clock. Use this instead of LocalDateTime.now(). */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public Clock getClock() {
        return clock;
    }

    /** Replaces the clock. Only for SeedData (to date the demo records) and tests. */
    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ========== Transactions ==========

    /**
     * Runs work as one atomic step and returns its result. Transactions may be nested; when
     * the outermost one finishes without an exception, the data is saved.
     */
    public <T> T transaction(Supplier<T> work) {
        lock.lock();
        try {
            T result = work.get();
            if (lock.getHoldCount() == 1) {
                persist();
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /** Runs work as one atomic step, like {@link #transaction(Supplier)}. */
    public void transaction(Runnable work) {
        transaction(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Runs several reads as one consistent step, without saving. Use it when a screen's data
     * comes from more than one call, so the borrowing-deadline timer (UC12) cannot change the
     * records between the calls. To change anything, use {@link #transaction} instead.
     */
    public <T> T read(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    /** A new id such as "L12". Ids are unique across all kinds of record. Call under the lock. */
    private String newId(String prefix) {
        return prefix + nextId++;
    }

    private <T> List<T> select(Collection<T> items, Predicate<T> filter) {
        return read(() -> items.stream().filter(filter).toList());
    }

    // ========== Users ==========

    /**
     * Adds the account if its username is free, checking and adding in one step so two
     * registrations cannot take the same username (UC1 A1).
     * @return false if the username is used by any account, or was used by a deleted one
     */
    public boolean addUser(User user) {
        return transaction(() -> {
            if (usernameExists(user.getUsername())) {
                return false;
            }
            user.markAdded(now());
            users.put(user.getUsername(), user);
            return true;
        });
    }

    /** Usernames are matched ignoring case and surrounding spaces. Returns null if there is no such account. */
    public User findUser(String username) {
        if (username == null) {
            return null;
        }
        String key = username.trim().toLowerCase(Locale.ROOT);
        return read(() -> users.get(key));
    }

    /**
     * True if the username cannot be registered: an account has it, or a deleted account had
     * it. A deleted account's username is never given out again, because the deals it took
     * part in are kept as records under that username.
     */
    public boolean usernameExists(String username) {
        return findUser(username) != null || isRetiredUsername(username);
    }

    /** True if an account with this username was permanently deleted ({@link #deleteAccount}). */
    public boolean isRetiredUsername(String username) {
        if (username == null) {
            return false;
        }
        String key = username.trim().toLowerCase(Locale.ROOT);
        return read(() -> retiredUsernames.contains(key));
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

    /**
     * Permanently deletes an account, as one atomic step, for the admin's Delete User (Manage
     * All User Accounts and Reputation 6.4.4, as changed by the client's answer 6 of 16 Nov
     * 2026: the reviews the user wrote are kept). It makes every change to the data that the
     * use case lists, so nothing is left waiting on an account that no longer exists:
     * <ul>
     *   <li><b>Deleted:</b> the account; every chat thread the user took part in, with its
     *       messages (UC6 Special Requirement 2); the notifications on the user's board.</li>
     *   <li><b>Cancelled:</b> the user's own open offers and the open offers on the user's
     *       listings are declined; the user's listings become Removed, except Sold, Given and
     *       already Removed ones.</li>
     *   <li><b>Kept, as the archived transaction history:</b> the user's listings and offers,
     *       accepted and completed deals included; the reviews about the user; the reviews the
     *       user wrote, which still count in the seller's rating and name their writer as
     *       {@value #DELETED_USER} ({@link #getReviewerName}); the reports the user filed and
     *       those filed on the user's listings. They still name the user by username, so the
     *       username is retired: it can never be registered again ({@link #usernameExists}).</li>
     * </ul>
     * A deal that was accepted but not completed stays as it is; deciding what happens to it
     * belongs to the admin's use case.
     *
     * <p>The caller records the reason and the returned {@link DeletedAccount#snapshot() snapshot}
     * in the Audit Log ({@link #addAuditLogEntry}; Special Requirement 4) and notifies the
     * affected users, who are in the returned lists. Like every method here, this does not
     * check who is logged in.
     *
     * @return what was deleted and cancelled
     * @throws IllegalArgumentException if there is no such account
     */
    public DeletedAccount deleteAccount(String username) {
        return transaction(() -> {
            User user = findUser(username);
            if (user == null) {
                throw new IllegalArgumentException("No account @" + username + " to delete.");
            }
            String name = user.getUsername();

            List<ChatThread> userThreads = threads.values().stream().filter(t -> t.isParticipant(name)).toList();
            List<Notification> board = notifications.stream()
                    .filter(n -> n.getRecipientUsername().equalsIgnoreCase(name)).toList();
            List<Offer> openOffers = offers.values().stream()
                    .filter(o -> o.isOpen() && (o.getBuyerUsername().equalsIgnoreCase(name)
                            || getListingOf(o).getSellerUsername().equalsIgnoreCase(name))).toList();
            List<Listing> removable = listings.values().stream()
                    .filter(l -> l.getSellerUsername().equalsIgnoreCase(name) && l.canBeRemoved()).toList();

            // Written before anything changes, so it shows the data as it was
            String snapshot = AccountSnapshot.describe(this, user, userThreads, board, openOffers, removable);

            openOffers.forEach(Offer::decline);
            removable.forEach(Listing::remove);
            userThreads.forEach(t -> threads.remove(t.getId()));
            notifications.removeAll(board);
            users.remove(name);
            retiredUsernames.add(name); // from now on, the reviews the user wrote name "Deleted User"
            user.setActive(false); // a session still holding this account is refused from now on
            return new DeletedAccount(user, userThreads, board, openOffers, removable, snapshot);
        });
    }

    // ========== Listings ==========

    /** Gives the listing an id, dates it as posted now, and adds it. */
    public Listing addListing(Listing listing) {
        return transaction(() -> {
            listing.markAdded(newId("L"), now());
            listings.put(listing.getId(), listing);
            return listing;
        });
    }

    /** Returns null if there is no such listing. */
    public Listing findListing(String id) {
        return read(() -> listings.get(id));
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

    /**
     * True if the listing is a live listing (glossary), one that buyers can see on Browse
     * Marketplace: status Live, Reserved or Lent, posted by a seller whose account is active.
     * This is wider than {@link #isAvailable}: a Reserved or Lent listing is live, but it
     * accepts no offers.
     */
    public boolean isLive(Listing listing) {
        return read(() -> {
            ListingStatus s = listing.getStatus();
            boolean liveStatus = s == ListingStatus.LIVE || s == ListingStatus.RESERVED || s == ListingStatus.LENT;
            return liveStatus && isSellerActive(listing);
        });
    }

    /**
     * True if the listing accepts offers, borrow requests and claims (glossary;
     * UC5 Special Requirement 1): its status is Live, it is not a giveaway with a pending
     * claim, and its seller is active (a deactivated seller's listings are hidden).
     */
    public boolean isAvailable(Listing listing) {
        return read(() -> listing.getStatus() == ListingStatus.LIVE
                && isSellerActive(listing)
                && !(listing instanceof GiveawayListing && hasPendingClaim(listing.getId())));
    }

    private boolean isSellerActive(Listing listing) {
        User seller = findUser(listing.getSellerUsername()); // matched like every username (regression log R7)
        return seller != null && seller.isActive();
    }

    /**
     * True if the listing has a pending claim. A giveaway is unavailable while it has one
     * (UC5 Special Requirement 1), and Browse shows that as its availability status (UC8).
     */
    public boolean hasPendingClaim(String listingId) {
        return read(() -> offers.values().stream().anyMatch(o -> o instanceof Claim
                && o.getListingId().equals(listingId) && o.getStatus() == OfferStatus.PENDING));
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

    // ========== Listing photos ==========

    /**
     * Stores an image as a listing photo and returns its id, to put into
     * {@link Listing#setPhotos}. The photo is kept encrypted in a file of its own, never
     * inside the listing, so saving the data stays fast. Checking that the bytes are an image
     * a screen can show is the caller's job ({@code PhotoService.importPhoto}).
     * @return the new photo's id, such as "I31"
     */
    public String addPhoto(byte[] image) {
        Objects.requireNonNull(image, "image");
        return transaction(() -> {
            String id = newId("I");
            if (storage != null) {
                storage.savePhoto(id, image);
            } else {
                photosInMemory.put(id, image.clone());
            }
            return id;
        });
    }

    /**
     * The image stored under a photo id from {@link #addPhoto}, or empty if there is none (a
     * listing whose photo file is missing then shows no photo).
     * @throws StorageException if the photo's file was changed or damaged
     */
    public Optional<byte[]> readPhoto(String photoId) {
        if (photoId == null) {
            return Optional.empty();
        }
        if (storage != null) {
            return isId(photoId) ? storage.loadPhoto(photoId) : Optional.empty();
        }
        return read(() -> Optional.ofNullable(photosInMemory.get(photoId)).map(byte[]::clone));
    }

    /** True for text made only of letters and digits, the form of every id this store gives out. */
    private static boolean isId(String text) {
        return text.matches("[A-Za-z0-9]+");
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
            offer.markAdded(newId("O"), now());
            offers.put(offer.getId(), offer);
            return offer;
        });
    }

    /** Returns null if there is no such offer. */
    public Offer findOffer(String id) {
        return read(() -> offers.get(id));
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
        return read(() -> threads.values().stream()
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
            thread.markAdded(newId("T"), now());
            threads.put(thread.getId(), thread);
            return thread;
        });
    }

    /** Returns null if there is no such thread. */
    public ChatThread findThreadById(String id) {
        return read(() -> threads.get(id));
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
            review.markAdded(newId("R"), now());
            reviews.put(review.getId(), review);
            return review;
        });
    }

    /** The review of a deal, or null if it has none. */
    public Review findReviewForOffer(String offerId) {
        return read(() -> reviews.values().stream()
                .filter(r -> r.getOfferId().equals(offerId)).findFirst().orElse(null));
    }

    public List<Review> getAllReviews() {
        return select(reviews.values(), r -> true);
    }

    /**
     * The seller's reviews that are shown to users, without the ones the admin hid: reviews
     * are "visible to all users unless the admin hides them" (UC10 Special Requirement 4;
     * UC8 Special Requirement 5; Manage All User Accounts Special Requirement 3).
     * Use this for every screen that shows reviews, the seller's own included.
     */
    public List<Review> getReviewsForSeller(String sellerUsername) {
        return select(reviews.values(), r -> !r.isHidden() && sellerUsername.equalsIgnoreCase(getSellerUsername(r)));
    }

    /**
     * Every review of the seller, hidden ones included. Only for the admin's moderation
     * (hiding and unhiding reviews, Task 3.8); never show this list to buyers or sellers.
     */
    public List<Review> getReviewsForSellerIncludingHidden(String sellerUsername) {
        return select(reviews.values(), r -> sellerUsername.equalsIgnoreCase(getSellerUsername(r)));
    }

    /**
     * The seller's rating as users see it, the derived attributes /ratingCount and
     * /averageRating as one value: the count and the average of the shown ratings (hidden ones
     * are not counted). Show it with {@code Formats.averageRating(rating)}.
     */
    public RatingSummary getRating(String sellerUsername) {
        return RatingSummary.of(getReviewsForSeller(sellerUsername));
    }

    /** The average of the seller's shown ratings (hidden ones are not counted), or empty if there are none. */
    public OptionalDouble getAverageRating(String sellerUsername) {
        RatingSummary rating = getRating(sellerUsername);
        return rating.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(rating.average());
    }

    /** The number of the seller's shown ratings (hidden ones are not counted). */
    public int getRatingCount(String sellerUsername) {
        return getRating(sellerUsername).count();
    }

    /** The seller who was reviewed: the seller of the reviewed deal's listing. */
    public String getSellerUsername(Review review) {
        return read(() -> listings.get(offers.get(review.getOfferId()).getListingId()).getSellerUsername());
    }

    /** The buyer who wrote the review. */
    public String getBuyerUsername(Review review) {
        return read(() -> offers.get(review.getOfferId()).getBuyerUsername());
    }

    /**
     * The name a screen shows as the writer of a review: {@value #DELETED_USER} if the buyer's
     * account was deleted (the review is kept; client answer 6), otherwise the buyer's full
     * name, as for an active or a deactivated account. Every screen that names a review's
     * writer uses this, so the buyer's, seller's and admin's screens name writers the same way.
     */
    public String getReviewerName(Review review) {
        return read(() -> {
            String buyer = getBuyerUsername(review);
            return isRetiredUsername(buyer) ? DELETED_USER : getFullName(buyer);
        });
    }

    // ========== Seller statistics ==========
    // The derived attributes of Seller in the class diagram, calculated from the listings, offers
    // and reviews so they are never out of date (View Seller Stats step 2; the admin's Seller
    // Ratings tab). /averageRating and /ratingCount are getAverageRating and getRatingCount above.

    /**
     * /totalViews: the views of all the seller's listings together, whatever their status
     * (a listing that was sold or removed keeps its views).
     */
    public int getTotalViews(String sellerUsername) {
        return read(() -> listings.values().stream()
                .filter(l -> l.getSellerUsername().equalsIgnoreCase(sellerUsername))
                .mapToInt(Listing::getViewCount).sum());
    }

    /**
     * /offersReceived: how many price offers, borrow requests and claims buyers made on the
     * seller's listings, whatever happened to them afterwards.
     */
    public int getOffersReceivedCount(String sellerUsername) {
        return getOffersForSeller(sellerUsername).size();
    }

    /**
     * /completedDeals: how many of the seller's deals are completed (glossary): a purchase or
     * claim the buyer marked complete, or a borrowed item that was returned.
     */
    public int getCompletedDealCount(String sellerUsername) {
        return (int) getOffersForSeller(sellerUsername).stream().filter(Offer::isCompleted).count();
    }

    // ========== Reports ==========

    /**
     * Gives the report an id and adds it, unless this buyer already reported this listing
     * (UC9 Special Requirement 3). The report must be filed by a buyer account on an existing
     * listing (class diagram, UC9). {@code ReportService} checks this first and tells the buyer;
     * here it is the inner layer of the barricade (Lecture 10, regression log R10).
     * @return false if the buyer already reported the listing
     * @throws IllegalArgumentException if there is no such listing, or the reporter is not a buyer account
     */
    public boolean addReport(Report report) {
        return transaction(() -> {
            if (listings.get(report.getListingId()) == null) {
                throw new IllegalArgumentException("No listing with id " + report.getListingId());
            }
            if (!(findUser(report.getBuyerUsername()) instanceof Buyer)) {
                throw new IllegalArgumentException("Only a buyer can report a listing: @" + report.getBuyerUsername());
            }
            if (hasReported(report.getBuyerUsername(), report.getListingId())) {
                return false;
            }
            report.markAdded(newId("P"));
            reports.put(report.getId(), report);
            return true;
        });
    }

    public boolean hasReported(String buyerUsername, String listingId) {
        return read(() -> reports.values().stream().anyMatch(r -> r.getListingId().equals(listingId)
                && r.getBuyerUsername().equalsIgnoreCase(buyerUsername)));
    }

    public List<Report> getReportsForListing(String listingId) {
        return select(reports.values(), r -> r.getListingId().equals(listingId));
    }

    public List<Report> getAllReports() {
        return select(reports.values(), r -> true);
    }

    // ========== Notifications ==========

    /** Gives the notification an id and a timestamp, and adds it to its recipient's board. */
    public Notification addNotification(Notification notification) {
        return transaction(() -> {
            notification.markAdded(newId("N"), now());
            notifications.add(notification);
            return notification;
        });
    }

    /** Returns null if there is no such notification. */
    public Notification findNotification(String id) {
        return read(() -> notifications.stream()
                .filter(n -> n.getId().equals(id)).findFirst().orElse(null));
    }

    /**
     * The user's notifications, newest first (UC4 step 2). Notifications with the same
     * timestamp are listed in reverse order of adding, so the latest still comes first.
     */
    public List<Notification> getNotificationsFor(String username) {
        return read(() -> {
            List<Notification> mine = new ArrayList<>(notifications.stream()
                    .filter(n -> n.getRecipientUsername().equalsIgnoreCase(username))
                    .toList());
            Collections.reverse(mine);
            mine.sort(Comparator.comparing(Notification::getTimestamp).reversed()); // stable sort
            return List.copyOf(mine);
        });
    }

    // ========== Admin records ==========

    public ListingActionRecord addListingAction(ListingActionRecord record) {
        return transaction(() -> {
            record.markAdded(newId("A"), now());
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

    /**
     * Gives the entry its log reference and date, and adds it. If it has a data snapshot and
     * the store saves to storage, the snapshot is also saved as its own encrypted .txt file
     * (class diagram: "Cascade with audit log with .txt file as data snapshot").
     */
    public AuditLogEntry addAuditLogEntry(AuditLogEntry entry) {
        return transaction(() -> {
            entry.markAdded(newId("G"), now());
            auditLog.add(entry);
            if (storage != null && entry.getDataSnapshot() != null) {
                storage.saveAuditSnapshot(entry.getLogReference(), entry.getDataSnapshot());
            }
            return entry;
        });
    }

    public List<AuditLogEntry> getAuditLog() {
        return select(auditLog, e -> true);
    }

    /** The snapshot file saved for an audit log entry, or empty if there is none (or no storage). */
    public Optional<String> readAuditSnapshotFile(String logReference) {
        return storage == null ? Optional.empty() : storage.loadAuditSnapshot(logReference);
    }

    // ========== Saving and loading ==========

    /**
     * Loads the saved data into this empty store, or, if nothing is saved yet, fills it with
     * the demo data and saves that. If the saved file cannot be read (changed, damaged, or
     * saved with another key), it is moved aside, not deleted, the demo data is used instead,
     * and {@link #getLoadProblem()} explains what happened.
     */
    public void loadOrSeed() {
        if (storage == null) {
            throw new IllegalStateException("This store has no storage to load from.");
        }
        try {
            Optional<StoreSnapshot> saved = storage.load();
            if (saved.isPresent()) {
                restore(saved.get());
                return;
            }
        } catch (StorageException e) {
            String movedTo = storage.setAsideUnreadable();
            loadProblem = "The saved data could not be read: " + e.getMessage() + " It was kept as "
                    + movedTo + ", and the demo data was loaded instead.";
        }
        transaction(() -> SeedData.populate(this)); // one transaction, so it is saved once at the end
    }

    /** Why the saved data could not be loaded at start-up, or null if it loaded normally. */
    public String getLoadProblem() {
        return loadProblem;
    }

    /** Everything in the store at this moment, for saving. */
    StoreSnapshot snapshot() {
        return read(() -> {
            StoreSnapshot s = new StoreSnapshot();
            s.nextId = nextId;
            s.users = List.copyOf(users.values());
            s.retiredUsernames = List.copyOf(retiredUsernames);
            s.listings = List.copyOf(listings.values());
            s.offers = List.copyOf(offers.values());
            s.threads = List.copyOf(threads.values());
            s.reviews = List.copyOf(reviews.values());
            s.reports = List.copyOf(reports.values());
            s.notifications = List.copyOf(notifications);
            s.listingActions = List.copyOf(listingActions);
            s.auditLog = List.copyOf(auditLog);
            return s;
        });
    }

    /** Puts loaded records back into this empty store. They keep their ids and dates. */
    private void restore(StoreSnapshot s) {
        read(() -> {
            if (!users.isEmpty() || !listings.isEmpty()) {
                throw new IllegalStateException("Saved data can only be loaded into an empty store.");
            }
            s.users.forEach(u -> users.put(u.getUsername(), u));
            retiredUsernames.addAll(s.retiredUsernames);
            s.listings.forEach(l -> listings.put(l.getId(), l));
            s.offers.forEach(o -> offers.put(o.getId(), o));
            s.threads.forEach(t -> threads.put(t.getId(), t));
            s.reviews.forEach(r -> reviews.put(r.getId(), r));
            s.reports.forEach(r -> reports.put(r.getId(), r));
            notifications.addAll(s.notifications);
            listingActions.addAll(s.listingActions);
            auditLog.addAll(s.auditLog);
            nextId = s.nextId;
            return null;
        });
    }

    /** Saves everything, if this store has storage. Called after each outermost write. */
    private void persist() {
        if (storage != null) {
            storage.save(snapshot()); // the storage skips the write if nothing changed
        }
    }
}
