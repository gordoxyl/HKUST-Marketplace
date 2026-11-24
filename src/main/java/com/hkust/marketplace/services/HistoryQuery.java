package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Category;

import java.time.LocalDate;

/**
 * What the buyer chose on Deal History (UC11 step 3): a date range, a category and part of a
 * seller's name. Every part is optional; {@link #ALL} asks for every completed deal, which is
 * what the screen shows when it opens (step 2).
 *
 * <p>The dates are kept as chosen. {@link DealHistoryService#find} checks their order (A2), so
 * the check is in one tested place and not in the screen.
 *
 * @param from     the first day of the date range, or null for no start
 * @param to       the last day of the date range, or null for no end
 * @param category null for every category
 * @param seller   part of the seller's name as typed; blank for no search
 */
public record HistoryQuery(LocalDate from, LocalDate to, Category category, String seller) {

    /** Every completed deal: what Deal History shows when it opens (UC11 step 2). */
    public static final HistoryQuery ALL = new HistoryQuery(null, null, null, "");

    /** Nothing typed is stored as blank text, never as null; spaces around the text are dropped. */
    public HistoryQuery {
        seller = seller == null ? "" : seller.strip();
    }
}
