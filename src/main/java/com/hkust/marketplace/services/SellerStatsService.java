package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Seller;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Task 2.8 View Seller Stats: the numbers the seller's stats screen shows, counted from the
 * store in one consistent step (View Seller Stats steps 3-5).
 *
 * <ul>
 *   <li><b>Number of views</b> - all the seller's listings together
 *       ({@link DataStore#getTotalViews}); a sold or removed listing keeps its views.</li>
 *   <li><b>Offers received</b> - every price offer, borrow request and claim ever made on the
 *       seller's listings, whatever happened to it afterwards.</li>
 *   <li><b>Completed deals</b> - the deals among them that are completed (sold, given, or a
 *       borrowed item returned), and the same split by listing type for the pie chart.</li>
 *   <li><b>Average rating</b> - over the shown reviews only; hidden ones are excluded
 *       ({@link DataStore#getAverageRating}).</li>
 * </ul>
 *
 * <p>The per-listing rows for the bar chart are the listing's own views and the number of
 * offers on it (pending, declined or completed: all of them). The screen only draws these
 * numbers; every count is here and covered by {@code SellerStatsServiceTest}.
 */
public class SellerStatsService {

    private final DataStore store;

    public SellerStatsService() {
        this(DataStore.getInstance());
    }

    public SellerStatsService(DataStore store) {
        this.store = store;
    }

    /**
     * The statistics of the logged-in seller.
     * @throws AccessDeniedException if the logged-in user is not a seller
     */
    public SellerStats forCurrentSeller() {
        Seller seller = AccessControl.requireSeller();
        return store.read(() -> {
            String username = seller.getUsername();
            List<Listing> listings = store.getListingsBySeller(username);

            List<SellerStats.ListingStat> perListing = new ArrayList<>();
            for (Listing listing : listings) {
                perListing.add(new SellerStats.ListingStat(listing.getId(), listing.getTitle(),
                        listing.getViewCount(), store.getOffersForListing(listing.getId()).size()));
            }

            Map<ListingType, Integer> completedByType = new EnumMap<>(ListingType.class);
            for (ListingType type : ListingType.values()) {
                completedByType.put(type, 0);
            }
            for (Offer offer : store.getOffersForSeller(username)) {
                if (offer.isCompleted()) {
                    Listing listing = store.getListingOf(offer);
                    completedByType.merge(listing.getType(), 1, Integer::sum);
                }
            }

            return new SellerStats(listings.size(), store.getTotalViews(username),
                    store.getOffersReceivedCount(username), store.getCompletedDealCount(username),
                    store.getAverageRating(username), store.getRatingCount(username),
                    perListing, completedByType);
        });
    }
}
