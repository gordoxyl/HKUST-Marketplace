package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.ListingDetail;
import com.hkust.marketplace.services.SellerProfile;
import com.hkust.marketplace.ui.ListingOverview;
import com.hkust.marketplace.ui.ListingSummary;
import com.hkust.marketplace.ui.Refreshable;
import com.hkust.marketplace.ui.ReviewList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The listing detail screen (UC8 step 7; Task 1.4): all photos, all the listing's fields, the
 * full description, the seller's name with average rating and reviews, the availability
 * status, and three buttons: the action for the listing type (disabled when the listing is
 * not available), Chat with Seller and Report Listing.
 *
 * <p>The photos, fields and description are the shared {@link ListingOverview}, which the
 * admin's listing screen also shows; this screen adds the buyer's parts below it. The buttons
 * and the seller's name do nothing by themselves. The dashboard connects them to Make Offer
 * (UC5), Chat About Listing (UC6), Report Listing (UC9) and the seller's profile (UC8 A3) with
 * the {@code setOn...} methods (UC8 Special Requirement 3).
 * What the screen says comes from the tested {@link ListingSummary}.
 */
public class ListingDetailView extends ScrollPane implements Refreshable {

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
        ListingSummary summary = ListingSummary.of(detail.listing(), UserRole.BUYER, store);

        VBox page = new VBox(16, new ListingOverview(summary, store),
                buttons(summary, detail.available()), seller(detail.seller()));
        page.setPadding(new Insets(4, 12, 12, 4));
        setContent(page);
        setFitToWidth(true);
    }

    /** How the screen reloads, for example when the timer returned the item and it is available again. */
    public void setOnRefresh(Runnable reload) {
        this.reload = reload;
    }

    @Override
    public void refresh() {
        reload.run();
    }

    /** What the action button does: start Make Offer (UC5) for this listing. */
    public void setOnAction(Runnable startOffer) {
        action.setOnAction(e -> startOffer.run());
    }

    /** What Chat with Seller does: open the chat about this listing (UC6). */
    public void setOnChat(Runnable openChat) {
        chat.setOnAction(e -> openChat.run());
    }

    /** What Report Listing does: start Report Listing (UC9). */
    public void setOnReport(Runnable startReport) {
        report.setOnAction(e -> startReport.run());
    }

    /** What choosing the seller's name does: show the seller's profile (UC8 A3). */
    public void setOnSellerProfile(Runnable showProfile) {
        sellerName.setOnAction(e -> showProfile.run());
    }

    // ---------- Layout: the buyer's parts below the shared overview ----------

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
        return ListingOverview.card(new VBox(10, name,
                new ReviewList(seller.rating(), seller.reviews())));
    }
}
