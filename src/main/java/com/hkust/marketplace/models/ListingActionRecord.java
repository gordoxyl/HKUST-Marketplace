package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/** A record of one action an admin took on a listing (ListingActionRecord in the domain class diagram). */
public class ListingActionRecord {

    private String id;
    private final String listingId;
    private final String adminUsername;
    private final ListingAction typeOfAction;
    private final String reason;
    private LocalDateTime actionDate;

    /**
     * @param reason may be null, except for the actions that require one
     * @throws IllegalArgumentException if the action requires a reason and none is given
     *         ({reason is required for RejectListing, TakedownListing and RemoveListing})
     */
    public ListingActionRecord(String listingId, String adminUsername, ListingAction typeOfAction, String reason) {
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.adminUsername = Objects.requireNonNull(adminUsername, "adminUsername");
        this.typeOfAction = Objects.requireNonNull(typeOfAction, "typeOfAction");
        boolean noReason = reason == null || reason.isBlank();
        if (typeOfAction.requiresReason() && noReason) {
            throw new IllegalArgumentException("A reason is required to " + typeOfAction.getDisplayName().toLowerCase() + ".");
        }
        this.reason = noReason ? null : reason.trim();
    }

    /** Null until the record is added to DataStore. */
    public String getId() { return id; }

    /** Called once by DataStore when the record is added: gives it its id and action date. */
    public void markAdded(String id, LocalDateTime actionDate) {
        if (this.id != null) {
            throw new IllegalStateException("Listing action record already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
        this.actionDate = Objects.requireNonNull(actionDate, "actionDate");
    }

    public String getListingId() { return listingId; }
    public String getAdminUsername() { return adminUsername; }
    public ListingAction getTypeOfAction() { return typeOfAction; }

    /** Null if no reason was given. */
    public String getReason() { return reason; }
    public LocalDateTime getActionDate() { return actionDate; }
}
