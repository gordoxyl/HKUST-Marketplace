package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;

import java.util.List;

/**
 * What a participant can do with an offer from inside its chat, through the deal actions
 * beside Send (UI consistency across the portals; assumptions register 71):
 * <ul>
 *   <li>the seller answers an offer as on Manage Offers &amp; Requests (3.3-3.5): a Pending
 *       offer can be accepted, declined or countered, except that a claim cannot be countered
 *       (Task 2.5); a Countered offer, waiting for the buyer, can still be declined;</li>
 *   <li>the buyer agrees to a counter by making a new offer with its terms (UC5 Special
 *       Requirement 4; client answer #2).</li>
 * </ul>
 * These follow the Offer state machine ({@code docs/state-machines.md}): accept and counter
 * need a Pending offer, decline an open one. No new rule is added.
 *
 * <p><b>Design (Lecture 15).</b> The control layer decides which buttons a screen shows, as
 * {@link Deal} does for My Deals, so the chat holds no rule. The services check the same
 * conditions again when an action is used, for a screen that is out of date.
 */
public enum OfferAction {

    ACCEPT, DECLINE, COUNTER, AGREE_TO_COUNTER;

    /**
     * The actions the viewer can take on the offer now, in the order of the dialog's buttons.
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
     * The buttons of the dialog that picks an offer in a chat on a listing of this type: Accept,
     * Decline and Counter for the seller (no Counter on a giveaway, whose claims cannot be
     * countered), and Agree for the buyer. A button the chosen offer does not allow is disabled.
     */
    public static List<OfferAction> buttons(ListingType type, boolean viewerIsBuyer) {
        if (viewerIsBuyer) {
            return List.of(AGREE_TO_COUNTER);
        }
        return type == ListingType.GIVEAWAY ? List.of(ACCEPT, DECLINE) : List.of(ACCEPT, DECLINE, COUNTER);
    }
}
