package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.Formats;
import com.hkust.marketplace.ui.TextInputs;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The forms of Make Offer (UC5 step 4, {Enter Terms}): one for the price of a price offer,
 * one for the period of a borrow request. A claim has no form.
 *
 * <p>These are boundary classes only (Lecture 15): they collect what the buyer typed and hand
 * it over unchecked. Whether a price or a period is acceptable is decided by
 * {@code OfferService}. Each form returns empty when the buyer cancels (A4).
 */
final class OfferDialogs {

    private static final ButtonType SUBMIT = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);

    private OfferDialogs() {
        // Utility class
    }

    /**
     * A borrow period exactly as it stands in the form: the chosen dates and the typed times.
     * Kept as entered, so that a form shown again has what the buyer typed, to be corrected.
     */
    record Period(LocalDate startDate, String startTime, LocalDate endDate, String endTime) {

        /** The form's contents for a suggested period; either part may be null. */
        static Period of(LocalDateTime start, LocalDateTime end) {
            return new Period(start == null ? null : start.toLocalDate(), Formats.timeOfDay(start),
                    end == null ? null : end.toLocalDate(), Formats.timeOfDay(end));
        }

        /** The start as a date and time, or null if the date is missing or the time is not a time of day. */
        LocalDateTime start() {
            return TextInputs.dateTime(startDate, startTime).orElse(null);
        }

        /** The end as a date and time, or null if the date is missing or the time is not a time of day. */
        LocalDateTime end() {
            return TextInputs.dateTime(endDate, endTime).orElse(null);
        }
    }

    /**
     * Asks for the price of a price offer.
     * @param price what the price box starts with: a suggestion, or what the buyer typed before
     * @return the price as typed, or empty if the buyer cancelled
     */
    static Optional<String> askPrice(Listing listing, String price) {
        TextField priceBox = new TextField(price);
        priceBox.setPromptText("such as 150 or 99.50");
        Label asking = Dialogs.hint("The seller asks " + listing.getPriceDisplay() + ". The seller can accept, decline or "
                + "counter your offer.");

        Dialog<ButtonType> dialog = dialog("Make Offer", "Your price offer for '" + listing.getTitle() + "'");
        dialog.getDialogPane().setContent(new VBox(8, new Label("Your price (HK$)"), priceBox, asking));
        dialog.setOnShown(e -> priceBox.requestFocus());
        return dialog.showAndWait().filter(SUBMIT::equals).map(button -> priceBox.getText());
    }

    /**
     * Asks for the period of a borrow request: a date and a time of day for the start and for
     * the end. Seconds can be typed, because the shortest period is a few seconds. The dates
     * read as on every other screen, "21 Nov 2026", whether chosen from the calendar or typed.
     * @param period what the boxes start with: a suggestion, or what the buyer entered before
     * @return the period as entered, or empty if the buyer cancelled
     */
    static Optional<Period> askPeriod(Listing listing, Period period) {
        DatePicker startDate = datePicker(period.startDate());
        TextField startTime = new TextField(period.startTime());
        DatePicker endDate = datePicker(period.endDate());
        TextField endTime = new TextField(period.endTime());
        for (TextField time : new TextField[] {startTime, endTime}) {
            time.setPromptText("HH:mm or HH:mm:ss");
            time.setPrefColumnCount(12); // room for the whole hint, and wider with a bigger font
        }

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Start"), startDate, startTime);
        form.addRow(1, new Label("End"), endDate, endTime);
        String lending = listing instanceof LendListing lend
                ? "The seller lends it for " + lend.getDurationDisplay() + ". " : "";
        Label rules = Dialogs.hint(lending + "The period cannot start in the past and must last at least "
                + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME) + ".");

        Dialog<ButtonType> dialog = dialog("Request Borrow", "Your borrow request for '" + listing.getTitle() + "'");
        dialog.getDialogPane().setContent(new VBox(10, form, rules));
        return dialog.showAndWait().filter(SUBMIT::equals).map(button -> new Period(
                shownDate(startDate), startTime.getText(), shownDate(endDate), endTime.getText()));
    }

    /**
     * A date picker that shows and reads its date in the form every screen uses, "21 Nov 2026"
     * ({@link Formats#datePickerConverter()}), and suggests that form while it is empty.
     */
    private static DatePicker datePicker(LocalDate date) {
        DatePicker picker = new DatePicker(date);
        picker.setConverter(Formats.datePickerConverter());
        picker.setPromptText("such as 21 Nov 2026");
        return picker;
    }

    /**
     * The date the picker shows now. A picker takes in typed text only on Enter or when it
     * loses focus, so a date typed just before Submit is read from the text here: the form never
     * sends a date other than the one on the screen. Text that is not a date gives no date,
     * which {@code OfferService} reports (A3).
     */
    private static LocalDate shownDate(DatePicker picker) {
        return picker.getConverter().fromString(picker.getEditor().getText());
    }

    /** A Make Offer form: Submit, or Cancel (A4). */
    private static Dialog<ButtonType> dialog(String title, String header) {
        return Dialogs.form(title, header, SUBMIT, ButtonType.CANCEL);
    }
}
