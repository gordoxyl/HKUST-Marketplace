package com.hkust.marketplace.ui;

import com.hkust.marketplace.services.ReportForm;

/**
 * The wording of the buyer's report form (UC9 Report Listing), kept apart from the JavaFX code
 * so it can be unit-tested.
 */
public final class ReportText {

    /** Under the form's fields: Special Requirements 2 and 4, in the buyer's words. */
    public static final String HINT = "Details are required for every reason. Only the admin sees reports, "
            + "and the seller is never told who reported a listing.";

    private ReportText() {
        // Utility class
    }

    /** The form's heading, with the listing (step 3): "Report 'Desk Lamp' to the admin". */
    public static String formHeader(ReportForm form) {
        return "Report '" + form.listingTitle() + "' to the admin";
    }

    /** What the buyer is told once the report is recorded (step 9). */
    public static String submitted(ReportForm form) {
        return "Your report of '" + form.listingTitle() + "' was submitted. The admin will review the listing.";
    }
}
