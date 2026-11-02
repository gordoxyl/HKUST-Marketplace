package com.hkust.marketplace.models;

/** Reasons a buyer can give when reporting a listing (UC9 Special Requirement 1). */
public enum ReportReason {
    PROHIBITED_ITEM("Prohibited item"),
    SCAM_OR_FRAUD("Scam or fraud"),
    MISLEADING_DESCRIPTION("Misleading description"),
    OFFENSIVE_CONTENT("Offensive content"),
    SPAM_OR_DUPLICATE("Spam or duplicate"),
    OTHER("Other");

    private final String displayName;

    ReportReason(String displayName) {
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
