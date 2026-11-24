package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.ReportReason;
import com.hkust.marketplace.services.ReportForm;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.ReportText;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * The form of Report Listing (UC9 steps 3-5, {Enter Report}): a reason from Special
 * Requirement 1's list, and the details.
 *
 * <p>A boundary class only (Lecture 15): it collects what the buyer chose and typed and hands
 * it over unchecked; whether the report is complete is decided by {@code ReportService}. The
 * form starts with no reason chosen, so the buyer chooses one (step 4). It returns empty when
 * the buyer cancels (A3).
 */
final class ReportDialog {

    private static final ButtonType SUBMIT = new ButtonType("Submit Report", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private ReportDialog() {
        // Utility class
    }

    /**
     * The form's contents exactly as entered, kept so that a form shown again has them, to be
     * corrected (A2).
     * @param reason  the chosen reason, or null if none is chosen
     * @param details the details as typed
     */
    record Entry(ReportReason reason, String details) {

        /** A new form: no reason chosen, nothing written. */
        static final Entry EMPTY = new Entry(null, "");
    }

    /**
     * Asks for the reason and the details.
     * @param entry what the form starts with: empty, or what the buyer entered before
     * @return the report as entered, or empty if the buyer cancelled (A3)
     */
    static Optional<Entry> ask(ReportForm form, Entry entry) {
        ComboBox<ReportReason> reason = new ComboBox<>(FXCollections.observableArrayList(form.reasons()));
        reason.setPromptText("Choose a reason");
        reason.setValue(entry.reason());
        reason.setMaxWidth(Double.MAX_VALUE);

        TextArea details = new TextArea(entry.details());
        details.setPromptText("What is wrong with this listing?");
        details.setWrapText(true);
        details.setPrefRowCount(4);
        details.setPrefColumnCount(32);

        Dialog<ButtonType> dialog = Dialogs.form("Report Listing", ReportText.formHeader(form), SUBMIT, CANCEL);
        dialog.getDialogPane().setContent(new VBox(8,
                new Label("Reason"), reason,
                new Label("Details"), details,
                Dialogs.hint(ReportText.HINT)));
        return dialog.showAndWait().filter(SUBMIT::equals)
                .map(button -> new Entry(reason.getValue(), details.getText()));
    }
}
