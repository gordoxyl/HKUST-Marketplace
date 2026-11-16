package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.CreateListingService;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.PhotoService;
import com.hkust.marketplace.services.ProfileService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.ChatList;
import com.hkust.marketplace.ui.ContentHost;
import com.hkust.marketplace.ui.Dialogs;
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
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SellerDashboardController implements ContentHost {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnCreate, btnStats, btnReviews, btnChats, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        showGreeting();
        currentActiveBtn = btnListings;
        showMyListings();
    }

    /** Greets the logged-in user by name. Runs again after a profile change, so a new name shows at once. */
    private void showGreeting() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Hello, " + user.getFullName());
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
            FlowPane grid = ListingCard.grid(); // shared listing card
            for (Listing l : myListings) {
                grid.getChildren().add(createListingCard(l));
            }
            root.getChildren().add(grid);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private ListingCard createListingCard(Listing l) {
        // Title, category, type and status (Task 2.4) on the shared listing card
        ListingCard card = new ListingCard(l, UserRole.SELLER);

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        editBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Edit form would open here.\n(Demo placeholder)").showAndWait());

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        delBtn.setOnAction(e -> {
            if (l.isLocked()) { // Manage Listings A2
                new Alert(Alert.AlertType.WARNING, "This listing can no longer be edited or deleted.").showAndWait();
                return;
            }
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Delete this listing?");
            conf.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    // Manage Listings step 3.2.4: a deleted listing keeps its record with status Removed
                    DataStore.getInstance().transaction(l::withdraw);
                    showMyListings();
                }
            });
        });

        card.addActions(editBtn, delBtn);
        return card;
    }

    @FXML
    private void showCreateListing() {
        setActive(btnCreate, "Create New Listing");
        contentPane.getChildren().clear();

        VBox form = new VBox(10);
        form.setMaxWidth(560);
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
        descArea.setPromptText("Description *");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        TextField priceField = new TextField();
        priceField.setPromptText("Price (HK$), required for Sell");
        priceField.setDisable(true);

        TextField durationField = new TextField();
        durationField.setPromptText("e.g. 7");
        durationField.setDisable(true);
        ComboBox<String> durationUnit = new ComboBox<>(FXCollections.observableArrayList(
                "seconds", "minutes", "hours", "days"));
        durationUnit.getSelectionModel().select("days");
        durationUnit.setDisable(true);

        ComboBox<MeetupLocation> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
        meetupCombo.setPromptText("Meet-up location *");
        meetupCombo.setMaxWidth(Double.MAX_VALUE);

        List<File> chosenPhotos = new ArrayList<>();
        Label photoLabel = new Label("No photos chosen (optional, up to 6)");
        photoLabel.setWrapText(true);
        photoLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Button choosePhotos = new Button("Add Photos…");
        choosePhotos.getStyleClass().addAll("btn-outline", "btn-sm");
        choosePhotos.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose listing photos");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images (" + PhotoService.SUPPORTED_FORMATS + ")",
                    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            List<File> files = chooser.showOpenMultipleDialog(
                    contentPane.getScene() == null ? null : contentPane.getScene().getWindow());
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (chosenPhotos.size() >= Listing.MAX_PHOTOS) {
                    Dialogs.error("Too many photos",
                            "A listing can have at most " + Listing.MAX_PHOTOS + " photos.");
                    break;
                }
                chosenPhotos.add(file);
            }
            showChosenPhotos(photoLabel, chosenPhotos);
        });

        // The type decides which extra field applies (Create Listings steps 3-4).
        typeCombo.valueProperty().addListener((observable, oldType, newType) -> {
            boolean sell = newType == ListingType.SELL;
            boolean lend = newType == ListingType.LEND;
            priceField.setDisable(!sell);
            durationField.setDisable(!lend);
            durationUnit.setDisable(!lend);
            if (!sell) {
                priceField.clear();
            }
            if (!lend) {
                durationField.clear();
            }
        });

        Label hint = new Label("Photos are optional; the first one is used as the thumbnail.");
        hint.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        Button submitBtn = new Button("Submit for Review");
        submitBtn.getStyleClass().add("btn-gold");
        submitBtn.setOnAction(e -> submitListing(typeCombo.getValue(), nameField.getText(),
                catCombo.getValue(), condCombo.getValue(), descArea.getText(), meetupCombo.getValue(),
                priceField.getText(), durationField.getText(), durationUnit.getValue(), chosenPhotos));

        form.getChildren().addAll(title,
                new Label("Listing Type *"), typeCombo,
                new Label("Item Name *"), nameField,
                new Label("Category *"), catCombo,
                new Label("Condition *"), condCombo,
                new Label("Description *"), descArea,
                new Label("Price (Sell only)"), priceField,
                new Label("Lending Duration (Lend only)"), new HBox(8, durationField, durationUnit),
                new Label("Meet-up Location *"), meetupCombo,
                new Label("Photos (optional)"), new HBox(8, choosePhotos), photoLabel, hint,
                submitBtn);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /**
     * Reads the form and asks the service to post the listing (Task 2.3). All rules are in
     * {@link CreateListingService}; this method only converts the typed values and shows the
     * service's message when it refuses.
     */
    private void submitListing(ListingType type, String name, Category category, ItemCondition condition,
                               String description, MeetupLocation location, String priceText,
                               String durationText, String durationUnit, List<File> chosenPhotos) {
        if (type == null) {
            Dialogs.error("Missing listing type", "Choose Sell, Lend or Give Away first.");
            return;
        }
        List<Path> photoFiles = chosenPhotos.stream().map(File::toPath).toList();
        CreateListingService service = new CreateListingService();
        Listing created;
        try {
            switch (type) {
                case SELL -> created = service.createSale(name, category, condition, description, location,
                        parsePrice(priceText), photoFiles);
                case LEND -> created = service.createLend(name, category, condition, description, location,
                        parseDuration(durationText, durationUnit), photoFiles);
                default -> created = service.createGiveaway(name, category, condition, description,
                        location, photoFiles);
            }
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        Dialogs.info("Listing submitted",
                "'" + created.getTitle() + "' is now Pending Review. The admin has been notified.");
        showMyListings();
    }

    /** A2: a typed price, turned into a number; a non-number is reported like any other rule. */
    private static double parsePrice(String priceText) {
        try {
            return Double.parseDouble(priceText.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("The price must be a positive number, such as 180 or 99.50.");
        }
    }

    /** A3: a typed duration in the chosen unit; the service checks the 5-second minimum. */
    private static Duration parseDuration(String durationText, String unit) {
        long amount;
        try {
            amount = Long.parseLong(durationText.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("The lending duration must be a whole number of seconds, "
                    + "minutes, hours or days.");
        }
        if (amount <= 0) {
            throw new ServiceException("The lending duration must be at least 5 seconds.");
        }
        try {
            return switch (unit == null ? "days" : unit) {
                case "seconds" -> Duration.ofSeconds(amount);
                case "minutes" -> Duration.ofMinutes(amount);
                case "hours" -> Duration.ofHours(amount);
                default -> Duration.ofDays(amount);
            };
        } catch (ArithmeticException e) {
            throw new ServiceException("The lending duration is too long.");
        }
    }

    /** A4: shows how many photos were chosen and their names. */
    private static void showChosenPhotos(Label label, List<File> files) {
        if (files.isEmpty()) {
            label.setText("No photos chosen (optional, up to 6)");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (File file : files) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(file.getName());
        }
        label.setText(files.size() + (files.size() == 1 ? " photo: " : " photos: ") + names);
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
    private void showChats() {
        setActive(btnChats, "Chats");
        show("Chats", new ChatList(new ChatService(), this)); // UC6, shared chat screens
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notifications");
        show("Notifications", new NotificationBoard(new NotificationService())); // UC4, shared board
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        show("My Profile", new ProfileView(new ProfileService(), this::showGreeting)); // UC3, shared profile screen
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
