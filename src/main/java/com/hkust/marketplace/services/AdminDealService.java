package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Control class for Task 3.5 All Deals Record: the list with filters, VIEW DETAILS and CSV
 * export. A deal is an accepted offer, in progress or completed; pending, countered and
 * declined offers are not deals. Every method starts with the admin role check. This class
 * does not use the ui layer.
 */
public class AdminDealService {

    private static final DateTimeFormatter CSV_DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH);

    private static final String CSV_HEADER =
            "Item Title,Buyer,Seller,Deal Type,Start Date,Return/Complete Date,Status";

    private final DataStore store;

    public AdminDealService() {
        this(DataStore.getInstance());
    }

    public AdminDealService(DataStore store) {
        this.store = store;
    }

    /**
     * Lists the deals for the admin (Task 3.5 steps 2-3), newest start date first. Every chosen
     * filter must match; a null filter field means "no filter".
     *
     * @param filter the filter options, or null for no filter
     * @return the matching deals; empty if none match (A1), never null
     */
    public List<DealRecord> findDeals(DealFilter filter) {
        AccessControl.requireAdmin();
        DealFilter f = filter == null ? DealFilter.none() : filter;
        String sellerText = normalise(f.seller());
        String buyerText = normalise(f.buyer());

        return store.read(() -> store.getAllOffers().stream()
                .filter(o -> o.getStatus() == OfferStatus.ACCEPTED)      // deals only
                .map(this::recordOf)
                .filter(r -> f.status() == null || r.state() == f.status())
                .filter(r -> f.type() == null || r.dealType() == f.type())
                .filter(r -> sellerText.isEmpty()
                        || r.sellerUsername().toLowerCase(Locale.ROOT).contains(sellerText))
                .filter(r -> buyerText.isEmpty()
                        || r.buyerUsername().toLowerCase(Locale.ROOT).contains(buyerText))
                .filter(r -> f.fromDate() == null || !r.startDate().isBefore(f.fromDate()))
                .filter(r -> f.toDate() == null || !r.startDate().isAfter(f.toDate()))
                .sorted(Comparator.comparing(DealRecord::startDate).reversed())
                .toList());
    }

    /**
     * The details of one deal for VIEW DETAILS (Task 3.5 step 4.1).
     *
     * @throws ServiceException if the offer does not exist or is not a deal (not accepted)
     */
    public DealDetails getDealDetails(String offerId) {
        AccessControl.requireAdmin();
        return store.read(() -> {
            Offer offer = store.findOffer(offerId);
            if (offer == null || offer.getStatus() != OfferStatus.ACCEPTED) {
                throw new ServiceException("This deal no longer exists.");
            }
            DealRecord record = recordOf(offer);
            Listing listing = record.listing();
            User buyer = store.findUser(offer.getBuyerUsername());   // null if the account was deleted
            User seller = store.findUser(listing.getSellerUsername());
            Review review = store.findReviewForOffer(offer.getId());

            Double amount = offer instanceof PriceOffer p ? p.getOfferedPrice() : null;
            LocalDateTime borrowStart = offer instanceof BorrowRequest b ? b.getProposedStartDate() : null;
            LocalDateTime borrowEnd = offer instanceof BorrowRequest b ? b.getProposedEndDate() : null;

            return new DealDetails(offer, listing,
                    offer.getBuyerUsername(), store.getFullName(offer.getBuyerUsername()),
                    buyer instanceof Buyer bu ? bu.getResidenceHall() : null,
                    listing.getSellerUsername(), store.getFullName(listing.getSellerUsername()),
                    seller instanceof Seller s ? s.getBio() : null,
                    listing.getType(), amount, borrowStart, borrowEnd,
                    record.startDate(), record.completedDate(), record.state(),
                    review != null, review != null && review.isHidden());
        });
    }

    /**
     * Exports deals as CSV text (Task 3.5 step 4.2), normally the currently filtered list. Every
     * text field is escaped. No file is written; the screen saves the returned string.
     */
    public String exportCsv(List<DealRecord> deals) {
        AccessControl.requireAdmin();
        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (DealRecord d : deals) {
            csv.append('\n')
                    .append(escapeCsv(d.listing().getTitle())).append(',')
                    .append(escapeCsv(d.buyerUsername())).append(',')
                    .append(escapeCsv(d.sellerUsername())).append(',')
                    .append(escapeCsv(typeName(d))).append(',')
                    .append(escapeCsv(formatDate(d.startDate()))).append(',')
                    .append(escapeCsv(formatDate(d.completedDate()))).append(',')
                    .append(escapeCsv(d.state().getDisplayName()));
        }
        return csv.toString();
    }

    // ---------- helpers ----------

    private DealRecord recordOf(Offer offer) {
        Listing listing = store.getListingOf(offer);
        LocalDateTime start = offer instanceof BorrowRequest r
                ? r.getProposedStartDate()      // the accepted request holds the agreed start
                : offer.getAcceptedDate();
        DealState state = !offer.isCompleted() ? DealState.IN_PROGRESS
                : offer instanceof BorrowRequest ? DealState.RETURNED
                : DealState.COMPLETED;
        return new DealRecord(offer, listing, offer.getBuyerUsername(), listing.getSellerUsername(),
                listing.getType(), start, offer.getCompletedDate(), state);
    }

    private static String normalise(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    private static String typeName(DealRecord d) {
        return switch (d.dealType()) {
            case SELL -> "Sale";
            case LEND -> "Loan";
            case GIVEAWAY -> "Giveaway";
        };
    }

    private static String formatDate(LocalDateTime time) {
        return time == null ? "" : time.format(CSV_DATE_TIME);
    }

    /** Quotes the field if it holds a comma, a double quote or a line break; doubles any quote. */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}