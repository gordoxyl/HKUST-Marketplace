package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.ui.Formats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Control class for Task 3.5 All Deals Record: list with filters and CSV export.
 * Both methods start with the admin role check.
 */
public class AdminDealService {

    private final DataStore store;

    public AdminDealService() {
        this(DataStore.getInstance());
    }

    public AdminDealService(DataStore store) {
        this.store = store;
    }

    /**
     * Lists all offers across the system for the admin (Task 3.5), filtered by any
     * combination of status, type, seller, buyer and offer date range. A null filter
     * field means "no filter on this". A null filter is treated as no filter.
     *
     * @param filter the filter options, or null for no filter
     * @return matching offers, never null; empty if none match (A1)
     */
    public List<Offer> findDeals(DealFilter filter) {
        AccessControl.requireAdmin();
        DealFilter f = filter == null ? DealFilter.none() : filter;

        String sellerText = f.seller() == null ? "" : f.seller().trim().toLowerCase(Locale.ROOT);
        String buyerText  = f.buyer()  == null ? "" : f.buyer().trim().toLowerCase(Locale.ROOT);

        return store.getAllOffers().stream()
                .filter(o -> f.status() == null || o.getStatus() == f.status())
                .filter(o -> f.type() == null || store.getListingOf(o).getType() == f.type())
                .filter(o -> sellerText.isEmpty()
                        || store.getListingOf(o).getSellerUsername().toLowerCase(Locale.ROOT).contains(sellerText))
                .filter(o -> buyerText.isEmpty()
                        || o.getBuyerUsername().toLowerCase(Locale.ROOT).contains(buyerText))
                .filter(o -> f.fromDate() == null || !o.getOfferDate().isBefore(f.fromDate()))
                .filter(o -> f.toDate()   == null || !o.getOfferDate().isAfter(f.toDate()))
                .toList();
    }

    /**
     * Exports a list of offers as a CSV string (Task 3.5 step 4.2). Titles that contain a
     * comma are wrapped in double quotes. No file I/O is performed; the caller saves or
     * downloads the string.
     *
     * <p>Headers: Deal ID, Item Title, Buyer, Seller, Type, Start Date, Complete Date, Status.
     *
     * @param deals the offers to export, typically the result of {@link #findDeals}
     * @return the CSV string including the header row
     */
    public String exportCsv(List<Offer> deals) {
        AccessControl.requireAdmin();
        StringBuilder sb = new StringBuilder(
                "Deal ID,Item Title,Buyer,Seller,Type,Start Date,Complete Date,Status");
        for (Offer offer : deals) {
            Listing listing = store.getListingOf(offer);
            sb.append('\n');
            sb.append(offer.getId()).append(',');
            sb.append(escapeCsvField(listing.getTitle())).append(',');
            sb.append(offer.getBuyerUsername()).append(',');
            sb.append(listing.getSellerUsername()).append(',');
            sb.append(listing.getType().getDisplayName()).append(',');
            sb.append(Formats.dateTime(offer.getOfferDate())).append(',');
            sb.append(Formats.dateTime(offer.getCompletedDate())).append(',');
            sb.append(offer.getStatus().getDisplayName());
        }
        return sb.toString();
    }

    /** Wraps a field in double quotes if it contains a comma, double quote or newline. */
    private static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}