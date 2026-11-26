package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.DealRules;
import com.hkust.marketplace.services.NotificationService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Adds practice listings and deals to the saved data, so a buyer's screens can be tried by
 * hand before the seller's screens exist. It does what a seller would do through Manage
 * Offers and Requests: accept, counter and decline the buyer's offers. Everything goes through
 * the same model events and {@link DealRules} as the app, so the data is exactly what the app
 * itself would have produced.
 *
 * <p>Run it with {@code scripts\practice-data.cmd} (Windows) or {@code sh scripts/practice-data.sh}
 * (macOS), with the app closed. It lives with the tests, so it is never part of the app.
 *
 * <p>What the chosen buyer gets:
 * <pre>
 * My Deals, current     Desk Fan             price offer accepted   -> Mark Complete (becomes Sold)
 *                       Free Notebooks       claim accepted         -> Mark Complete (becomes Given)
 *                       Camera Tripod        borrowed, due in 7 days -> Return Item
 *                       HDMI Cable           borrowed for 5 minutes -> reminded after 2.5 minutes, returned
 *                                            automatically after 5 (UC12), while the app runs
 *                       Bluetooth Speaker    price offer countered  -> Agree to Counter
 *                       Camping Tent         borrow request countered -> Agree to Counter
 *                       Scientific Calculator price offer pending
 * My Deals, completed   USB-C Hub            bought yesterday, not reviewed -> Rate Seller
 *                       Umbrella             borrowed, returned automatically
 * Only in the chat      Office Chair         price offer declined
 * Browse, available     Mechanical Keyboard (sale), Yoga Mat (sale), Rice Cooker (giveaway), Lab Coat (lend)
 * Browse, greyed out    Study Desk (Reserved), Projector (Lent), Winter Jacket (Claim Pending): another buyer's
 * </pre>
 */
public final class PracticeData {

    /** At the end of every practice listing's description, so the data can be recognised later. */
    public static final String MARKER = "[practice data]";

    private final DataStore store;
    private final DealRules rules;
    private final NotificationService notifications;
    private final String buyer;
    private final String otherBuyer;
    private final Clock realClock;
    private final LocalDateTime now;

