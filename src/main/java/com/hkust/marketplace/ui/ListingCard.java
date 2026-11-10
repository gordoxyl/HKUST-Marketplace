package com.hkust.marketplace.ui;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.UserRole;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * The listing card, the same in all three portals (non-functional requirement: shared
 * components for cards). It shows the thumbnail, listing type, price, title, category and
 * condition, seller, meet-up location, date and a status badge (UC8 step 2; Tasks 1.3, 2.4,
 * 3.2), and below them the buttons the screen adds:
 * <pre>{@code
 * FlowPane grid = ListingCard.grid();
 * for (Listing listing : store.getLiveListings()) {
 *     ListingCard card = new ListingCard(listing, UserRole.BUYER);
 *     card.setOnOpen(() -> openDetail(listing));   // click the card
 *     card.addActions(offerButton);                // any buttons the screen needs
 *     grid.getChildren().add(card);
 * }
 * }</pre>
 * What each line says, and when the card is greyed out, comes from the tested
 * {@link ListingSummary}; this class only lays it out.
 */
public class ListingCard extends VBox {

    /** The width of every card, so cards line up in a grid. */
    public static final double WIDTH = 260;

    private static final double THUMBNAIL_WIDTH = WIDTH - 32;
    private static final double THUMBNAIL_HEIGHT = 140;

    private final ListingSummary summary;
    private final HBox actions = new HBox(8);

    /** A card for a listing in the app's store, as seen by a user with the given role. */
    public ListingCard(Listing listing, UserRole viewer) {
        this(ListingSummary.of(listing, viewer, DataStore.getInstance()));
    }

    public ListingCard(ListingSummary summary) {
        super(8);
        this.summary = summary;
        getStyleClass().add("listing-card");
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);

        // Top row: listing type and status badges, then the price; none of them is ever cut short
        Label type = new Label(summary.typeText());
        type.getStyleClass().addAll("badge", "badge-type");
        Label badge = new Label(summary.badgeText());
        badge.getStyleClass().addAll("badge", summary.badgeStyle());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label price = new Label(summary.priceText());
        price.getStyleClass().add("listing-card-price");
        for (Label fixed : new Label[] {type, badge, price}) {
            fixed.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox top = new HBox(6, type, badge, spacer, price);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(summary.title());
        title.getStyleClass().add("listing-card-title");
        title.setWrapText(true);

        getChildren().addAll(thumbnail(), top, title, line(summary.detailsText()));
        summary.sellerText().ifPresent(text -> getChildren().add(line(text)));
        getChildren().add(line(summary.locationText()));
        summary.photosText().ifPresent(text -> getChildren().add(line(text)));

        Label date = new Label(summary.dateText());
        date.getStyleClass().add("listing-card-date");
        getChildren().add(date);

        if (summary.greyedOut()) {
            getStyleClass().add("listing-card-unavailable");
        }
    }

    /** A wrapping grid with the usual spacing, for a screen that shows several cards. */
    public static FlowPane grid() {
        FlowPane grid = new FlowPane(16, 16);
        grid.setPrefWrapLength(3 * WIDTH + 2 * 16);
        return grid;
    }

    /** A grid already holding the given cards. */
    public static FlowPane grid(Collection<? extends ListingCard> cards) {
        FlowPane grid = grid();
        grid.getChildren().addAll(cards);
        return grid;
    }

    /** What the card shows; for example {@code summary().available()} to enable a buyer's action button. */
    public ListingSummary summary() {
        return summary;
    }

    /** Adds buttons in a row at the bottom of the card, in the order given. */
    public ListingCard addActions(Button... buttons) {
        if (!getChildren().contains(actions)) {
            actions.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(actions);
        }
        actions.getChildren().addAll(buttons);
        return this;
    }

    /**
     * Runs the action when the card is clicked, such as opening the listing detail screen (UC8
     * step 5). A click on one of the card's buttons runs only that button.
     */
    public ListingCard setOnOpen(Runnable open) {
        setCursor(Cursor.HAND);
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isOnButton(event.getTarget())) {
                open.run();
            }
        });
        return this;
    }

    private boolean isOnButton(Object target) {
        for (Node node = target instanceof Node n ? n : null; node != null && node != this; node = node.getParent()) {
            if (node instanceof ButtonBase) {
                return true;
            }
        }
        return false;
    }

    private Node thumbnail() {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("listing-card-thumbnail");
        frame.setPrefSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        frame.setMinHeight(THUMBNAIL_HEIGHT);
        frame.setMaxHeight(THUMBNAIL_HEIGHT);
        Label none = new Label("No photo");
        none.getStyleClass().add("listing-card-date");
        ObservableList<Node> children = frame.getChildren();
        children.add(none);

        summary.thumbnailUrl().ifPresent(url -> {
            // Loads in the background; "No photo" stays until the photo is shown, and if the
            // file cannot be read.
            Image image = new Image(url, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true, true, true);
            ImageView view = new ImageView(image);
            children.add(view);
            Runnable update = () -> {
                if (image.isError()) {
                    children.remove(view);
                    none.setVisible(true);
                } else if (image.getProgress() >= 1) {
                    none.setVisible(false);
                }
            };
            image.progressProperty().addListener((obs, old, now) -> update.run());
            image.errorProperty().addListener((obs, old, now) -> update.run());
            update.run();
        });
        return frame;
    }

    private static Label line(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("listing-card-line");
        label.setWrapText(true);
        return label;
    }
}
