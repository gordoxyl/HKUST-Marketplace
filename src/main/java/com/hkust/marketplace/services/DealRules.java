package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The deal lifecycle that all three portals share: what happens to a listing, its other
 * offers and the people involved when a deal is accepted, completed or returned, or when a
 * listing's open offers must be declined. See docs/state-machines.md.
 *
 * <p>Who calls what:
 * <ul>
 *   <li>{@link #accept} — the seller's Manage Offers &amp; Requests (step 3.3).</li>
 *   <li>{@link #declineOpenOffers} — the seller's Manage Listings (A3), the admin's listing
 *       removal (5.5.4) and account deactivation or deletion (6.2.4, 6.4.4).</li>
 *   <li>{@link #complete} — the buyer's Manage My Deals (UC7 steps 6-8).</li>
 *   <li>{@link #returnItem} — the buyer's self-return (UC7 A2) and the borrowing-deadline
 *       timer (UC12 step 3).</li>
 * </ul>
 *
 * <p>This is a control class for business logic, not tied to one actor. It does not check
 * the logged-in user: the calling service checks the role first ({@link AccessControl}),
 * then calls these methods. Each method is one atomic step, and it can also run inside the
 * caller's transaction.
 */
public class DealRules {

    private final DataStore store;
    private final NotificationService notifications;

    public DealRules() {
        this(DataStore.getInstance());
    }

    public DealRules(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    /**
     * The seller accepts a pending offer, borrow request or claim (Manage Offers &amp; Requests
     * 3.3.3-3.3.6; UC7 Special Requirement 5): the offer is Accepted, the listing becomes
     * Reserved (sale, giveaway) or Lent (lend), every other open offer on the listing is
     * declined, and each affected buyer is notified.
     * @throws ServiceException if the offer is not pending, the listing is no longer Live (A2),
     *         or it is a borrow request whose period has already ended
     */
    public void accept(Offer offer) {
        store.transaction(() -> {
            Listing listing = store.getListingOf(offer);
            if (offer.getStatus() != OfferStatus.PENDING) {
                throw new ServiceException("Only a pending " + offer.getKindName() + " can be accepted.");
            }
            // Checked on the status, not isAvailable(): a giveaway is "unavailable" exactly because of the claim being accepted
            if (listing.getStatus() != ListingStatus.LIVE) {
                throw new ServiceException("This listing is no longer available.");
            }
            // A borrow period that is over cannot begin: the item would be lent and returned
            // automatically at once, with a completion date before its acceptance (regression log R8)
            if (offer instanceof BorrowRequest request && !store.now().isBefore(request.getDueDate())) {
                throw new ServiceException("The borrow period of this request has already ended. "
                        + "Decline it, or counter with new dates.");
            }
            offer.accept(store.now());
            listing.acceptDeal();
            declineOpenOffers(listing, NotificationCategory.OFFER_UPDATE, "the seller accepted another offer");
            notifications.send(offer.getBuyerUsername(), NotificationCategory.OFFER_UPDATE,
                    store.getFullName(listing.getSellerUsername()) + " accepted your " + offer.getKindName()
                            + " for '" + listing.getTitle() + "'.");
        });
    }

    /**
     * Declines every pending or countered offer on the listing and notifies each buyer:
     * "Your claim for 'Desk Lamp' was declined because {because}."
     * @param category OFFER_UPDATE normally, or LISTING_REMOVED when the admin removed the listing (Task 1.8)
     * @param because  why, completing the sentence, such as "the admin removed the listing"
     * @return the offers that were declined
     */
    public List<Offer> declineOpenOffers(Listing listing, NotificationCategory category, String because) {
        return store.transaction(() -> {
            List<Offer> declined = new ArrayList<>();
            for (Offer other : store.getOffersForListing(listing.getId())) {
                if (other.isOpen()) {
                    other.decline();
                    declined.add(other);
                    notifications.send(other.getBuyerUsername(), category, "Your " + other.getKindName()
                            + " for '" + listing.getTitle() + "' was declined because " + because + ".");
                }
            }
            return declined;
        });
    }

    /**
     * The buyer marks an accepted price offer or claim complete (UC7 steps 6-8): the deal gets
     * today's completion date, the listing becomes Sold or Given (or stays Removed), and the
     * seller is notified.
     * @throws IllegalArgumentException for a borrow request, which is completed by returning the item
     * @throws ServiceException if the deal is not accepted or is already completed
     */
    public void complete(Offer deal) {
        if (deal instanceof BorrowRequest) {
            throw new IllegalArgumentException("A borrow request is completed by returning the item (UC7 Special Requirement 1).");
        }
        store.transaction(() -> {
            if (deal.getStatus() != OfferStatus.ACCEPTED || deal.isCompleted()) {
                throw new ServiceException("Only an accepted deal that is not yet completed can be marked complete.");
            }
            Listing listing = store.getListingOf(deal);
            deal.complete(store.now());
            listing.completeDeal();
            notifications.send(listing.getSellerUsername(), NotificationCategory.DEAL_COMPLETED,
                    store.getFullName(deal.getBuyerUsername()) + " marked the deal for '" + listing.getTitle() + "' complete.");
        });
    }

    /**
     * Returns a borrowed item, either by the buyer (UC7 A2) or automatically when the borrowing
     * period ends (UC12 step 3). The listing becomes Live again unless the admin removed it,
     * and the seller is notified; for an automatic return the buyer is notified too.
     * Only the first return counts (UC12 Special Requirement 5).
     *
     * <p>Checking that a self-return happens before the due date (UC7 Special Requirement 3)
     * is the caller's job, because only Manage My Deals has that rule.
     *
     * @param automatic true for UC12: the completion date is the due date and the deal is marked auto-returned
     * @throws ServiceException if the request is not accepted or the item was already returned
     */
    public void returnItem(BorrowRequest request, boolean automatic) {
        store.transaction(() -> {
            if (request.getStatus() != OfferStatus.ACCEPTED) {
                throw new ServiceException("Only an accepted borrow request can be returned.");
            }
            if (request.isCompleted()) {
                throw new ServiceException("This item has already been returned.");
            }
            Listing listing = store.getListingOf(request);
            if (automatic) {
                request.autoReturn();
            } else {
                request.complete(store.now());
            }
            listing.completeDeal();

            String buyerName = store.getFullName(request.getBuyerUsername());
            if (automatic) {
                notifications.send(request.getBuyerUsername(), NotificationCategory.DEAL_COMPLETED,
                        "'" + listing.getTitle() + "' was returned automatically because the borrowing period ended.");
                notifications.send(listing.getSellerUsername(), NotificationCategory.DEAL_COMPLETED,
                        "'" + listing.getTitle() + "', borrowed by " + buyerName
                                + ", was returned automatically because the borrowing period ended.");
            } else {
                notifications.send(listing.getSellerUsername(), NotificationCategory.DEAL_COMPLETED,
                        buyerName + " returned '" + listing.getTitle() + "'.");
            }
        });
    }
}
