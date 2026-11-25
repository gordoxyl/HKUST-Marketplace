package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.ServiceException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
 * category, content and timestamp. Unread ones are marked, and can be marked as read one by
 * one (UC4 step 3) or all at once with Mark all as read (assumptions register 91). A category
 * filter shows one category at a time (assumptions register 93).
 * <pre>{@code
 * host.show("Notification Board", new NotificationBoard(new NotificationService()));
 * }</pre>
 * All rules (whose notifications, the order, the categories, marking as read) are in the tested
 * {@link NotificationService}; this class only displays them. After marking, it also updates the
 * sidebar's {@link NotificationDot}.
 */
public class NotificationBoard extends ScrollPane implements Refreshable {

    private static final String ALL_CATEGORIES = "All categories";

    private final NotificationService service;
    private final VBox list = new VBox(12);
    /** The category the filter shows; null for all of them. It stays chosen when the board reloads. */
    private NotificationCategory shownCategory;

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
        List<Notification> all;
        List<NotificationCategory> categories;
        List<Notification> shown;
        try {
            all = service.getMyNotifications();
            categories = service.getMyCategories();
            shown = shownCategory == null ? all : service.getMyNotifications(shownCategory);
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }

        int unread = (int) all.stream().filter(n -> !n.isRead()).count();
        list.getChildren().add(heading(unread, categories));

        if (all.isEmpty()) {
            // UC4 A1
            Label empty = new Label("There are no notifications yet.");
            empty.getStyleClass().add("text-muted");
            list.getChildren().add(empty);
            return;
        }
        for (Notification notification : shown) {
            list.getChildren().add(item(notification));
        }
    }

    /** How many are unread; then the category filter, and Mark all as read while anything is unread. */
    private Node heading(int unread, List<NotificationCategory> categories) {
        Label summary = new Label(Formats.unreadSummary(unread));
        summary.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(10, summary, spacer);
        heading.setAlignment(Pos.CENTER_LEFT);
        if (!categories.isEmpty()) {
            heading.getChildren().add(categoryFilter(categories));
        }
        if (unread > 0) {
            Button markAll = new Button("Mark all as read");
            markAll.getStyleClass().addAll("btn-outline", "btn-sm");
            markAll.setMinWidth(Region.USE_PREF_SIZE);
            markAll.setOnAction(e -> marked(service::markAllRead));
            heading.getChildren().add(markAll);
        }
        return heading;
    }

    /** "All categories", then each category the user's notifications have, as the buyer's filters offer choices ({@link Choices}). */
    private ComboBox<String> categoryFilter(List<NotificationCategory> categories) {
        NotificationCategory[] values = categories.toArray(NotificationCategory[]::new);
        ComboBox<String> filter = Choices.withAll(ALL_CATEGORIES, values);
        if (shownCategory != null) {
            filter.getSelectionModel().select(shownCategory.toString());
        }
        filter.setOnAction(e -> {
            shownCategory = Choices.chosen(filter, values);
            refresh();
        });
        return filter;
    }

    private VBox item(Notification notification) {
        Label category = new Label(notification.getCategory().getDisplayName());
        category.getStyleClass().addAll("badge", "badge-sold");
        Label time = new Label(Formats.dateTime(notification.getTimestamp()));
        time.getStyleClass().add("notif-time");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, category, time, spacer);
        top.setAlignment(Pos.CENTER_LEFT);

        Label content = new Label(notification.getContent());
        content.setWrapText(true);
        content.getStyleClass().add("notif-text");

        VBox box = new VBox(6, top, content);
        box.getStyleClass().add("notif-item");
        if (notification.isRead()) {
            box.getStyleClass().add("notif-read");
        } else {
            Label unread = new Label("Unread");
            unread.getStyleClass().addAll("badge", "badge-pending");
            Button markRead = new Button("Mark as read");
            markRead.getStyleClass().addAll("btn-outline", "btn-sm");
            markRead.setMinWidth(Region.USE_PREF_SIZE);
            markRead.setOnAction(e -> marked(() -> service.markRead(notification.getId()))); // UC4 step 3
            top.getChildren().addAll(unread, markRead);
        }
        return box;
    }

    /**
     * Marks one notification or all of them as read, then shows the board and the sidebar's dot
     * as they are now. A refusal (the account can no longer act) is shown and changes nothing.
     */
    private void marked(Runnable marking) {
        try {
            marking.run();
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        refresh();
        NotificationDot.update();
    }
}
