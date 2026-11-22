package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.ProfileService;
import com.hkust.marketplace.ui.ContentHost;
import com.hkust.marketplace.ui.ListingCard;
import com.hkust.marketplace.ui.NotificationBoard;
import com.hkust.marketplace.ui.ProfileView;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

import com.hkust.marketplace.services.AdminListingService;
import com.hkust.marketplace.services.ListingFilter;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.LiveUpdates;
import java.util.Optional;

public class AdminDashboardController implements ContentHost {
    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final AdminListingService listingService = new AdminListingService();

    @FXML
    public void initialize() {
        showGreeting();
        currentActiveBtn = btnReview;
        // Reloads the open screen when the borrowing timer (UC12) commits a data change.
        // Remove this line if LiveUpdates is not yet in main.
        LiveUpdates.setOnDataChanged(() -> contentPane.getScene() != null, this::refreshCurrentScreen);
        showReview();
    }

    private void refreshCurrentScreen() {
        if (currentActiveBtn == btnReview)           showReview();
        else if (currentActiveBtn == btnAllListings) showAllListings();
        else if (currentActiveBtn == btnUsers)       showUsers();
        else if (currentActiveBtn == btnDeals)       showDeals();
        else if (currentActiveBtn == btnRatings)     showRatings();
    }

    /** Shows the logged-in admin's name. Runs again after a profile change, so a new name shows at once. */
    private void showGreeting() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Admin: " + user.getFullName());
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    /** Lets shared components open a screen here, such as a listing or a chat thread. */
    @Override
    public void show(String title, Node content) {
        pageTitle.setText(title);
        contentPane.getChildren().setAll(content);
    }

    @FXML
    private void showReview() {
        setActive(btnReview, "Listing Review & Approval");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        List<Listing> pending;
        try {
            pending = listingService.findListings(
                    ListingFilter.none().withStatus(ListingStatus.PENDING_REVIEW));
        } catch (ServiceException ex) {
            Dialogs.problem(ex);
            return;
        }

        if (pending.isEmpty()) {
            Label empty = new Label("No listings are waiting for review.");
            empty.getStyleClass().add("text-muted");
            root.getChildren().add(empty);
        } else {
            FlowPane grid = ListingCard.grid();
            for (Listing l : pending) {
                grid.getChildren().add(createPendingCard(l));
            }
            root.getChildren().add(grid);
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private ListingCard createPendingCard(Listing l) {
        ListingCard card = new ListingCard(l, UserRole.ADMIN);

        Button approve = new Button("Approve");
        approve.getStyleClass().addAll("btn-success", "btn-sm");
        approve.setOnAction(e -> {
            if (!Dialogs.confirm("Approve listing", "Approve '" + l.getTitle() + "'?")) {
                return;
            }
            try {
                listingService.approveListing(l.getId());
                Dialogs.info("Listing approved", "'" + l.getTitle() + "' is now live.");
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
            }
            showReview();
        });

        Button reject = new Button("Reject");
        reject.getStyleClass().addAll("btn-danger", "btn-sm");
        reject.setOnAction(e -> {
            Optional<String> reason = Dialogs.askReason("Reject listing",
                    "Why is '" + l.getTitle() + "' rejected? The seller will see this reason.");
            if (reason.isEmpty()) {
                return;
            }
            try {
                listingService.rejectListing(l.getId(), reason.get());
                Dialogs.info("Listing rejected", "The seller has been notified.");
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
            }
            showReview();
        });

        card.addActions(approve, reject);
        return card;
    }

    @FXML
    private void showAllListings() {
        setActive(btnAllListings, "Manage All Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        Button addBtn = new Button("+ Add Listing (on behalf of seller)");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Admin can create a listing on behalf of a seller here.\nSame form fields as Seller Create Listing.\n(UI placeholder)").showAndWait());
        root.getChildren().add(addBtn);

        FlowPane grid = ListingCard.grid(); // shared listing card
        for (Listing l : DataStore.getInstance().getAllListings()) {
            ListingCard card = new ListingCard(l, UserRole.ADMIN);

            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-outline", "btn-sm");
            edit.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Edit listing dialog (demo).").showAndWait());

            Button remove = new Button("Remove");
            remove.getStyleClass().addAll("btn-danger", "btn-sm");
            remove.setOnAction(e -> {
                ListingStatus s = l.getStatus();
                if (s == ListingStatus.SOLD || s == ListingStatus.GIVEN || s == ListingStatus.REMOVED) {
                    new Alert(Alert.AlertType.WARNING, "A " + s.getDisplayName().toLowerCase()
                            + " listing cannot be removed.").showAndWait();
                    return;
                }
                Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Permanently remove this listing?");
                conf.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        // A removed listing keeps its record with status Removed (Manage Marketplace Listings 5.5.4)
                        DataStore.getInstance().transaction(l::remove);
                        showAllListings();
                    }
                });
            });

