package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;

import java.util.List;

/**
 * What each party can do with an offer now (assumptions register 71):
 * <ul>
 *   <li>the seller answers it on Manage Offers &amp; Requests (3.3-3.5; Task 2.5), the only
 *       place where offers are answered: a Pending offer can be accepted, declined or countered,
 *       except that a claim cannot be countered; a Countered offer, waiting for the buyer, can
 *       still be declined. A chat only links the seller to that screen;</li>
 *   <li>the buyer agrees to a counter from the chat, by making a new offer with its terms (UC5
 *       Special Requirement 4; client answer #2).</li>
 * </ul>
 * These follow the Offer state machine ({@code docs/state-machines.md}): accept and counter
 * need a Pending offer, decline an open one. No new rule is added.
 *
 * <p><b>Design (Lecture 15).</b> The control layer decides which buttons a screen shows and
 * enables, as {@link Deal} does for My Deals, so the screens hold no rule. The services check
 * the same conditions again when an action is used, for a screen that is out of date.
 */
public enum OfferAction {

    ACCEPT, DECLINE, COUNTER, AGREE_TO_COUNTER;

    /**
     * The actions the viewer can take on the offer now, in the order of the screens' buttons.
     * @param viewerIsBuyer true for the buyer of the offer's chat, false for the listing's seller
     * @return the actions; empty if the offer is accepted, declined or completed, or if it is
     *         not countered and the viewer is the buyer
     */
    public static List<OfferAction> allowedFor(Offer offer, boolean viewerIsBuyer) {
        if (viewerIsBuyer) {
            return offer.getStatus() == OfferStatus.COUNTERED ? List.of(AGREE_TO_COUNTER) : List.of();
        }
        return switch (offer.getStatus()) {
            case PENDING -> offer instanceof Claim ? List.of(ACCEPT, DECLINE) : List.of(ACCEPT, DECLINE, COUNTER);
            case COUNTERED -> List.of(DECLINE);
            case ACCEPTED, DECLINED -> List.of();
        };
    }

    /**
     * The seller's buttons for an offer on a listing of this type, in order, on Manage Offers
     * &amp; Requests: Accept, Decline and Counter, without Counter on a giveaway, whose claims
     * cannot be countered (Special Requirement 1). A button the offer does not allow now
     * ({@link #allowedFor}) is shown disabled.
     */
    public static List<OfferAction> sellerButtons(ListingType type) {
        return type == ListingType.GIVEAWAY ? List.of(ACCEPT, DECLINE) : List.of(ACCEPT, DECLINE, COUNTER);
    }
}
