package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * UC11 View Deal History, for the Buyer (Task 1.9): every completed deal of the buyer, newest
 * deal date first, narrowed down by a date range, a category and part of the seller's name.
 *
 * <p><b>Design (Lecture 15).</b> This is the use case's control class. Its boundary is the
 * buyer's Deal History screen ({@code controllers.buyer.DealHistoryView}); the entities are
 * {@link Offer}, {@link Listing} and the store. The history is not stored anywhere: it is worked
 * out from the deals' completion dates each time it is shown, so a deal enters it the moment it
 * is completed, by the buyer or by the borrowing timer (Special Requirement 1). The query is
 * here and not in {@code DataStore}, which would otherwise keep growing (God Class,
 * Lecture 18). It reads the store through {@link DataStore#read}, so it sees the data whole
 * even while the timer completes deals on its own thread (active class, Lecture 15).
 *
 * <p><b>Defensive programming (Lecture 10).</b> What the screen sends is checked here, not in the
 * screen: the order of the date range (step 4, A2). The dates and the category cannot be
 * unreadable, because the screen offers them as a calendar and a list (Lecture 11: make the
 * error impossible). The use case changes nothing (its postcondition), and this class only reads.
 */
public class DealHistoryService {

    /** UC11 A2. */
    static final String INVALID_DATE_RANGE =
            "The date range is invalid. Choose a start date on or before the end date.";

    /**
     * UC11 step 2: newest deal date first. Deals with the same deal date are shown newest offer
     * first, so the order never depends on how the store happens to keep them.
     */
    static final Comparator<HistoryEntry> NEWEST_FIRST = Comparator
            .comparing((HistoryEntry entry) -> entry.deal().getCompletedDate())
            .thenComparing(entry -> entry.deal().getOfferDate())
            .reversed();

    private final DataStore store;

    public DealHistoryService() {
        this(DataStore.getInstance());
    }

    public DealHistoryService(DataStore store) {
        this.store = store;
    }

    // ========== Show History, Refine and Show Results (UC11 steps 2-4) ==========

    /**
     * The logged-in buyer's completed deals that match the query, newest deal date first. A
     * deal matches when its deal date falls within the date range (both ends included; either
     * end may be missing), its listing is in the chosen category, and its seller's name contains
     * the search text, whatever the case (step 4). {@link HistoryQuery#ALL} gives every
     * completed deal (step 2).
     * @return the matching deals; empty if there are none (A1)
     * @throws InvalidDateRangeException if the date range starts after it ends (A2); nothing is
     *         read then
     */
    public List<HistoryEntry> find(HistoryQuery query) {
        Objects.requireNonNull(query, "query");
        Buyer buyer = AccessControl.requireBuyer();
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new InvalidDateRangeException(INVALID_DATE_RANGE);
        }
        return store.read(() -> store.getOffersByBuyer(buyer.getUsername()).stream()
                .filter(Offer::isCompleted) // Special Requirement 1; the glossary's "Completed deal"
                .map(this::entryOf)
                .filter(entry -> inRange(entry.dealDate(), query.from(), query.to()))
                .filter(entry -> inCategory(entry.listing(), query.category()))
                .filter(entry -> sellerMatches(entry.sellerName(), query.seller()))
                .sorted(NEWEST_FIRST)
                .toList());
    }

    // ========== The filters (step 4) ==========

    /** The day of the deal date is within the range: on or after its first day, and on or before its last. */
    static boolean inRange(LocalDateTime dealDate, LocalDate from, LocalDate to) {
        LocalDate day = dealDate.toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    /** No category is chosen, or the deal's listing is in it. */
    static boolean inCategory(Listing listing, Category category) {
        return category == null || listing.getCategory() == category;
    }

    /** No search text was typed, or the seller's name as shown contains it; upper and lower case are the same. */
    static boolean sellerMatches(String sellerName, String search) {
        return search.isEmpty()
                || sellerName.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private HistoryEntry entryOf(Offer deal) {
        Listing listing = store.getListingOf(deal);
        return new HistoryEntry(deal, listing, store.getFullName(listing.getSellerUsername()));
    }
}
