package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.Conversation;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * The dialog behind the buyer's Agree to Counter in a chat (UC5 step 1 and Special Requirement
 * 4; assumptions register 71): the offers the seller countered, newest first with the newest
 * chosen, each with the buyer's terms and the seller's counter. Agree returns the chosen offer,
 * and the chat then runs Make Offer with the counter's terms.
 *
 * <ul>
 *   <li>The list scrolls after about four offers, so the dialog keeps its size and its
 *       buttons stay in view.</li>
 *   <li>Agree is on the left and Cancel is on its own at the right; Esc and the window's close
 *       button cancel too.</li>
 * </ul>
 *
 * <p>A boundary class only (Lecture 15): which offers there are comes from
 * {@link Conversation#offersToActOn()}, and the wording from {@link ChatText} and
 * {@link OfferText}.
 */
final class OfferChoiceDialog {

    /** About four offers fit before the list scrolls. */
    private static final double LIST_HEIGHT = 170;
    private static final double LIST_WIDTH = 460;

    private OfferChoiceDialog() {
        // Utility class
    }

    /**
     * Shows the countered offers and waits for the buyer's choice.
     * @param conversation the buyer's chat
     * @param countered    the offers the seller countered, newest first; at least one
     * @return the chosen offer, or empty if the buyer cancelled
     */
    static Optional<Offer> show(Conversation conversation, List<Offer> countered) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(ChatText.AGREE_TO_COUNTER);
        dialog.setHeaderText(ChatText.choiceHeading(conversation, countered.size()));
        dialog.getDialogPane().setPadding(new Insets(10));

        ButtonType agree = new ButtonType(ChatText.AGREE, ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(agree, ButtonType.CANCEL);
        Node agreeButton = dialog.getDialogPane().lookupButton(agree);
        agreeButton.getStyleClass().add("btn-primary");

        ToggleGroup choices = new ToggleGroup();
        VBox list = new VBox(4);
        for (Offer offer : countered) {
            list.getChildren().add(row(offer, choices));
        }
        ScrollPane listPane = new ScrollPane(list);
        listPane.setFitToWidth(true);
        listPane.setMaxHeight(LIST_HEIGHT);
        listPane.setPrefWidth(LIST_WIDTH);
        dialog.getDialogPane().setContent(listPane);

        agreeButton.disableProperty().bind(choices.selectedToggleProperty().isNull());
        choices.selectToggle(choices.getToggles().get(0)); // the newest
        Dialogs.placeOnAppWindow(dialog);

        return dialog.showAndWait()
                .filter(agree::equals)
                .map(button -> (Offer) choices.getSelectedToggle().getUserData());
    }

    /** One countered offer to choose: the buyer's terms and the counter, with its status badge as in the chat history. */
    private static HBox row(Offer offer, ToggleGroup choices) {
        RadioButton option = new RadioButton(ChatText.choice(offer));
        option.setUserData(offer);
        option.setToggleGroup(choices);
        option.setWrapText(true);

        Label status = new Label(OfferText.status(offer));
        status.getStyleClass().addAll("badge", OfferText.badgeStyle(offer));
        status.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, option, spacer, status);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("chat-result");
        row.setOnMouseClicked(e -> option.setSelected(true));
        return row;
    }
}
