package com.hkust.marketplace.models;

import java.util.Objects;

/** A buyer's report of a listing to the admin (Report in the domain class diagram; UC9). */
public class Report {

    private String id;
    private final String listingId;
    private final String buyerUsername;
    private final ReportReason reason;
    private final String details;

    /** @throws IllegalArgumentException if the reason or details are missing (UC9 A2) */
    public Report(String listingId, String buyerUsername, ReportReason reason, String details) {
        if (reason == null || details == null || details.isBlank()) {
            throw new IllegalArgumentException("Both a reason and details are required.");
        }
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.buyerUsername = Objects.requireNonNull(buyerUsername, "buyerUsername");
        this.reason = reason;
        this.details = details.trim();
    }

    /** Null until the report is added to DataStore. */
    public String getId() { return id; }

    /** Called once by DataStore when the report is added. */
    public void assignId(String id) {
        if (this.id != null) {
            throw new IllegalStateException("Report already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
    }

    public String getListingId() { return listingId; }

    /** Only the admin may see who reported a listing (UC9 Special Requirement 4). */
    public String getBuyerUsername() { return buyerUsername; }
    public ReportReason getReason() { return reason; }
    public String getDetails() { return details; }
}
