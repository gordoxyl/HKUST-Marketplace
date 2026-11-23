package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.RatingSummary;
import com.hkust.marketplace.models.Review;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A seller's average rating and reviews, each with its stars, date, comment and the seller's
 * reply, if any. Shown the same way on the listing detail screen and on the seller's profile
 * (UC8 step 7 and A3; Task 1.10).
 * <pre>{@code
 * new ReviewList(profile.rating(), profile.reviews())
 * }</pre>
 * Pass only the reviews that may be shown: {@code DataStore.getReviewsForSeller} already
 * leaves out the ones the admin hid. The wording is in the tested {@link ReviewText}.
 *
 * <p>It names no writer, on purpose: UC8 step 7 and A3 list only the rating, comment, date
 * and reply (team decision, 20 Nov 2026). The seller's and admin's review screens, whose
 * steps include the buyer, name the writer with {@code DataStore.getReviewerName}, which
 * gives "Deleted User" for a deleted buyer (client answer 6).
 */
public class ReviewList extends VBox {

    /** @param rating the seller's rating, counted from the same reviews ({@code SellerProfile} makes sure of it) */
    public ReviewList(RatingSummary rating, List<Review> reviews) {
        super(8);
        Label heading = new Label(ReviewText.ratingHeading(rating));
        heading.getStyleClass().add("listing-card-title");
        getChildren().add(heading);
        for (Review review : reviews) {
            getChildren().add(item(review));
        }
    }

    private static VBox item(Review review) {
        Label header = new Label(ReviewText.header(review));
        header.getStyleClass().add("review-stars");
        VBox item = new VBox(3, header, text(ReviewText.comment(review), "chat-text"));
        ReviewText.reply(review).ifPresent(reply -> item.getChildren().add(text(reply, "listing-card-line")));
        item.getStyleClass().add("notif-item");
        return item;
    }

    private static Label text(String content, String styleClass) {
        Label label = new Label(content);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); // wrap long text instead of cutting it short
        label.getStyleClass().add(styleClass);
        return label;
    }
}
