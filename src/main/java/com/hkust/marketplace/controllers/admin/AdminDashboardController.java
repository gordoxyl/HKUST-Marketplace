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


//createPendingCard,showReview(),initialize(),refreshCurrentScreen()
import com.hkust.marketplace.services.AdminUserService;
import com.hkust.marketplace.services.UserFilter;
import com.hkust.marketplace.ui.Formats;

//showUsers(), createUserRow()
import com.hkust.marketplace.services.UserDetails;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import java.util.stream.Collectors;

public class AdminDashboardController implements ContentHost {
    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final AdminListingService listingService = new AdminListingService();
    private final AdminUserService userService = new AdminUserService();

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

        // ----- filter bar -----
        TextField searchField = new TextField();
        searchField.setPromptText("Search username or name...");
        searchField.setPrefWidth(200);

        ComboBox<String> roleBox = new ComboBox<>();
        roleBox.getItems().addAll("All roles", "Buyer", "Seller");
        roleBox.getSelectionModel().selectFirst();

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("All statuses", "Active", "Deactivated");
        statusBox.getSelectionModel().selectFirst();

        Button applyBtn = new Button("Apply filters");
        applyBtn.getStyleClass().addAll("btn-primary", "btn-sm");

        HBox filterBar = new HBox(10, searchField,
                new Label("Role:"), roleBox,
                new Label("Status:"), statusBox,
                applyBtn);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox results = new VBox(10);
        root.getChildren().addAll(filterBar, results);

        Runnable[] refreshRef = new Runnable[1];   // lets the lambda call itself
        Runnable refresh = () -> {
            results.getChildren().clear();

            // Build service filter from Role and Status combos
            UserFilter filter = UserFilter.none();
            switch (roleBox.getValue()) {
                case "Buyer"  -> filter = filter.withRole(UserRole.BUYER);
                case "Seller" -> filter = filter.withRole(UserRole.SELLER);
            }
            switch (statusBox.getValue()) {
                case "Active"      -> filter = filter.withActive(true);
                case "Deactivated" -> filter = filter.withActive(false);
            }

            List<User> users;
            try {
                users = userService.findUsers(filter);
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                return;
            }

            // Username / full-name text search is applied client-side: UserFilter has no text field
            String query = searchField.getText().strip().toLowerCase(java.util.Locale.ROOT);
            if (!query.isEmpty()) {
                users = users.stream()
                        .filter(u -> u.getUsername().contains(query)
                                || u.getFullName().toLowerCase(java.util.Locale.ROOT).contains(query))
                        .toList();
            }

            if (users.isEmpty()) {
                Label none = new Label("No results found.");    // spec A3
                none.getStyleClass().add("text-muted");
                results.getChildren().add(none);
                return;
            }
            for (User u : users) {
                results.getChildren().add(createUserRow(u, () -> refreshRef[0].run()));
            }
        };

        refreshRef[0] = refresh;

