package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.util.List;

/**
 * Notifications for all three portals (UC4 View Notifications, shared by the User actor).
 *
 * <p>Other services call {@link #send} or {@link #sendToAdmins} when an event happens
 * (UC4 Special Requirement 1). These calls do not check the logged-in user, because the
 * system, not the user, sends notifications. The board methods ({@link #getMyNotifications},
 * {@link #markRead}, {@link #getUnreadCount}) work only on the logged-in user's own board.
 */
public class NotificationService {

    private final DataStore store;

    public NotificationService() {
        this(DataStore.getInstance());
    }

    public NotificationService(DataStore store) {
        this.store = store;
    }

    // ========== Sending (called by other services) ==========

    /**
     * Puts a notification on the user's board, timestamped now.
     * @throws IllegalArgumentException if there is no such account
     */
    public Notification send(String recipientUsername, NotificationCategory category, String content) {
        if (store.findUser(recipientUsername) == null) {
            throw new IllegalArgumentException("No account @" + recipientUsername + " to notify.");
        }
        return store.addNotification(new Notification(recipientUsername, category, content));
    }

    /** Sends the same notification to every admin account (Task 3.6). */
    public void sendToAdmins(NotificationCategory category, String content) {
        for (User admin : store.getUsersByRole(UserRole.ADMIN)) {
            send(admin.getUsername(), category, content);
        }
    }

    // ========== UC4 View Notifications (logged-in user) ==========

    /** The logged-in user's notifications, newest first (UC4 step 2). */
    public List<Notification> getMyNotifications() {
        User user = AccessControl.requireLoggedIn();
        return store.getNotificationsFor(user.getUsername());
    }

    /**
     * Marks one of the logged-in user's notifications as read (UC4 step 3).
     * @throws AccessDeniedException if the notification does not exist or belongs to someone else
     */
    public void markRead(String notificationId) {
        User user = AccessControl.requireLoggedIn();
        store.transaction(() -> {
            Notification notification = store.findNotification(notificationId);
            if (notification == null || !notification.getRecipientUsername().equalsIgnoreCase(user.getUsername())) {
                throw new AccessDeniedException("This notification is not on your board.");
            }
            notification.markRead();
        });
    }

    /** How many of the logged-in user's notifications are unread, for a badge on the board's button. */
    public int getUnreadCount() {
        return (int) getMyNotifications().stream().filter(n -> !n.isRead()).count();
    }
}
