package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * What the Make Offer form starts with (UC5 steps 2-4), after the listing was found to be
 * available. Which terms it has follows the listing's type:
 * <ul>
 *   <li>a sale listing: a suggested price for the buyer to correct;</li>
 *   <li>a lend listing: a suggested start and end for the buyer to correct;</li>
 *   <li>a giveaway: no terms, because a claim needs no input (step 3).</li>
 * </ul>
 * When the buyer is agreeing to a seller's counter, the suggestions are the counter's price or
 * dates (step 4; Special Requirement 4).
 *
 * @param listing the listing the buyer is acting on
 * @param price   the suggested price as text for the price box, such as "180"; empty unless it is a sale listing
 * @param start   the suggested start of the borrow period; null unless it is a lend listing
 * @param end     the suggested end of the borrow period; null unless it is a lend listing
 */
public record OfferForm(Listing listing, String price, LocalDateTime start, LocalDateTime end) {

    public OfferForm {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(price, "price");
    }
}
