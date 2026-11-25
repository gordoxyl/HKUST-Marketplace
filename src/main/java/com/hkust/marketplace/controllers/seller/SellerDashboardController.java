package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.services.Amounts;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.Conversation;
import com.hkust.marketplace.services.CreateListingService;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.OfferAction;
import com.hkust.marketplace.services.PhotoService;
import com.hkust.marketplace.services.ProfileService;
import com.hkust.marketplace.services.SellerListingService;
import com.hkust.marketplace.services.SellerOfferService;
import com.hkust.marketplace.services.SellerReviewService;
import com.hkust.marketplace.services.SellerStats;
import com.hkust.marketplace.services.SellerStatsService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.ChatList;
import com.hkust.marketplace.ui.ChatView;
import com.hkust.marketplace.ui.ContentHost;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.Formats;
import com.hkust.marketplace.ui.ListingCard;
import com.hkust.marketplace.ui.LiveUpdates;
import com.hkust.marketplace.ui.NotificationBoard;
import com.hkust.marketplace.ui.NotificationDot;
import com.hkust.marketplace.ui.OfferText;
import com.hkust.marketplace.ui.ProfileView;
import com.hkust.marketplace.ui.Refreshable;
import com.hkust.marketplace.ui.Scrolling;
import com.hkust.marketplace.ui.TextInputs;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SellerDashboardController implements ContentHost {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnOffers, btnCreate, btnStats, btnReviews, btnChats, btnNotifications, btnProfile;

    /** The button of the reply form (Manage Reviews step 4). */
    private static final ButtonType SEND_REPLY =
            new ButtonType("Send reply", ButtonBar.ButtonData.OK_DONE);

    /** The button of the counter forms (Manage Offers &amp; Requests 3.5). */
    private static final ButtonType SEND_COUNTER =
            new ButtonType("Send counter", ButtonBar.ButtonData.OK_DONE);

    private Button currentActiveBtn;

    /** Reloads the screen on show when the timer changed the data; null if it cannot reload. */
    private Runnable reloadCurrentScreen;

    @FXML
    public void initialize() {
        showGreeting();
        currentActiveBtn = btnListings;
        // UC12 step 4: an open screen shows a returned item at once, without the seller clicking.
        LiveUpdates.setOnDataChanged(() -> contentPane.getScene() != null, this::refreshCurrentScreen);
        showMyListings();
        NotificationDot.attach(btnNotifications, new NotificationService());
    }

    /** Reloads the screen now shown, if it can reload (ui/Refreshable, or rebuilt from the store). */
    private void refreshCurrentScreen() {
        if (contentPane.getChildren().isEmpty()) {
            return;
        }
        if (contentPane.getChildren().get(0) instanceof Refreshable screen) {
            screen.refresh();
        } else if (reloadCurrentScreen != null) {
            reloadCurrentScreen.run();
        }
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
        NotificationDot.update();
    }

    /** Lets shared components open a screen here, such as a listing or a chat thread. */
    @Override
    public void show(String title, Node content) {
        pageTitle.setText(title);
        contentPane.getChildren().setAll(content);
        reloadCurrentScreen = null;
        NotificationDot.update();
    }

    @FXML
    private void showMyListings() {
        setActive(btnListings, "My Listings");
        contentPane.getChildren().clear();
        reloadCurrentScreen = this::showMyListings;

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        List<Listing> myListings = new SellerListingService().myListings();

        if (myListings.isEmpty()) {
            Label empty = new Label("You have not posted any listings yet.");
            empty.getStyleClass().add("text-muted");
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
        editBtn.setOnAction(e -> showEditListing(l)); // Manage Listings 3.1

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        delBtn.setOnAction(e -> deleteListing(l)); // Manage Listings 3.2

        card.addActions(editBtn, delBtn);
        return card;
    }

    /** Manage Listings 3.2: confirm, then let the service delete it and decline its open offers. */
    /** Manage Listings 3.2: a locked listing is refused before any confirmation (A2). */
    private void deleteListing(Listing listing) {
        if (listing.isLocked()) { // A2: Reserved, Sold, Lent, Given or Removed
            Dialogs.error("Listing locked", "This listing can no longer be edited or deleted.");
            return;
        }
        int openOffers;
        try {
            openOffers = new SellerListingService().openOfferCount(listing.getId());
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        String message = "Delete '" + listing.getTitle() + "'? This cannot be undone."
                + (openOffers == 0 ? ""
                : " " + openOffers + " open offer" + (openOffers == 1 ? "" : "s")
                        + " will be declined and the buyer" + (openOffers == 1 ? "" : "s") + " notified.");
        if (!Dialogs.confirm("Delete listing", message)) {
            return;
        }
        try {
            new SellerListingService().deleteListing(listing.getId());
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        showMyListings();
    }

    @FXML
    private void showCreateListing() {
        showListingForm(null); // Task 2.3
    }

    /** Manage Listings 3.1: the same form, filled with the listing's current values. */
    private void showEditListing(Listing existing) {
        if (existing.isLocked()) { // Manage Listings A2
            Dialogs.error("Listing locked", "This listing can no longer be edited or deleted.");
            return;
        }
        showListingForm(existing);
    }

    /**
     * The listing form, shared by Create Listing (Task 2.3) and the Edit button on My Listings
     * (Task 2.4). The type never changes after posting, so it is fixed when editing.
     */
    private void showListingForm(Listing existing) {
        boolean editing = existing != null;
        setActive(editing ? btnListings : btnCreate, editing ? "Edit Listing" : "Create New Listing");
        contentPane.getChildren().clear();
        reloadCurrentScreen = null; // a timer event must not replace a form being filled in

        VBox form = new VBox(10);
        form.setMaxWidth(560);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));

        Label title = new Label(editing ? "Edit '" + existing.getTitle() + "'" : "Post a new item");
        title.getStyleClass().add("section-title");

        ComboBox<ListingType> typeCombo = new ComboBox<>(FXCollections.observableArrayList(ListingType.values()));
        typeCombo.setPromptText("Listing type *");
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        if (editing) {
            typeCombo.setValue(existing.getType());
            typeCombo.setDisable(true); // a listing's type never changes (models/Listing)
        }

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

        // Manage Listings 3.1: open the form with the listing's current values.
        if (editing) {
            if (existing instanceof SaleListing sale) {
                priceField.setDisable(false);
                priceField.setText(Amounts.plain(sale.getPrice()));
            } else if (existing instanceof LendListing lend) {
                durationField.setDisable(false);
                durationUnit.setDisable(false);
                prefillDuration(lend.getLendingDuration(), durationField, durationUnit);
            }
            nameField.setText(existing.getTitle());
            catCombo.setValue(existing.getCategory());
            condCombo.setValue(existing.getCondition());
            descArea.setText(existing.getDescription());
            meetupCombo.setValue(existing.getMeetupLocation());
        }

        List<File> chosenPhotos = new ArrayList<>();
        Label photoLabel = new Label(editing
                ? keptPhotosText(existing)
                : "No photos chosen (optional, up to " + Listing.MAX_PHOTOS + ")");
        photoLabel.setWrapText(true);
        photoLabel.getStyleClass().add("dialog-hint");
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
            if (editing) {
                return;
            }
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
        hint.getStyleClass().add("field-hint");

        Button submitBtn = new Button(editing ? "Save Changes" : "Submit for Review");
        submitBtn.getStyleClass().add("btn-gold");
        submitBtn.setOnAction(e -> {
            if (editing) {
                saveListingEdit(existing, nameField.getText(), catCombo.getValue(), condCombo.getValue(),
                        descArea.getText(), meetupCombo.getValue(), priceField.getText(),
                        durationField.getText(), durationUnit.getValue(), chosenPhotos);
            } else {
                submitListing(typeCombo.getValue(), nameField.getText(), catCombo.getValue(),
                        condCombo.getValue(), descArea.getText(), meetupCombo.getValue(),
                        priceField.getText(), durationField.getText(), durationUnit.getValue(),
                        chosenPhotos);
            }
        });

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
            Dialogs.error("Missing listing type", "Choose Sell, Lend or Giveaway first.");
            return;
        }
        List<Path> photoFiles = chosenPhotos.stream().map(File::toPath).toList();
        CreateListingService service = new CreateListingService();
        Listing created;
        try {
            switch (type) {
                case SELL -> created = service.createSale(name, category, condition, description, location,
                        priceText, photoFiles);
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

    /**
     * Manage Listings 3.1: reads the form and asks the service to save the changes. The type
     * cannot change, so it decides which update method applies.
     */
    private void saveListingEdit(Listing existing, String name, Category category,
                                 ItemCondition condition, String description, MeetupLocation location,
                                 String priceText, String durationText, String durationUnit,
                                 List<File> chosenPhotos) {
        List<Path> photoFiles = chosenPhotos.stream().map(File::toPath).toList();
        SellerListingService service = new SellerListingService();
        Listing saved;
        try {
            // Manage Listings A3: warn before saving, because saving declines the open offers.
            int openOffers = service.openOfferCount(existing.getId());
            if (openOffers > 0 && !Dialogs.confirm("Open offers on this listing",
                    "This listing has " + openOffers + " open offer"
                            + (openOffers == 1 ? "" : "s")
                            + ". Saving your changes declines them and notifies the buyers. Continue?")) {
                return;
            }
            saved = switch (existing.getType()) {
                case SELL -> service.updateSale(existing.getId(), name, category, condition, description,
                        location, priceText, photoFiles);
                case LEND -> service.updateLend(existing.getId(), name, category, condition, description,
                        location, parseDuration(durationText, durationUnit), photoFiles);
                default -> service.updateGiveaway(existing.getId(), name, category, condition, description,
                        location, photoFiles);
            };
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        String note = saved.getStatus() == ListingStatus.PENDING_REVIEW
                ? " and is Pending Review again" : "";
        Dialogs.info("Changes saved", "'" + saved.getTitle() + "' was updated" + note + ".");
        showMyListings();
    }

    /** Fills the duration fields with the current duration, in its largest whole unit. */
    private static void prefillDuration(Duration duration, TextField amount, ComboBox<String> unit) {
        if (duration.toDays() > 0 && duration.equals(Duration.ofDays(duration.toDays()))) {
            amount.setText(String.valueOf(duration.toDays()));
            unit.getSelectionModel().select("days");
        } else if (duration.toHours() > 0 && duration.equals(Duration.ofHours(duration.toHours()))) {
            amount.setText(String.valueOf(duration.toHours()));
            unit.getSelectionModel().select("hours");
        } else if (duration.toMinutes() > 0 && duration.equals(Duration.ofMinutes(duration.toMinutes()))) {
            amount.setText(String.valueOf(duration.toMinutes()));
            unit.getSelectionModel().select("minutes");
        } else {
            amount.setText(String.valueOf(duration.toSeconds()));
            unit.getSelectionModel().select("seconds");
        }
    }

    /** The photo label on the edit form: the current photos are kept unless new ones are chosen. */
    private static String keptPhotosText(Listing listing) {
        int count = listing.getPhotos().size();
        if (count == 0) {
            return "No photos yet (optional, up to " + Listing.MAX_PHOTOS + ")";
        }
        return count + (count == 1 ? " photo is kept" : " photos are kept")
                + " unless you choose new files (up to " + Listing.MAX_PHOTOS + ")";
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
            label.setText("No photos chosen (optional, up to " + Listing.MAX_PHOTOS + ")");
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

    /** Task 2.5: every open offer, borrow request and claim on the seller's listings, by listing. */
    @FXML
    private void showOffers() {
        showOffersFor(List.of());
    }

    /**
     * Manage Offers &amp; Requests. The offers in {@code waiting}, from a chat's Respond in
     * Offers &amp; Requests link, are marked with {@link Scrolling#JUMP_HIGHLIGHT} until the
     * screen is built again, and the newest one is scrolled into view, as a chat search result is
     * (UC6 A2 step 4; assumptions register 71).
     */
    private void showOffersFor(List<Offer> waiting) {
        setActive(btnOffers, "Offers & Requests");
        contentPane.getChildren().clear();
        reloadCurrentScreen = this::showOffers;

        Set<String> marked = waiting.stream().map(Offer::getId).collect(Collectors.toSet());
        List<Node> markedRows = new ArrayList<>();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));
        List<Offer> offers = new SellerOfferService().openOffers();
        if (offers.isEmpty()) {
            Label empty = new Label("No offers, borrow requests or claims are waiting for an answer.");
            empty.getStyleClass().add("text-muted");
            root.getChildren().add(empty);
        } else {
            Map<String, List<Offer>> byListing = new LinkedHashMap<>();
            for (Offer offer : offers) {
                byListing.computeIfAbsent(offer.getListingId(), id -> new ArrayList<>()).add(offer);
            }
            for (Map.Entry<String, List<Offer>> entry : byListing.entrySet()) {
                root.getChildren().add(offerSection(DataStore.getInstance().findListing(entry.getKey()),
                        entry.getValue(), marked, markedRows));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
        if (!markedRows.isEmpty()) {
            Scrolling.bringIntoView(scroll, markedRows.get(0)); // waiting is newest first
        }
    }

    /** One card per listing that has offers waiting, with a row for each offer. */
    private VBox offerSection(Listing listing, List<Offer> offers, Set<String> marked,
                              List<Node> markedRows) {
        Label title = new Label(listing.getTitle());
        title.getStyleClass().add("section-title");
        Label meta = new Label(listing.getType().getDisplayName() + " · "
                + listing.getStatus().getDisplayName() + " · " + offers.size()
                + (offers.size() == 1 ? " offer waiting" : " offers waiting"));
        meta.getStyleClass().add("dialog-hint");

        VBox section = new VBox(10);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        section.getChildren().addAll(title, meta);
        for (Offer offer : offers) {
            section.getChildren().add(offerRow(listing, offer, marked, markedRows));
        }
        return section;
    }

    /** One buyer's offer: who, what, when, and the actions of Manage Offers &amp; Requests. */
    private Node offerRow(Listing listing, Offer offer, Set<String> marked, List<Node> markedRows) {
        DataStore store = DataStore.getInstance();
        Label who = new Label(store.getFullName(offer.getBuyerUsername())
                + " (@" + offer.getBuyerUsername() + ")");
        who.getStyleClass().add("fixed-value");
        Label what = new Label(OfferText.summary(offer));
        Label when = new Label(Formats.dateTime(offer.getOfferDate()));
        when.getStyleClass().add("notif-time");
        Label status = new Label(OfferText.status(offer));
        status.getStyleClass().addAll("badge", OfferText.badgeStyle(offer));

        VBox text = new VBox(3, who, what);
        OfferText.counter(offer).ifPresent(counter -> text.getChildren().add(new Label(counter)));
        text.getChildren().add(when);
        HBox.setHgrow(text, Priority.ALWAYS);

        // The buttons and the rule are the shared OfferAction's (Task 2.5; assumptions register 71).
        List<OfferAction> allowed = OfferAction.allowedFor(offer, false);
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        for (OfferAction action : OfferAction.sellerButtons(listing.getType())) {
            Button button = switch (action) {
                case ACCEPT -> actionButton("Accept", "btn-success", () -> acceptOffer(offer, listing));
                case DECLINE -> actionButton("Decline", "btn-danger", () -> declineOffer(offer));
                case COUNTER -> actionButton("Counter", "btn-outline", () -> counterOffer(offer));
                case AGREE_TO_COUNTER -> throw new IllegalStateException("Not a seller action.");
            };
            button.setDisable(!allowed.contains(action));
            actions.getChildren().add(button);
        }
        actions.getChildren().add(status);
        Button chat = new Button("Open Chat");
        chat.getStyleClass().addAll("btn-outline", "btn-sm");
        chat.setOnAction(e -> openOfferChat(offer));
        actions.getChildren().add(chat);

        HBox row = new HBox(12, text, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 0, 0));
        row.getStyleClass().add("offer-row");
        if (marked.contains(offer.getId())) {
            row.getStyleClass().add(Scrolling.JUMP_HIGHLIGHT);
            markedRows.add(row);
        }
        return row;
    }

    private static Button actionButton(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styleClass, "btn-sm");
        button.setOnAction(e -> action.run());
        return button;
    }

    /** 3.3: accept, after confirming; the service reserves or lends and declines the other offers. */
    private void acceptOffer(Offer offer, Listing listing) {
        String becomes = listing.getType() == ListingType.LEND ? "Lent" : "Reserved";
        if (!Dialogs.confirm("Accept " + offer.getKindName(),
                "Accept this " + offer.getKindName() + " for '" + listing.getTitle() + "'? "
                        + "The listing becomes " + becomes
                        + " and the other offers on it are declined.")) {
            return;
        }
        try {
            new SellerOfferService().accept(offer.getId());
        } catch (ServiceException e) {
            Dialogs.problem(e); // A2: the listing is no longer available
            return;
        }
        Dialogs.info("Offer accepted", "The buyer has been notified.");
        showOffers();
    }

    /** 3.4: decline one offer and tell its buyer. */
    private void declineOffer(Offer offer) {
        if (!Dialogs.confirm("Decline " + offer.getKindName(),
                "Decline this " + offer.getKindName() + "? The buyer will be told.")) {
            return;
        }
        try {
            new SellerOfferService().decline(offer.getId());
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        Dialogs.info("Offer declined", "The buyer has been notified.");
        showOffers();
    }

    /** 3.5: a price for a price offer, new dates for a borrow request; a claim has neither. */
    private void counterOffer(Offer offer) {
        if (offer instanceof PriceOffer price) {
            counterPrice(price);
        } else if (offer instanceof BorrowRequest request) {
            counterBorrow(request);
        }
    }

    /** The shared form; after a refused price it comes back with what was typed, until sent or cancelled. */
    private void counterPrice(PriceOffer offer) {
        String typed = null;
        while (true) {
            TextField priceBox = new TextField(typed != null
                    ? typed : Amounts.plain(offer.getOfferedPrice()));
            priceBox.setPromptText("such as 150 or 99.50");
            Dialog<ButtonType> dialog = Dialogs.form("Counter price offer",
                    "Counter " + DataStore.getInstance().getFullName(offer.getBuyerUsername())
                            + "'s price offer", SEND_COUNTER, ButtonType.CANCEL);
            dialog.getDialogPane().setContent(new VBox(8,
                    new Label("New price (HK$)"), priceBox,
                    Dialogs.hint("Type whole dollars with up to two decimals, such as 150 or 99.50.")));
            dialog.setOnShown(e -> priceBox.requestFocus());
            Optional<ButtonType> answer = dialog.showAndWait();
            if (answer.isEmpty() || answer.get() != SEND_COUNTER) {
                return;
            }
            typed = priceBox.getText();
            try {
                new SellerOfferService().counterPrice(offer.getId(), typed);
            } catch (ServiceException e) {
                Dialogs.problem(e);
                continue;
            }
            Dialogs.info("Counter sent", "The buyer has been notified.");
            showOffers();
            return;
        }
    }

    /** The same, with a date and a time of day for the start and the end (3.5, A4). */
    private void counterBorrow(BorrowRequest request) {
        LocalDate startDate = request.getProposedStartDate().toLocalDate();
        String startTime = Formats.timeOfDay(request.getProposedStartDate());
        LocalDate endDate = request.getProposedEndDate().toLocalDate();
        String endTime = Formats.timeOfDay(request.getProposedEndDate());
        while (true) {
            DatePicker startPicker = new DatePicker(startDate);
            startPicker.setConverter(Formats.datePickerConverter());
            TextField startField = new TextField(startTime);
            DatePicker endPicker = new DatePicker(endDate);
            endPicker.setConverter(Formats.datePickerConverter());
            TextField endField = new TextField(endTime);
            for (TextField time : new TextField[]{startField, endField}) {
                time.setPromptText("HH:mm or HH:mm:ss");
                time.setPrefWidth(150);
            }
            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(8);
            form.addRow(0, new Label("Start"), startPicker, startField);
            form.addRow(1, new Label("End"), endPicker, endField);
            Label rules = Dialogs.hint("The buyer asked for "
                    + Formats.period(request.getProposedStartDate(), request.getProposedEndDate())
                    + ". The period cannot start in the past and must last at least "
                    + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME) + ".");

            Dialog<ButtonType> dialog = Dialogs.form("Counter borrow request",
                    "Counter " + DataStore.getInstance().getFullName(request.getBuyerUsername())
                            + "'s borrow request", SEND_COUNTER, ButtonType.CANCEL);
            dialog.getDialogPane().setContent(new VBox(10, form, rules));
            Optional<ButtonType> answer = dialog.showAndWait();
            if (answer.isEmpty() || answer.get() != SEND_COUNTER) {
                return;
            }
            startDate = shownDate(startPicker);
            startTime = startField.getText();
            endDate = shownDate(endPicker);
            endTime = endField.getText();
            try {
                new SellerOfferService().counterBorrow(request.getId(),
                        TextInputs.dateTime(startDate, startTime).orElse(null),
                        TextInputs.dateTime(endDate, endTime).orElse(null));
            } catch (ServiceException e) {
                Dialogs.problem(e);
                continue;
            }
            Dialogs.info("Counter sent", "The buyer has been notified.");
            showOffers();
            return;
        }
    }

    /** The date shown in a picker, including one typed without pressing Enter (as the buyer's forms do). */
    private static LocalDate shownDate(DatePicker picker) {
        return picker.getConverter().fromString(picker.getEditor().getText());
    }

    /** 3.6: the chat thread linked to the offer, in the shared chat screen; Back returns to the list. */
    private void openOfferChat(Offer offer) {
        try {
            ChatService service = new ChatService();
            Conversation chat = service.openForOffer(offer.getId());
            new ChatList(service, this, this::addOfferLink)
                    .openFrom(chat, "Back to Offers & Requests", this::showOffers);
        } catch (ServiceException e) {
            Dialogs.problem(e);
        }
    }

    /** The seller's link from a chat to its offers on Offers &amp; Requests (assumptions register 71). */
    private void addOfferLink(ChatView chat) {
        chat.setOnRespondToOffers(this::showOffersFor);
    }

    @FXML
    private void showStats() {
        setActive(btnStats, "Seller Stats");
        contentPane.getChildren().clear();
        reloadCurrentScreen = null;

        SellerStats stats;
        try {
            stats = new SellerStatsService().getMyStats();
        } catch (ServiceException e) {
            Dialogs.problem(e); // only a seller can open this screen
            return;
        }

        VBox root = new VBox(20);
        root.setPadding(new Insets(4));

        // Step 3: the numbers of the use case, and the rating exactly as the shared core words it.
        HBox metrics = new HBox(16);
        metrics.getChildren().addAll(
                metricCard("Listings", String.valueOf(stats.listings()), "stat-navy"),
                metricCard("Views", String.valueOf(stats.views()), "stat-blue"),
                metricCard("Offers received", String.valueOf(stats.offersReceived()), "stat-gold"),
                metricCard("Completed deals", String.valueOf(stats.completedDeals()), "stat-green"),
                metricCard("Avg rating (" + stats.rating().count() + ")",
                        stats.rating().isEmpty()
                                ? Formats.NONE
                                : String.format("%.1f", stats.rating().average()),
                        "stat-gold"));
        root.getChildren().add(metrics);

        Label average = new Label("Average rating: " + Formats.averageRating(stats.rating()));
        average.getStyleClass().add("dialog-hint");
        root.getChildren().add(average);

        // Steps 4-5, with A1 when there is nothing to chart.
        if (stats.perListing().isEmpty()) {
            root.getChildren().add(emptyChart("There isn't enough data to draw charts yet."));
        } else {
            root.getChildren().add(new HBox(20, listingChart(stats), completedDealsChart(stats)));
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /** Step 4: the views and the offers received for each of the seller's listings. */
    private static Node listingChart(SellerStats stats) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelRotation(-30);
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Views and offers per listing");
        chart.setAnimated(false);
        chart.setPrefSize(620, 300);
        XYChart.Series<String, Number> views = new XYChart.Series<>();
        views.setName("Views");
        XYChart.Series<String, Number> offers = new XYChart.Series<>();
        offers.setName("Offers");
        for (SellerStats.ListingStats listing : stats.perListing()) {
            String label = Formats.preview(listing.title(), 18);
            views.getData().add(new XYChart.Data<>(label, listing.views()));
            offers.getData().add(new XYChart.Data<>(label, listing.offers()));
        }
        chart.getData().addAll(views, offers);
        return chart;
    }

    /** The pie chart of View Seller Stats step 5: the completed deals split by listing type. */
    private static Node completedDealsChart(SellerStats stats) {
        PieChart chart = new PieChart();
        chart.setTitle("Completed deals by type");
        chart.setAnimated(false);
        chart.setPrefSize(420, 300);
        for (ListingType type : ListingType.values()) {
            int completed = stats.completedByType().getOrDefault(type, 0);
            if (completed > 0) {
                chart.getData().add(new PieChart.Data(type.getDisplayName() + " (" + completed + ")", completed));
            }
        }
        if (chart.getData().isEmpty()) {
            return emptyChart("No completed deals yet.");
        }
        return chart;
    }

    /** Stands in for a chart that has nothing to draw yet. */
    private static Node emptyChart(String message) {
        Label empty = new Label(message);
        empty.getStyleClass().add("text-muted");
        StackPane placeholder = new StackPane(empty);
        placeholder.getStyleClass().add("card");
        placeholder.setPrefSize(420, 300);
        return placeholder;
    }

    private static VBox metricCard(String label, String value, String valueStyle) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(140);
        card.setPadding(new Insets(16));
        Label val = new Label(value);
        val.getStyleClass().addAll("stat-value", valueStyle);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("dialog-hint");
        card.getChildren().addAll(val, lbl);
        return card;
    }

    @FXML
    private void showReviews() {
        setActive(btnReviews, "Reviews & Feedback");
        contentPane.getChildren().clear();
        reloadCurrentScreen = null;

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        SellerReviewService service = new SellerReviewService();
        RatingSummary rating;
        List<Review> reviews;
        try {
            rating = service.getMyRating();
            reviews = service.getMyReviews();
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }

        Label average = new Label("Average rating: " + Formats.averageRating(rating));
        average.getStyleClass().add("dialog-hint");
        root.getChildren().add(average);

        if (reviews.isEmpty()) {
            Label empty = new Label("No reviews yet. A buyer can review you after a completed deal.");
            empty.getStyleClass().add("text-muted");
            root.getChildren().add(empty);
        } else {
            for (Review r : reviews) {
                VBox item = new VBox(6);
                item.getStyleClass().add("card");
                Label stars = new Label(Formats.stars(r.getRating()));
                stars.getStyleClass().add("review-stars-large");
                Label comment = new Label(r.getComment());
                comment.setWrapText(true);
                DataStore store = DataStore.getInstance();
                Label meta = new Label("by " + store.getReviewerName(r) + " · "
                        + store.getListingOf(store.findOffer(r.getOfferId())).getTitle()
                        + " · " + Formats.date(r.getReviewDate()));
                meta.getStyleClass().add("dialog-hint");
                item.getChildren().addAll(stars, comment, meta);
                if (r.getSellerReply() != null) {
                    Label yourReply = new Label("Your reply: " + r.getSellerReply());
                    yourReply.setWrapText(true);
                    yourReply.getStyleClass().add("fixed-value");
                    item.getChildren().add(yourReply);
                }

                HBox actions = new HBox(8);
                Button reply = new Button(r.getSellerReply() == null ? "Reply" : "Replied");
                reply.getStyleClass().addAll("btn-outline", "btn-sm");
                reply.setDisable(r.getSellerReply() != null); // one reply per review
                reply.setOnAction(e -> replyToReview(r));
                Button flag = new Button(r.isFlagged() ? "Flagged" : "Flag");
                flag.getStyleClass().addAll("btn-danger", "btn-sm");
                flag.setDisable(r.isFlagged());
                flag.setOnAction(e -> flagReview(r));
                actions.getChildren().addAll(reply, flag);
                if (r.isFlagged()) {
                    Label flagged = new Label("Awaiting moderation");
                    flagged.getStyleClass().addAll("badge", "badge-pending");
                    actions.getChildren().add(flagged);
                }
                item.getChildren().add(actions);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
    }

    /**
     * Manage Reviews steps 3-5: asks for the reply and sends it to the buyer. The form comes
     * back with what was typed after a refusal, until it is accepted or cancelled.
     */
    private void replyToReview(Review review) {
        String typed = "";
        while (true) {
            TextArea replyArea = new TextArea(typed);
            replyArea.setPrefRowCount(3);
            replyArea.setWrapText(true);
            Dialog<ButtonType> dialog = Dialogs.form("Reply to review",
                    "Your reply to " + DataStore.getInstance().getReviewerName(review) + "'s review",
                    SEND_REPLY, ButtonType.CANCEL);
            dialog.getDialogPane().setContent(new VBox(8,
                    new Label("Your reply"), replyArea,
                    Dialogs.hint("The buyer receives your reply as a notification. "
                            + "A review can have one reply.")));
            Optional<ButtonType> answer = dialog.showAndWait();
            if (answer.isEmpty() || answer.get() != SEND_REPLY) {
                return;
            }
            typed = replyArea.getText();
            try {
                new SellerReviewService().reply(review.getId(), typed);
            } catch (ServiceException e) {
                Dialogs.problem(e);
                continue;
            }
            Dialogs.info("Reply posted", "Your reply has been posted; the buyer has been notified.");
            showReviews();
            return;
        }
    }

    /** Manage Reviews step 6: marks an inappropriate review for the admin. */
    private void flagReview(Review review) {
        if (!Dialogs.confirm("Flag review", "Flag this review for the admin as inappropriate?")) {
            return;
        }
        try {
            new SellerReviewService().flag(review.getId());
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        Dialogs.info("Review flagged", "The review is awaiting moderation by the admin.");
        showReviews();
    }

    @FXML
    private void showChats() {
        setActive(btnChats, "Chats");
        // The 3-argument constructor gives every chat the link to its offers (Task 2.5).
        show("Chats", new ChatList(new ChatService(), this, this::addOfferLink));
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