        applyBtn.setOnAction(e -> refresh.run());
        refresh.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createUserRow(User u, Runnable reload) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);

        // Info block
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(u.getFullName() + " (@" + u.getUsername() + ")");
        name.getStyleClass().add("section-title");
        Label meta = new Label(u.getRole() + " · Registered " + Formats.date(u.getCreatedAt()));
        meta.getStyleClass().add("text-muted");
        info.getChildren().addAll(name, meta);

        Label status = new Label(u.isActive() ? "Active" : "Deactivated");
        status.getStyleClass().addAll("badge", u.isActive() ? "badge-live" : "badge-rejected");

        row.getChildren().addAll(info, status);

        // View button (6.1)
        Button view = new Button("View");
        view.getStyleClass().addAll("btn-outline", "btn-sm");
        view.setOnAction(e -> {
            UserDetails details;
            try {
                details = userService.getUserDetails(u.getUsername());
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                return;
            }
            // Format the details as readable text for Dialogs.info
            StringBuilder sb = new StringBuilder();
            sb.append("Full name:  ").append(details.user().getFullName()).append('\n');
            sb.append("Username:   @").append(details.user().getUsername()).append('\n');
            sb.append("Role:       ").append(details.user().getRole()).append('\n');
            sb.append("Status:     ").append(details.user().isActive() ? "Active" : "Deactivated").append('\n');
            sb.append("Registered: ").append(Formats.date(details.user().getCreatedAt())).append('\n');

            if (details.residenceHall() != null) {
                sb.append("Residence:  ").append(details.residenceHall()).append('\n');
            }
            if (details.bio() != null) {
                sb.append("Bio:        ").append(details.bio()).append('\n');
            }
            if (!details.preferredLocations().isEmpty()) {
                String locs = details.preferredLocations().stream()
                        .map(MeetupLocation::getDisplayName).collect(Collectors.joining(", "));
                sb.append("Locations:  ").append(locs).append('\n');
            }
            sb.append('\n');
            sb.append("Rating:         ").append(Formats.averageRating(details.averageRating(), details.ratingCount())).append('\n');
            sb.append("Active listings: ").append(details.activeListings().size()).append('\n');
            sb.append("Completed deals: ").append(details.completedDeals().size()).append('\n');
            sb.append("Open offers:     ").append(details.pendingOffers().size());

            Dialogs.info("User details — @" + u.getUsername(), sb.toString());
        });
        row.getChildren().add(view);

        // Edit button (Task 3.3 item 7)
        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-outline", "btn-sm");
        edit.setOnAction(e -> {
            // Build a small dialog with fullName and (if seller) bio
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Edit user — @" + u.getUsername());
            dialog.setHeaderText("Editing @" + u.getUsername()
                    + " — the username and role cannot be changed.");
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            TextField nameField = new TextField(u.getFullName());
            nameField.setPromptText("Full name (required)");

            VBox form = new VBox(8, new Label("Full name"), nameField);

            TextArea bioArea = null;
            if (u instanceof Seller seller) {
                bioArea = new TextArea(seller.getBio() != null ? seller.getBio() : "");
                bioArea.setPromptText("Bio (optional)");
                bioArea.setPrefRowCount(3);
                form.getChildren().addAll(new Label("Bio"), bioArea);
            }
            dialog.getDialogPane().setContent(form);

            // Keep a final reference for the lambda
            TextArea finalBioArea = bioArea;

            dialog.showAndWait().ifPresent(btn -> {
                if (btn != ButtonType.OK) return;

                String newBio = (finalBioArea != null) ? finalBioArea.getText() : null;
                // Pass existing locations back so the editor does not clear them
                List<MeetupLocation> existingLocations = (u instanceof Seller s)
                        ? s.getPreferredMeetupLocations()
                        : null;
                try {
                    userService.editUser(u.getUsername(), nameField.getText(), newBio, existingLocations);
                    Dialogs.info("User updated", "@" + u.getUsername() + " has been updated.");
                    reload.run();
                } catch (ServiceException ex) {
                    Dialogs.problem(ex);
                }
            });
        });
        row.getChildren().add(edit);

        // Deactivate / Activate toggle
        if (u.isActive()) {
            Button deactivate = new Button("Deactivate");
            deactivate.getStyleClass().addAll("btn-danger", "btn-sm");
            deactivate.setOnAction(e -> {
                Optional<String> reason = Dialogs.askReason("Deactivate account",
                        "Why is @" + u.getUsername() + " deactivated? The user will see this reason.");
                if (reason.isEmpty()) return;
                try {
                    userService.deactivateUser(u.getUsername(), reason.get());
                    Dialogs.info("Account deactivated", "@" + u.getUsername() + " has been deactivated.");
                } catch (ServiceException ex) {
                    Dialogs.problem(ex);
                }
                reload.run();
            });
            row.getChildren().add(deactivate);
        } else {
            Button activate = new Button("Activate");
            activate.getStyleClass().addAll("btn-success", "btn-sm");
            activate.setOnAction(e -> {
                try {
                    userService.activateUser(u.getUsername());
                    Dialogs.info("Account activated", "@" + u.getUsername() + " has been activated.");
                } catch (ServiceException ex) {
                    Dialogs.problem(ex);
                }
                reload.run();
            });
            row.getChildren().add(activate);
        }

        // Delete button
        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("btn-danger", "btn-sm");
        delete.setOnAction(e -> {
            if (!Dialogs.confirm("Delete user", "Permanently delete @" + u.getUsername()
                    + "? Their chats are archived in the Audit Log and the username can never be used again.")) {
                return;
            }
            Optional<String> reason = Dialogs.askReason("Delete account",
                    "Why is @" + u.getUsername() + " deleted?");
            if (reason.isEmpty()) return;
            try {
                userService.deleteUser(u.getUsername(), reason.get());
                Dialogs.info("Account deleted", "@" + u.getUsername() + " has been deleted.");
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
            }
            reload.run();
        });
        row.getChildren().add(delete);
        return row;
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
