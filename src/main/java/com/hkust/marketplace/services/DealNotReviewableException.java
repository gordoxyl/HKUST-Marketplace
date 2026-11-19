package com.hkust.marketplace.services;

/**
 * The deal cannot be reviewed (UC10 A1): it is not one of the buyer's deals, it is not
 * completed, or it has been reviewed already (Special Requirement 1). Unlike a missing rating
 * or comment, the buyer cannot fix this by correcting the form, so the use case ends; a screen
 * catches this type to tell the two apart:
 * <pre>{@code
 * try {
 *     reviews.submit(offerId, rating, comment);
 * } catch (DealNotReviewableException e) {
 *     Dialogs.problem(e);            // A1: the use case ends
 * } catch (ServiceException e) {
 *     Dialogs.problem(e);            // A2: back to the form, to correct the review
 * }
 * }</pre>
 */
public class DealNotReviewableException extends ServiceException {

    public DealNotReviewableException(String message) {
        super(message);
    }
}
