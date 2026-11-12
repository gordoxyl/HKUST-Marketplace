package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.services.BrowseService;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.Conversation;
import com.hkust.marketplace.services.ListingDetail;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.ProfileService;
import com.hkust.marketplace.services.SellerProfile;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.ChatList;
import com.hkust.marketplace.ui.ChatText;
import com.hkust.marketplace.ui.ChatView;
import com.hkust.marketplace.ui.ContentHost;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.NotificationBoard;
import com.hkust.marketplace.ui.ProfileView;
import com.hkust.marketplace.ui.SubScreen;
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

/**
 * The buyer's dashboard: the sidebar, and the main area that shows one screen at a time.
 * It only moves between screens and connects their buttons to the use cases; the rules are in
 * the services, and each screen is a class of its own ({@link BrowseView},
 * {@link ListingDetailView}, {@link SellerProfileView} and the shared ones in {@code ui}).
 */
public class BuyerDashboardController implements ContentHost {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnHistory, btnFavourites, btnChats, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    private final BrowseService browseService = new BrowseService();
    private final ChatService chatService = new ChatService();
    /** The Browse screen now open. It is kept while a listing is open, so Back returns to it as it was. */
    private BrowseView browseView;

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

    // ========== UC8 Browse Marketplace ==========

    @FXML
    private void showBrowse() {
        setActive(btnBrowse, "Browse Marketplace");
        browseView = new BrowseView(browseService,
                listing -> openListing(listing.getId(), "Back to listings", this::returnToBrowse));
        show("Browse Marketplace", browseView);
    }

    /** UC8 step 8: back to the listings, with the keyword, filters and sort order as they were. */
    private void returnToBrowse() {
        browseView.refresh();
        show("Browse Marketplace", browseView);
    }

    /**
     * Opens a listing's detail screen, which adds one view (UC8 steps 5-7). The screen's
     * buttons start the other use cases (UC8 Special Requirement 3).
     * @param backLabel where Back leads, such as "Back to listings"
     * @param back      shows the screen the listing was opened from
     */
    private void openListing(String listingId, String backLabel, Runnable back) {
        ListingDetail detail;
        try {
            detail = browseService.openListing(listingId);
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        Listing listing = detail.listing();
        ListingDetailView view = new ListingDetailView(detail);
        Node screen = new SubScreen(backLabel, back, view);
        Runnable backToListing = () -> show(listing.getTitle(), screen); // not a new opening: no extra view

        view.setOnAction(() -> startOffer(listing));
        view.setOnChat(() -> openChat(listing, backToListing));
        view.setOnReport(() -> reportListing(listing));
        view.setOnSellerProfile(() -> showSellerProfile(listing.getSellerUsername(), backToListing));
        show(listing.getTitle(), screen);
    }

    /** UC8 A3: the seller's profile, from the seller's name on the detail screen. */
    private void showSellerProfile(String sellerUsername, Runnable backToListing) {
        try {
            SellerProfile seller = browseService.getSellerProfile(sellerUsername);
            show("Seller: " + seller.fullName(),
                    new SubScreen("Back to listing", backToListing, new SellerProfileView(seller)));
        } catch (ServiceException e) {
            Dialogs.problem(e);
        }
    }

    /** UC6: the buyer's chat with the seller about this listing, from the detail screen. */
    private void openChat(Listing listing, Runnable backToListing) {
        try {
            Conversation chat = chatService.openForListing(listing.getId());
            show(ChatText.title(chat),
                    new SubScreen("Back to listing", backToListing, new ChatView(chatService, chat)));
        } catch (ServiceException e) {
            Dialogs.problem(e);
        }
    }

    /** UC5 Make Offer starts here. It is the next item to be built (B2). */
    private void startOffer(Listing listing) {
        Dialogs.info("Not built yet", "Making an offer, borrow request or claim on '" + listing.getTitle()
                + "' (UC5) is the next part to be built.");
    }

    /** UC9 Report Listing starts here. It is built later (B7). */
    private void reportListing(Listing listing) {
        Dialogs.info("Not built yet", "Reporting '" + listing.getTitle() + "' (UC9) is built later.");
    }

    // ========== Screens that are still the skeleton's demo ==========

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
        show("Chats", new ChatList(chatService, this)); // UC6, shared chat screens
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
