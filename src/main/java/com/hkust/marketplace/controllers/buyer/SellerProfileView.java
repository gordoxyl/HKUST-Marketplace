package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.services.SellerProfile;
import com.hkust.marketplace.ui.BrowseText;
import com.hkust.marketplace.ui.ReviewList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A seller's profile as a buyer sees it (UC8 A3; Task 1.10): full name, bio, average rating,
 * and the seller's reviews, each with its rating, comment, date and the seller's reply.
 */
public class SellerProfileView extends ScrollPane {

    public SellerProfileView(SellerProfile seller) {
        Label name = new Label(seller.fullName());
        name.getStyleClass().add("section-title");
        Label username = new Label("@" + seller.username());
        username.getStyleClass().add("listing-card-line");
        Label bio = new Label(BrowseText.bio(seller.bio()));
        bio.setWrapText(true);
        bio.setMinHeight(Region.USE_PREF_SIZE);
        bio.getStyleClass().add("chat-text");

        VBox about = new VBox(6, name, username, bio);
        about.getStyleClass().add("card");
        about.setPadding(new Insets(14, 16, 14, 16));

        VBox reviews = new VBox(new ReviewList(seller.averageRating(), seller.ratingCount(), seller.reviews()));
        reviews.getStyleClass().add("card");
        reviews.setPadding(new Insets(14, 16, 14, 16));

        VBox page = new VBox(16, about, reviews);
        page.setPadding(new Insets(4, 12, 12, 4));
        setContent(page);
        setFitToWidth(true);
        setStyle("-fx-background-color: transparent;");
    }
}
