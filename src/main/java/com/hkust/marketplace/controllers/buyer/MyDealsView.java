package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.Deal;
import com.hkust.marketplace.services.MyDealsService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.DealText;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.OfferText;
import com.hkust.marketplace.ui.Refreshable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The My Deals screen (UC7; Task 1.6): the buyer's current deals, followed by the completed
 * ones. Each deal shows its listing, seller, type, status, terms, the seller's counter and,
 * for a borrowed item, the due date; and it has the buttons for what can be done with it.
 *
 * <p>This is the use case's boundary (Lecture 15). Which buttons a deal gets is decided by
 * {@link MyDealsService} ({@link Deal#canBeMarkedComplete()} and the like), and the wording
 * is in the tested {@link DealText} and {@link OfferText}. This class asks the buyer to
 * confirm (steps 4-5; A3), calls the service, and hands the rest to the dashboard through
 * {@link Actions}: reopening the listing or the chat (A4), agreeing to a counter (UC5) and
 * rating the seller (UC10).
 */
public class MyDealsView extends ScrollPane implements Refreshable {

    /** What the dashboard does when the buyer leaves My Deals for another use case. */
    public interface Actions {

        /** A4: show the deal's listing detail screen. */
        void viewListing(Deal deal);

        /** A4: show the deal's chat thread. */
        void openChat(Deal deal);

        /** Special Requirement 4: agree to the seller's counter, through Make Offer (UC5). */
        void agreeToCounter(Deal deal);

        /** Step 9 and Special Requirement 6: rate and review the seller, through Review Seller (UC10). */
        void rateSeller(Deal deal);
    }

    private final MyDealsService service;
    private final Actions actions;
    private final VBox list = new VBox(12);

    public MyDealsView(MyDealsService service, Actions actions) {
        this.service = service;
        this.actions = actions;
        list.setPadding(new Insets(4, 12, 12, 4));
        setContent(list);
        setFitToWidth(true);
        refresh();
    }

    /**
     * Reloads the deals: after one was completed or returned, when the buyer comes back to this
     * screen, or when the borrowing-deadline timer returned an item (UC12 step 4).
     */
    @Override
    public final void refresh() {
        list.getChildren().clear();
        List<Deal> deals;
        try {
            deals = service.getMyDeals();
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        if (deals.isEmpty()) {
            Label none = new Label(DealText.NO_DEALS); // A1
            none.setWrapText(true);
            none.getStyleClass().add("text-muted");
            list.getChildren().add(none);
            return;
        }
        section("Current deals", deals.stream().filter(Deal::isCurrent).toList());
        section("Completed deals", deals.stream().filter(deal -> !deal.isCurrent()).toList());
    }

    private void section(String title, List<Deal> deals) {
        if (deals.isEmpty()) {
            return;
        }
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        list.getChildren().add(heading);
        for (Deal deal : deals) {
            list.getChildren().add(card(deal));
        }
    }

    // ---------- One deal (UC7 step 2) ----------

    private Node card(Deal deal) {
        Offer offer = deal.offer();
        Label title = new Label(deal.listing().getTitle());
        title.getStyleClass().add("listing-card-title");
        Label status = new Label(OfferText.status(offer));
        status.getStyleClass().addAll("badge", OfferText.badgeStyle(offer));
        status.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(10, title, status);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(5, top, line(DealText.about(deal), "listing-card-line"),
                line(OfferText.summary(offer), "chat-offer-terms"));
        OfferText.counter(offer).ifPresent(counter -> card.getChildren().add(line(counter, "chat-text")));
        DealText.due(deal).ifPresent(due -> card.getChildren().add(line(due, "chat-text")));
        card.getChildren().addAll(line(DealText.dates(deal), "listing-card-date"), buttons(deal));
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));
        return card;
    }

    /** The buttons for what can be done with the deal now, then the two that reopen its listing and chat. */
    private HBox buttons(Deal deal) {
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(4, 0, 0, 0));
        if (deal.canBeMarkedComplete()) {
            buttons.getChildren().add(button("Mark Complete", "btn-primary", () -> markComplete(deal)));
        }
        if (deal.canBeReturned()) {
            buttons.getChildren().add(button("Return Item", "btn-primary", () -> returnItem(deal)));
        }
        if (deal.hasCounter()) {
            buttons.getChildren().add(button("Agree to Counter", "btn-primary", () -> actions.agreeToCounter(deal)));
        }
        if (deal.canBeReviewed()) {
            buttons.getChildren().add(button("Rate Seller", "btn-primary", () -> actions.rateSeller(deal)));
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buttons.getChildren().addAll(spacer,
                button("View Listing", "btn-outline", () -> actions.viewListing(deal)),
                button("Open Chat", "btn-outline", () -> actions.openChat(deal)));
        return buttons;
    }

    // ---------- Mark complete (UC7 steps 3-9) and return (A2) ----------

    private void markComplete(Deal deal) {
        if (!Dialogs.confirm("Mark Complete", DealText.confirmComplete(deal))) {
            return; // A3: the deal is left unchanged
        }
        Deal completed;
        try {
            completed = service.markComplete(deal.offer().getId());
        } catch (ServiceException e) {
            Dialogs.problem(e);
            refresh();
            return;
        }
        refresh(); // step 7: shown as completed
        if (Dialogs.confirm("Deal completed", DealText.ratePrompt(completed))) { // step 9
            actions.rateSeller(completed);
        }
    }

    private void returnItem(Deal deal) {
        if (!Dialogs.confirm("Return Item", DealText.confirmReturn(deal))) {
            return; // A3
        }
        Deal returned;
        try {
            returned = service.returnItem(deal.offer().getId());
        } catch (ServiceException e) {
            Dialogs.problem(e);
            refresh();
            return;
        }
        refresh(); // shown as returned
        if (Dialogs.confirm("Item returned", DealText.returnedRatePrompt(returned))) { // client answer #3
            actions.rateSeller(returned);
        }
    }

    // ---------- Small parts ----------

    private static Button button(String text, String style, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll(style, "btn-sm");
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Label line(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
