package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.services.DealNotReviewableException;
import com.hkust.marketplace.services.ReviewForm;
import com.hkust.marketplace.services.ReviewService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.ReviewText;

import java.util.Optional;

/**
 * Walks the buyer through UC10 Review Seller, from any of its starting points (step 1): the
 * prompt after a deal is marked complete (UC7 step 9), the prompt after a borrowed item is
 * returned by the buyer or automatically (client answer #3), and the Rate Seller button of a
 * completed deal on My Deals.
 *
 * <p>This is the use case's boundary (Lecture 15): it shows the form and the messages in the
 * order of the flow of events and passes what the buyer entered to {@link ReviewService},
 * which makes every decision. The alternative flows end here as the use case says:
 * <ul>
 *   <li>A1, the deal cannot be reviewed: a message, and the use case ends.</li>
 *   <li>A2, no rating, and an empty written review (client answer #8): a message, and the form
 *       comes back with what the buyer entered, to be corrected ({Enter Review}).</li>
 *   <li>A3, skip: nothing is recorded and nothing is said; the deal keeps its Rate Seller
 *       button on My Deals.</li>
 * </ul>
 */
final class ReviewSellerFlow {

    private final ReviewService reviews;

    ReviewSellerFlow(ReviewService reviews) {
        this.reviews = reviews;
    }

    /**
     * Review Seller for one of the buyer's deals.
     * @return true if a review was submitted
     */
    boolean start(String offerId) {
        ReviewForm form;
        try {
            form = reviews.start(offerId); // {Check Deal}
        } catch (ServiceException e) {
            Dialogs.problem(e); // A1
            return false;
        }
        ReviewDialog.Entry entry = ReviewDialog.Entry.EMPTY;
        while (true) {
            Optional<ReviewDialog.Entry> entered = ReviewDialog.ask(form, entry); // {Enter Review}
            if (entered.isEmpty()) {
                return false; // A3
            }
            entry = entered.get();
            try {
                reviews.submit(form.offerId(), entry.rating(), entry.comment()); // {Check Rating}, steps 7-9
                Dialogs.info("Review submitted", ReviewText.submitted(form)); // step 10
                return true;
            } catch (DealNotReviewableException e) {
                Dialogs.problem(e); // A1: nothing was recorded, and the use case ends
                return false;
            } catch (ServiceException e) {
                Dialogs.problem(e); // A2, or no written review: back to {Enter Review}
            }
        }
    }
}
