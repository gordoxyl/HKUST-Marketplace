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

//showAllListings
import com.hkust.marketplace.services.ListingDetails;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

//showDeals
import com.hkust.marketplace.services.AdminDealService;
import com.hkust.marketplace.services.DealDetails;
import com.hkust.marketplace.services.DealFilter;
import com.hkust.marketplace.services.DealRecord;
import com.hkust.marketplace.services.DealState;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;


public class AdminDashboardController implements ContentHost {
    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final AdminListingService listingService = new AdminListingService();
    private final AdminUserService userService = new AdminUserService();
    private Runnable listingsReload = () -> { };
    private final AdminDealService dealService = new AdminDealService();
    private List<DealRecord> currentDeals = List.of();   // the list the Export button saves

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

        // ----- filter bar (spec step 2) -----
        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().add("All statuses");
        for (ListingStatus s : ListingStatus.values()) statusBox.getItems().add(s.getDisplayName());
        statusBox.getItems().add("Reported");
        statusBox.getSelectionModel().selectFirst();

        ComboBox<Object> categoryBox = new ComboBox<>();
        categoryBox.getItems().add("All categories");
        categoryBox.getItems().addAll((Object[]) Category.values());
        categoryBox.getSelectionModel().selectFirst();

        ComboBox<Object> typeBox = new ComboBox<>();
        typeBox.getItems().add("All types");
        typeBox.getItems().addAll((Object[]) ListingType.values());
        typeBox.getSelectionModel().selectFirst();

        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller...");
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");

        Button apply = new Button("Apply filters");
        apply.getStyleClass().addAll("btn-primary", "btn-sm");
        Button add = new Button("+ Add Listing");
        add.getStyleClass().addAll("btn-gold", "btn-sm");

        HBox filterBar = new HBox(10, statusBox, categoryBox, typeBox, sellerField, from, to, apply, add);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        FlowPane results = ListingCard.grid();
        VBox resultArea = new VBox(10);
        root.getChildren().addAll(filterBar, resultArea);

        listingsReload = () -> {
            resultArea.getChildren().clear();
            results.getChildren().clear();

            ListingFilter filter = ListingFilter.none();
            String status = statusBox.getValue();
            if ("Reported".equals(status)) {
                filter = filter.withReportedOnly(true);               // not a status: its own flag
            } else {
                for (ListingStatus s : ListingStatus.values()) {
                    if (s.getDisplayName().equals(status)) filter = filter.withStatus(s);
                }
            }
            if (categoryBox.getValue() instanceof Category c) filter = filter.withCategory(c);
            if (typeBox.getValue() instanceof ListingType t) filter = filter.withType(t);
            filter = filter.withSeller(sellerField.getText());
            // If your ListingFilter uses LocalDate, pass from.getValue() and to.getValue() directly
            filter = filter.withDateRange(from.getValue(), to.getValue());

            List<Listing> found;
            try {
                found = listingService.findListings(filter);
            } catch (ServiceException ex) {                           // "The date range is invalid."
                Dialogs.problem(ex);
                return;
            }
            if (found.isEmpty()) {
                Label none = new Label("No listings found.");         // spec A2
                none.getStyleClass().add("text-muted");
                resultArea.getChildren().add(none);
                return;
            }
            for (Listing l : found) {
                results.getChildren().add(createManageCard(l));
            }
            resultArea.getChildren().add(results);
        };

