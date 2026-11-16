package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * UC8 Browse Marketplace, for the Buyer (Tasks 1.3 and 1.4): searching, filtering and sorting
 * the live listings, opening a listing's detail, and viewing the seller's profile and reviews.
 * <pre>{@code
 * List<Listing> results = browse.search(new BrowseQuery("lamp", null, null, null, "", "100", ListingSort.NEWEST));
 * ListingDetail detail = browse.openListing(listing.getId());     // adds one view
 * SellerProfile seller = browse.getSellerProfile(listing.getSellerUsername());
 * }</pre>
 * This use case changes nothing except a listing's view count.
 */
public class BrowseService {

    /** UC8 A2, shown when a typed price is not an amount of HK$0 or more, or the minimum is above the maximum. */
    static final String INVALID_PRICE_RANGE = "The price range is invalid. "
            + "Enter amounts of HK$0 or more, with the minimum no greater than the maximum.";

    private static final Comparator<Listing> NEWEST_FIRST =
            Comparator.comparing(Listing::getPostedDate, Comparator.reverseOrder());

    private final DataStore store;

    public BrowseService() {
        this(DataStore.getInstance());
    }

    public BrowseService(DataStore store) {
        this.store = store;
    }

    // ========== Listings (UC8 steps 2-4) ==========

    /**
     * The live listings that match the query, in its sort order. A listing matches when its
     * title or description contains the keyword (upper and lower case are the same) and it
     * fits every chosen filter. For prices, a giveaway counts as HK$0 and a lend listing has
     * no price: lend listings are left out when a price range is set, and come after the
     * priced listings when sorting by price (Special Requirement 4).
     * @return the matching listings; empty if none match (A1)
     * @throws ServiceException if the price range is invalid (A2)
     */
    public List<Listing> search(BrowseQuery query) {
        AccessControl.requireBuyer();
        OptionalDouble min = amount(query.minPrice());
        OptionalDouble max = amount(query.maxPrice());
        if (min.isPresent() && max.isPresent() && min.getAsDouble() > max.getAsDouble()) {
            throw new ServiceException(INVALID_PRICE_RANGE);
        }
        String keyword = query.keyword().toLowerCase(Locale.ROOT);
        return store.getLiveListings().stream()
                .filter(listing -> containsKeyword(listing, keyword))
                .filter(listing -> query.category() == null || listing.getCategory() == query.category())
                .filter(listing -> query.type() == null || listing.getType() == query.type())
                .filter(listing -> query.condition() == null || listing.getCondition() == query.condition())
                .filter(listing -> !query.hasPriceRange() || isPricedWithin(listing, min, max))
                .sorted(order(query.sort()))
                .toList();
    }

    /** The typed amount, or empty if nothing was typed. */
    private static OptionalDouble amount(String typed) {
        if (typed.isEmpty()) {
            return OptionalDouble.empty();
        }
        OptionalDouble amount = Amounts.parse(typed);
        if (amount.isEmpty()) {
            throw new ServiceException(INVALID_PRICE_RANGE); // negative, or not a number
        }
        return amount;
    }

