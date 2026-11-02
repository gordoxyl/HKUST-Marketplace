package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One item on a user's notification board (Notification in the domain class diagram; UC4).
 * Notifications are clear, timestamped and categorized (Tasks 1.8, 2.7, 3.6).
 */
public class Notification {

    private String id;
    private final String recipientUsername;
    private final NotificationCategory category;
    private final String content;
    private final LocalDateTime timestamp = LocalDateTime.now();
    private boolean read;

    public Notification(String recipientUsername, NotificationCategory category, String content) {
        this.recipientUsername = Objects.requireNonNull(recipientUsername, "recipientUsername");
        this.category = Objects.requireNonNull(category, "category");
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Null until the notification is added to DataStore. */
    public String getId() { return id; }

    /** Called once by DataStore when the notification is added. */
    public void assignId(String id) {
        if (this.id != null) {
            throw new IllegalStateException("Notification already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
    }

    public String getRecipientUsername() { return recipientUsername; }
    public NotificationCategory getCategory() { return category; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }

    /** A notification stays unread until its recipient marks it read (UC4 Special Requirement 3). */
    public boolean isRead() { return read; }
    public void markRead() { read = true; }
}
