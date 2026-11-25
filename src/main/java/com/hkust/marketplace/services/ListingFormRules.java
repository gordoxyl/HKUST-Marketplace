package com.hkust.marketplace.services;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.MeetupLocation;

import java.time.Duration;
import java.util.OptionalDouble;

/**
 * The field checks the listing forms share: Create Listings (Task 2.3) and Manage Listings
 * (Task 2.4). Both write the same fields, so they must refuse the same input with the same
 * message; keeping the checks here means the two forms cannot drift apart.
 *
 * <p>Only field contents are checked here. Whether the seller may change a given listing at
 * all (ownership, locked status) belongs to {@link SellerListingService}.
 */
final class ListingFormRules {

    private ListingFormRules() {
        // Utility class
    }

    /** A1: every field the form asks for, whatever the listing type. */
    static void requireCommonFields(String title, Category category, ItemCondition condition,
                                    String description, MeetupLocation meetupLocation) {
        if (title == null || title.isBlank()) {
            throw new ServiceException("The item name cannot be empty.");
        }
        if (category == null) {
            throw new ServiceException("Please choose a category.");
        }
        if (condition == null) {
            throw new ServiceException("Please choose the item's condition.");
        }
        if (description == null || description.isBlank()) {
            throw new ServiceException("The description cannot be empty.");
        }
        if (meetupLocation == null) {
            throw new ServiceException("Please choose a meet-up location.");
        }
    }

    /**
     * A2: the asking price of a Sell listing, exactly as it was typed. The text is untrusted
     * input, so it is parsed here at the barricade with {@link Amounts#parse}: digits with up to
     * two decimals, such as "180" or "99.50"; "1e3", "NaN" and "abc" are refused.
     * @return the price as a number
     */
    static double requirePriceText(String typedPrice) {
        OptionalDouble price = Amounts.parse(typedPrice);
        if (price.isEmpty() || price.getAsDouble() <= 0) {
            throw new ServiceException("The price must be a positive number, such as 180 or 99.50.");
        }
        return price.getAsDouble();
    }

    /** A3: how long a Lend listing may be borrowed, at least the minimum borrow time. */
    static void requireLendingDuration(Duration lendingDuration) {
        if (lendingDuration == null
                || lendingDuration.compareTo(BorrowRequest.MIN_BORROW_TIME) < 0) {
            throw new ServiceException("The lending duration must be at least "
                    + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME) + ".");
        }
    }

    /** A4: a listing can show at most {@link Listing#MAX_PHOTOS} photos. */
    static void requirePhotoCount(int count) {
        if (count > Listing.MAX_PHOTOS) {
            throw new ServiceException("A listing can have at most " + Listing.MAX_PHOTOS + " photos.");
        }
    }
}
