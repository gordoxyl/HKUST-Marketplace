package com.hkust.marketplace.services;

/**
 * The listing no longer accepts an offer, borrow request or claim (UC5 A1): it was reserved,
 * lent, sold, given or removed, another buyer's claim on it is pending, or its seller was
 * deactivated. Unlike the other problems of Make Offer, the buyer cannot fix this one by
 * correcting what they typed, so the use case ends; a screen catches this type to tell the
 * two apart:
 * <pre>{@code
 * try {
 *     offers.submitPriceOffer(listingId, typed);
 * } catch (ListingUnavailableException e) {
 *     Dialogs.problem(e);            // A1: the use case ends
 * } catch (ServiceException e) {
 *     Dialogs.problem(e);            // A2: back to the form, to correct the price
 * }
 * }</pre>
 */
public class ListingUnavailableException extends ServiceException {

    public ListingUnavailableException() {
        super("This listing is no longer available.");
    }
}
