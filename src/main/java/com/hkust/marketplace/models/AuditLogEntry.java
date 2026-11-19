package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One entry in the admin's Audit Log (Audit Log in the domain class diagram).
 * Used for account deactivation and deletion and for hiding reviews (Manage All User
 * Accounts and Reputation). Before an account is deleted, its data is kept here as a
 * snapshot, which the store also writes to an encrypted .txt file of its own
 * ({@code marketplace-audit/<id>.txt}; see {@link com.hkust.marketplace.data.EncryptedJsonStorage}).
 */
public class AuditLogEntry {

    private String logReference;
    private final String adminUsername;
    private final String username;
    private final String actionTaken;
    private final String reason;
    private LocalDateTime actionDate;
    private final String dataSnapshot;

    /**
     * @param username     the account the action was taken on
     * @param dataSnapshot the deleted data, or null if nothing was deleted
     */
    public AuditLogEntry(String adminUsername, String username, String actionTaken, String reason, String dataSnapshot) {
        this.adminUsername = Objects.requireNonNull(adminUsername, "adminUsername");
        this.username = Objects.requireNonNull(username, "username");
        this.actionTaken = Objects.requireNonNull(actionTaken, "actionTaken");
        this.reason = reason;
        this.dataSnapshot = dataSnapshot;
    }

    /** Null until the entry is added to DataStore. */
    public String getLogReference() { return logReference; }

    /** Called once by DataStore when the entry is added: gives it its log reference and action date. */
    public void markAdded(String logReference, LocalDateTime actionDate) {
        if (this.logReference != null) {
            throw new IllegalStateException("Audit log entry already has reference " + this.logReference);
        }
        this.logReference = Objects.requireNonNull(logReference, "logReference");
        this.actionDate = Objects.requireNonNull(actionDate, "actionDate");
    }

    public String getAdminUsername() { return adminUsername; }
    public String getUsername() { return username; }
    public String getActionTaken() { return actionTaken; }
    public String getReason() { return reason; }
    public LocalDateTime getActionDate() { return actionDate; }
    public String getDataSnapshot() { return dataSnapshot; }
}
