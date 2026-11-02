package com.hkust.marketplace.models;

/** Status of a price offer, borrow request or claim (OfferStatus in the domain class diagram). */
public enum OfferStatus {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    DECLINED("Declined"),
    COUNTERED("Countered");

    private final String displayName;

    OfferStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The display name, so combo boxes and tables show readable text. */
    @Override
    public String toString() {
        return displayName;
    }
}
