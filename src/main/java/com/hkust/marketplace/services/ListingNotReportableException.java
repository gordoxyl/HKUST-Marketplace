package com.hkust.marketplace.services;

/**
 * The listing cannot be reported: the buyer has reported it already (UC9 A1, Special
 * Requirement 3), or can no longer open it, so the use case's precondition no longer holds.
 * Unlike a missing reason or details (A2), the buyer cannot fix this by correcting the form, so
 * the use case ends; a screen catches this type to tell the two apart:
 * <pre>{@code
 * try {
 *     reports.submit(listingId, reason, details);
 * } catch (ListingNotReportableException e) {
 *     Dialogs.problem(e);            // A1: the use case ends
 * } catch (ServiceException e) {
 *     Dialogs.problem(e);            // A2: back to the form, to correct the report
 * }
 * }</pre>
 */
public class ListingNotReportableException extends ServiceException {

    public ListingNotReportableException(String message) {
        super(message);
    }
}
