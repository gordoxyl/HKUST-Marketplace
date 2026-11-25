package com.hkust.marketplace.ui;

import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.ServiceException;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * The red dot on a portal's Notifications button, with the number of unread notifications,
 * the same in all three portals (assumptions register 92). It shows UC4 step 2's "unread" mark
 * before the board is opened. The number is {@link NotificationService#getUnreadCount()}; at
 * zero the dot is hidden. Opening the board changes nothing: only marking notifications as
 * read lowers the number (UC4 Special Requirement 3).
 * <pre>{@code
 * NotificationDot.attach(btnNotifications, new NotificationService()); // in the dashboard's initialize()
 * NotificationDot.update();                                           // in its show(title, content)
 * }</pre>
 * The dot also updates when its dashboard appears in the window, the {@link NotificationBoard}
 * updates it after marking, and {@link LiveUpdates} after the borrowing-deadline timer has sent
 * a reminder or returned an item (UC12). A change caused by the user's own action, such as an
 * admin's account change that every admin is told about, shows at the dashboard's next screen.
 *
 * <p>Like {@link LiveUpdates}, this is the Observer pattern in its simplest form (Lecture 17):
 * the dot of the dashboard on show is the one observer, and attaching a dot replaces the one
 * before it. When its dashboard leaves the window (logout), it is forgotten. Use it on the
 * JavaFX thread.
 */
public final class NotificationDot {

    /** The dot's id, for tests that read the number. */
    public static final String ID = "unreadDot";

    private static Button button;
    private static NotificationService unread;
    private static Label dot;

    private NotificationDot() {
        // Utility class
    }

    /**
     * Shows the dot on this dashboard's Notifications button, in place of any earlier dashboard's.
     * @param notificationsButton the sidebar button that opens the notification board
     * @param service             counts the logged-in user's unread notifications
     */
    public static void attach(Button notificationsButton, NotificationService service) {
        Label newDot = new Label();
        newDot.setId(ID);
        newDot.getStyleClass().add("notif-dot");
        newDot.setTooltip(new Tooltip());
        notificationsButton.setGraphic(newDot);
        notificationsButton.setContentDisplay(ContentDisplay.RIGHT);
        notificationsButton.sceneProperty().addListener((observed, before, now) -> {
            if (now != null) {
                update(); // the dashboard is now in the window
            } else if (button == notificationsButton) {
                button = null; // logged out: forget the old dashboard, so it can be freed
            }
        });
        button = notificationsButton;
        unread = service;
        dot = newDot;
        update();
    }

    /** Shows the number of unread notifications as it is now, and hides the dot when there are none. */
    public static void update() {
        if (button == null) {
            return; // no dashboard with a dot is open
        }
        int count = count();
        dot.setText(count > 0 ? Formats.unreadCount(count) : "");
        dot.getTooltip().setText(Formats.unreadSummary(count));
        dot.setVisible(count > 0);
    }

    /**
     * The logged-in user's unread notifications. If the account can no longer act, the dot shows
     * none, and the user's next action reports why, as the buyer's prompt after an automatic
     * return does.
     */
    private static int count() {
        try {
            return unread.getUnreadCount();
        } catch (ServiceException e) {
            return 0;
        }
    }
}
