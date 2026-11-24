package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ReportReason;

import java.util.List;
import java.util.Objects;

/**
 * What the report form shows (UC9 step 3), after the buyer was found not to have reported the
 * listing yet.
 *
 * @param listingId    the listing being reported
 * @param listingTitle its title, for the form's heading
 * @param reasons      the reasons to choose from, in Special Requirement 1's order
 */
public record ReportForm(String listingId, String listingTitle, List<ReportReason> reasons) {

    public ReportForm {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(listingTitle, "listingTitle");
        reasons = List.copyOf(reasons);
    }
}