            card.addActions(edit, remove);
            grid.getChildren().add(card);
        }
        root.getChildren().add(grid);
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showUsers() {
        setActive(btnUsers, "Manage All Users");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        for (User u : DataStore.getInstance().getAllUsers()) {
            if (u.getRole() == UserRole.ADMIN) continue;

            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(3);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label name = new Label(u.getFullName() + " (@" + u.getUsername() + ")");
            name.setStyle("-fx-font-weight: bold;");
            Label role = new Label(u.getRole().name() + (u.isActive() ? " · Active" : " · Deactivated"));
            role.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
            info.getChildren().addAll(name, role);

            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-outline", "btn-sm");
            edit.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Edit user dialog (demo).").showAndWait());

            Button toggle = new Button(u.isActive() ? "Deactivate" : "Activate");
            toggle.getStyleClass().addAll(u.isActive() ? "btn-danger" : "btn-success", "btn-sm");
            toggle.setOnAction(e -> {
                // Inside a transaction, so the change is saved (regression log R4)
                DataStore.getInstance().transaction(() -> u.setActive(!u.isActive()));
                showUsers();
            });

            Button delete = new Button("Delete");
            delete.getStyleClass().addAll("btn-danger", "btn-sm");
            delete.setOnAction(e -> {
                Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                        "Permanently delete user @" + u.getUsername() + "?");
                conf.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        DataStore.getInstance().transaction(() -> u.setActive(false)); // saved (R4)
                        new Alert(Alert.AlertType.INFORMATION, "User deactivated (demo safety).").showAndWait();
                        showUsers();
                    }
                });
            });

            row.getChildren().addAll(info, edit, toggle, delete);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showDeals() {
        setActive(btnDeals, "All Deals Record");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search by item / user...");
        ComboBox<String> typeFilter = new ComboBox<>(FXCollections.observableArrayList("All Types", "Sell", "Lend", "Giveaway"));
        typeFilter.getSelectionModel().selectFirst();
        filters.getChildren().addAll(search, typeFilter);
        root.getChildren().add(filters);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        String[] headers = {"Item", "Buyer", "Seller", "Type", "Status", "Date"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[idx]));
            table.getColumns().add(col);
        }
        table.setItems(FXCollections.observableArrayList(
                new String[]{"Calculus Textbook", "alice", "charlie", "Sell", "Accepted", "2026-01-15"},
                new String[]{"USB Hub", "bob", "diana", "Giveaway", "Completed", "2025-12-01"}
        ));
        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings Overview");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(10);
        TextField userFilter = new TextField();
        userFilter.setPromptText("Filter by seller...");
        ComboBox<String> scoreFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All scores", "5 stars", "4 stars", "3 stars", "2 stars", "1 star"));
        scoreFilter.getSelectionModel().selectFirst();
        filters.getChildren().addAll(userFilter, scoreFilter);
        root.getChildren().add(filters);

        for (Review r : DataStore.getInstance().getAllReviews()) {
            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(4);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label stars = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()) +
                    "  @" + DataStore.getInstance().getSellerUsername(r) + " by @" + DataStore.getInstance().getBuyerUsername(r));
            stars.setStyle("-fx-font-weight: bold;");
            Label comment = new Label(r.getComment());
            comment.setWrapText(true);
            comment.setStyle("-fx-text-fill: #5a5a7a;");
            info.getChildren().addAll(stars, comment);

            Button hide = new Button(r.isHidden() ? "Unhide" : "Hide");
            hide.getStyleClass().addAll("btn-outline", "btn-sm");
            hide.setOnAction(e -> {
                DataStore.getInstance().transaction(() -> r.setHidden(!r.isHidden())); // saved (R4)
                showRatings();
            });
            row.getChildren().addAll(info, hide);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");   // was "Admin Notifications"
        show("Notification Board", new NotificationBoard(new NotificationService()));
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "Manage Profile");             // was "My Profile"
        show("Manage Profile", new ProfileView(new ProfileService(), this::showGreeting));
    }
    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