        apply.setOnAction(e -> listingsReload.run());
        add.setOnAction(e -> {
            if (showListingForm(null)) listingsReload.run();
        });
        listingsReload.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private ListingCard createManageCard(Listing l) {
        ListingCard card = new ListingCard(l, UserRole.ADMIN);

        Button view = new Button("View");
        view.getStyleClass().addAll("btn-outline", "btn-sm");
        view.setOnAction(e -> {
            try {
                ListingDetails d = listingService.getListingDetails(l.getId());   // spec 5.1
                StringBuilder sb = new StringBuilder();
                sb.append("Title: ").append(d.listing().getTitle()).append('\n')
                        .append("Seller: ").append(d.sellerFullName()).append(" (@").append(d.listing().getSellerUsername()).append(")\n")
                        .append("Type: ").append(d.listing().getType()).append(" · ").append(d.listing().getPriceDisplay()).append('\n')
                        .append("Category: ").append(d.listing().getCategory()).append(" · ").append(d.listing().getCondition()).append('\n')
                        .append("Meet-up: ").append(d.listing().getMeetupLocation()).append('\n')
                        .append("Status: ").append(d.listing().getStatus()).append('\n')
                        .append("Posted: ").append(Formats.dateTime(d.listing().getPostedDate())).append('\n')
                        .append("Description: ").append(d.listing().getDescription() == null ? Formats.NONE : d.listing().getDescription()).append("\n\n");
                if (d.reports().isEmpty()) {
                    sb.append("No reports.");
                } else {
                    sb.append("Reports (").append(d.reports().size()).append("):\n");
                    for (Report r : d.reports()) {
                        sb.append("• ").append(r.getReason()).append(" by @").append(r.getBuyerUsername())
                                .append(": ").append(r.getDetails()).append('\n');
                    }
                }
                Dialogs.info("Listing details", sb.toString());
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                listingsReload.run();
            }
        });

        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-outline", "btn-sm");
        edit.setOnAction(e -> {
            if (showListingForm(l)) listingsReload.run();
        });

        Button remove = new Button("Remove");
        remove.getStyleClass().addAll("btn-danger", "btn-sm");
        remove.setOnAction(e -> {
            if (!Dialogs.confirm("Remove listing", "Remove '" + l.getTitle()
                    + "'? Open offers will be declined and the buyers notified.")) {
                return;
            }
            Optional<String> reason = Dialogs.askReason("Remove listing",
                    "Why is '" + l.getTitle() + "' removed?");
            if (reason.isEmpty()) return;                              // cancelled (A3)
            try {
                listingService.removeListing(l.getId(), reason.get());
                Dialogs.info("Listing removed", "The seller and affected buyers have been notified.");
            } catch (ServiceException ex) {
                Dialogs.problem(ex);                                   // e.g. already sold/given/removed
            }
            listingsReload.run();
        });

        card.addActions(view, edit, remove);
        return card;
    }

    /**
     * One form for Edit (existing != null) and Add (existing == null).
     * Only reads input and calls the service. Returns true if something was saved.
     */
    private boolean showListingForm(Listing existing) {
        boolean adding = existing == null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(adding ? "Add listing" : "Edit listing");
        dialog.setHeaderText(adding ? "Add a listing on behalf of a seller (it goes Live at once)"
                : "Editing '" + existing.getTitle() + "' (the status does not change)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller username");
        ComboBox<ListingType> typeBox = new ComboBox<>(FXCollections.observableArrayList(ListingType.values()));
        ComboBox<ItemCondition> conditionBox = new ComboBox<>(FXCollections.observableArrayList(ItemCondition.values()));
        TextField titleField = new TextField(adding ? "" : existing.getTitle());
        TextArea descArea = new TextArea(adding || existing.getDescription() == null ? "" : existing.getDescription());
        descArea.setPrefRowCount(3);
        ComboBox<Category> categoryBox = new ComboBox<>(FXCollections.observableArrayList(Category.values()));
        ComboBox<MeetupLocation> locationBox = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
        TextField priceField = new TextField();
        priceField.setPromptText("Price (HK$)");
        TextField durationField = new TextField();
        durationField.setPromptText("Lending duration");
        ComboBox<String> unitBox = new ComboBox<>(FXCollections.observableArrayList("seconds", "minutes", "hours", "days"));
        unitBox.getSelectionModel().select("days");

        if (!adding) {
            categoryBox.setValue(existing.getCategory());
            locationBox.setValue(existing.getMeetupLocation());
            if (existing instanceof SaleListing s) priceField.setText(String.valueOf(s.getPrice()));
        }

        VBox form = new VBox(8);
        if (adding) {
            form.getChildren().addAll(new Label("Seller username *"), sellerField,
                    new Label("Listing type *"), typeBox,
                    new Label("Condition *"), conditionBox);
        }
        form.getChildren().addAll(new Label("Title *"), titleField, new Label("Description"), descArea,
                new Label("Category *"), categoryBox, new Label("Meet-up location *"), locationBox);
        boolean showPrice = adding || existing instanceof SaleListing;
        boolean showDuration = adding || existing instanceof LendListing;
        if (showPrice) form.getChildren().addAll(new Label("Price (Sell only)"), priceField);
        if (showDuration) form.getChildren().addAll(new Label("Lending duration (Lend only)"),
                new HBox(8, durationField, unitBox));
        dialog.getDialogPane().setContent(form);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) return false;   // cancel (A3)

