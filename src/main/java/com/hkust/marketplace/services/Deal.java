package com.hkust.marketplace.services;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * One of the buyer's deals as My Deals shows it (UC7 step 2): the price offer, borrow request
 * or claim, its listing and seller, and what the buyer can do with it right now. The three
 * "can" answers are worked out by {@link MyDealsService} from the rules of the use cases, so
 * a screen only shows the matching buttons and never decides.
 *
 * @param offer               the deal itself: type, status, terms and the seller's counter, if any
 * @param listing             the listing the deal is on
 * @param sellerName          the seller's full name, or the username if the account was deleted
 * @param canBeMarkedComplete true for an accepted price offer or claim that is not completed yet
 *                            (UC7 step 3; Special Requirement 1)
 * @param canBeReturned       true for an accepted borrow request whose item has not been returned
 *                            and whose due date has not come yet (UC7 A2; Special Requirement 3)
 * @param canBeReviewed       true for a completed deal that the buyer has not reviewed yet
 *                            (UC7 Special Requirement 6; UC10 step 2)
 */
public record Deal(Offer offer, Listing listing, String sellerName,
                   boolean canBeMarkedComplete, boolean canBeReturned, boolean canBeReviewed) {

    public Deal {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(sellerName, "sellerName");
    }

    /** A current deal: pending, countered, or accepted and not yet completed (UC7 step 2). */
    public boolean isCurrent() {
        return offer.isCurrent();
    }

    /** True if the seller countered the offer; the buyer can agree to the counter through Make Offer (Special Requirement 4). */
    public boolean hasCounter() {
        return offer.getStatus() == OfferStatus.COUNTERED;
    }

    /**
     * The due date of a borrowed item: the end of an accepted borrow request (Special
     * Requirement 2). Empty for every other deal, and for a borrow request that is not accepted.
     */
    public Optional<LocalDateTime> dueDate() {
        return offer instanceof BorrowRequest request && request.getStatus() == OfferStatus.ACCEPTED
                ? Optional.of(request.getDueDate())
                : Optional.empty();
    }
}