    private PracticeData(DataStore store, String buyer, String otherBuyer) {
        this.store = store;
        this.rules = new DealRules(store);
        this.notifications = new NotificationService(store);
        this.buyer = buyer;
        this.otherBuyer = otherBuyer;
        this.realClock = store.getClock();
        this.now = store.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Adds the practice listings and deals for the buyer, as one saved step.
     * @param buyer      the buyer who gets the deals
     * @param otherBuyer another buyer, who holds the listings that are unavailable to the first
     * @throws IllegalArgumentException if either account is not a buyer, or the sellers
     *                                  {@code charlie} and {@code diana} of the demo data are missing
     */
    public static void addTo(DataStore store, String buyer, String otherBuyer) {
        for (String username : List.of(buyer, otherBuyer)) {
            if (!(store.findUser(username) instanceof Buyer)) {
                throw new IllegalArgumentException("There is no buyer account @" + username + ".");
            }
        }
        for (String username : List.of("charlie", "diana")) {
            if (!(store.findUser(username) instanceof Seller)) {
                throw new IllegalArgumentException("The demo seller @" + username + " is missing.");
            }
        }
        PracticeData practice = new PracticeData(store, store.findUser(buyer).getUsername(),
                store.findUser(otherBuyer).getUsername());
        store.transaction(() -> {
            try {
                practice.addEverything();
            } finally {
                store.setClock(practice.realClock);
            }
        });
    }

    /** True if practice data was added to this store before. */
    public static boolean isIn(DataStore store) {
        return store.getAllListings().stream()
                .anyMatch(listing -> listing.getDescription() != null && listing.getDescription().contains(MARKER));
    }

    private void addEverything() {
        // ---------- Browse: available listings of every type ----------
        at(now.minusDays(5));
        sale("charlie", "Mechanical Keyboard", Category.ELECTRONICS, ItemCondition.LIKE_NEW, "Blue switches, barely used.", 320);
        at(now.minusDays(4));
        sale("diana", "Yoga Mat", Category.SPORTS_EQUIPMENT, ItemCondition.USED, "6 mm thick, purple.", 45);
        giveaway("diana", "Rice Cooker", Category.KITCHENWARE, ItemCondition.USED, "Small, works well.");
        at(now.minusDays(3));
        lend("charlie", "Lab Coat", Category.CLOTHING, ItemCondition.USED, "Size M, for CHEM labs.", Duration.ofDays(14));

        // ---------- Browse: listings another buyer holds, shown greyed out ----------
        Listing desk = sale("diana", "Study Desk", Category.FURNITURE, ItemCondition.USED, "120 cm wide, with a drawer.", 150);
        accept(priceOffer(desk, otherBuyer, 140));                                  // Reserved
        Listing projector = lend("charlie", "Projector", Category.ELECTRONICS, ItemCondition.LIKE_NEW, "1080p, with HDMI cable.", Duration.ofDays(3));
        accept(borrow(projector, otherBuyer, now.plusHours(1), now.plusDays(3)));   // Lent
        Listing jacket = giveaway("charlie", "Winter Jacket", Category.CLOTHING, ItemCondition.USED, "Size L, warm.");
        claim(jacket, otherBuyer);                                                  // Claim Pending

        // ---------- The buyer's current deals ----------
        at(now.minusDays(2));
        Listing fan = sale("charlie", "Desk Fan", Category.ELECTRONICS, ItemCondition.USED, "USB powered, quiet.", 80);
        Offer fanOffer = priceOffer(fan, buyer, 70);
        say(fanOffer, buyer, "Would you take 70 for the fan?");
        at(now.minusDays(1));
        say(fanOffer, "charlie", "Deal. Meet at the Library tomorrow?");
        accept(fanOffer);                                                           // Reserved -> Mark Complete

        Listing notebooks = giveaway("diana", "Free Notebooks", Category.STATIONERY, ItemCondition.NEW, "Five unused A4 notebooks.");
        accept(claim(notebooks, buyer));                                            // Reserved -> Mark Complete

        Listing tripod = lend("diana", "Camera Tripod", Category.ELECTRONICS, ItemCondition.LIKE_NEW, "Extends to 1.6 m.", Duration.ofDays(7));
        accept(borrow(tripod, buyer, now.minusHours(2), now.plusDays(7)));          // Lent -> Return Item

        at(now);
        Listing cable = lend("charlie", "HDMI Cable", Category.ELECTRONICS, ItemCondition.USED, "2 m long.", Duration.ofMinutes(5));
        accept(borrow(cable, buyer, now, now.plusMinutes(5)));                      // Lent, due in 5 minutes (UC12)

        at(now.minusDays(1));
        Listing speaker = sale("charlie", "Bluetooth Speaker", Category.ELECTRONICS, ItemCondition.LIKE_NEW, "Waterproof, 10 hours of battery.", 300);
        PriceOffer speakerOffer = (PriceOffer) priceOffer(speaker, buyer, 220);
        speakerOffer.counter(260);                                                  // -> Agree to Counter
        countered(speakerOffer, speaker);

        Listing tent = lend("diana", "Camping Tent", Category.SPORTS_EQUIPMENT, ItemCondition.USED, "Sleeps two.", Duration.ofDays(5));
        BorrowRequest tentRequest = (BorrowRequest) borrow(tent, buyer, now.plusDays(1), now.plusDays(6));
        tentRequest.counter(now.plusDays(3), now.plusDays(6));                      // -> Agree to Counter
        countered(tentRequest, tent);

        Listing calculator = sale("diana", "Scientific Calculator", Category.ELECTRONICS, ItemCondition.USED, "Casio fx-991.", 120);
        priceOffer(calculator, buyer, 100);                                         // Pending

        Listing chair = sale("charlie", "Office Chair", Category.FURNITURE, ItemCondition.USED, "Adjustable height.", 200);
        Offer chairOffer = priceOffer(chair, buyer, 90);
        chairOffer.decline();                                                       // only in the chat
        notifications.send(buyer, NotificationCategory.OFFER_UPDATE,
                seller(chair) + " declined your price offer for '" + chair.getTitle() + "'.");

        // ---------- The buyer's completed deals ----------
        at(now.minusDays(4));
        Listing hub = sale("diana", "USB-C Hub", Category.ELECTRONICS, ItemCondition.LIKE_NEW, "Four ports.", 90);
        Offer hubOffer = priceOffer(hub, buyer, 85);
        at(now.minusDays(3));
        accept(hubOffer);
        at(now.minusDays(1));
        rules.complete(hubOffer);                                                   // Sold, not reviewed -> Rate Seller

        at(now.minusDays(6));
        Listing umbrella = lend("charlie", "Umbrella", Category.OTHERS, ItemCondition.USED, "Large, black.", Duration.ofDays(2));
        BorrowRequest umbrellaRequest = (BorrowRequest) borrow(umbrella, buyer, now.minusDays(5), now.minusDays(2));
        at(now.minusDays(5));
        accept(umbrellaRequest);
        at(now.minusDays(2));
        rules.returnItem(umbrellaRequest, true);                                    // returned automatically, Live again
    }

    // ---------- What a seller does ----------

    private Listing sale(String seller, String title, Category category, ItemCondition condition, String description, double price) {
        return post(new SaleListing(seller, title, category, condition, description + " " + MARKER, meetup(seller), price));
    }

    private Listing lend(String seller, String title, Category category, ItemCondition condition, String description, Duration duration) {
        return post(new LendListing(seller, title, category, condition, description + " " + MARKER, meetup(seller), duration));
    }

    private Listing giveaway(String seller, String title, Category category, ItemCondition condition, String description) {
        return post(new GiveawayListing(seller, title, category, condition, description + " " + MARKER, meetup(seller)));
    }

    private static MeetupLocation meetup(String seller) {
        return seller.equals("charlie") ? MeetupLocation.LIBRARY : MeetupLocation.STUDENT_CENTRE;
    }

    /** Posts the listing and approves it, as the admin would. */
    private Listing post(Listing listing) {
        store.addListing(listing);
        listing.approve();
        return listing;
    }

    /** The seller accepts: Reserved or Lent, the other open offers declined, the buyer notified. */
    private void accept(Offer offer) {
        rules.accept(offer);
    }

    private void countered(Offer offer, Listing listing) {
        notifications.send(offer.getBuyerUsername(), NotificationCategory.OFFER_UPDATE,
                seller(listing) + " countered your " + offer.getKindName() + " for '" + listing.getTitle() + "'.");
    }

    private String seller(Listing listing) {
        return store.getFullName(listing.getSellerUsername());
    }

    // ---------- What a buyer does ----------

    private Offer priceOffer(Listing listing, String from, double price) {
        return made(listing, store.addOffer(new PriceOffer(listing.getId(), from, price)), "made a price offer for");
    }

    private Offer borrow(Listing listing, String from, LocalDateTime start, LocalDateTime end) {
        return made(listing, store.addOffer(new BorrowRequest(listing.getId(), from, start, end)), "sent a borrow request for");
    }

    private Offer claim(Listing listing, String from) {
        return made(listing, store.addOffer(new Claim(listing.getId(), from)), "claimed");
    }

    private Offer made(Listing listing, Offer offer, String what) {
        notifications.send(listing.getSellerUsername(), NotificationCategory.OFFER_RECEIVED,
                store.getFullName(offer.getBuyerUsername()) + " " + what + " '" + listing.getTitle() + "'.");
        return offer;
    }

    private void say(Offer offer, String sender, String text) {
        store.findThread(offer.getBuyerUsername(), offer.getListingId()).addMessage(new Message(sender, text, store.now()));
    }

    /** Dates what follows, as the demo data does. */
    private void at(LocalDateTime when) {
        ZoneId zone = realClock.getZone();
        store.setClock(Clock.fixed(when.atZone(zone).toInstant(), zone));
    }

    // ---------- Running it on the app's saved data ----------

    /**
     * Adds the practice data to the app's saved data in the folder it is run from.
     * Arguments: the buyer's username (default {@code alice}),
     * and {@code --force} to add another set although one is already there.
     */
    public static void main(String[] args) {
        boolean force = List.of(args).contains("--force");
        String chosen = Arrays.stream(args).filter(arg -> !arg.startsWith("--")).findFirst().orElse(null);

        DataStore store = DataStore.getInstance();
        if (store.getLoadProblem() != null) {
            System.out.println("The saved data could not be read, so nothing was added.");
            System.exit(1);
        }
        String buyer = chosen != null ? chosen : "alice";
        String other = buyer.equalsIgnoreCase("bob") ? "alice" : "bob";
        if (isIn(store) && !force) {
            System.out.println("Practice data is already in this save file. Add --force (-Force on Windows) for "
                    + "another set, or reset the data first.");
            System.exit(2);
        }
        try {
            addTo(store, buyer, other);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage() + " Nothing was added.");
            System.exit(1);
        }
        System.out.println("Added practice data for @" + buyer + " (the other buyer is @" + other + ").");
        System.out.println("  My Deals: Desk Fan and Free Notebooks (Mark Complete), Camera Tripod and HDMI Cable (Return Item;");
        System.out.println("            the cable is due in 5 minutes: reminder after 2.5, then returned automatically),");
        System.out.println("            Bluetooth Speaker and Camping Tent (Agree to Counter),");
        System.out.println("            Scientific Calculator (pending), USB-C Hub (completed, Rate Seller), Umbrella (returned).");
        System.out.println("  Browse:   4 more available listings; Study Desk, Projector and Winter Jacket are greyed out.");
    }
}
