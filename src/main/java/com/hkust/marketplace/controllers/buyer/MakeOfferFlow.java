package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.ListingUnavailableException;
import com.hkust.marketplace.services.OfferForm;
import com.hkust.marketplace.services.OfferService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.OfferText;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Walks the buyer through UC5 Make Offer, from any of its three starting points (step 1): the
 * action button on a listing's detail screen, the Make Offer option in its chat, or agreeing
 * to a seller's counter.
 *
 * <p>This is the use case's boundary (Lecture 15): it shows the forms and messages in the
 * order of the flow of events and passes what the buyer entered to {@link OfferService},
 * which makes every decision. The alternative flows end here as the use case says:
 * <ul>
 *   <li>A1, listing not available: a message, and the use case ends.</li>
 *   <li>A2 and A3, invalid price or period: a message, and the form comes back with what the
 *       buyer entered, to be corrected ({Enter Terms}).</li>
 *   <li>A4, cancel: the use case ends without a message.</li>
 * </ul>
 */
final class MakeOfferFlow {

    private final OfferService offers;

    MakeOfferFlow(OfferService offers) {
        this.offers = offers;
    }

    /**
     * Make Offer on a listing, from its detail screen or its chat.
     * @return true if an offer, borrow request or claim was submitted
     */
    boolean start(String listingId) {
        return run(() -> offers.start(listingId));
    }

    /**
     * Make Offer by agreeing to the seller's counter on one of the buyer's offers: the form
     * starts with the counter's terms (Special Requirement 4).
     * @return true if the new offer was submitted
     */
    boolean startFromCounter(String offerId) {
        return run(() -> offers.startFromCounter(offerId));
    }

    private boolean run(Supplier<OfferForm> begin) {
        OfferForm form;
        try {
            form = begin.get(); // {Check Availability}
        } catch (ServiceException e) {
            Dialogs.problem(e); // A1
            return false;
        }
        Listing listing = form.listing();
        return switch (listing.getType()) {
            case SELL -> offerPrice(listing, form.price());
            case LEND -> requestBorrow(listing, OfferDialogs.Period.of(form.start(), form.end()));
            case GIVEAWAY -> submit(listing, () -> offers.submitClaim(listing.getId())) == Outcome.SUBMITTED;
        };
    }

    /** {Enter Terms} for a sale listing, repeated until the price is accepted or the buyer cancels. */
    private boolean offerPrice(Listing listing, String suggested) {
        String price = suggested;
        while (true) {
            Optional<String> typed = OfferDialogs.askPrice(listing, price);
            if (typed.isEmpty()) {
                return false; // A4
            }
            price = typed.get();
            String entered = price;
            Outcome outcome = submit(listing, () -> offers.submitPriceOffer(listing.getId(), entered));
            if (outcome != Outcome.CORRECT_AND_RETRY) {
                return outcome == Outcome.SUBMITTED;
            }
        }
    }

    /** {Enter Terms} for a lend listing, repeated until the period is accepted or the buyer cancels. */
    private boolean requestBorrow(Listing listing, OfferDialogs.Period suggested) {
        OfferDialogs.Period period = suggested;
        while (true) {
            Optional<OfferDialogs.Period> entered = OfferDialogs.askPeriod(listing, period);
            if (entered.isEmpty()) {
                return false; // A4
            }
            period = entered.get();
            OfferDialogs.Period typed = period;
            Outcome outcome = submit(listing,
                    () -> offers.submitBorrowRequest(listing.getId(), typed.start(), typed.end()));
            if (outcome != Outcome.CORRECT_AND_RETRY) {
                return outcome == Outcome.SUBMITTED;
            }
        }
    }

    private enum Outcome { SUBMITTED, CORRECT_AND_RETRY, ENDED }

    /** {Check Terms} and {Record Offer}: submits, and tells the buyer how it went (steps 5-9). */
    private static Outcome submit(Listing listing, Supplier<Offer> submission) {
        try {
            Offer offer = submission.get();
            Dialogs.info("Submitted", OfferText.submitted(offer, listing.getTitle())); // step 9
            return Outcome.SUBMITTED;
        } catch (ListingUnavailableException e) {
            Dialogs.problem(e); // A1: nothing was recorded, and the use case ends
            return Outcome.ENDED;
        } catch (ServiceException e) {
            Dialogs.problem(e); // A2 or A3: back to {Enter Terms}
            return Outcome.CORRECT_AND_RETRY;
        }
    }
}
