package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.Conversation;
import com.hkust.marketplace.services.CreateListingService;
import com.hkust.marketplace.services.NotificationService;
import com.hkust.marketplace.services.PhotoService;
import com.hkust.marketplace.services.ProfileService;
import com.hkust.marketplace.services.SellerListingService;
import com.hkust.marketplace.services.SellerOfferService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.ChatList;
import com.hkust.marketplace.ui.ChatText;
import com.hkust.marketplace.ui.ChatView;
import com.hkust.marketplace.ui.ContentHost;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.Formats;
import com.hkust.marketplace.ui.ListingCard;
import com.hkust.marketplace.ui.NotificationBoard;
import com.hkust.marketplace.ui.OfferText;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class SellerDashboardController implements ContentHost {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnOffers, btnCreate, btnStats, btnReviews, btnChats, btnNotifications, btnProfile;

    /** The format the counter dialog reads and writes: the same one {@link Formats#dateTime} shows. */
    private static final DateTimeFormatter COUNTER_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

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

        List<Listing> myListings = new SellerListingService().myListings();

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
                priceField.setText(String.valueOf(sale.getPrice()));
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
                : "No photos chosen (optional, up to 6)");
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
        hint.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

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
        try {
            // Manage Listings A3: warn before saving, because saving declines the open offers.
            int openOffers = service.openOfferCount(existing.getId());
            if (openOffers > 0 && !Dialogs.confirm("Open offers on this listing",
                    "This listing has " + openOffers + " open offer"
                            + (openOffers == 1 ? "" : "s")
                            + ". Saving your changes declines them and notifies the buyers. Continue?")) {
                return;
            }
            switch (existing.getType()) {
                case SELL -> service.updateSale(existing.getId(), name, category, condition, description,
                        location, parsePrice(priceText), photoFiles);
                case LEND -> service.updateLend(existing.getId(), name, category, condition, description,
                        location, parseDuration(durationText, durationUnit), photoFiles);
                default -> service.updateGiveaway(existing.getId(), name, category, condition, description,
                        location, photoFiles);
            }
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        Dialogs.info("Changes saved", "'" + existing.getTitle()
                + "' was updated and is Pending Review again.");
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
            return "No photos yet (optional, up to 6)";
        }
        return count + (count == 1 ? " photo is kept" : " photos are kept")
                + " unless you choose new files (up to 6)";
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

    /** Task 2.5: every open offer, borrow request and claim on the seller's listings, by listing. */
    @FXML
    private void showOffers() {
        setActive(btnOffers, "Offers & Requests");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));
        List<Offer> offers = new SellerOfferService().openOffers();
        if (offers.isEmpty()) {
            Label empty = new Label("No offers, borrow requests or claims are waiting for an answer.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            Map<String, List<Offer>> byListing = new LinkedHashMap<>();
            for (Offer offer : offers) {
                byListing.computeIfAbsent(offer.getListingId(), id -> new ArrayList<>()).add(offer);
            }
            for (Map.Entry<String, List<Offer>> entry : byListing.entrySet()) {
                root.getChildren().add(offerSection(DataStore.getInstance().findListing(entry.getKey()),
                        entry.getValue()));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /** One card per listing that has offers waiting, with a row for each offer. */
    private VBox offerSection(Listing listing, List<Offer> offers) {
        Label title = new Label(listing.getTitle());
        title.getStyleClass().add("section-title");
        Label meta = new Label(listing.getType().getDisplayName() + " · "
                + listing.getStatus().getDisplayName() + " · " + offers.size()
                + (offers.size() == 1 ? " offer waiting" : " offers waiting"));
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        VBox section = new VBox(10);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        section.getChildren().addAll(title, meta);
        for (Offer offer : offers) {
            section.getChildren().add(offerRow(listing, offer));
        }
        return section;
    }

    /** One buyer's offer: who, what, when, and the actions of Manage Offers &amp; Requests. */
    private Node offerRow(Listing listing, Offer offer) {
        DataStore store = DataStore.getInstance();
        Label who = new Label(store.getFullName(offer.getBuyerUsername())
                + " (@" + offer.getBuyerUsername() + ")");
        who.setStyle("-fx-font-weight: bold;");
        Label what = new Label(OfferText.summary(offer));
        Label when = new Label(Formats.dateTime(offer.getOfferDate()));
        when.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        Label status = new Label(OfferText.status(offer));
        status.getStyleClass().addAll("badge", OfferText.badgeStyle(offer));

        VBox text = new VBox(3, who, what);
        OfferText.counter(offer).ifPresent(counter -> text.getChildren().add(new Label(counter)));
        text.getChildren().add(when);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button accept = new Button("Accept");
        accept.getStyleClass().addAll("btn-success", "btn-sm");
        accept.setDisable(offer.getStatus() != OfferStatus.PENDING
                || listing.getStatus() != ListingStatus.LIVE);
        accept.setOnAction(e -> acceptOffer(offer, listing));

        Button decline = new Button("Decline");
        decline.getStyleClass().addAll("btn-danger", "btn-sm");
        decline.setOnAction(e -> declineOffer(offer));

        HBox actions = new HBox(8, accept, decline, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (offer instanceof PriceOffer || offer instanceof BorrowRequest) {
            Button counter = new Button("Counter");
            counter.getStyleClass().addAll("btn-outline", "btn-sm");
            counter.setDisable(offer.getStatus() != OfferStatus.PENDING);
            counter.setOnAction(e -> counterOffer(offer));
            actions.getChildren().add(counter);
        }
        Button chat = new Button("Open Chat");
        chat.getStyleClass().addAll("btn-outline", "btn-sm");
        chat.setOnAction(e -> openOfferChat(offer));
        actions.getChildren().add(chat);

        HBox row = new HBox(12, text, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 0, 0));
        row.setStyle("-fx-border-color: #e8f0f8; -fx-border-width: 1 0 0 0;");
        return row;
    }

    /** 3.3: accept, after confirming; the service reserves/lends and declines the other offers. */
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

    private void counterPrice(PriceOffer offer) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(offer.getOfferedPrice()));
        dialog.setTitle("Counter price offer");
        dialog.setHeaderText("Counter " + DataStore.getInstance().getFullName(offer.getBuyerUsername())
                + "'s price offer");
        dialog.setContentText("New price (HK$):");
        Optional<String> typed = dialog.showAndWait();
        if (typed.isEmpty()) {
            return;
        }
        double price;
        try {
            price = Double.parseDouble(typed.get().trim());
        } catch (NumberFormatException e) {
            Dialogs.error("Invalid price",
                    "The counter price must be a positive amount, such as 150 or 99.50.");
            return;
        }
        try {
            new SellerOfferService().counterPrice(offer.getId(), price);
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        Dialogs.info("Counter sent", "The buyer has been notified.");
        showOffers();
    }

    private void counterBorrow(BorrowRequest request) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Counter borrow request");
        dialog.setHeaderText("Counter " + DataStore.getInstance().getFullName(request.getBuyerUsername())
                + "'s borrow request");
        ButtonType send = new ButtonType("Send counter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);

        TextField startField = new TextField(COUNTER_FORMAT.format(request.getProposedStartDate()));
        TextField endField = new TextField(COUNTER_FORMAT.format(request.getProposedEndDate()));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Start (dd MMM yyyy HH:mm)"), 0, 0);
        grid.add(startField, 1, 0);
        grid.add(new Label("End (dd MMM yyyy HH:mm)"), 0, 1);
        grid.add(endField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != send) {
            return;
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startField.getText().trim(), COUNTER_FORMAT);
            LocalDateTime end = LocalDateTime.parse(endField.getText().trim(), COUNTER_FORMAT);
            new SellerOfferService().counterBorrow(request.getId(), start, end);
        } catch (DateTimeParseException e) {
            Dialogs.error("Invalid dates", "Type both dates as, for example, 16 Nov 2026 09:00.");
            return;
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        Dialogs.info("Counter sent", "The buyer has been notified.");
        showOffers();
    }

    /** 3.6: the chat thread linked to the offer, through the shared chat screens. */
    private void openOfferChat(Offer offer) {
        ChatService chat = new ChatService();
        Conversation conversation = chat.openForOffer(offer.getId());
        show(ChatText.title(conversation), new ChatView(chat, conversation));
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
