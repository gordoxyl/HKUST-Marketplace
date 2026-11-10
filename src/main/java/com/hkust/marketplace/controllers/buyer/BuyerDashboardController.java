package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.ProfileService;
import com.hkust.marketplace.ui.ChatList;
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

public class BuyerDashboardController implements ContentHost {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnHistory, btnFavourites, btnChats, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        showGreeting();
        currentActiveBtn = btnBrowse;
        showBrowse();
    }

    /** Greets the logged-in user by name. Runs again after a profile change, so a new name shows at once. */
    private void showGreeting() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Hello, " + user.getFullName());
        }
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        }
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
    private void showBrowse() {
        setActive(btnBrowse, "Browse Marketplace");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        // Filter bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> categoryFilter = new ComboBox<>(FXCollections.observableArrayList("All Categories"));
        for (Category c : Category.values()) categoryFilter.getItems().add(c.getDisplayName());
        categoryFilter.getSelectionModel().selectFirst();
        ComboBox<String> typeFilter = new ComboBox<>(FXCollections.observableArrayList("All Types", "Sell", "Lend", "Giveaway"));
        typeFilter.getSelectionModel().selectFirst();
        TextField searchField = new TextField();
        searchField.setPromptText("Search listings...");
        searchField.setPrefWidth(220);
        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        filterBar.getChildren().addAll(
                new Label("Category:"), categoryFilter,
                new Label("Type:"), typeFilter,
                searchField, searchBtn
        );
        root.getChildren().add(filterBar);

        // Listing grid (shared listing card)
        FlowPane grid = ListingCard.grid();

        List<Listing> live = DataStore.getInstance().getLiveListings();
        if (live.isEmpty()) {
            Label empty = new Label("No live listings at the moment.");
            empty.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 15px;");
            root.getChildren().add(empty);
        } else {
            for (Listing listing : live) {
                grid.getChildren().add(createListingCard(listing));
            }
            root.getChildren().add(grid);
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        contentPane.getChildren().add(scroll);
    }

    private ListingCard createListingCard(Listing listing) {
        ListingCard card = new ListingCard(listing, UserRole.BUYER);

        // The buyer can act only on an available listing (UC8 Special Requirement 1)
        Button actionBtn = new Button();
        actionBtn.setDisable(!card.summary().available());
        actionBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        switch (listing.getType()) {
            case SELL -> {
                actionBtn.setText("Make Offer");
                actionBtn.setOnAction(e -> showOfferDialog(listing));
            }
            case LEND -> {
                actionBtn.setText("Request Borrow");
                actionBtn.setOnAction(e -> showBorrowDialog(listing));
            }
            case GIVEAWAY -> {
                actionBtn.setText("Claim Item");
                actionBtn.setOnAction(e -> claimItem(listing));
            }
        }

        card.addActions(actionBtn);
        return card;
    }

    private void showOfferDialog(Listing listing) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(listing instanceof SaleListing sale ? sale.getPrice() : 0));
        dialog.setTitle("Make an Offer");
        dialog.setHeaderText("Offer for: " + listing.getTitle());
        dialog.setContentText("Your offer price (HK$):");
        dialog.showAndWait().ifPresent(priceStr -> {
            try {
                double price = Double.parseDouble(priceStr);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Offer Submitted");
                alert.setHeaderText(null);
                alert.setContentText("Your offer of HK$" + price + " has been sent to " + DataStore.getInstance().getFullName(listing.getSellerUsername()) + ".\n\n(Demo only – no backend)");
                alert.showAndWait();
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Invalid price.").showAndWait();
            }
        });
    }

    private void showBorrowDialog(Listing listing) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Borrow Request");
        alert.setHeaderText("Request to borrow: " + listing.getTitle());
        alert.setContentText("Proposed duration: " + (listing instanceof LendListing lend ? lend.getDurationDisplay() : "N/A") +
                "\n\nSend borrow request to seller?");
        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                new Alert(Alert.AlertType.INFORMATION, "Borrow request sent!\n(Demo only)").showAndWait();
            }
        });
    }

    private void claimItem(Listing listing) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Claim Item");
        alert.setHeaderText("Claim: " + listing.getTitle());
        alert.setContentText("Are you sure you want to claim this giveaway item?");
        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                new Alert(Alert.AlertType.INFORMATION, "Item claimed successfully!\n(Demo only)").showAndWait();
            }
        });
    }

    @FXML
    private void showMyDeals() {
        setActive(btnDeals, "My Deals");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        Label info = new Label("Active offers, borrow requests and claims");
        info.setStyle("-fx-text-fill: #5a5a7a;");
        root.getChildren().add(info);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<String[], String> colTitle = new TableColumn<>("Item");
        colTitle.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[0]));
        TableColumn<String[], String> colSeller = new TableColumn<>("Seller");
        colSeller.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[1]));
        TableColumn<String[], String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[2]));
        TableColumn<String[], String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[3]));
        TableColumn<String[], String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[4]));

        table.getColumns().addAll(colTitle, colSeller, colType, colStatus, colAction);

        // Dummy rows
        table.setItems(FXCollections.observableArrayList(
                new String[]{"Calculus Textbook (Stewart)", "Charlie Lee", "Buy", "Accepted", "Chat / Complete"},
                new String[]{"Graphing Calculator TI-84", "Diana Ng", "Borrow", "Pending", "View"}
        ));

        root.getChildren().add(table);
        Label note = new Label("💡 Self-return for borrowed items and mark-complete are available here in the full version.");
        note.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        root.getChildren().add(note);

        contentPane.getChildren().add(root);
    }

    @FXML
    private void showHistory() {
        setActive(btnHistory, "Deal History");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        TextField search = new TextField();
        search.setPromptText("Search by title / seller...");
        filters.getChildren().addAll(new Label("Date range:"), from, new Label("–"), to, search);
        root.getChildren().add(filters);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<String[], String> c1 = new TableColumn<>("Item Title");
        c1.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[0]));
        TableColumn<String[], String> c2 = new TableColumn<>("Seller");
        c2.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[1]));
        TableColumn<String[], String> c3 = new TableColumn<>("Deal Date");
        c3.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        TableColumn<String[], String> c4 = new TableColumn<>("Type");
        c4.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[3]));
        TableColumn<String[], String> c5 = new TableColumn<>("Outcome");
        c5.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[4]));
        table.getColumns().addAll(c1, c2, c3, c4, c5);
        table.setItems(FXCollections.observableArrayList(
                new String[]{"Old Chemistry Book", "Charlie Lee", "2025-11-12", "Bought", "Completed"},
                new String[]{"USB Hub", "Diana Ng", "2025-10-03", "Received", "Completed"}
        ));
        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showFavourites() {
        setActive(btnFavourites, "Favourites & Following");
        contentPane.getChildren().clear();
        VBox root = new VBox(16);
        root.setPadding(new Insets(4));
        root.setAlignment(Pos.CENTER);
        Label icon = new Label("♥");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #A68137;");
        Label title = new Label("Favourites, Bookmarks & Following");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label desc = new Label("Save listings you like and follow favourite sellers.\nThis section is a UI placeholder for students to implement.");
        desc.setStyle("-fx-text-fill: #5a5a7a; -fx-text-alignment: center;");
        desc.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        root.getChildren().addAll(icon, title, desc);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showChats() {
        setActive(btnChats, "Chats");
        show("Chats", new ChatList(new ChatService(), this)); // UC6, shared chat screens
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        show("Notification Board", new NotificationBoard(new NotificationService())); // UC4, shared board
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "Manage Profile");
        show("Manage Profile", new ProfileView(new ProfileService(), this::showGreeting)); // UC3, shared profile screen
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
