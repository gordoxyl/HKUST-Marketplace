package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.*;
import com.hkust.marketplace.ui.*;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AdminDashboardController implements ContentHost {
    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;
    private final AdminListingService listingService = new AdminListingService();
    private final AdminUserService userService = new AdminUserService();
    private Runnable listingsReload = () -> {};
    private final AdminDealService dealService = new AdminDealService();
    private List<DealRecord> currentDeals = List.of();
    private final AdminReputationService reputationService = new AdminReputationService();
    private final PhotoService photoService = new PhotoService(DataStore.getInstance());

    private static final double COL_ACTIONS_MIN = 100;
    private static final double COL_RETURN_DATE_MIN = 160;

    /** The All Listings screen as last shown, so that going back to it keeps its filters. */
    private Node allListingsScreen;

    @FXML
    public void initialize() {
        showGreeting();
        currentActiveBtn = btnReview;
        LiveUpdates.setOnDataChanged(() -> contentPane.getScene() != null, this::refreshCurrentScreen);
        showReview();
        NotificationDot.attach(btnNotifications, new NotificationService());
    }

    private void refreshCurrentScreen() {
        if (currentActiveBtn == btnReview) showReview();
        else if (currentActiveBtn == btnAllListings) showAllListings();
        else if (currentActiveBtn == btnUsers) showUsers();
        else if (currentActiveBtn == btnDeals) showDeals();
        else if (currentActiveBtn == btnRatings) showRatings();
    }

    private void showGreeting() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Admin: " + user.getFullName());
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
        NotificationDot.update();
    }

    @Override
    public void show(String title, Node content) {
        pageTitle.setText(title);
        contentPane.getChildren().setAll(content);
        NotificationDot.update();
    }

    // ==========================================
    // Manage Listings (Review)
    // ==========================================
    @FXML
    private void showReview() {
        setActive(btnReview, "Listing Review");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        List<Listing> pending;
        try {
            pending = listingService.findListings(ListingFilter.none().withStatus(ListingStatus.PENDING_REVIEW));
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
            if (!Dialogs.confirm("Approve listing", "Approve '" + l.getTitle() + "'?")) return;
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
            Optional<String> reason = Dialogs.askReason("Reject listing", "Why is '" + l.getTitle() + "' rejected? The seller will see this reason.");
            if (reason.isEmpty()) return;
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

    // ==========================================
    // All Listings
    // ==========================================
    @FXML
    private void showAllListings() {
        setActive(btnAllListings, "All Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().add("All statuses");
        for (ListingStatus s : ListingStatus.values()) statusBox.getItems().add(s.getDisplayName());
        statusBox.getItems().add("Reported");
        statusBox.getSelectionModel().selectFirst();

        ComboBox<Object> categoryBox = new ComboBox<>();
        categoryBox.getItems().add("All categories");
        categoryBox.getItems().addAll(List.of(Category.values()));
        categoryBox.getSelectionModel().selectFirst();

        ComboBox<Object> typeBox = new ComboBox<>();
        typeBox.getItems().add("All types");
        typeBox.getItems().addAll(List.of(ListingType.values()));
        typeBox.getSelectionModel().selectFirst();

        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller...");

        DatePicker from = new DatePicker();
        from.setPromptText("From");
        from.setConverter(Formats.datePickerConverter());
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        to.setConverter(Formats.datePickerConverter());

        Button search = new Button("Search");
        search.getStyleClass().addAll("btn-primary", "btn-sm");
        Button reset = new Button("Reset");
        reset.getStyleClass().addAll("btn-outline", "btn-sm");
        Button add = new Button("+ Add Listing");
        add.getStyleClass().addAll("btn-gold", "btn-sm");

        FlowPane filterBar = new FlowPane(10, 8, statusBox, categoryBox, typeBox, sellerField, from, to, search, reset, add);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getChildren().forEach(n -> ((Region) n).setMinWidth(Region.USE_PREF_SIZE));

        FlowPane results = ListingCard.grid();
        VBox resultArea = new VBox(10);
        root.getChildren().addAll(filterBar, resultArea);

        listingsReload = () -> {
            resultArea.getChildren().clear();
            results.getChildren().clear();

            ListingFilter filter = ListingFilter.none();
            String status = statusBox.getValue();
            if ("Reported".equals(status)) {
                filter = filter.withReportedOnly(true);
            } else {
                for (ListingStatus s : ListingStatus.values()) {
                    if (s.getDisplayName().equals(status)) filter = filter.withStatus(s);
                }
            }
            if (categoryBox.getValue() instanceof Category c) filter = filter.withCategory(c);
            if (typeBox.getValue() instanceof ListingType t) filter = filter.withType(t);
            filter = filter.withSeller(sellerField.getText());
            filter = filter.withDateRange(from.getValue(), to.getValue());

            List<Listing> found;
            try {
                found = listingService.findListings(filter);
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                return;
            }
            if (found.isEmpty()) {
                Label none = new Label("No listings found.");
                none.getStyleClass().add("text-muted");
                resultArea.getChildren().add(none);
                return;
            }
            for (Listing l : found) {
                results.getChildren().add(createManageCard(l));
            }
            resultArea.getChildren().add(results);
        };

        search.setOnAction(e -> listingsReload.run());
        reset.setOnAction(e -> {
            statusBox.getSelectionModel().selectFirst();
            categoryBox.getSelectionModel().selectFirst();
            typeBox.getSelectionModel().selectFirst();
            sellerField.clear();
            from.setValue(null);
            to.setValue(null);
            listingsReload.run();
        });
        add.setOnAction(e -> showListingForm(null));
        listingsReload.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        allListingsScreen = scroll;
        contentPane.getChildren().add(scroll);
    }

    /** Back to All Listings as last shown, with its filters, and with its results brought up to date. */
    private void returnToAllListings() {
        setActive(btnAllListings, "All Listings");
        listingsReload.run();
        show("All Listings", allListingsScreen);
    }

    /**
     * Removes the listing once the admin confirms and gives the reason (Task 3.7); the seller and
     * the buyers with open offers are told. Then runs {@code after}, unless the admin cancelled.
     */
    private void confirmAndRemove(Listing l, Runnable after) {
        if (!Dialogs.confirm("Remove listing", "Remove '" + l.getTitle() + "'? Open offers will be declined and the buyers notified.")) return;
        Optional<String> reason = Dialogs.askReason("Remove listing", "Why is '" + l.getTitle() + "' removed?");
        if (reason.isEmpty()) return;
        try {
            listingService.removeListing(l.getId(), reason.get());
            Dialogs.info("Listing removed", "The seller and affected buyers have been notified.");
        } catch (ServiceException ex) {
            Dialogs.problem(ex);
        }
        after.run();
    }

    private ListingCard createManageCard(Listing l) {
        ListingCard card = new ListingCard(l, UserRole.ADMIN);

        Button view = new Button("View");
        view.getStyleClass().addAll("btn-outline", "btn-sm");
        view.setOnAction(e -> showListingDetails(l));

        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-outline", "btn-sm");
        edit.setOnAction(e -> showListingForm(l));

        Button remove = new Button("Remove");
        remove.getStyleClass().addAll("btn-danger", "btn-sm");
        remove.setOnAction(e -> confirmAndRemove(l, listingsReload));

        card.addActions(view, edit, remove);
        return card;
    }

    private void showListingDetails(Listing l) {
        try {
            ListingDetails d = listingService.getListingDetails(l.getId());
            VBox detailLayout = new VBox(16);
            detailLayout.setPadding(new Insets(10));

            // The full listing details, shown as in every portal (Manage Marketplace Listings 5.1)
            DataStore store = DataStore.getInstance();
            ListingOverview overview = new ListingOverview(ListingSummary.of(d.listing(), UserRole.ADMIN, store), store);

            // Reports
            VBox reportsBox = new VBox(10);
            reportsBox.getStyleClass().add("card");
            reportsBox.setPadding(new Insets(14));
            Label reportsTitle = new Label("Reports (" + d.reports().size() + ")");
            reportsTitle.getStyleClass().add("listing-card-title");
            reportsBox.getChildren().add(reportsTitle);
            if (d.reports().isEmpty()) {
                Label noRep = new Label("No reports.");
                noRep.getStyleClass().add("text-muted");
                reportsBox.getChildren().add(noRep);
            } else {
                for (Report r : d.reports()) {
                    Label repLabel = new Label("• " + r.getReason() + " by @" + r.getBuyerUsername() + ": " + r.getDetails());
                    repLabel.setWrapText(true);
                    reportsBox.getChildren().add(repLabel);
                }
            }

            // Actions
            Button editBtn = new Button("Edit");
            editBtn.getStyleClass().add("btn-primary");
            editBtn.setOnAction(e -> showListingForm(l));

            Button removeBtn = new Button("Remove");
            removeBtn.getStyleClass().add("btn-danger");
            removeBtn.setOnAction(e -> confirmAndRemove(l, this::returnToAllListings));

            HBox actions = new HBox(10, editBtn, removeBtn);

            detailLayout.getChildren().addAll(overview, reportsBox, actions);

            ScrollPane scroll = new ScrollPane(detailLayout);
            scroll.setFitToWidth(true);
            show("Listing Details", new SubScreen("Back to All Listings", this::returnToAllListings, scroll));

        } catch (ServiceException ex) {
            Dialogs.problem(ex);
        }
    }

    private void showListingForm(Listing existing) {
        boolean adding = existing == null;

        VBox form = new VBox(10);
        form.setPadding(new Insets(20));
        form.setMaxWidth(560);
        form.getStyleClass().add("card");

        Label header = new Label(adding ? "Add a listing on behalf of a seller" : "Editing '" + existing.getTitle() + "'");
        header.getStyleClass().add("section-title");

        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller username");

        ComboBox<ListingType> typeBox = new ComboBox<>(FXCollections.observableArrayList(ListingType.values()));
        ComboBox<ItemCondition> conditionBox = new ComboBox<>(FXCollections.observableArrayList(ItemCondition.values()));
        TextField titleField = new TextField(adding ? "" : existing.getTitle());

        TextArea descArea = new TextArea(adding || existing.getDescription() == null ? "" : existing.getDescription());
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        ComboBox<Category> categoryBox = new ComboBox<>(FXCollections.observableArrayList(Category.values()));
        ComboBox<MeetupLocation> locationBox = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));

        TextField priceField = new TextField();
        priceField.setPromptText("Price (HK$)");

        TextField durationField = new TextField();
        ComboBox<String> unitBox = new ComboBox<>(FXCollections.observableArrayList("seconds", "minutes", "hours", "days"));
        unitBox.getSelectionModel().select("days");

        // Photos
        List<String> photoIds = new ArrayList<>();
        if (!adding && existing.getPhotos() != null) {
            photoIds.addAll(existing.getPhotos());
        }
        Label photoStatus = new Label(photoIds.isEmpty() ? "No photos selected" : photoIds.size() + " photo(s) attached");
        Button selectPhotosBtn = new Button("Select Photos");
        selectPhotosBtn.getStyleClass().add("btn-outline");
        selectPhotosBtn.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            List<File> files = chooser.showOpenMultipleDialog(contentPane.getScene().getWindow());
            if (files != null) {
                photoIds.clear();
                for (File f : files) {
                    if (photoIds.size() >= Listing.MAX_PHOTOS) {
                        Dialogs.info("Photos", "Only the first " + Listing.MAX_PHOTOS + " photos were kept.");
                        break;
                    }
                    try {
                        photoIds.add(photoService.importPhoto(f.toPath()));
                    } catch (ServiceException ex) {
                        Dialogs.problem(ex);
                    }
                }
                photoStatus.setText(photoIds.size() + " photo(s) attached.");
            }
        });
        HBox photoBox = new HBox(10, selectPhotosBtn, photoStatus);
        photoBox.setAlignment(Pos.CENTER_LEFT);

        if (!adding) {
            categoryBox.setValue(existing.getCategory());
            conditionBox.setValue(existing.getCondition());
            locationBox.setValue(existing.getMeetupLocation());
            if (existing instanceof SaleListing s) priceField.setText(Amounts.plain(s.getPrice()));
            if (existing instanceof LendListing l) {
                durationField.setPromptText("Leave empty to keep " + Formats.duration(l.getLendingDuration()));
            }
        }

        if (adding) {
            form.getChildren().addAll(new Label("Seller username *"), sellerField, new Label("Listing type *"), typeBox);
            enableFieldsForType(typeBox.getValue(), priceField, durationField, unitBox);
            typeBox.valueProperty().addListener((observable, oldType, newType) ->
                    enableFieldsForType(newType, priceField, durationField, unitBox));
        }

        form.getChildren().addAll(new Label("Title *"), titleField, new Label("Description"), descArea,
                new Label("Category *"), categoryBox, new Label("Condition *"), conditionBox,
                new Label("Meet-up location *"), locationBox);

        boolean showPrice = adding || existing instanceof SaleListing;
        boolean showDuration = adding || existing instanceof LendListing;
        if (showPrice) form.getChildren().addAll(new Label("Price (Sell only)"), priceField);
        HBox durationRow = new HBox(8, durationField, unitBox);
        HBox.setHgrow(durationField, Priority.ALWAYS); // wide enough for its hint, such as "Leave empty to keep 1 day 12 hours"
        if (showDuration) form.getChildren().addAll(new Label("Lending duration (Lend only)"), durationRow);

        form.getChildren().addAll(new Label("Photos (optional)"), photoBox);

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("btn-gold");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");
        cancelBtn.setOnAction(e -> returnToAllListings());

        saveBtn.setOnAction(e -> {
            Double price = null;
            Duration duration = null;
            try {
                if (!priceField.getText().isBlank()) {
                    java.util.OptionalDouble parsed = Amounts.parse(priceField.getText().strip());
                    if (parsed.isEmpty()) throw new IllegalArgumentException("Invalid price");
                    price = parsed.getAsDouble();
                }
                if (!durationField.getText().isBlank()) {
                    long n = Long.parseLong(durationField.getText().strip());
                    duration = switch (unitBox.getValue()) {
                        case "seconds" -> Duration.ofSeconds(n);
                        case "minutes" -> Duration.ofMinutes(n);
                        case "hours" -> Duration.ofHours(n);
                        default -> Duration.ofDays(n);
                    };
                }
            } catch (IllegalArgumentException ex) {
                Dialogs.error("Invalid input", "The price and the duration must be valid numbers.");
                return;
            }

            try {
                if (adding) {
                    listingService.addListing(sellerField.getText(), typeBox.getValue(), titleField.getText(),
                            descArea.getText(), categoryBox.getValue(), conditionBox.getValue(),
                            locationBox.getValue(), price, duration, photoIds);
                    Dialogs.info("Listing added", "The listing is Live and the seller has been notified.");
                } else {
                    listingService.editListing(existing.getId(), titleField.getText(), descArea.getText(),
                            categoryBox.getValue(), conditionBox.getValue(), locationBox.getValue(),
                            price, duration, photoIds);
                    Dialogs.info("Listing updated", "The seller has been notified.");
                }
                returnToAllListings();
            } catch (ServiceException ex) {
                Dialogs.problem(ex); // Keep form intact on error
            }
        });

        form.getChildren().add(new HBox(10, saveBtn, cancelBtn));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        show(adding ? "Add Listing" : "Edit Listing", new SubScreen("Back to All Listings", this::returnToAllListings, scroll));
    }

    /**
     * Add listing takes the fields of the seller's Create Listing (Task 3.7, Task 2.3): a price only
     * for Sell and a lending duration only for Lend. The field that does not apply is disabled and
     * emptied; both are disabled until a type is chosen.
     */
    private static void enableFieldsForType(ListingType type, TextField priceField, TextField durationField,
                                            ComboBox<String> unitBox) {
        boolean sell = type == ListingType.SELL;
        boolean lend = type == ListingType.LEND;
        priceField.setDisable(!sell);
        durationField.setDisable(!lend);
        unitBox.setDisable(!lend);
        if (!sell) priceField.clear();
        if (!lend) durationField.clear();
    }

    // ==========================================
    // Manage Users
    // ==========================================
    @FXML
    private void showUsers() {
        setActive(btnUsers, "Manage Users");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        DatePicker fromDate = new DatePicker();
        fromDate.setPromptText("Reg. From");
        fromDate.setConverter(Formats.datePickerConverter());
        DatePicker toDate = new DatePicker();
        toDate.setPromptText("Reg. To");
        toDate.setConverter(Formats.datePickerConverter());

        TextField searchField = new TextField();
        searchField.setPromptText("Search username or name...");
        searchField.setPrefColumnCount(18); // wide enough for the whole hint, and wider with a bigger font

        ComboBox<String> roleBox = new ComboBox<>();
        roleBox.getItems().addAll("All roles", "Buyer", "Seller");
        roleBox.getSelectionModel().selectFirst();

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("All statuses", "Active", "Deactivated");
        statusBox.getSelectionModel().selectFirst();

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        FlowPane filterBar = new FlowPane(10, 8, searchField, new Label("Role:"), roleBox,
                new Label("Status:"), statusBox, new Label("Reg. From:"), fromDate,
                new Label("Reg. To:"), toDate, searchBtn, resetBtn);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox results = new VBox(10);
        root.getChildren().addAll(filterBar, results);

        Runnable[] refreshRef = new Runnable[1];
        Runnable refresh = () -> {
            results.getChildren().clear();
            UserFilter filter = UserFilter.none();
            if ("Buyer".equals(roleBox.getValue())) filter = filter.withRole(UserRole.BUYER);
            if ("Seller".equals(roleBox.getValue())) filter = filter.withRole(UserRole.SELLER);
            if ("Active".equals(statusBox.getValue())) filter = filter.withActive(true);
            if ("Deactivated".equals(statusBox.getValue())) filter = filter.withActive(false);

            filter = filter.withText(searchField.getText());
            if (fromDate.getValue() != null || toDate.getValue() != null) {
                filter = filter.withRegistrationRange(
                        fromDate.getValue() == null ? null : fromDate.getValue().atStartOfDay(),
                        toDate.getValue() == null ? null : toDate.getValue().atTime(LocalTime.MAX));
            }

            List<User> users;
            try {
                users = userService.findUsers(filter);
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                return;
            }

            if (users.isEmpty()) {
                Label none = new Label("No results found.");
                none.getStyleClass().add("text-muted");
                results.getChildren().add(none);
                return;
            }
            for (User u : users) {
                results.getChildren().add(createUserRow(u, () -> refreshRef[0].run()));
            }
        };

        refreshRef[0] = refresh;
        searchBtn.setOnAction(e -> refresh.run());
        resetBtn.setOnAction(e -> {
            searchField.clear();
            roleBox.getSelectionModel().selectFirst();
            statusBox.getSelectionModel().selectFirst();
            fromDate.setValue(null);
            toDate.setValue(null);
            refresh.run();
        });

        refresh.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createUserRow(User u, Runnable reload) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(u.getFullName() + " (@" + u.getUsername() + ")");
        name.getStyleClass().add("section-title");

        String roleStr = u.getRole().getDisplayName();
        Label meta = new Label(roleStr + " · Registered " + Formats.date(u.getCreatedAt()));
        meta.getStyleClass().add("text-muted");
        info.getChildren().addAll(name, meta);

        Label status = new Label(u.isActive() ? "Active" : "Deactivated");
        status.getStyleClass().addAll("badge", u.isActive() ? "badge-live" : "badge-rejected");

        row.getChildren().addAll(info, status);

        // View
        Button view = new Button("View");
        view.getStyleClass().addAll("btn-outline", "btn-sm");
        view.setOnAction(e -> showUserDetails(u.getUsername()));
        row.getChildren().add(view);

        // Edit
        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-outline", "btn-sm");
        edit.setOnAction(e -> showUserForm(u));
        row.getChildren().add(edit);

        // Toggle Active
        if (u.isActive()) {
            Button deactivate = new Button("Deactivate");
            deactivate.getStyleClass().addAll("btn-danger", "btn-sm");
            deactivate.setOnAction(e -> {
                Optional<String> reason = Dialogs.askReason("Deactivate account", "Why is @" + u.getUsername() + " deactivated? The user will see this reason.");
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

        // Delete
        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("btn-danger", "btn-sm");
        delete.setOnAction(e -> {
            if (!Dialogs.confirm("Delete user", "Permanently delete @" + u.getUsername() + "? Their chats are archived in the Audit Log and the username can never be used again.")) return;
            Optional<String> reason = Dialogs.askReason("Delete account", "Why is @" + u.getUsername() + " deleted?");
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

    private void showUserDetails(String username) {
        try {
            UserDetails details = userService.getUserDetails(username);
            VBox layout = new VBox(16);
            layout.setPadding(new Insets(10));

            Label title = new Label("User Details: @" + username);
            title.getStyleClass().add("section-title");

            VBox personal = new VBox(6);
            personal.getStyleClass().add("card");
            personal.setPadding(new Insets(14));
            personal.getChildren().addAll(
                    new Label("Full name: " + details.user().getFullName()),
                    new Label("Role: " + details.user().getRole().getDisplayName()),
                    new Label("Status: " + (details.user().isActive() ? "Active" : "Deactivated")),
                    new Label("Registered: " + Formats.date(details.user().getCreatedAt()))
            );
            if (details.residenceHall() != null) personal.getChildren().add(new Label("Residence: " + details.residenceHall()));
            if (details.bio() != null) personal.getChildren().add(new Label("Bio: " + details.bio()));
            if (!details.preferredLocations().isEmpty()) {
                String locs = details.preferredLocations().stream().map(MeetupLocation::getDisplayName).collect(Collectors.joining(", "));
                personal.getChildren().add(new Label("Locations: " + locs));
            }

            VBox stats = new VBox(6);
            stats.getStyleClass().add("card");
            stats.setPadding(new Insets(14));
            stats.getChildren().addAll(
                    new Label("Rating: " + Formats.averageRating(details.rating())),
                    new Label("Active listings: " + details.activeListings().size()),
                    new Label("Completed deals: " + details.completedDeals().size()),
                    new Label("Open offers: " + details.pendingOffers().size())
            );

            layout.getChildren().addAll(title, personal, stats);

            ScrollPane scroll = new ScrollPane(layout);
            scroll.setFitToWidth(true);
            show("User Details", new SubScreen("Back to Manage Users", this::showUsers, scroll));

        } catch (ServiceException ex) {
            Dialogs.problem(ex);
        }
    }

    private void showUserForm(User u) {
        VBox form = new VBox(10);
        form.setMaxWidth(560);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));

        Label header = new Label("Editing @" + u.getUsername());
        header.getStyleClass().add("section-title");
        Label sub = new Label("The username and role cannot be changed.");
        sub.getStyleClass().add("text-muted");

        TextField nameField = new TextField(u.getFullName());
        nameField.setPromptText("Full name (required)");

        VBox content = new VBox(8, new Label("Full name"), nameField);

        TextArea bioArea = null;
        if (u instanceof Seller seller) {
            bioArea = new TextArea(seller.getBio() != null ? seller.getBio() : "");
            bioArea.setPromptText("Bio (optional)");
            bioArea.setPrefRowCount(3);
            content.getChildren().addAll(new Label("Bio"), bioArea);
        }

        TextArea finalBioArea = bioArea;

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("btn-gold");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");
        cancelBtn.setOnAction(e -> showUsers());

        saveBtn.setOnAction(e -> {
            String newBio = (finalBioArea != null) ? finalBioArea.getText() : null;
            List<MeetupLocation> existingLocations = (u instanceof Seller s) ? s.getPreferredMeetupLocations() : null;
            try {
                userService.editUser(u.getUsername(), nameField.getText(), newBio, existingLocations);
                Dialogs.info("User updated", "@" + u.getUsername() + " has been updated.");
                showUsers();
            } catch (ServiceException ex) {
                Dialogs.problem(ex); // Keep form state
            }
        });

        form.getChildren().addAll(header, sub, content, new HBox(10, saveBtn, cancelBtn));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        show("Edit User", new SubScreen("Back to Manage Users", this::showUsers, scroll));
    }

    // ==========================================
    // All Deals
    // ==========================================
    @FXML
    private void showDeals() {
        setActive(btnDeals, "All Deals");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        ComboBox<Object> statusBox = new ComboBox<>();
        statusBox.getItems().add("All statuses");
        statusBox.getItems().addAll(List.of(DealState.values()));
        statusBox.getSelectionModel().selectFirst();

        ComboBox<Object> typeBox = new ComboBox<>();
        typeBox.getItems().add("All types");
        typeBox.getItems().addAll(List.of(ListingType.values()));
        typeBox.getSelectionModel().selectFirst();

        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller...");
        TextField buyerField = new TextField();
        buyerField.setPromptText("Buyer...");

        DatePicker from = new DatePicker();
        from.setPromptText("From");
        from.setConverter(Formats.datePickerConverter());
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        to.setConverter(Formats.datePickerConverter());

        Button search = new Button("Search");
        search.getStyleClass().addAll("btn-primary", "btn-sm");
        Button reset = new Button("Reset");
        reset.getStyleClass().addAll("btn-outline", "btn-sm");
        Button export = new Button("Export CSV");
        export.getStyleClass().addAll("btn-gold", "btn-sm");

        FlowPane filterBar = new FlowPane(10, 8, statusBox, typeBox, sellerField, buyerField, from, to, search, reset, export);
        filterBar.getChildren().forEach(n -> ((Region) n).setMinWidth(Region.USE_PREF_SIZE));
        filterBar.setAlignment(Pos.CENTER_LEFT);

        TableView<DealRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Item title", r -> r.listing().getTitle()));
        table.getColumns().add(column("Buyer", DealRecord::buyerUsername));
        table.getColumns().add(column("Seller", DealRecord::sellerUsername));
        table.getColumns().add(column("Deal type", r -> r.dealType().getDisplayName()));
        table.getColumns().add(column("Start date", r -> Formats.dateTime(r.startDate())));

        TableColumn<DealRecord, String> returnCol = column("Return/Complete date", r -> Formats.dateTime(r.completedDate()));
        returnCol.setMinWidth(COL_RETURN_DATE_MIN);
        table.getColumns().add(returnCol);
        table.getColumns().add(column("Status", r -> r.state().getDisplayName()));

        TableColumn<DealRecord, Void> actions = new TableColumn<>("Actions");
        actions.setMinWidth(COL_ACTIONS_MIN);
        actions.setCellFactory(col -> new TableCell<>() {
            private final Button view = new Button("View Details");
            {
                view.getStyleClass().addAll("btn-outline", "btn-sm");
                view.setMinWidth(Region.USE_PREF_SIZE);
                view.setOnAction(e -> showDealDetails(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : view);
            }
        });
        table.getColumns().add(actions);

        Label none = new Label("No deals found.");
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
        search.setOnAction(e -> refresh.run());
        reset.setOnAction(e -> {
            statusBox.getSelectionModel().selectFirst();
            typeBox.getSelectionModel().selectFirst();
            sellerField.clear();
            buyerField.clear();
            from.setValue(null);
            to.setValue(null);
            refresh.run();
        });

        export.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save deals as CSV");
            chooser.setInitialFileName("deals.csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
            File file = chooser.showSaveDialog(SceneManager.getPrimaryStage());
            if (file == null) return;
            try {
                String csv = dealService.exportCsv(currentDeals);
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

    private static TableColumn<DealRecord, String> column(String title, java.util.function.Function<DealRecord, String> value) {
        TableColumn<DealRecord, String> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(value.apply(c.getValue())));
        return col;
    }

    private void showDealDetails(DealRecord record) {
        try {
            DealDetails d = dealService.getDealDetails(record.offer().getId());

            VBox layout = new VBox(16);
            layout.setPadding(new Insets(10));

            VBox buyerBox = createDetailBox("BUYER",
                    d.buyerFullName() + " (@" + d.buyerUsername() + ")" +
                            (d.residenceHall() != null ? "\nResidence: " + d.residenceHall() : ""));

            VBox sellerBox = createDetailBox("SELLER",
                    d.sellerFullName() + " (@" + d.sellerUsername() + ")" +
                            (d.sellerBio() != null ? "\nBio: " + d.sellerBio() : ""));

            VBox itemBox = createDetailBox("ITEM",
                    d.listing().getTitle() + " · " + d.listing().getCategory() + " · " + d.listing().getCondition() +
                            "\nMeet-up: " + d.listing().getMeetupLocation());

            String txDetails = "Type: " + d.dealType().getDisplayName() + "\n" +
                    (d.amount() != null ? "Amount: " + Formats.price(d.amount()) + "\n" : "") +
                    (d.borrowStart() != null ? "Borrow period: " + Formats.period(d.borrowStart(), d.borrowEnd()) + "\n" : "") +
                    "Start: " + Formats.dateTime(d.startDate()) + "\n" +
                    "Completed: " + Formats.dateTime(d.completedDate()) + "\n" +
                    "Status: " + d.state().getDisplayName();
            VBox txBox = createDetailBox("TRANSACTION", txDetails);

            String review = !d.reviewed() ? "Not reviewed yet" : d.reviewHidden() ? "Reviewed (hidden by admin)" : "Reviewed";
            VBox reviewBox = createDetailBox("REVIEW", review);

            layout.getChildren().addAll(buyerBox, sellerBox, itemBox, txBox, reviewBox);

            ScrollPane scroll = new ScrollPane(layout);
            scroll.setFitToWidth(true);
            show("Deal Details", new SubScreen("Back to All Deals", this::showDeals, scroll));

        } catch (ServiceException ex) {
            Dialogs.problem(ex);
        }
    }

    private VBox createDetailBox(String title, String content) {
        VBox box = new VBox(4);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(14));
        Label t = new Label(title);
        t.getStyleClass().add("listing-card-title");
        Label c = new Label(content);
        c.setWrapText(true);
        box.getChildren().addAll(t, c);
        return box;
    }

    // ==========================================
    // Seller Ratings
    // ==========================================
    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        TextField sellerField = new TextField();
        sellerField.setPromptText("Filter by seller...");
        Button search = new Button("Search");
        search.getStyleClass().addAll("btn-primary", "btn-sm");
        Button reset = new Button("Reset");
        reset.getStyleClass().addAll("btn-outline", "btn-sm");

        FlowPane filterBar = new FlowPane(10, 8, sellerField, search, reset);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox results = new VBox(10);
        root.getChildren().addAll(filterBar, results);

        Runnable refresh = () -> {
            results.getChildren().clear();
            List<SellerRating> sellers;
            try {
                sellers = reputationService.findSellerRatings(sellerField.getText());
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                return;
            }
            if (sellers.isEmpty()) {
                Label none = new Label("No results found.");
                none.getStyleClass().add("text-muted");
                results.getChildren().add(none);
                return;
            }
            for (SellerRating s : sellers) {
                results.getChildren().add(createSellerRow(s));
            }
        };
        search.setOnAction(e -> refresh.run());
        reset.setOnAction(e -> {
            sellerField.clear();
            refresh.run();
        });
        refresh.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createSellerRow(SellerRating s) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(s.fullName() + " (@" + s.username() + ")");
        name.getStyleClass().add("section-title");
        Label meta = new Label(Formats.averageRating(s.rating())
                + " · " + s.rating().count() + " review(s) · " + s.completedDeals() + " completed deal(s)");
        meta.getStyleClass().add("text-muted");
        info.getChildren().addAll(name, meta);

        Button view = new Button("View Reviews");
        view.getStyleClass().addAll("btn-outline", "btn-sm");
        view.setOnAction(e -> showSellerReviews(s));

        row.getChildren().addAll(info, view);
        return row;
    }

    private void showSellerReviews(SellerRating seller) {
        setActive(btnRatings, "Reviews of @" + seller.username());
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        ComboBox<String> scoreBox = new ComboBox<>(FXCollections.observableArrayList(
                "All scores", "5 stars", "4 stars", "3 stars", "2 stars", "1 star"));
        scoreBox.getSelectionModel().selectFirst();

        DatePicker from = new DatePicker();
        from.setPromptText("From");
        from.setConverter(Formats.datePickerConverter());
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        to.setConverter(Formats.datePickerConverter());

        Button search = new Button("Search");
        search.getStyleClass().addAll("btn-primary", "btn-sm");
        Button reset = new Button("Reset");
        reset.getStyleClass().addAll("btn-outline", "btn-sm");

        FlowPane filterBar = new FlowPane(10, 8, scoreBox, from, to, search, reset);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox results = new VBox(10);
        root.getChildren().addAll(filterBar, results);

        Runnable[] refreshRef = new Runnable[1];
        Runnable refresh = () -> {
            results.getChildren().clear();
            ReviewFilter filter = ReviewFilter.none().withSeller(seller.username());
            if (scoreBox.getSelectionModel().getSelectedIndex() > 0) {
                filter = filter.withRating(5 - (scoreBox.getSelectionModel().getSelectedIndex() - 1));
            }
            filter = filter.withDateRange(
                    from.getValue() == null ? null : from.getValue().atStartOfDay(),
                    to.getValue() == null ? null : to.getValue().atTime(LocalTime.MAX));

            List<Review> reviews;
            try {
                reviews = reputationService.findReviewsForSeller(seller.username(), filter);
            } catch (ServiceException ex) {
                Dialogs.problem(ex);
                return;
            }

            DataStore store = DataStore.getInstance();
            if (reviews.isEmpty()) {
                Label none = new Label("No results found.");
                none.getStyleClass().add("text-muted");
                results.getChildren().add(none);
                return;
            }
            for (Review r : reviews) {
                results.getChildren().add(createReviewRow(r, store, () -> refreshRef[0].run()));
            }
        };
        refreshRef[0] = refresh;

        search.setOnAction(e -> refresh.run());
        reset.setOnAction(e -> {
            scoreBox.getSelectionModel().selectFirst();
            from.setValue(null);
            to.setValue(null);
            refresh.run();
        });
        refresh.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        show("Reviews of @" + seller.username(), new SubScreen("Back to Seller Ratings", this::showRatings, scroll));
    }

    private HBox createReviewRow(Review r, DataStore store, Runnable reload) {
        Offer deal = store.findOffer(r.getOfferId());
        String item = store.getListingOf(deal).getTitle();

        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label head = new Label(Formats.stars(r.getRating()) + "  by " + store.getReviewerName(r)
                + " · " + item + " · deal " + Formats.date(deal.getCompletedDate()));
        head.getStyleClass().add("section-title");
        Label comment = new Label(r.getComment() == null ? Formats.NONE : r.getComment());
        comment.setWrapText(true);
        comment.getStyleClass().add("text-muted");
        info.getChildren().addAll(head, comment);
        ReviewText.reply(r).ifPresent(reply -> {
            Label replyLabel = new Label(reply); // Manage Reviews 3.1.5: the reply shows wherever the review does
            replyLabel.setWrapText(true);
            replyLabel.setMinHeight(Region.USE_PREF_SIZE); // a long reply wraps instead of being cut short
            replyLabel.getStyleClass().add("listing-card-line");
            info.getChildren().add(replyLabel);
        });

        if (r.isFlagged()) {
            Label flagged = new Label("Flagged by seller");
            flagged.getStyleClass().addAll("badge", "badge-pending");
            info.getChildren().add(flagged);
        }

        if (r.isHidden()) {
            Label hidden = new Label("Hidden");
            hidden.getStyleClass().addAll("badge", "badge-rejected");
            info.getChildren().add(hidden);
        }
        row.getChildren().add(info);

        if (!r.isHidden()) {
            Button hide = new Button("Hide");
            hide.getStyleClass().addAll("btn-danger", "btn-sm");
            hide.setOnAction(e -> {
                Optional<String> reason = Dialogs.askReason("Hide review", "Why is this review hidden? Both the buyer and the seller will be told.");
                if (reason.isEmpty()) return;
                try {
                    reputationService.hideReview(r.getId(), reason.get());
                    Dialogs.info("Review hidden", "The buyer and the seller have been notified.");
                } catch (ServiceException ex) {
                    Dialogs.problem(ex);
                }
                reload.run();
            });
            row.getChildren().add(hide);
        }
        return row;
    }

    // ==========================================
    // Other
    // ==========================================
    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        show("Notification Board", new NotificationBoard(new NotificationService()));
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "Manage Profile");
        show("Manage Profile", new ProfileView(new ProfileService(), this::showGreeting));
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}