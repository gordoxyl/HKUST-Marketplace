package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.services.ListingNotReportableException;
import com.hkust.marketplace.services.ReportForm;
import com.hkust.marketplace.services.ReportService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.ReportText;

import java.util.Optional;

/**
 * Walks the buyer through UC9 Report Listing, from the Report Listing button of a listing's
 * detail screen (step 1).
 *
 * <p>This is the use case's boundary (Lecture 15): it shows the form and the messages in the
 * order of the flow of events and passes what the buyer entered to {@link ReportService},
 * which makes every decision. The alternative flows end here as the use case says:
 * <ul>
 *   <li>A1, the buyer has reported the listing already, or can no longer open it: a message,
 *       and the use case ends.</li>
 *   <li>A2, a missing reason or details: a message, and the form comes back with what the
 *       buyer entered, to be corrected ({Enter Report}).</li>
 *   <li>A3, cancel: nothing is recorded and nothing is said.</li>
 * </ul>
 * The detail screen stays as it is after a report; pressing Report Listing again gives A1.
 */
final class ReportListingFlow {

    private final ReportService reports;

    ReportListingFlow(ReportService reports) {
        this.reports = reports;
    }

    /** Report Listing for the listing the buyer is viewing. */
    void start(String listingId) {
        ReportForm form;
        try {
            form = reports.start(listingId); // {Check Report}
        } catch (ServiceException e) {
            Dialogs.problem(e); // A1: the use case ends
            return;
        }
        ReportDialog.Entry entry = ReportDialog.Entry.EMPTY;
        while (true) {
            Optional<ReportDialog.Entry> entered = ReportDialog.ask(form, entry); // {Enter Report}
            if (entered.isEmpty()) {
                return; // A3
            }
            entry = entered.get();
            try {
                reports.submit(form.listingId(), entry.reason(), entry.details()); // {Check Details}, steps 7-8
                Dialogs.info("Report submitted", ReportText.submitted(form)); // step 9
                return;
            } catch (ListingNotReportableException e) {
                Dialogs.problem(e); // A1: nothing was recorded, and the use case ends
                return;
            } catch (ServiceException e) {
                Dialogs.problem(e); // A2: back to {Enter Report}
            }
        }
    }
}
