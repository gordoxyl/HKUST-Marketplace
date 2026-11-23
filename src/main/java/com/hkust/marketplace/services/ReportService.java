package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Report;
import com.hkust.marketplace.models.ReportReason;

import java.util.List;

/**
 * UC9 Report Listing, for the Buyer (Task 1.4's Report Listing button): reporting a listing to
 * the admin with a reason and details, once per listing.
 *
 * <p><b>Design (Lecture 15).</b> This is the use case's control class. Its boundary is
 * {@code controllers.buyer.ReportListingFlow} with the form {@code ReportDialog}, which starts
 * from the Report Listing button of the listing's detail screen (step 1). The entities are
 * {@link Report}, the reported {@link Listing} and the admins' notifications (step 8).
 *
 * <p><b>Defensive programming (Lecture 10).</b> Everything the form sends is untrusted: the
 * listing's id, a reason that may be missing, and the details. Each is checked here, in the
 * order of the flow of events: the listing first, because a listing that cannot be reported
 * ends the use case whatever was typed (A1), then the reason and the details (A2). The
 * {@link Report} entity only receives clean values, and {@code DataStore.addReport} checks
 * the listing and the reporter again, as the barricade's inner layer.
 *
 * <p><b>Special Requirement 4.</b> Only the admin sees reports: the notification goes to the
 * admins only and does not name the reporter, and no buyer or seller service reads reports.
 */
public class ReportService {

    /** UC9 A1 (Special Requirement 3). */
    static final String ALREADY_REPORTED = "You have already reported this listing.";
    /** UC9 A2, in the spec's words; the {@link Report} model's own check says the same. */
    static final String REASON_AND_DETAILS_REQUIRED = "Both a reason and details are required.";

    private final DataStore store;
    private final NotificationService notifications;

    public ReportService() {
        this(DataStore.getInstance());
    }

    public ReportService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    // ========== Check Report and show the form (steps 1-3) ==========

    /**
     * Starts reporting the listing the logged-in buyer is viewing: checks that the buyer has not
     * reported it yet (step 2), and returns what the form shows (step 3).
     * @throws ListingNotReportableException if the buyer has reported the listing already (A1),
     *         or can no longer open it: the use case ends
     */
    public ReportForm start(String listingId) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.read(() -> {
            Listing listing = requireReportable(buyer, listingId);
            return new ReportForm(listing.getId(), listing.getTitle(), List.of(ReportReason.values()));
        });
    }

    // ========== Check Details, record the report and notify the admins (steps 5-8) ==========

    /**
     * Records the buyer's report of a listing and notifies every admin (steps 6-8). It is one
     * atomic step, so a buyer reports a listing once even if the form is submitted twice at the
     * same moment (Special Requirement 3).
     * @param reason  the reason the buyer chose, or null if none was chosen
     * @param details the details as typed
     * @return the recorded report, its details without the spaces around them
     * @throws ListingNotReportableException if the listing can no longer be reported (A1), or
     *         can no longer be opened: the use case ends
     * @throws ServiceException if the reason or the details are missing (A2): the buyer corrects
     *         the form
     */
    public Report submit(String listingId, ReportReason reason, String details) {
        Buyer buyer = AccessControl.requireBuyer();
        return store.transaction(() -> {
            Listing listing = requireReportable(buyer, listingId); // {Check Report} again: the form may be out of date
            if (reason == null || details == null || details.isBlank()) {
                throw new ServiceException(REASON_AND_DETAILS_REQUIRED); // {Check Details}, A2
            }
            Report report = new Report(listing.getId(), buyer.getUsername(), reason, details);
            boolean added = store.addReport(report); // step 7
            assert added : "not reported yet: checked above, under the same lock";
            notifications.sendToAdmins(NotificationCategory.LISTING_REPORTED,
                    news(listing, store.getFullName(listing.getSellerUsername()), reason)); // step 8

            // Postconditions of the use case. Assertions document and verify what must hold
            // here; they are not input checks (Lecture 10).
            assert store.hasReported(buyer.getUsername(), listing.getId()) : "the buyer has reported the listing";
            assert store.getReportsForListing(listing.getId()).contains(report) : "the admin can see the report";
            return report;
        });
    }

    // ========== Rules ==========

    /** {Check Report}: UC9's precondition, step 2, A1 and Special Requirement 3. */
    private Listing requireReportable(Buyer buyer, String listingId) {
        Listing listing = store.findListing(listingId);
        if (listing == null || !ListingAccess.canOpen(store, buyer, listing)) {
            throw new ListingNotReportableException(ListingAccess.NOT_AVAILABLE);
        }
        if (store.hasReported(buyer.getUsername(), listing.getId())) {
            throw new ListingNotReportableException(ALREADY_REPORTED);
        }
        return listing;
    }

    /**
     * What the admins' notification says (step 8): "'Desk Lamp' by Diana Ng was reported (Scam
     * or fraud) and needs moderation." It names neither the reporter nor the details; the admin
     * sees both when viewing the listing (Manage Marketplace Listings 5.1).
     */
    static String news(Listing listing, String sellerName, ReportReason reason) {
        return "'" + listing.getTitle() + "' by " + sellerName + " was reported (" + reason.getDisplayName()
                + ") and needs moderation.";
    }
}