        // Parsing only; every rule is checked in the service
        Double price = null;
        Duration duration = null;
        try {
            if (!priceField.getText().isBlank()) price = Double.parseDouble(priceField.getText().strip());
            if (!durationField.getText().isBlank()) {
                long n = Long.parseLong(durationField.getText().strip());
                duration = switch (unitBox.getValue()) {
                    case "seconds" -> Duration.ofSeconds(n);
                    case "minutes" -> Duration.ofMinutes(n);
                    case "hours" -> Duration.ofHours(n);
                    default -> Duration.ofDays(n);
                };
            }
        } catch (NumberFormatException ex) {
            Dialogs.error("Not possible", "The price and the duration must be numbers.");
            return false;
        }

        try {
            if (adding) {
                listingService.addListing(sellerField.getText(), typeBox.getValue(), titleField.getText(),
                        descArea.getText(), categoryBox.getValue(), conditionBox.getValue(),
                        locationBox.getValue(), price, duration, null);
                Dialogs.info("Listing added", "The listing is Live and the seller has been notified.");
            } else {
                listingService.editListing(existing.getId(), titleField.getText(), descArea.getText(),
                        categoryBox.getValue(), locationBox.getValue(), price, duration, null);
                Dialogs.info("Listing updated", "The seller has been notified.");
            }
            return true;
        } catch (ServiceException ex) {
            Dialogs.problem(ex);                                       // A1 / A4 messages come from the service
            return false;
        }
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

        // ----- filter bar (spec step 2: Status, Date range, Deal type, Seller, Buyer) -----
        ComboBox<Object> statusBox = new ComboBox<>();
        statusBox.getItems().add("All statuses");
        statusBox.getItems().addAll((Object[]) DealState.values());
        statusBox.getSelectionModel().selectFirst();

        ComboBox<Object> typeBox = new ComboBox<>();
        typeBox.getItems().add("All types");
        typeBox.getItems().addAll((Object[]) ListingType.values());
        typeBox.getSelectionModel().selectFirst();

        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller...");
        TextField buyerField = new TextField();
        buyerField.setPromptText("Buyer...");
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");

        Button apply = new Button("Apply filters");
        apply.getStyleClass().addAll("btn-primary", "btn-sm");
        Button export = new Button("Export CSV");
        export.getStyleClass().addAll("btn-gold", "btn-sm");

        HBox filterBar = new HBox(10, statusBox, typeBox, sellerField, buyerField, from, to, apply, export);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        // ----- table (spec step 3) -----
        TableView<DealRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Item title", r -> r.listing().getTitle()));
        table.getColumns().add(column("Buyer", DealRecord::buyerUsername));
        table.getColumns().add(column("Seller", DealRecord::sellerUsername));
        table.getColumns().add(column("Deal type", r -> dealTypeName(r.dealType())));
        table.getColumns().add(column("Start date", r -> Formats.dateTime(r.startDate())));
        table.getColumns().add(column("Return/Complete date", r -> Formats.dateTime(r.completedDate())));
        table.getColumns().add(column("Status", r -> r.state().getDisplayName()));

