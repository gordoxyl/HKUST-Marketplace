package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * UC7 Manage My Deals, for the Buyer (Task 1.6): the buyer's current and completed deals,
 * marking a purchase or claim complete, and returning a borrowed item before its due date.
 *
 * <p><b>Design (Lecture 15).</b> This is the use case's control class. The boundary is the
 * buyer's My Deals screen ({@code controllers.buyer.MyDealsView}), which also asks the buyer
 * to confirm (steps 4-5; A3) before calling {@link #markComplete} or {@link #returnItem}. The
 * entities are {@link Offer}, {@link Listing} and the store. What a completed or returned deal
 * does to the listing and whom it notifies is shared with the borrowing-deadline timer, so it
 * lives in {@link DealRules}; this class adds the buyer's own rules in front of it.
 *
 * <p><b>Defensive programming (Lecture 10).</b> A deal is named by its id, which is untrusted:
 * each method checks that the deal exists and is the logged-in buyer's, has the right type,
 * and is in the one state that allows the action (Special Requirement 1). The buttons on the
 * screen follow {@link Deal#canBeMarkedComplete()} and {@link Deal#canBeReturned()}, but the
 * same rules are checked again here, for a screen that is out of date or bypassed.
 */
public class MyDealsService {

    /** UC7 Special Requirement 3: after the due date the item is returned by UC12, not by the buyer. */
    static final String PERIOD_ENDED = "The borrowing period has ended. The item is returned automatically.";

    private final DataStore store;
    private final DealRules rules;

    public MyDealsService() {
        this(DataStore.getInstance());
    }

    public MyDealsService(DataStore store) {
        this.store = store;
        this.rules = new DealRules(store);
    }

    // ========== Show Deals (UC7 steps 1-2) ==========

    /**
     * The logged-in buyer's deals: the current ones (pending, countered, or accepted and not
     * yet completed), the most recently made first, followed by the completed ones, the most
     * recently completed first. Declined deals are neither, so they are not listed; they stay
     * in the chat history.
     * @return the deals; empty if the buyer has none (A1)
     */
    public List<Deal> getMyDeals() {
        Buyer buyer = AccessControl.requireBuyer();
        return store.read(() -> {
            List<Offer> mine = store.getOffersByBuyer(buyer.getUsername());
            List<Deal> deals = new ArrayList<>();
            mine.stream().filter(Offer::isCurrent)
                    .sorted(Comparator.comparing(Offer::getOfferDate, Comparator.reverseOrder()))
                    .forEach(offer -> deals.add(dealOf(offer)));
            mine.stream().filter(Offer::isCompleted)
                    .sorted(Comparator.comparing(Offer::getCompletedDate, Comparator.reverseOrder()))
                    .forEach(offer -> deals.add(dealOf(offer)));
            return List.copyOf(deals);
        });
    }

    // ========== Mark a purchase or claim complete (UC7 steps 3-8) ==========

    /**
     * Marks the buyer's accepted price offer or claim complete, after the buyer confirmed: the
     * deal gets today as its completion date, the listing becomes Sold or Given, and the
     * seller is notified. The screen then prompts the buyer to rate the seller (step 9).
     * @return the deal as it is now, completed
     * @throws ServiceException if the deal is not the buyer's, is a borrow request (which is
     *         completed by returning the item), is not accepted, or is already completed
     */
    public Deal markComplete(String offerId) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.transaction(() -> {
            Offer deal = requireOwnDeal(buyer, offerId);
            if (deal instanceof BorrowRequest) {
                throw new ServiceException("A borrowed item is completed by returning it, not by marking it complete.");
            }
            rules.complete(deal); // refuses a deal that is not accepted, or is already completed
            return dealOf(deal);
        });
    }

    // ========== Return a borrowed item (UC7 A2) ==========

    /**
     * Returns the item of the buyer's accepted borrow request before its due date, after the
     * buyer confirmed: the deal gets today as its completion date, the listing becomes Live
     * again unless the admin removed it meanwhile, and the seller is notified.
     * @return the deal as it is now, completed
     * @throws ServiceException if the deal is not the buyer's, is not a borrow request, is not
     *         accepted, was already returned, or its due date has come (Special Requirement 3)
     */
    public Deal returnItem(String offerId) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.transaction(() -> {
            Offer deal = requireOwnDeal(buyer, offerId);
            if (!(deal instanceof BorrowRequest request)) {
                throw new ServiceException("Only a borrowed item can be returned. Mark this deal complete instead.");
            }
            if (isBorrowed(request) && !isBeforeDueDate(request)) {
                throw new ServiceException(PERIOD_ENDED);
            }
            rules.returnItem(request, false); // refuses a request that is not accepted, or was already returned
            return dealOf(deal);
        });
    }

    // ========== Rules ==========

    private Offer requireOwnDeal(Buyer buyer, String offerId) {
        Offer deal = store.findOffer(offerId);
        if (deal == null || !deal.getBuyerUsername().equalsIgnoreCase(buyer.getUsername())) {
            throw new ServiceException("This is not one of your deals.");
        }
        return deal;
    }

    /** Accepted and not completed: the only state in which a deal can be completed or returned (Special Requirement 1). */
    private static boolean isInProgress(Offer deal) {
        return deal.getStatus() == OfferStatus.ACCEPTED && !deal.isCompleted();
    }

    private static boolean isBorrowed(Offer deal) {
        return deal instanceof BorrowRequest && isInProgress(deal);
    }

    /** Self-return is offered only before the due date; at the due date itself it is over (Special Requirement 3). */
    private boolean isBeforeDueDate(BorrowRequest request) {
        return store.now().isBefore(request.getDueDate());
    }

    private Deal dealOf(Offer offer) {
        Listing listing = store.getListingOf(offer);
        boolean borrowed = isBorrowed(offer);
        return new Deal(offer, listing, store.getFullName(listing.getSellerUsername()),
                isInProgress(offer) && !borrowed,
                borrowed && isBeforeDueDate((BorrowRequest) offer),
                offer.isCompleted() && store.findReviewForOffer(offer.getId()) == null);
    }
}
