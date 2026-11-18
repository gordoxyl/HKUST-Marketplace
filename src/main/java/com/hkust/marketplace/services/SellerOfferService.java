package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Task 2.5 Manage Offers &amp; Requests: the seller's inbox of the price offers, borrow
 * requests and claims made on their listings, and the four actions the use case gives them
 * (Manage Offers &amp; Requests 3.3-3.6).
 *
 * <ul>
 *   <li><b>Accept</b> - {@link DealRules#accept} does the cross-role work: the offer becomes
 *       Accepted, the listing Reserved or Lent, every other open offer is declined with a
 *       notification, and the buyer is told.</li>
 *   <li><b>Decline</b> - only an open (Pending or Countered) offer; the buyer is notified.</li>
 *   <li><b>Counter</b> - only a Pending price offer or borrow request (A3: a claim cannot be
 *       countered); the terms are validated (A4) and the buyer is notified.</li>
 *   <li><b>Open chat</b> is the shared {@link ChatService#openForOffer}, called by the screen.</li>
 * </ul>
 *
 * <p>Every method checks that the logged-in user is a seller and that the offer is on one of
 * that seller's listings, so a seller cannot act on someone else's offer.
 */
public class SellerOfferService {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final DataStore store;
    private final DealRules rules;
    private final NotificationService notifications;

    public SellerOfferService() {
        this(DataStore.getInstance());
    }

    public SellerOfferService(DataStore store) {
        this.store = store;
        this.rules = new DealRules(store);
        this.notifications = new NotificationService(store);
    }

    /**
     * Manage Offers &amp; Requests step 2: the offers still waiting for an answer on the
     * seller's listings, oldest first, so the screen can group them by listing.
     * @throws AccessDeniedException if the logged-in user is not a seller
     */
    public List<Offer> openOffers() {
        Seller seller = AccessControl.requireSeller();
        return store.getOffersForSeller(seller.getUsername()).stream()
                .filter(Offer::isOpen)
                .toList();
    }

    /**
     * Accepts one offer (Manage Offers &amp; Requests 3.3): the listing becomes Reserved or
     * Lent, the other open offers are declined, and the buyer is notified.
     * @throws ServiceException if the offer is not pending or its listing is no longer Live (A2)
     */
    public void accept(String offerId) {
        Seller seller = AccessControl.requireSeller();
        rules.accept(requireOwnedOffer(offerId, seller));
    }

    /**
     * Declines one open offer (3.4) and tells its buyer.
     * @throws ServiceException if the offer is already accepted or declined
     */
    public void decline(String offerId) {
        Seller seller = AccessControl.requireSeller();
        store.transaction(() -> {
            Offer offer = requireOwnedOffer(offerId, seller);
            if (!offer.isOpen()) {
                throw new ServiceException("This " + offer.getKindName() + " is no longer open.");
            }
            Listing listing = store.getListingOf(offer);
            offer.decline();
            notifications.send(offer.getBuyerUsername(), NotificationCategory.OFFER_UPDATE,
                    seller.getFullName() + " declined your " + offer.getKindName()
                            + " for '" + listing.getTitle() + "'.");
        });
    }

    /**
     * Counters a pending price offer with another price (3.5) and tells the buyer, who answers
     * by making a new offer (UC5 Special Requirement 4).
     * @throws ServiceException if the offer is not a pending price offer, or the price is not positive
     */
    public void counterPrice(String offerId, double price) {
        Seller seller = AccessControl.requireSeller();
        Offer offer = requireOwnedOffer(offerId, seller);
        if (offer instanceof Claim) {
            throw new ServiceException("Claims cannot be countered.");
        }
        if (!(offer instanceof PriceOffer priceOffer)) {
            throw new ServiceException("Only a price offer can be countered with a price.");
        }
        requirePending(offer);
        if (!(price > 0) || Double.isInfinite(price)) {
            throw new ServiceException("The counter price must be a positive amount, such as 150 or 99.50.");
        }
        store.transaction(() -> {
            Listing listing = store.getListingOf(offer);
            priceOffer.counter(price);
            notifications.send(offer.getBuyerUsername(), NotificationCategory.OFFER_UPDATE,
                    seller.getFullName() + " countered your price offer for '" + listing.getTitle()
                            + "' with " + SaleListing.formatPrice(price) + ".");
        });
    }

    /**
     * Counters a pending borrow request with other dates (3.5, A4) and tells the buyer.
     * @throws ServiceException if the offer is not a pending borrow request, a date is missing,
     *                          the start is in the past, or the period is shorter than the
     *                          minimum borrow time
     */
    public void counterBorrow(String offerId, LocalDateTime start, LocalDateTime end) {
        Seller seller = AccessControl.requireSeller();
        Offer offer = requireOwnedOffer(offerId, seller);
        if (offer instanceof Claim) {
            throw new ServiceException("Claims cannot be countered.");
        }
        if (!(offer instanceof BorrowRequest request)) {
            throw new ServiceException("Only a borrow request can be countered with dates.");
        }
        requirePending(offer);
        requireCounterPeriod(start, end);
        store.transaction(() -> {
            Listing listing = store.getListingOf(offer);
            request.counter(start, end);
            notifications.send(offer.getBuyerUsername(), NotificationCategory.OFFER_UPDATE,
                    seller.getFullName() + " countered your borrow request for '" + listing.getTitle()
                            + "' with " + DATE_TIME.format(start) + " – " + DATE_TIME.format(end) + ".");
        });
    }

    /** The offer must exist and be on one of the logged-in seller's listings. */
    private Offer requireOwnedOffer(String offerId, Seller seller) {
        Offer offer = store.findOffer(offerId);
        if (offer == null) {
            throw new ServiceException("This offer is no longer available.");
        }
        Listing listing = store.getListingOf(offer);
        if (listing == null || !listing.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new ServiceException("This offer is not on one of your listings.");
        }
        return offer;
    }

    /** Only a Pending offer can be countered; a Countered one waits for the buyer's answer. */
    private static void requirePending(Offer offer) {
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new ServiceException("Only a pending " + offer.getKindName() + " can be countered.");
        }
    }

    /** Manage Offers &amp; Requests A4, in the order the use case names the problems. */
    private void requireCounterPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ServiceException("Please enter both the start and the end of the counter period, "
                    + "each with a date and a time.");
        }
        if (start.isBefore(store.now())) {
            throw new ServiceException("The counter period cannot start in the past.");
        }
        if (Duration.between(start, end).compareTo(BorrowRequest.MIN_BORROW_TIME) < 0) {
            throw new ServiceException("The counter period must end at least "
                    + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME)
                    + " after it starts.");
        }
    }
}
