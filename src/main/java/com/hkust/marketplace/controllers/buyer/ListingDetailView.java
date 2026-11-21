package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.ListingDetail;
import com.hkust.marketplace.services.SellerProfile;
import com.hkust.marketplace.ui.ListingSummary;
import com.hkust.marketplace.ui.PhotoFrame;
import com.hkust.marketplace.ui.Refreshable;
import com.hkust.marketplace.ui.ReviewList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The listing detail screen (UC8 step 7; Task 1.4): all photos, all the listing's fields, the
 * full description, the seller's name with average rating and reviews, the availability
 * status, and three buttons: the action for the listing type (disabled when the listing is
 * not available), Chat with Seller and Report Listing.
 *
 * <p>The buttons and the seller's name do nothing by themselves. The dashboard connects them
 * to Make Offer (UC5), Chat About Listing (UC6), Report Listing (UC9) and the seller's
 * profile (UC8 A3) with the {@code setOn...} methods (UC8 Special Requirement 3).
 * What the screen says comes from the tested {@link ListingSummary}.
 */
public class ListingDetailView extends ScrollPane implements Refreshable {

    private static final double PHOTO_WIDTH = 200;
    private static final double PHOTO_HEIGHT = 150;

    private final Button action = new Button();
    private final Button chat = new Button("Chat with Seller");
    private final Button report = new Button("Report Listing");
    private final Hyperlink sellerName = new Hyperlink();
    private Runnable reload = () -> { };

    /** A detail screen for a listing in the app's store. */
    public ListingDetailView(ListingDetail detail) {
        this(detail, DataStore.getInstance());
    }

    /** @param store the store that holds the listing's photos */
    public ListingDetailView(ListingDetail detail, DataStore store) {
        Listing listing = detail.listing();
        ListingSummary summary = ListingSummary.of(listing, UserRole.BUYER, store);

        VBox page = new VBox(16, photos(listing, store), heading(summary), fields(summary),
                description(summary), buttons(summary, detail.available()), seller(detail.seller()));
        page.setPadding(new Insets(4, 12, 12, 4));
        setContent(page);
        setFitToWidth(true);
    }

    /** How the screen reloads, for example when the timer returned the item and it is available again. */
    public ListingDetailView setOnRefresh(Runnable reload) {
        this.reload = reload;
        return this;
    }

    @Override
    public void refresh() {
        reload.run();
    }

    /** What the action button does: start Make Offer (UC5) for this listing. */
    public ListingDetailView setOnAction(Runnable startOffer) {
        action.setOnAction(e -> startOffer.run());
        return this;
    }

    /** What Chat with Seller does: open the chat about this listing (UC6). */
    public ListingDetailView setOnChat(Runnable openChat) {
        chat.setOnAction(e -> openChat.run());
        return this;
    }

    /** What Report Listing does: start Report Listing (UC9). */
    public ListingDetailView setOnReport(Runnable startReport) {
        report.setOnAction(e -> startReport.run());
        return this;
    }

    /** What choosing the seller's name does: show the seller's profile (UC8 A3). */
    public ListingDetailView setOnSellerProfile(Runnable showProfile) {
        sellerName.setOnAction(e -> showProfile.run());
        return this;
    }

    // ---------- Layout ----------

    /** All the listing's photos, up to 6, in order; one empty frame if there are none. */
    private static Node photos(Listing listing, DataStore store) {
        FlowPane photos = new FlowPane(10, 10);
        for (String photoId : listing.getPhotos()) {
            photos.getChildren().add(new PhotoFrame(store, photoId, PHOTO_WIDTH, PHOTO_HEIGHT));
        }
        if (listing.getPhotos().isEmpty()) {
            photos.getChildren().add(new PhotoFrame(store, null, PHOTO_WIDTH, PHOTO_HEIGHT));
        }
        return photos;
    }

    private static Node heading(ListingSummary summary) {
        Label title = new Label(summary.title());
        title.getStyleClass().add("section-title");
        title.setWrapText(true);
        Label availability = new Label(summary.badgeText());
        availability.getStyleClass().addAll("badge", summary.badgeStyle());
        availability.setMinWidth(Region.USE_PREF_SIZE);
        Label price = new Label(summary.priceText());
        price.getStyleClass().add("listing-card-price");
        price.setMinWidth(Region.USE_PREF_SIZE);
        HBox heading = new HBox(12, title, availability, price);
        heading.setAlignment(Pos.CENTER_LEFT);
        return heading;
    }

    /** Every field of the listing, as a table of names and values. */
    private static Node fields(ListingSummary summary) {
        GridPane table = new GridPane();
        table.setHgap(24);
        table.setVgap(6);
        int row = 0;
        for (ListingSummary.Field field : summary.fields()) {
            Label name = new Label(field.name());
            name.getStyleClass().add("listing-card-line");
            Label value = new Label(field.value());
            value.getStyleClass().add("chat-text");
            table.addRow(row++, name, value);
        }
        return card(table);
    }

    private static Node description(ListingSummary summary) {
        Label heading = new Label("Description");
        heading.getStyleClass().add("listing-card-title");
        Label text = new Label(summary.descriptionText());
        text.setWrapText(true);
        text.setMinHeight(Region.USE_PREF_SIZE); // the full description, never cut short
        text.getStyleClass().add("chat-text");
        return card(new VBox(6, heading, text));
    }

    private Node buttons(ListingSummary summary, boolean available) {
        action.setText(summary.actionText());
        action.getStyleClass().add("btn-primary");
        action.setDisable(!available); // UC8 Special Requirement 1
        chat.getStyleClass().add("btn-outline");
        report.getStyleClass().add("btn-outline");
        HBox buttons = new HBox(10, action, chat, report);
        buttons.setAlignment(Pos.CENTER_LEFT);
        return buttons;
    }

    /** The seller's name, which opens the profile, with the average rating and the reviews. */
    private Node seller(SellerProfile seller) {
        Label label = new Label("Seller:");
        label.getStyleClass().add("listing-card-title");
        sellerName.setText(seller.fullName());
        HBox name = new HBox(4, label, sellerName);
        name.setAlignment(Pos.CENTER_LEFT);
        return card(new VBox(10, name,
                new ReviewList(seller.rating(), seller.reviews())));
    }

    private static VBox card(Node content) {
        VBox card = new VBox(content);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));
        return card;
    }
}
