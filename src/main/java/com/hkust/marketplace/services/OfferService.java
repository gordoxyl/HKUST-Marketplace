package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * UC5 Make Offer, for the Buyer (Task 1.5): a price offer on a sale listing, a borrow request
 * on a lend listing, or a claim on a giveaway.
 *
 * <p><b>Design (Lecture 15).</b> This is the use case's control class. Its boundary classes
 * are the buyer's screens ({@code controllers.buyer.MakeOfferFlow} and its dialogs); its
 * entity classes are {@link Offer} and its subclasses, {@link Listing}, the chat thread and
 * {@link DataStore}. The screens hold no rule: they pass on what the buyer typed and show
 * what comes back.
 *
 * <p><b>How a screen uses it</b>, in the order of the use case:
 * <pre>{@code
 * OfferForm form = offers.start(listingId);                 // steps 1-4: available? what to suggest?
 * Offer sent = offers.submitPriceOffer(listingId, typed);   // steps 5-9, for a sale listing
 * Offer sent = offers.submitBorrowRequest(listingId, start, end);   // for a lend listing
 * Offer sent = offers.submitClaim(listingId);               // for a giveaway: no form (step 3)
 * OfferForm form = offers.startFromCounter(offerId);        // step 1, agreeing to a seller's counter
 * }</pre>
 *
 * <p><b>Defensive programming (Lecture 10).</b> Everything that arrives here is untrusted: the
 * price is still the text the buyer typed, a date may be missing, and the listing may have
 * changed since the screen was drawn. The {@code submit} methods are the barricade: they
 * check the terms (A2, A3) and the availability (A1) and refuse with a
 * {@link ServiceException} written for the buyer. Behind the barricade the model classes take
 * clean values, and their own checks ({@code IllegalArgumentException}) only guard against
 * programming errors.
 *
 * <p><b>Atomic step (Special Requirement 2).</b> The last availability check and the recording
 * of the offer are one {@link DataStore#transaction}, so of two buyers who claim the same
 * giveaway at the same moment only the first succeeds.
 */
public class OfferService {

    /** UC5 A2: the price is empty, not a number, or not positive. */
    static final String INVALID_PRICE = "The price must be a positive amount, such as 150 or 99.50.";
    /** UC5 A3: a date is missing. */
    static final String PERIOD_INCOMPLETE = "Please enter both the start and the end of the borrow period, "
            + "each with a date and a time.";
    /** UC5 A3: the start is in the past. */
    static final String PERIOD_STARTS_IN_PAST = "The borrow period cannot start in the past.";
    /** UC5 A3: the period is shorter than the minimum borrow time (Special Requirement 3). */
    static final String PERIOD_TOO_SHORT = "The borrow period must end at least "
            + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME) + " after it starts.";

    /** The suggested borrow period starts this far ahead, so it has not already begun when the buyer submits. */
    static final Duration SUGGESTED_START_DELAY = Duration.ofMinutes(1);

    private final DataStore store;
    private final NotificationService notifications;

    public OfferService() {
        this(DataStore.getInstance());
    }

    public OfferService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    // ========== Check Availability and Enter Terms (UC5 steps 1-4) ==========

    /**
     * Begins Make Offer on a listing, from the action button on its detail screen or the Make
     * Offer option in its chat: checks that the listing is available and returns what the form
     * starts with. For a sale that is the listing's price, for a lend listing a period that
     * starts in a minute and lasts the listing's lending duration; the buyer corrects them.
     * @throws ListingUnavailableException if the listing is not available (A1)
     */
    public OfferForm start(String listingId) {
        AccessControl.requireBuyer();
        return store.read(() -> form(requireAvailable(listingId), null));
    }

    /**
     * Begins Make Offer by agreeing to a seller's counter on one of the buyer's earlier offers
     * (step 1): the form starts with the counter's price or dates (step 4). Submitting it makes
     * a new offer for the seller to accept or decline; the countered offer itself stays as it
     * is (Special Requirements 4 and 5).
     * @throws ServiceException            if there is no such offer of this buyer, or the seller has not countered it
     * @throws ListingUnavailableException if the listing is not available (A1)
     */
    public OfferForm startFromCounter(String offerId) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.read(() -> {
            Offer countered = store.findOffer(offerId);
            if (countered == null || !countered.getBuyerUsername().equalsIgnoreCase(buyer.getUsername())) {
                throw new ServiceException("This is not one of your offers.");
            }
            if (countered.getStatus() != OfferStatus.COUNTERED) {
                throw new ServiceException("The seller has not countered this " + countered.getKindName() + ".");
            }
            return form(requireAvailable(countered.getListingId()), countered);
        });
    }

    /** @param countered the offer whose counter the buyer agrees to, or null for a fresh offer */
    private OfferForm form(Listing listing, Offer countered) {
        if (listing instanceof SaleListing sale) {
            double price = countered instanceof PriceOffer offer ? offer.getCounterPrice() : sale.getPrice();
            return new OfferForm(listing, Amounts.plain(price), null, null);
        }
        if (listing instanceof LendListing lend) {
            if (countered instanceof BorrowRequest request) {
                return new OfferForm(listing, "", request.getCounterStartDate(), request.getCounterEndDate());
            }
            LocalDateTime start = store.now().plus(SUGGESTED_START_DELAY).truncatedTo(ChronoUnit.SECONDS);
            return new OfferForm(listing, "", start, start.plus(lend.getLendingDuration()));
        }
        return new OfferForm(listing, "", null, null); // a giveaway: a claim needs no input (step 3)
    }

    // ========== Check Terms and Record Offer (UC5 steps 5-9) ==========

    /**
     * Submits a price offer on a sale listing.
     * @param price the price as the buyer typed it, in HK$
     * @return the new offer, Pending
     * @throws ServiceException            if the price is empty, not a number or not positive (A2); nothing is recorded
     * @throws ListingUnavailableException if the listing is no longer available (A1); nothing is recorded
     */
    public Offer submitPriceOffer(String listingId, String price) {
        Buyer buyer = AccessControl.requireBuyer();
        OptionalDouble amount = Amounts.parse(price);
        if (amount.isEmpty() || amount.getAsDouble() <= 0) {
            throw new ServiceException(INVALID_PRICE);
        }
        return record(buyer, listingId, () -> new PriceOffer(listingId, buyer.getUsername(), amount.getAsDouble()));
    }

    /**
     * Submits a borrow request on a lend listing, for the period from start to end.
     * @param start null if the buyer gave no complete start date and time
     * @param end   null if the buyer gave no complete end date and time
     * @return the new borrow request, Pending
     * @throws ServiceException            if a date is missing, the start is in the past, or the period is
     *                                     shorter than the minimum borrow time (A3); the message says which
     * @throws ListingUnavailableException if the listing is no longer available (A1)
     */
    public Offer submitBorrowRequest(String listingId, LocalDateTime start, LocalDateTime end) {
        Buyer buyer = AccessControl.requireBuyer();
        checkPeriod(start, end);
        return record(buyer, listingId, () -> new BorrowRequest(listingId, buyer.getUsername(), start, end));
    }

    /**
     * Claims a giveaway with one action (Task 1.5); a claim has no terms to check.
     * @return the new claim, Pending
     * @throws ListingUnavailableException if the giveaway is no longer available, for example
     *                                     because another buyer's claim is pending (A1)
     */
    public Offer submitClaim(String listingId) {
        Buyer buyer = AccessControl.requireBuyer();
        return record(buyer, listingId, () -> new Claim(listingId, buyer.getUsername()));
    }

    /**
     * UC5 A3, in the order the use case names the problems. Three decisions, one of them with
     * two conditions, give a cyclomatic complexity of 5; OfferServiceTest has a test for each
     * of the five independent paths (Lecture 12, basis path testing).
     */
    private void checkPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ServiceException(PERIOD_INCOMPLETE);
        }
        if (start.isBefore(store.now())) {
            throw new ServiceException(PERIOD_STARTS_IN_PAST);
        }
        if (Duration.between(start, end).compareTo(BorrowRequest.MIN_BORROW_TIME) < 0) {
            throw new ServiceException(PERIOD_TOO_SHORT);
        }
    }

    /**
     * UC5 steps 7 and 8 as one atomic step (Special Requirement 2): checks again that the
     * listing is available, records the offer as Pending in the chat thread between the buyer
     * and the listing (the store creates the thread if needed), and notifies the seller.
     * @param newOffer makes the offer from terms that were already checked
     */
    private Offer record(Buyer buyer, String listingId, Supplier<Offer> newOffer) {
        return store.transaction(() -> {
            Listing listing = requireAvailable(listingId);
            Offer offer = newOffer.get();
            if (!offer.canBeMadeOn(listing)) {
                throw new ServiceException("A " + offer.getKindName() + " cannot be made on a "
                        + listing.getType().getDisplayName().toLowerCase() + " listing.");
            }
            store.addOffer(offer);
            notifications.send(listing.getSellerUsername(), NotificationCategory.OFFER_RECEIVED,
                    news(buyer.getFullName(), offer, listing));

            // Postconditions of the use case. Assertions document and verify what must hold
            // here; they are not input checks (Lecture 10).
            assert offer.getStatus() == OfferStatus.PENDING : "a new offer is Pending";
            assert store.findThread(buyer.getUsername(), listingId) != null : "the offer is in the buyer's chat thread";
            return offer;
        });
    }

    /** UC5 A1, used at {Check Availability} and again at {Record Offer}. */
    private Listing requireAvailable(String listingId) {
        Listing listing = store.findListing(listingId);
        if (listing == null || !store.isAvailable(listing)) {
            throw new ListingUnavailableException();
        }
        return listing;
    }

    /** What the seller's notification says (step 8). */
    private static String news(String buyerName, Offer offer, Listing listing) {
        String title = "'" + listing.getTitle() + "'";
        if (offer instanceof PriceOffer price) {
            return buyerName + " made a price offer of " + SaleListing.formatPrice(price.getOfferedPrice())
                    + " for " + title + ".";
        }
        if (offer instanceof BorrowRequest) {
            return buyerName + " sent a borrow request for " + title + ".";
        }
        return buyerName + " claimed " + title + ".";
    }
}
