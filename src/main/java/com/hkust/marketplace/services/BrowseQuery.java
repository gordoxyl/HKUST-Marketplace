package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.ListingType;

/**
 * What the buyer asked Browse Marketplace to show (UC8 step 3): a keyword, the filters and a
 * sort order. Every part is optional; {@link #ALL} asks for every live listing, newest first.
 *
 * <p>The two prices are kept as the buyer typed them. {@link BrowseService#search} checks them
 * (UC8 A2), so the check is in one tested place and not in the screen.
 *
 * @param keyword   looked for in the title and the description; blank for no keyword
 * @param category  null for every category
 * @param type      null for every listing type
 * @param condition null for every condition
 * @param minPrice  the lowest price as typed, in HK$; blank for no minimum
 * @param maxPrice  the highest price as typed, in HK$; blank for no maximum
 * @param sort      the order of the results
 */
public record BrowseQuery(String keyword, Category category, ListingType type, ItemCondition condition,
                          String minPrice, String maxPrice, ListingSort sort) {

    /** Every live listing, newest first: what Browse shows when it opens (UC8 step 2). */
    public static final BrowseQuery ALL = new BrowseQuery("", null, null, null, "", "", ListingSort.NEWEST);

    /** Nothing typed or chosen is stored as blank text and the newest-first order, never as null. */
    public BrowseQuery {
        keyword = keyword == null ? "" : keyword.strip();
        minPrice = minPrice == null ? "" : minPrice.strip();
        maxPrice = maxPrice == null ? "" : maxPrice.strip();
        sort = sort == null ? ListingSort.NEWEST : sort;
    }

    /** True if the buyer set a price range: a minimum, a maximum, or both. */
    public boolean hasPriceRange() {
        return !minPrice.isEmpty() || !maxPrice.isEmpty();
    }
}
