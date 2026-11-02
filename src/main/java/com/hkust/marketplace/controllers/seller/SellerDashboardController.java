package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SellerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnCreate, btnStats, btnReviews, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Hello, " + user.getFullName());
        currentActiveBtn = btnListings;
        showMyListings();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showMyListings() {
        setActive(btnListings, "My Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Listing> myListings = DataStore.getInstance()
                .getListingsBySeller(user != null ? user.getUsername() : "");

        if (myListings.isEmpty()) {
            Label empty = new Label("You have not posted any listings yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            for (Listing l : myListings) {
                root.getChildren().add(createListingRow(l));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createListingRow(Listing l) {
        HBox row = new HBox(16);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(l.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label meta = new Label(l.getCategory() + " · " + l.getType().getDisplayName() + " · " + l.getMeetupLocation());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        info.getChildren().addAll(title, meta);

        Label status = new Label(l.getStatus().name().replace('_', ' '));
        status.getStyleClass().add("badge");
        switch (l.getStatus()) {
            case LIVE -> status.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> status.getStyleClass().add("badge-pending");
            case REJECTED -> status.getStyleClass().add("badge-rejected");
            default -> status.getStyleClass().add("badge-sold");
        }

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        editBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Edit form would open here.\n(Demo placeholder)").showAndWait());

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        delBtn.setOnAction(e -> {
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Delete this listing?");
            conf.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    // Manage Listings step 3.2.4: a deleted listing keeps its record with status Removed
                    DataStore.getInstance().transaction(() -> l.setStatus(ListingStatus.REMOVED));
                    showMyListings();
                }
            });
        });

        row.getChildren().addAll(info, status, editBtn, delBtn);
        return row;
    }

    @FXML
    private void showCreateListing() {
        setActive(btnCreate, "Create New Listing");
        contentPane.getChildren().clear();

        VBox form = new VBox(12);
        form.setMaxWidth(520);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));

        Label title = new Label("Post a new item");
        title.getStyleClass().add("section-title");

        ComboBox<ListingType> typeCombo = new ComboBox<>(FXCollections.observableArrayList(ListingType.values()));
        typeCombo.setPromptText("Listing type *");
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        TextField nameField = new TextField();
        nameField.setPromptText("Item name *");
        ComboBox<Category> catCombo = new ComboBox<>(FXCollections.observableArrayList(Category.values()));
        catCombo.setPromptText("Category *");
        catCombo.setMaxWidth(Double.MAX_VALUE);
        ComboBox<ItemCondition> condCombo = new ComboBox<>(FXCollections.observableArrayList(ItemCondition.values()));
        condCombo.setPromptText("Condition *");
        condCombo.setMaxWidth(Double.MAX_VALUE);
        TextArea descArea = new TextArea();
        descArea.setPromptText("Description");
        descArea.setPrefRowCount(3);

        TextField priceField = new TextField();
        priceField.setPromptText("Price (HK$) – required for Sell");
        TextField daysField = new TextField();
        daysField.setPromptText("Lending duration (days) – required for Lend");

        ComboBox<MeetupLocation> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
        meetupCombo.setPromptText("Meet-up location *");
        meetupCombo.setMaxWidth(Double.MAX_VALUE);

        Label photoNote = new Label("📷 Photos: up to 6 (UI placeholder – file chooser not wired in demo)");
        photoNote.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");

        Button submitBtn = new Button("Submit for Review");
        submitBtn.getStyleClass().add("btn-gold");
        submitBtn.setOnAction(e -> {
            if (typeCombo.getValue() == null || nameField.getText().isBlank() ||
                    catCombo.getValue() == null || condCombo.getValue() == null ||
                    meetupCombo.getValue() == null) {
                new Alert(Alert.AlertType.WARNING, "Please fill all required fields.").showAndWait();
                return;
            }
            User user = SessionManager.getCurrentUser();
            String itemName = nameField.getText().trim();
            Listing listing;
            switch (typeCombo.getValue()) {
                case SELL -> {
                    try {
                        listing = new SaleListing(user.getUsername(), itemName, catCombo.getValue(), condCombo.getValue(),
                                descArea.getText(), meetupCombo.getValue(), Double.parseDouble(priceField.getText()));
                    } catch (IllegalArgumentException ex) { // also catches NumberFormatException
                        new Alert(Alert.AlertType.WARNING, "Valid price required for Sell listings.").showAndWait();
                        return;
                    }
                }
                case LEND -> {
                    try {
                        listing = new LendListing(user.getUsername(), itemName, catCombo.getValue(), condCombo.getValue(),
                                descArea.getText(), meetupCombo.getValue(), Duration.ofDays(Integer.parseInt(daysField.getText())));
                    } catch (IllegalArgumentException ex) { // also catches NumberFormatException
                        new Alert(Alert.AlertType.WARNING, "Valid lending days required.").showAndWait();
                        return;
                    }
                }
                default -> listing = new GiveawayListing(user.getUsername(), itemName, catCombo.getValue(),
                        condCombo.getValue(), descArea.getText(), meetupCombo.getValue());
            }
            DataStore.getInstance().addListing(listing);
            // Notify admin
            for (User admin : DataStore.getInstance().getUsersByRole(UserRole.ADMIN)) {
                DataStore.getInstance().addNotification(new Notification(admin.getUsername(),
                        NotificationCategory.LISTING_SUBMITTED,
                        user.getFullName() + " submitted '" + listing.getTitle() + "' for review."));
            }
            new Alert(Alert.AlertType.INFORMATION,
                    "Listing submitted! Status: Pending Review.\nAdmin will review shortly.\n(Demo – in-memory only)").showAndWait();
            showMyListings();
        });

        form.getChildren().addAll(title,
                new Label("Listing Type *"), typeCombo,
                new Label("Item Name *"), nameField,
                new Label("Category *"), catCombo,
                new Label("Condition *"), condCombo,
                new Label("Description"), descArea,
                new Label("Price (Sell only)"), priceField,
                new Label("Lending Days (Lend only)"), daysField,
                new Label("Meet-up Location *"), meetupCombo,
                photoNote, submitBtn);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showStats() {
        setActive(btnStats, "Seller Stats");
        contentPane.getChildren().clear();

        VBox root = new VBox(20);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Listing> myListings = DataStore.getInstance()
                .getListingsBySeller(user != null ? user.getUsername() : "");

        // Metric cards
        HBox metrics = new HBox(16);
        metrics.getChildren().addAll(
                metricCard("Listings", String.valueOf(myListings.size()), "#003974"),
                metricCard("Avg Rating", String.format("%.1f", user != null
                        ? DataStore.getInstance().getAverageRating(user.getUsername()).orElse(0) : 0), "#A68137"),
                metricCard("Reviews", String.valueOf(user != null
                        ? DataStore.getInstance().getRatingCount(user.getUsername()) : 0), "#2e7d32"),
                metricCard("Views (demo)", "128", "#1565c0")
        );
        root.getChildren().add(metrics);

        // Bar chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Listings by Category (demo data)");
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(280);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Textbooks", 3));
        series.getData().add(new XYChart.Data<>("Electronics", 5));
        series.getData().add(new XYChart.Data<>("Furniture", 2));
        series.getData().add(new XYChart.Data<>("Others", 1));
        barChart.getData().add(series);

        // Pie chart
        PieChart pie = new PieChart();
        pie.setTitle("Listing Status Distribution");
        pie.setPrefHeight(280);
        pie.getData().add(new PieChart.Data("Live", 4));
        pie.getData().add(new PieChart.Data("Pending", 1));
        pie.getData().add(new PieChart.Data("Sold", 2));

        HBox charts = new HBox(20, barChart, pie);
        root.getChildren().add(charts);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox metricCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(140);
        card.setPadding(new Insets(16));
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        card.getChildren().addAll(val, lbl);
        return card;
    }

    @FXML
    private void showReviews() {
        setActive(btnReviews, "Reviews & Feedback");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Review> reviews = DataStore.getInstance()
                .getReviewsForSeller(user != null ? user.getUsername() : "");

        if (reviews.isEmpty()) {
            root.getChildren().add(new Label("No reviews yet."));
        } else {
            for (Review r : reviews) {
                VBox item = new VBox(6);
                item.getStyleClass().add("card");
                Label stars = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()));
                stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
                Label comment = new Label(r.getComment());
                comment.setWrapText(true);
                DataStore store = DataStore.getInstance();
                Label meta = new Label("by @" + store.getBuyerUsername(r) + " · "
                        + store.getListingOf(store.findOffer(r.getOfferId())).getTitle());
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
                HBox actions = new HBox(8);
                Button reply = new Button("Reply");
                reply.getStyleClass().addAll("btn-outline", "btn-sm");
                reply.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Reply sent to buyer (demo).").showAndWait());
                Button flag = new Button("Flag");
                flag.getStyleClass().addAll("btn-danger", "btn-sm");
                flag.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Review flagged for admin (demo).").showAndWait());
                actions.getChildren().addAll(reply, flag);
                item.getChildren().addAll(stars, comment, meta, actions);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notifications");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User user = SessionManager.getCurrentUser();
        List<Notification> notifs = DataStore.getInstance()
                .getNotificationsFor(user != null ? user.getUsername() : "");
        if (notifs.isEmpty()) {
            root.getChildren().add(new Label("No notifications."));
        } else {
            for (Notification n : notifs) {
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                Label t = new Label(n.getCategory().getDisplayName());
                t.setStyle("-fx-font-weight: bold;");
                Label m = new Label(n.getContent());
                m.setWrapText(true);
                m.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
                Label meta = new Label(n.getCategory() + " · " +
                        n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(t, m, meta);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();
        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(12);
        form.setMaxWidth(420);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        form.getChildren().add(new Label("Edit Seller Profile") {{ getStyleClass().add("section-title"); }});

        TextField name = new TextField(user != null ? user.getFullName() : "");
        PasswordField pass = new PasswordField();
        pass.setPromptText("New password (optional)");
        Seller seller = user instanceof Seller s ? s : null;
        TextArea bio = new TextArea(seller != null && seller.getBio() != null ? seller.getBio() : "");
        bio.setPrefRowCount(3);
        ComboBox<MeetupLocation> meetup = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
        if (seller != null && !seller.getPreferredMeetupLocations().isEmpty()) {
            meetup.setValue(seller.getPreferredMeetupLocations().get(0));
        }
        meetup.setMaxWidth(Double.MAX_VALUE);

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-gold");
        save.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Profile saved (demo).").showAndWait());

        form.getChildren().addAll(
                new Label("Full Name"), name,
                new Label("New Password"), pass,
                new Label("Bio"), bio,
                new Label("Preferred Meet-up"), meetup,
                save);
        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
