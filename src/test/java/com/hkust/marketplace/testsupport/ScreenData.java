package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.services.DealRules;
import com.hkust.marketplace.services.PasswordHasher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data of a screen test's own, added to the app's store that the screens use
 * ({@link OffScreenApp#store()}) through the same model events and deal rules the app uses.
 * The tests of a run share that store, so each test names its accounts and listings in a way
 * no other test does. Accounts log in with {@link SeedData#DEMO_PASSWORD}.
 * <pre>{@code
 * ScreenData data = new ScreenData(app.store());
 * data.buyer("mdbuyer", "Mia Deal");
 * data.seller("mdseller", "Sam Deal");
 * SaleListing kettle = data.sale("mdseller", "Kettle to Complete", 80);
 * data.accept(data.priceOffer("mdbuyer", kettle, 70));
 * }</pre>
 * It never logs anyone in, so a test can also change data while a user is logged in on the
 * screens, as another user or the borrowing-deadline timer would.
 */
public final class ScreenData {

    private static final String DEMO_HASH = PasswordHasher.hash(SeedData.DEMO_PASSWORD);

    private final DataStore store;
    private final DealRules rules;

    public ScreenData(DataStore store) {
        this.store = store;
        this.rules = new DealRules(store);
    }

    // ---------- Accounts ----------

    /** A buyer who gave no residence hall. */
    public void buyer(String username, String fullName) {
        store.addUser(new Buyer(username, fullName, DEMO_HASH, null));
    }

    public void seller(String username, String fullName) {
        store.addUser(new Seller(username, fullName, DEMO_HASH));
    }

    /** The admin deactivates the account; a user logged in with it is refused from their next action. */
    public void deactivate(String username) {
        store.transaction(() -> store.findUser(username).setActive(false));
    }

    /** The admin deletes the account (Task 3.3, client answer 6): its listings are removed, past deals are kept. */
    public void deleteAccount(String username) {
        store.deleteAccount(username);
    }

    // ---------- Listings, approved by the admin (Live) ----------

    public SaleListing sale(String seller, String title, double price) {
        return live(new SaleListing(seller, title, Category.ELECTRONICS, ItemCondition.USED,
                "Test item: " + title, MeetupLocation.LIBRARY, price));
    }

    public LendListing lend(String seller, String title, Duration lendingDuration) {
        return live(new LendListing(seller, title, Category.ELECTRONICS, ItemCondition.USED,
                "Test item: " + title, MeetupLocation.LIBRARY, lendingDuration));
    }

    public GiveawayListing giveaway(String seller, String title) {
        return live(new GiveawayListing(seller, title, Category.FURNITURE, ItemCondition.USED,
                "Test item: " + title, MeetupLocation.LIBRARY));
    }

    private <L extends Listing> L live(L listing) {
        store.addListing(listing);
        store.transaction(listing::approve);
        return listing;
    }

    /**
     * Gives the listing these photos, in order; the first is its thumbnail.
     * @return the photos' ids, in the same order
     */
    public List<String> photos(Listing listing, byte[]... images) {
        List<String> ids = new ArrayList<>();
        for (byte[] image : images) {
            ids.add(store.addPhoto(image));
        }
        store.transaction(() -> listing.setPhotos(ids));
        return ids;
    }

    /**
     * Overwrites the photo's file with bytes that are not an encrypted photo, as a damaged disk
     * would. The file is in the run's data folder (storage manual §3).
     */
    public void damagePhotoFile(String photoId) {
        String folder = Objects.requireNonNull(System.getProperty(DataStore.DATA_FOLDER_PROPERTY),
                "the test run's data folder, which TestDataFolder sets");
        Path file = Path.of(folder, "marketplace-photos", photoId + ".img");
        try {
            Files.writeString(file, "damaged");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not damage " + file, e);
        }
    }

    /** The admin removes the listing (Task 3.7): its open offers are declined. */
    public void removeByAdmin(Listing listing) {
        store.transaction(() -> {
            rules.declineOpenOffers(listing, NotificationCategory.LISTING_REMOVED, "the admin removed the listing");
            listing.remove();
        });
    }

    // ---------- Deals: made in the buyer's chat thread with the listing, as Make Offer does ----------

    public PriceOffer priceOffer(String buyer, Listing listing, double price) {
        return (PriceOffer) offer(new PriceOffer(listing.getId(), buyer, price));
    }

    public BorrowRequest borrowRequest(String buyer, Listing listing, LocalDateTime start, LocalDateTime end) {
        return (BorrowRequest) offer(new BorrowRequest(listing.getId(), buyer, start, end));
    }

    public Claim claim(String buyer, Listing listing) {
        return (Claim) offer(new Claim(listing.getId(), buyer));
    }

    private Offer offer(Offer offer) {
        return store.transaction(() -> {
            store.getOrCreateThread(offer.getBuyerUsername(), offer.getListingId());
            return store.addOffer(offer);
        });
    }

    /** The seller accepts the offer. */
    public void accept(Offer offer) {
        rules.accept(offer);
    }

    /** The seller counters the price offer. */
    public void counter(PriceOffer offer, double price) {
        store.transaction(() -> offer.counter(price));
    }

    /** The seller declines the offer. */
    public void decline(Offer offer) {
        store.transaction(offer::decline);
    }

    /** The buyer marks the deal complete. */
    public void complete(Offer deal) {
        rules.complete(deal);
    }

    // ---------- Chats and notifications ----------

    /** A message in the buyer's chat thread with the listing, from the buyer or the seller. */
    public void message(String buyer, Listing listing, String sender, String text) {
        store.transaction(() -> {
            ChatThread thread = store.getOrCreateThread(buyer, listing.getId());
            thread.addMessage(new Message(sender, text, store.now()));
        });
    }

    public void notification(String username, NotificationCategory category, String content) {
        store.addNotification(new Notification(username, category, content));
    }

    // ---------- Time ----------

    /**
     * Waits until the store's clock is past the moment, as the real time a short loan needs to
     * end. How long that is is known now, so it is one wait.
     */
    public void waitUntilAfter(LocalDateTime moment) {
        long millis = Duration.between(store.now(), moment).toMillis() + 10; // just past it, whatever the clock's step
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + moment, e);
        }
    }
}
