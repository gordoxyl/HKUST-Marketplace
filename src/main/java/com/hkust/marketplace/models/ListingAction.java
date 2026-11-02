package com.hkust.marketplace.models;

/** Actions an admin can take on a listing (ListingAction in the domain class diagram). */
public enum ListingAction {
    EDIT_LISTING("Edit Listing"),
    ADD_LISTING("Add Listing"),
    TAKEDOWN_LISTING("Take Down Listing"),
    REMOVE_LISTING("Remove Listing"),
    APPROVE_LISTING("Approve Listing"),
    REJECT_LISTING("Reject Listing");

    private final String displayName;

    ListingAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * True if the admin must give a reason for this action
     * ({reason is required for RejectListing, TakedownListing and RemoveListing}).
     */
    public boolean requiresReason() {
        return this == REJECT_LISTING || this == TAKEDOWN_LISTING || this == REMOVE_LISTING;
    }

    /** The display name, so combo boxes and tables show readable text. */
    @Override
    public String toString() {
        return displayName;
    }
}
