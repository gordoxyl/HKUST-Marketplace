package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.Conversation;
import com.hkust.marketplace.services.OfferAction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dialog behind the deal actions beside Send in a chat (assumptions register 71): the
 * offers the viewer can act on, newest first with the newest chosen, and one button per
 * action. The seller answers an offer (Accept, Decline, Counter); the buyer agrees to a
 * counter (Agree). Both portals get the same dialog.
 *
 * <ul>
 *   <li>The list scrolls after about four offers, so the dialog keeps its size and its
 *       buttons stay in view.</li>
 *   <li>The actions are on the left and Cancel is on its own at the right; Esc and the
 *       window's close button cancel too.</li>
 *   <li>An action that the chosen offer does not allow is disabled, as on the seller's
 *       Offers &amp; Requests: a Countered offer can only be declined.</li>
 * </ul>
 *
 * <p>A boundary class only (Lecture 15): which offers and actions there are comes from
 * {@link Conversation#offersToActOn()} and {@link OfferAction}, and the wording from
 * {@link ChatText} and {@link OfferText}.
 */
final class OfferChoiceDialog {

    /** About four offers fit before the list scrolls. */
    private static final double LIST_HEIGHT = 170;
    private static final double LIST_WIDTH = 460;

    /** The offer the viewer chose, and what to do with it. */
    record Choice(Offer offer, OfferAction action) { }

    private OfferChoiceDialog() {
        // Utility class
    }

    /**
     * Shows the offers and waits for the viewer's choice.
     * @param offers the offers to choose from, newest first; at least one
     * @return the chosen offer and action, or empty if the viewer cancelled
     */
    static Optional<Choice> show(Conversation conversation, List<Offer> offers) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(ChatText.choiceTitle(conversation));
        dialog.setHeaderText(ChatText.choiceHeading(conversation, offers.size()));
        dialog.getDialogPane().setPadding(new Insets(10));

        Map<ButtonType, OfferAction> actions = new LinkedHashMap<>();
        for (OfferAction action : OfferAction.buttons(conversation.listing().getType(), conversation.viewerIsBuyer())) {
            ButtonType button = new ButtonType(ChatText.actionLabel(action), ButtonBar.ButtonData.LEFT);
            actions.put(button, action);
            dialog.getDialogPane().getButtonTypes().add(button);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        actions.forEach((button, action) ->
                dialog.getDialogPane().lookupButton(button).getStyleClass().add(styleClass(action)));

        ToggleGroup choices = new ToggleGroup();
        VBox list = new VBox(4);
        for (Offer offer : offers) {
            list.getChildren().add(row(conversation, offer, choices));
        }
        ScrollPane listPane = new ScrollPane(list);
        listPane.setFitToWidth(true);
        listPane.setMaxHeight(LIST_HEIGHT);
        listPane.setPrefWidth(LIST_WIDTH);
        dialog.getDialogPane().setContent(listPane);

        choices.selectedToggleProperty().addListener(
                (property, before, chosen) -> enableFor(dialog, actions, conversation, chosen));
        choices.selectToggle(choices.getToggles().get(0)); // the newest
        Dialogs.placeOnAppWindow(dialog);

        return dialog.showAndWait()
                .filter(actions::containsKey)
                .map(button -> new Choice((Offer) choices.getSelectedToggle().getUserData(), actions.get(button)));
    }

    /** One offer to choose: what it proposed and when, with its status badge as in the chat history. */
    private static HBox row(Conversation conversation, Offer offer, ToggleGroup choices) {
        RadioButton option = new RadioButton(ChatText.choice(conversation, offer));
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

    /** Enables the buttons of the actions the chosen offer allows, and disables the others. */
    private static void enableFor(Dialog<ButtonType> dialog, Map<ButtonType, OfferAction> actions,
                                  Conversation conversation, Toggle chosen) {
        List<OfferAction> allowed = chosen == null ? List.of() : conversation.actionsFor((Offer) chosen.getUserData());
        actions.forEach((button, action) ->
                dialog.getDialogPane().lookupButton(button).setDisable(!allowed.contains(action)));
    }

    /** The same button colours as on Offers &amp; Requests and My Deals. */
    private static String styleClass(OfferAction action) {
        return switch (action) {
            case ACCEPT -> "btn-success";
            case DECLINE -> "btn-danger";
            case COUNTER -> "btn-outline";
            case AGREE_TO_COUNTER -> "btn-primary";
        };
    }
}