        TableColumn<DealRecord, Void> actions = new TableColumn<>("Actions");
        actions.setCellFactory(col -> new TableCell<>() {
            private final Button view = new Button("View Details");
            {
                view.getStyleClass().addAll("btn-outline", "btn-sm");
                view.setOnAction(e -> showDealDetails(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : view);
            }
        });
        table.getColumns().add(actions);

        Label none = new Label("No deals found.");                       // spec A1
        none.getStyleClass().add("text-muted");
        none.setVisible(false);
        none.setManaged(false);

        root.getChildren().addAll(filterBar, none, table);

        Runnable refresh = () -> {
            DealFilter filter = DealFilter.none();
            if (statusBox.getValue() instanceof DealState s) filter = filter.withStatus(s);
            if (typeBox.getValue() instanceof ListingType t) filter = filter.withType(t);
            filter = filter.withSeller(sellerField.getText()).withBuyer(buyerField.getText())
                    .withDateRange(
                            from.getValue() == null ? null : from.getValue().atStartOfDay(),
                            to.getValue() == null ? null : to.getValue().atTime(LocalTime.MAX));
            try {
                currentDeals = dealService.findDeals(filter);
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                return;
            }
            table.getItems().setAll(currentDeals);
            boolean empty = currentDeals.isEmpty();
            none.setVisible(empty);
            none.setManaged(empty);
            table.setVisible(!empty);
            table.setManaged(!empty);
        };
        apply.setOnAction(e -> refresh.run());

        export.setOnAction(e -> {                                         // spec 4.2 and A2
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save deals as CSV");
            chooser.setInitialFileName("deals.csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
            File file = chooser.showSaveDialog(SceneManager.getPrimaryStage());
            if (file == null) {
                return;                                                   // cancelled
            }
            try {
                String csv = dealService.exportCsv(currentDeals);         // the currently filtered deals
                Files.writeString(file.toPath(), csv);
                Dialogs.info("Export complete", "Saved " + currentDeals.size() + " deal(s) to " + file.getName() + ".");
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
            } catch (IOException ex) {
                Dialogs.error("Export failed", "Export failed. Please try again or refine your filters.");
            }
        });

        refresh.run();
        contentPane.getChildren().add(root);
    }

    private static TableColumn<DealRecord, String> column(String title,
                                                          java.util.function.Function<DealRecord, String> value) {
        TableColumn<DealRecord, String> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(value.apply(c.getValue())));
        return col;
    }

    private static String dealTypeName(ListingType type) {
        return switch (type) {
            case SELL -> "Sale";
            case LEND -> "Loan";
            case GIVEAWAY -> "Giveaway";
        };
    }

    /** VIEW DETAILS (spec 4.1). Only reads the record and shows it. */
    private void showDealDetails(DealRecord record) {
        try {
            DealDetails d = dealService.getDealDetails(record.offer().getId());
            StringBuilder sb = new StringBuilder();
            sb.append("BUYER\n  ").append(d.buyerFullName()).append(" (@").append(d.buyerUsername()).append(")\n");
            if (d.residenceHall() != null) sb.append("  Residence: ").append(d.residenceHall()).append('\n');
            sb.append("SELLER\n  ").append(d.sellerFullName()).append(" (@").append(d.sellerUsername()).append(")\n");
            if (d.sellerBio() != null) sb.append("  Bio: ").append(d.sellerBio()).append('\n');
            sb.append("ITEM\n  ").append(d.listing().getTitle()).append(" · ").append(d.listing().getCategory())
                    .append(" · ").append(d.listing().getCondition()).append('\n')
                    .append("  Meet-up: ").append(d.listing().getMeetupLocation()).append('\n');
            sb.append("TRANSACTION\n  Type: ").append(dealTypeName(d.dealType())).append('\n');
            if (d.amount() != null) sb.append("  Amount: ").append(Formats.price(d.amount())).append('\n');
            if (d.borrowStart() != null) sb.append("  Borrow period: ")
                    .append(Formats.period(d.borrowStart(), d.borrowEnd())).append('\n');
            sb.append("  Start: ").append(Formats.dateTime(d.startDate())).append('\n')
                    .append("  Completed: ").append(Formats.dateTime(d.completedDate())).append('\n')
                    .append("  Status: ").append(d.state().getDisplayName()).append('\n');
            sb.append("REVIEW\n  ").append(!d.reviewed() ? "Not reviewed yet"
                    : d.reviewHidden() ? "Reviewed (hidden by admin)" : "Reviewed");
            Dialogs.info("Deal details", sb.toString());
        } catch (ServiceException ex) {
            Dialogs.problem(ex);
        }
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
