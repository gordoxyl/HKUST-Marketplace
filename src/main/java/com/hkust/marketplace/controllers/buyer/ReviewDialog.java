package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.services.ReviewForm;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.ReviewText;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * The form of Review Seller (UC10 steps 3-5, {Enter Review}): the seller and the deal's
 * listing, a rating from 1 to 5 stars and the written review.
 *
 * <p>A boundary class only (Lecture 15): it collects what the buyer chose and typed and hands
 * it over unchecked; whether the review is complete is decided by {@code ReviewService}. The
 * form starts with no stars chosen, so the buyer chooses a rating (step 4), and a chosen button
 * can be clicked again to clear it (which A2 then reports). The form returns empty when the
 * buyer skips the review (A3).
 */
final class ReviewDialog {

    private static final ButtonType SUBMIT = new ButtonType("Submit Review", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType SKIP = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);

    private ReviewDialog() {
        // Utility class
    }

    /**
     * The form's contents exactly as entered, kept so that a form shown again has them, to be
     * corrected (A2).
     * @param rating  the chosen stars, or null if none is chosen
     * @param comment the written review as typed
     */
    record Entry(Integer rating, String comment) {

        /** A new form: no stars chosen, nothing written. */
        static final Entry EMPTY = new Entry(null, "");
    }

    /**
     * Asks for the rating and the written review.
     * @param entry what the form starts with: empty, or what the buyer entered before
     * @return the review as entered, or empty if the buyer skipped it (A3)
     */
    static Optional<Entry> ask(ReviewForm form, Entry entry) {
        ToggleGroup stars = new ToggleGroup();
        HBox starButtons = new HBox(6);
        for (int rating = Review.MIN_RATING; rating <= Review.MAX_RATING; rating++) {
            ToggleButton star = new ToggleButton(rating + " ★");
            star.setUserData(rating);
            star.setToggleGroup(stars);
            star.setSelected(Integer.valueOf(rating).equals(entry.rating()));
            starButtons.getChildren().add(star);
        }
        Label chosen = new Label(ReviewText.chosenRating(entry.rating()));
        chosen.getStyleClass().add("review-stars");
        stars.selectedToggleProperty().addListener(
                (property, before, now) -> chosen.setText(ReviewText.chosenRating(ratingOf(now))));

        TextArea comment = new TextArea(entry.comment());
        comment.setPromptText("How did the deal go? A written review is required.");
        comment.setWrapText(true);
        comment.setPrefRowCount(4);
        comment.setPrefColumnCount(32);

        Dialog<ButtonType> dialog = Dialogs.form("Review Seller", ReviewText.formHeader(form), SUBMIT, SKIP);
        dialog.getDialogPane().setContent(new VBox(8,
                new Label("Your rating"), starButtons, chosen,
                new Label("Your review"), comment,
                Dialogs.hint("Your rating and review show on the seller's profile and on each of their "
                        + "listings. They cannot be changed once submitted.")));
        return dialog.showAndWait().filter(SUBMIT::equals)
                .map(button -> new Entry(ratingOf(stars.getSelectedToggle()), comment.getText()));
    }

    /** The stars of a rating button, or null when no button is chosen. */
    private static Integer ratingOf(Toggle toggle) {
        return toggle == null ? null : (Integer) toggle.getUserData();
    }
}
