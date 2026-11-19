package com.hkust.marketplace.ui;

import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.utils.SceneManager;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * The dialogs every portal uses, so confirmations, messages and errors look and behave the
 * same everywhere (non-functional requirement: consistent interaction across the three
 * portals). Destructive actions must ask {@link #confirm} first.
 * <pre>{@code
 * if (Dialogs.confirm("Delete listing", "Delete 'Desk Lamp'? This cannot be undone.")) {
 *     try {
 *         service.deleteListing(id);
 *         Dialogs.info("Listing deleted", "'Desk Lamp' was removed.");
 *     } catch (ServiceException e) {
 *         Dialogs.problem(e);
 *     }
 * }
 * }</pre>
 * This class only shows JavaFX dialogs; checks on what users type are in {@link TextInputs},
 * where they are unit-tested.
 */
public final class Dialogs {

    private Dialogs() {
        // Utility class
    }

    /** Asks the user to confirm; true only if they chose OK. */
    public static boolean confirm(String title, String message) {
        Alert alert = alert(Alert.AlertType.CONFIRMATION, title, message);
        return alert.showAndWait().filter(answer -> answer == ButtonType.OK).isPresent();
    }

    /** Tells the user that something worked. */
    public static void info(String title, String message) {
        alert(Alert.AlertType.INFORMATION, title, message).showAndWait();
    }

    /** Tells the user that something went wrong. */
    public static void error(String title, String message) {
        alert(Alert.AlertType.ERROR, title, message).showAndWait();
    }

    /** Shows why a service refused an action; the message is written for the user. */
    public static void problem(ServiceException e) {
        error("Not possible", e.getMessage());
    }

    /**
     * Asks for a reason, which is required, for example to reject or remove a listing. Asks
     * again if nothing is typed.
     * @return the reason without surrounding spaces, or empty if the user cancelled
     */
    public static Optional<String> askReason(String title, String question) {
        while (true) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle(title);
            dialog.setHeaderText(question);
            dialog.setContentText("Reason:");
            placeOnAppWindow(dialog);
            Optional<String> typed = dialog.showAndWait();
            if (typed.isEmpty()) {
                return Optional.empty(); // cancelled
            }
            Optional<String> reason = TextInputs.required(typed.get());
            if (reason.isPresent()) {
                return reason;
            }
            error(title, "A reason is required.");
        }
    }

    /**
     * An empty form dialog with a heading and two buttons, centred on the app's window, for a
     * screen to fill with its fields:
     * <pre>{@code
     * Dialog<ButtonType> dialog = Dialogs.form("Make Offer", "Your price offer for 'Desk Lamp'", SUBMIT, ButtonType.CANCEL);
     * dialog.getDialogPane().setContent(new VBox(8, new Label("Your price (HK$)"), priceBox));
     * }</pre>
     * A form dialog uses the app's stylesheet, because it has the app's window as its owner.
     * @param submit the button that sends the form
     * @param cancel the button that closes it without sending
     */
    public static Dialog<ButtonType> form(String title, String header, ButtonType submit, ButtonType cancel) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().addAll(submit, cancel);
        dialog.getDialogPane().setPadding(new Insets(10));
        placeOnAppWindow(dialog);
        return dialog;
    }

    /** A note in a form dialog, such as the rules for what to type. */
    public static Label hint(String text) {
        Label hint = new Label(text);
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        hint.getStyleClass().add("dialog-hint");
        return hint;
    }

    private static Alert alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        placeOnAppWindow(alert);
        return alert;
    }

    /**
     * Centres the dialog on the app's window, when there is one. With the window as its owner,
     * the dialog also uses the app's stylesheet.
     */
    static void placeOnAppWindow(Dialog<?> dialog) {
        Stage stage = SceneManager.getPrimaryStage();
        if (stage != null) {
            dialog.initOwner(stage);
        }
    }
}
