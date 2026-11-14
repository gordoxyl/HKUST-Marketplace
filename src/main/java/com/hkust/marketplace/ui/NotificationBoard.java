package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.ServiceException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The notification board, the same in all three portals (UC4 View Notifications; Tasks 1.8,
 * 2.7, 3.6). It shows the logged-in user's notifications newest first, each with its
 * category, content and timestamp; unread ones are marked and can be marked as read.
 * <pre>{@code
 * host.show("Notification Board", new NotificationBoard(new NotificationService()));
 * }</pre>
 * All rules (whose notifications, the order, marking as read) are in the tested
 * {@link NotificationService}; this class only displays them.
 */
public class NotificationBoard extends ScrollPane implements Refreshable {

    private final NotificationService service;
    private final VBox list = new VBox(12);

    public NotificationBoard(NotificationService service) {
        this.service = service;
        list.setPadding(new Insets(4));
        setContent(list);
        setFitToWidth(true);
        refresh();
    }

    /** Reloads the board, for example after a notification was marked as read, or a due-date reminder came. */
    @Override
    public final void refresh() {
        list.getChildren().clear();
        List<Notification> notifications;
        try {
            notifications = service.getMyNotifications();
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }

        int unread = (int) notifications.stream().filter(n -> !n.isRead()).count();
        Label summary = new Label(Formats.unreadSummary(unread));
        summary.getStyleClass().add("section-title");
        list.getChildren().add(summary);

        if (notifications.isEmpty()) {
            // UC4 A1
            Label empty = new Label("There are no notifications yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            list.getChildren().add(empty);
            return;
        }
        for (Notification notification : notifications) {
            list.getChildren().add(item(notification));
        }
    }

    private VBox item(Notification notification) {
        Label category = new Label(notification.getCategory().getDisplayName());
        category.getStyleClass().addAll("badge", "badge-sold");
        Label time = new Label(Formats.dateTime(notification.getTimestamp()));
        time.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, category, time, spacer);
        top.setAlignment(Pos.CENTER_LEFT);

        Label content = new Label(notification.getContent());
        content.setWrapText(true);
        content.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 13px;");

        VBox box = new VBox(6, top, content);
        box.getStyleClass().add("notif-item");
        if (notification.isRead()) {
            box.getStyleClass().add("notif-read");
        } else {
            Label unread = new Label("Unread");
            unread.getStyleClass().addAll("badge", "badge-pending");
            Button markRead = new Button("Mark as read");
            markRead.getStyleClass().addAll("btn-outline", "btn-sm");
            markRead.setOnAction(e -> {
                try {
                    service.markRead(notification.getId()); // UC4 step 3
                    refresh();
                } catch (ServiceException ex) {
                    Dialogs.problem(ex);
                }
            });
            top.getChildren().addAll(unread, markRead);
        }
        return box;
    }
}