    private static boolean containsKeyword(Listing listing, String keyword) {
        return contains(listing.getTitle(), keyword) || contains(listing.getDescription(), keyword);
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    /** The price used for filtering and sorting: a sale's price, HK$0 for a giveaway, none for a lend listing. */
    private static OptionalDouble priceOf(Listing listing) {
        if (listing instanceof SaleListing sale) {
            return OptionalDouble.of(sale.getPrice());
        }
        return listing instanceof GiveawayListing ? OptionalDouble.of(0) : OptionalDouble.empty();
    }

    private static boolean isPricedWithin(Listing listing, OptionalDouble min, OptionalDouble max) {
        OptionalDouble price = priceOf(listing);
        return price.isPresent()
                && (min.isEmpty() || price.getAsDouble() >= min.getAsDouble())
                && (max.isEmpty() || price.getAsDouble() <= max.getAsDouble());
    }

    /** Listings with the same price, and the unpriced ones, are ordered newest first. */
    private static Comparator<Listing> order(ListingSort sort) {
        Comparator<Listing> pricedFirst = Comparator.comparing(listing -> priceOf(listing).isEmpty());
        Comparator<Listing> byPrice = Comparator.comparingDouble(listing -> priceOf(listing).orElse(0));
        return switch (sort) {
            case NEWEST -> NEWEST_FIRST;
            case PRICE_LOW_TO_HIGH -> pricedFirst.thenComparing(byPrice).thenComparing(NEWEST_FIRST);
            case PRICE_HIGH_TO_LOW -> pricedFirst.thenComparing(byPrice.reversed()).thenComparing(NEWEST_FIRST);
        };
    }

    // ========== Listing detail (UC8 steps 5-7) ==========

    /**
     * Opens a listing's detail screen: adds one to its view count (Special Requirement 2) and
     * returns what the screen shows. A buyer can open any live listing, available or not, and
     * any listing they have a deal on, whatever its status, so that it can be reopened from
     * My Deals.
     * @throws ServiceException if there is no such listing, or it is not live and the buyer has no deal on it
     */
    public ListingDetail openListing(String listingId) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.transaction(() -> {
            Listing listing = requireOpenable(buyer, listingId);
            listing.incrementViewCount();
            return detailOf(listing);
        });
    }

    /**
     * What an already open detail screen should show now, without counting a view: for
     * redrawing it after the buyer acted on the listing, for example after a claim made the
     * giveaway unavailable (UC5).
     * @throws ServiceException under the same conditions as {@link #openListing}
     */
    public ListingDetail reloadListing(String listingId) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.read(() -> detailOf(requireOpenable(buyer, listingId)));
    }

    private Listing requireOpenable(Buyer buyer, String listingId) {
        Listing listing = store.findListing(listingId);
        if (listing == null || !(store.isLive(listing) || hasDealOn(buyer, listing))) {
            throw new ServiceException("This listing is no longer on the marketplace.");
        }
        return listing;
    }

    private ListingDetail detailOf(Listing listing) {
        return new ListingDetail(listing, store.isAvailable(listing), profileOf(listing.getSellerUsername()));
    }

    private boolean hasDealOn(Buyer buyer, Listing listing) {
        return store.getOffersForListing(listing.getId()).stream()
                .anyMatch(offer -> offer.getBuyerUsername().equalsIgnoreCase(buyer.getUsername()));
    }

    // ========== Seller profile (UC8 A3) ==========

    /**
     * The profile of a seller: full name, bio, average rating and reviews, each with the
     * seller's reply, if any. Reviews the admin hid are left out (Special Requirement 5).
     * @throws ServiceException if the username is not a seller's
     */
    public SellerProfile getSellerProfile(String sellerUsername) {
        AccessControl.requireBuyer();
        return store.read(() -> {
            User user = store.findUser(sellerUsername);
            // A seller whose account the admin deleted is still named by the listings kept as records
            boolean deletedSeller = user == null && sellerUsername != null
                    && !store.getListingsBySeller(sellerUsername.strip()).isEmpty();
            if (!(user instanceof Seller) && !deletedSeller) {
                throw new ServiceException("This seller's profile is not available.");
            }
            return profileOf(sellerUsername.strip());
        });
    }

    private SellerProfile profileOf(String sellerUsername) {
        User user = store.findUser(sellerUsername);
        String username = user != null ? user.getUsername() : sellerUsername.toLowerCase(Locale.ROOT);
        String bio = user instanceof Seller seller ? seller.getBio() : null;
        List<Review> newestFirst = store.getReviewsForSeller(username).stream()
                .sorted(Comparator.comparing(Review::getReviewDate, Comparator.reverseOrder()))
                .toList();
        return new SellerProfile(username, store.getFullName(username), bio,
                store.getAverageRating(username), store.getRatingCount(username), newestFirst);
    }
}
