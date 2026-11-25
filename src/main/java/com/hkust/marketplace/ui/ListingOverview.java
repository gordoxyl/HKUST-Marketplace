package com.hkust.marketplace.ui;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Listing;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The top of a listing's detail screen, the same in every portal: all photos, the title with
 * the status badge and the price, every field, and the full description (UC8 step 7; the
 * admin's "full listing details", Manage Marketplace Listings 5.1). Each portal adds its own
 * parts below it: the buyer's buttons and the seller's reviews
 * ({@code controllers/buyer/ListingDetailView}), the admin's reports and actions.
 * <pre>{@code
 * ListingSummary summary = ListingSummary.of(listing, UserRole.ADMIN, store);
 * VBox page = new VBox(16, new ListingOverview(summary, store), ListingOverview.card(reports));
 * }</pre>
 * It was moved out of the buyer's detail screen so that the admin's listing screen shows a
 * listing the same way instead of repeating this layout (Lecture 10: refactoring, remove a
 * duplicate; Lecture 17: favour composition). What it says comes from the tested
 * {@link ListingSummary}.
 */
public class ListingOverview extends VBox {

    private static final double PHOTO_WIDTH = 200;
    private static final double PHOTO_HEIGHT = 150;

    /**
     * @param summary what to show, for the viewer's role
     * @param store   the store that holds the listing's photos
     */
    public ListingOverview(ListingSummary summary, DataStore store) {
        super(16, photos(summary.listing(), store), heading(summary), fields(summary), description(summary));
    }

    /** One part of a detail screen in a white card, as this overview shows its fields and description. */
    public static VBox card(Node content) {
        VBox card = new VBox(content);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));
        return card;
    }

    /** All the listing's photos, at most {@link Listing#MAX_PHOTOS}, in order; one empty frame if there are none. */
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
}
