package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.services.Amounts;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.Formats;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * What {@link SellerOfferAnswers} asks the seller, built with the shared {@link Dialogs}: the
 * same confirmation, information and {@link Dialogs#form form} dialogs as Make Offer and Review
 * Seller in the buyer's portal (non-functional requirement: a consistent interaction across the
 * portals). The counter forms are owned by the app's window, so they use the app stylesheet and
 * are centred on it, and their rules are under the fields with {@link Dialogs#hint}.
 *
 * <p>A form only collects what was typed: whether a price or a period is acceptable is decided
 * by {@code SellerOfferService}, and {@link SellerOfferAnswers} shows the refusals and opens
 * the form again with what was typed.
 */
final class DialogPrompts implements SellerOfferAnswers.SellerPrompts {

    private static final ButtonType SEND = new ButtonType("Send counter", ButtonBar.ButtonData.OK_DONE);

    @Override
    public boolean confirm(String title, String message) {
        return Dialogs.confirm(title, message);
    }

    @Override
    public void info(String title, String message) {
        Dialogs.info(title, message);
    }

    @Override
    public void problem(ServiceException e) {
        Dialogs.problem(e);
    }

    /**
     * The price box starts with what the seller typed before, or with the buyer's price the
     * first time. The amount rules are in {@code SellerOfferService}.
     */
    @Override
    public Optional<String> askCounterPrice(PriceOffer offer, String typedBefore) {
        TextField priceBox = new TextField(typedBefore != null
                ? typedBefore
                : Amounts.plain(offer.getOfferedPrice()));
        priceBox.setPromptText("such as 150 or 99.50");

        Dialog<ButtonType> dialog = Dialogs.form("Counter price offer",
                "Counter " + DataStore.getInstance().getFullName(offer.getBuyerUsername())
                        + "'s price offer", SEND, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8,
                new Label("New price (HK$)"), priceBox,
                Dialogs.hint("Type whole dollars with up to two decimals, such as 150 or 99.50.")));
        dialog.setOnShown(e -> priceBox.requestFocus());
        return dialog.showAndWait().filter(SEND::equals).map(button -> priceBox.getText());
    }

    /**
     * A date and a time of day for the start and for the end, as on the buyer's Request Borrow
     * form. Seconds can be typed, because the shortest borrow period is a few seconds; each pair
     * is turned into a time with {@code TextInputs.dateTime}.
     */
    @Override
    public Optional<SellerOfferAnswers.SellerPrompts.Period> askCounterPeriod(
            BorrowRequest request, SellerOfferAnswers.SellerPrompts.Period before) {
        LocalDateTime proposedStart = request.getProposedStartDate();
        LocalDateTime proposedEnd = request.getProposedEndDate();
        DatePicker startDate = new DatePicker(before != null
                ? before.startDate() : proposedStart.toLocalDate());
        TextField startTime = new TextField(before != null
                ? before.startTime() : Formats.timeOfDay(proposedStart));
        DatePicker endDate = new DatePicker(before != null
                ? before.endDate() : proposedEnd.toLocalDate());
        TextField endTime = new TextField(before != null
                ? before.endTime() : Formats.timeOfDay(proposedEnd));
        for (TextField time : new TextField[]{startTime, endTime}) {
            time.setPromptText("HH:mm or HH:mm:ss");
            time.setPrefWidth(150);
        }

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Start"), startDate, startTime);
        form.addRow(1, new Label("End"), endDate, endTime);
        Label rules = Dialogs.hint("The buyer asked for " + Formats.period(proposedStart, proposedEnd)
                + ". The period cannot start in the past and must last at least "
                + LendListing.formatDuration(BorrowRequest.MIN_BORROW_TIME) + ".");

        Dialog<ButtonType> dialog = Dialogs.form("Counter borrow request",
                "Counter " + DataStore.getInstance().getFullName(request.getBuyerUsername())
                        + "'s borrow request", SEND, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(10, form, rules));
        return dialog.showAndWait().filter(SEND::equals).map(button ->
                new SellerOfferAnswers.SellerPrompts.Period(startDate.getValue(), startTime.getText(),
                        endDate.getValue(), endTime.getText()));
    }
}
