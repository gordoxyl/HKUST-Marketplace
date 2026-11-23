package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.ChatEntry;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.Conversation;
import com.hkust.marketplace.services.OfferAction;
import com.hkust.marketplace.services.ServiceException;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The chat screen, the same in the buyer and seller portals (UC6 Chat About Listing; Tasks
 * 1.5 and 2.5): the history of messages and offers with sender and time, a box to write a
 * message, and a search of the history that jumps to the chosen message and highlights it.
 * <pre>{@code
 * Conversation chat = chatService.openForOffer(offer.getId());       // or openForListing(listingId)
 * host.show(ChatText.title(chat), new ChatView(chatService, chat));
 * }</pre>
 *
 * <p><b>Deal actions.</b> The offers in the history only show what happened and their status.
 * What can be done with them is in one row under Send, the same in both portals, and a button
 * is there only while it is that person's turn (assumptions register 71):
 * <ul>
 *   <li>the buyer portal adds Make Offer with {@link #setOnMakeOffer} and, while the seller
 *       has countered, Agree to Counter with {@link #setOnAgreeToCounter} (UC5 step 1);</li>
 *   <li>the seller portal adds Answer Offers (or Answer Requests, Answer Claim) with
 *       {@link #setOnAnswerOffer}: the same actions as Offers &amp; Requests.</li>
 * </ul>
 * Agree to Counter and Answer Offers open {@code OfferChoiceDialog}, which lists the offers to
 * act on and returns the one chosen with its action.
 *
 * <p>All rules (who may read and write, empty messages, searching, which offers allow which
 * action) are in the tested {@link ChatService}, {@link Conversation} and {@link OfferAction};
 * the wording is in {@link ChatText} and {@link OfferText}. This class only lays the chat out.
 */
public class ChatView extends VBox implements Refreshable {

    private static final double BUBBLE_WIDTH = 520;

    private final ChatService service;
    private Conversation conversation;

    private final VBox history = new VBox(4);
    private final ScrollPane historyPane = new ScrollPane(history);
    /** The history's rows, in order: row i shows the entry at position i. */
    private final List<Node> rows = new ArrayList<>();
    private Node highlighted;
    /** True while the newest message should stay in view; false after jumping to a search result. */
    private boolean followNewest = true;

    private final TextField searchField = new TextField();
    private final VBox searchResults = new VBox(6);
    private final TextArea input = new TextArea();
    /** The deal actions under Send; empty and hidden while there is nothing to do. */
    private final HBox dealActions = new HBox(8);
    /** The buyer's Make Offer; null while it is not offered. */
    private Runnable makeOffer;
    /** The buyer's Agree to Counter; null while it is not offered. */
    private AgreeToCounter agreeToCounter;
    /** The seller's answers to offers; null while they are not offered. */
    private OfferAnswers offerAnswers;

    public ChatView(ChatService service, Conversation conversation) {
        super(12);
        this.service = service;
        this.conversation = conversation;
        setPadding(new Insets(4));

        history.getStyleClass().add("chat-history");
        history.heightProperty().addListener((obs, old, height) -> {
            if (followNewest) {
                historyPane.setVvalue(1); // keep the newest message in view as the history is laid out
            }
        });
        historyPane.setFitToWidth(true);
        VBox.setVgrow(historyPane, Priority.ALWAYS);

        searchResults.getStyleClass().add("card");
        searchResults.setPadding(new Insets(10, 14, 10, 14));
        hideSearch();

        getChildren().addAll(header(), searchResults, historyPane, composer());
        showHistory();
    }

    /** The chat this screen shows, as it was last loaded: the listing it is about and the people in it. */
    public Conversation conversation() {
        return conversation;
    }

    // ---------- Deal actions: what each portal plugs in ----------

    /**
     * What the buyer's Agree to Counter does with the countered offer they chose: Make Offer
     * with the counter's terms filled in (UC5 step 1 and Special Requirement 4).
     */
    @FunctionalInterface
    public interface AgreeToCounter {

        /**
         * Pre: the offer is one of this chat's offers and the seller has countered it.
         * @param countered the buyer's offer that the seller countered
         * @return true if a new offer was submitted; false if the buyer cancelled or it was refused
         */
        boolean agree(Offer countered);
    }

    /**
     * What the seller can do with an open offer from inside the chat: the same actions as Manage
     * Offers &amp; Requests 3.3-3.5 (UI consistency; assumptions register 71).
     *
     * <p>The chat calls these on the JavaFX thread, after the seller chose one offer in the
     * answer dialog and that dialog closed. Each method returns true if the offer was changed,
     * and false if the seller cancelled or the service refused. The chat reloads after every
     * call, and on false opens the answer dialog again. An implementation:
     * <ul>
     *   <li>asks its usual confirmation or counter form, calls the seller's service with
     *       {@code offer.getId()} (the object may be out of date), and shows the outcome;</li>
     *   <li>catches {@code ServiceException}, shows it, and returns false;</li>
     *   <li>does not change the screen: the seller stays in the chat.</li>
     * </ul>
     * Interactions added later come as {@code default} methods, so an implementation keeps
     * compiling (open-closed principle, Lecture 18).
     */
    public interface OfferAnswers {

        /**
         * Pre: the offer was Pending when the dialog opened, is on this chat's listing, and the
         * viewer is that listing's seller.
         * Post: if true, the offer is Accepted (the service also declined the other open
         * offers); if false, nothing changed.
         * @param offer the offer the seller chose
         * @return true if the offer was accepted
         */
        boolean accept(Offer offer);

        /**
         * Pre: the offer was open (Pending or Countered) when the dialog opened.
         * Post: if true, the offer is Declined; if false, nothing changed.
         * @param offer the offer the seller chose
         * @return true if the offer was declined
         */
        boolean decline(Offer offer);

        /**
         * Pre: the offer was Pending and is a price offer or a borrow request, never a claim.
         * Post: if true, the offer is Countered; if false, nothing changed.
         * @param offer the offer the seller chose
         * @return true if the offer was countered
         */
        boolean counter(Offer offer);
    }

    /**
     * Adds the Make Offer option for the buyer (UC5 step 1: "the Make Offer option in the
     * listing's chat thread"), named after the listing type: Make Offer, Request Borrow or
     * Claim Item. The action runs Make Offer; afterwards the history is reloaded, so a new
     * offer appears in it. Nothing is shown when the viewer is the seller.
     */
    public void setOnMakeOffer(Runnable makeOffer) {
        this.makeOffer = makeOffer;
        showActions();
    }

    /**
     * Adds Agree to Counter for the buyer (UC5 step 1: "choosing to agree to a seller's counter
     * on one of their earlier offers"). The button is shown while the seller has countered one
     * of the buyer's offers in this chat, with how many: "Agree to Counter (1)". It opens the
     * list of countered offers, each with the buyer's terms and the seller's counter, and Agree
     * runs {@code agree}. Nothing is shown when the viewer is the seller.
     */
    public void setOnAgreeToCounter(AgreeToCounter agree) {
        this.agreeToCounter = agree;
        showActions();
    }

    /**
     * Gives the chat the seller's answers. While the chat has open offers, the seller sees
     * Answer Offers (n), Answer Requests (n) or Answer Claim; it opens the list of open offers
     * with Accept, Decline and Counter (no Counter for a claim), which call {@code answers}.
     * Without answers, or when the viewer is the buyer, nothing is shown.
     */
    public void setOnAnswerOffer(OfferAnswers answers) {
        this.offerAnswers = answers;
        showActions();
    }

    /** Reloads the history, for example after an offer was made or the other party wrote. */
    @Override
    public final void refresh() {
        reload();
    }

    /** Reloads the history and the deal actions; false if the chat could not be loaded (the problem was shown). */
    private boolean reload() {
        try {
            conversation = service.reload(conversation);
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return false;
        }
        showHistory();
        return true;
    }

    // ---------- Layout ----------

    private Node header() {
        Label title = new Label(conversation.listing().getTitle());
        title.getStyleClass().add("listing-card-title");
        Label subtitle = new Label(ChatText.subtitle(conversation));
        subtitle.getStyleClass().add("listing-card-line");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField.setPromptText("Search this chat");
        searchField.setPrefWidth(220);
        searchField.setOnAction(e -> search());
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().addAll("btn-outline", "btn-sm");
        searchButton.setOnAction(e -> search());

        HBox header = new HBox(10, new VBox(2, title, subtitle), spacer, searchField, searchButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("card");
        header.setPadding(new Insets(12, 16, 12, 16));
        return header;
    }

    private Node composer() {
        input.setPromptText("Write a message. Enter sends it; Shift+Enter starts a new line.");
        input.setPrefRowCount(2);
        input.setWrapText(true);
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER) {
                return;
            }
            event.consume();
            if (event.isShiftDown()) {
                input.replaceSelection("\n");
            } else {
                send();
            }
        });
        HBox.setHgrow(input, Priority.ALWAYS);

        Button send = new Button("Send");
        send.getStyleClass().add("btn-primary");
        send.setOnAction(e -> send());

        HBox writing = new HBox(10, input, send);
        writing.setAlignment(Pos.BOTTOM_LEFT);
        dealActions.setAlignment(Pos.CENTER_RIGHT);
        return new VBox(8, writing, dealActions);
    }

    // ---------- History (UC6 step 2) ----------

    private void showHistory() {
        showActions();
        history.getChildren().clear();
        rows.clear();
        highlighted = null;
        followNewest = true;
        if (conversation.entries().isEmpty()) {
            Label empty = new Label("No messages yet.");
            empty.getStyleClass().add("listing-card-line");
            history.getChildren().add(empty);
            return;
        }
        for (ChatEntry entry : conversation.entries()) {
            Node row = entry.isOffer() ? offerRow(entry) : messageRow(entry);
            rows.add(row);
            history.getChildren().add(row);
        }
        historyPane.setVvalue(1);
    }

    /** A message: the viewer's own on the right, the other party's on the left. */
    private Node messageRow(ChatEntry entry) {
        boolean mine = conversation.isSentByViewer(entry);
        VBox bubble = new VBox(3, meta(entry), text(entry.content(), "chat-text"));
        bubble.getStyleClass().addAll("chat-bubble", mine ? "chat-mine" : "chat-theirs");
        bubble.setMaxWidth(BUBBLE_WIDTH);
        return row(bubble, mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    }

    /** An offer, borrow request or claim, shown in the middle with its current status (Special Requirement 3). */
    private Node offerRow(ChatEntry entry) {
        Offer offer = entry.offer();
        Label status = new Label(OfferText.status(offer));
        status.getStyleClass().addAll("badge", OfferText.badgeStyle(offer));
        status.setMinWidth(Region.USE_PREF_SIZE);
        HBox terms = new HBox(8, text(OfferText.summary(offer), "chat-offer-terms"), status);
        terms.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(3, meta(entry), terms);
        OfferText.counter(offer).ifPresent(counter -> card.getChildren().add(text(counter, "chat-text")));
        card.getStyleClass().add("chat-offer");
        card.setMaxWidth(BUBBLE_WIDTH);
        return row(card, Pos.CENTER);
    }

    private static Node row(Node content, Pos alignment) {
        HBox row = new HBox(content);
        row.setAlignment(alignment);
        row.getStyleClass().add("chat-row");
        return row;
    }

    /** Sender and time, above every message and offer. */
    private Label meta(ChatEntry entry) {
        Label meta = new Label(ChatText.header(conversation, entry));
        meta.getStyleClass().add("chat-meta");
        return meta;
    }

    private static Label text(String content, String styleClass) {
        Label label = new Label(content);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); // wrap long text instead of cutting it short
        label.getStyleClass().add(styleClass);
        return label;
    }

    // ---------- Deal actions under Send (assumptions register 71) ----------

    /** Shows the deal buttons that fit the chat as it is now, and hides the row when there are none. */
    private void showActions() {
        dealActions.getChildren().clear();
        int waiting = conversation.offersToActOn().size();
        if (conversation.viewerIsBuyer()) {
            addBuyerActions(waiting);
        } else {
            addSellerActions(waiting);
        }
        boolean any = !dealActions.getChildren().isEmpty();
        dealActions.setVisible(any);
        dealActions.setManaged(any);
    }

    /** Make Offer, and Agree to Counter while the seller has countered ({@code waiting} offers). */
    private void addBuyerActions(int waiting) {
        if (makeOffer != null) {
            dealActions.getChildren().add(actionButton(OfferText.actionText(conversation.listing().getType()), () -> {
                makeOffer.run();
                refresh();
            }));
        }
        if (agreeToCounter != null && waiting > 0) {
            dealActions.getChildren().add(actionButton(ChatText.agreeButton(waiting),
                    () -> actOnOffers(choice -> agreeToCounter.agree(choice.offer()))));
        }
    }

    /** Answer Offers, Answer Requests or Answer Claim while the chat has open offers ({@code waiting}). */
    private void addSellerActions(int waiting) {
        if (offerAnswers != null && waiting > 0) {
            dealActions.getChildren().add(actionButton(ChatText.answerButton(conversation.listing().getType(), waiting),
                    () -> actOnOffers(this::answer)));
        }
    }

    private static Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("btn-outline");
        button.setOnAction(e -> action.run());
        return button;
    }

    /** The seller's chosen answer, passed to the seller portal's {@link OfferAnswers}. */
    private boolean answer(OfferChoiceDialog.Choice choice) {
        return switch (choice.action()) {
            case ACCEPT -> offerAnswers.accept(choice.offer());
            case DECLINE -> offerAnswers.decline(choice.offer());
            case COUNTER -> offerAnswers.counter(choice.offer());
            case AGREE_TO_COUNTER -> throw new IllegalStateException("A seller cannot agree to a counter.");
        };
    }

    /**
     * Lists the offers the viewer can act on, runs the chosen action and reloads the chat. When
     * an action is cancelled or refused (it returns false), the list opens again, until the
     * viewer cancels it, an action succeeds, or no offer is left to act on.
     */
    private void actOnOffers(Function<OfferChoiceDialog.Choice, Boolean> act) {
        while (true) {
            List<Offer> offers = conversation.offersToActOn();
            if (offers.isEmpty()) {
                return;
            }
            Optional<OfferChoiceDialog.Choice> chosen = OfferChoiceDialog.show(conversation, offers);
            if (chosen.isEmpty()) {
                return; // cancelled
            }
            OfferChoiceDialog.Choice choice = chosen.get();
            // Precondition of the action (Lecture 10): the dialog enables only what the offer allows
            assert conversation.actionsFor(choice.offer()).contains(choice.action())
                    : "the chosen offer allows " + choice.action();
            boolean changed;
            try {
                changed = act.apply(choice);
            } catch (ServiceException e) { // a safety net: the action should show its own problems
                Dialogs.problem(e);
                changed = false;
            }
            if (!reload() || changed) {
                return;
            }
        }
    }

    // ---------- Writing (UC6 steps 3-6, A1) ----------

    private void send() {
        try {
            conversation = service.send(conversation, input.getText());
        } catch (ServiceException e) {
            Dialogs.problem(e); // A1: the message stays in the box
            input.requestFocus();
            return;
        }
        input.clear();
        hideSearch();
        showHistory();
        input.requestFocus();
    }

    // ---------- Searching (UC6 A2) ----------

    private void search() {
        String keyword = searchField.getText();
        List<ChatEntry> matches;
        try {
            conversation = service.reload(conversation);
            showHistory(); // the rows on screen now match the positions of the matches
            matches = service.search(conversation, keyword);
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }

        Label summary = new Label(ChatText.searchSummary(keyword, matches.size())); // A2 step 2
        summary.getStyleClass().add("chat-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("Close search");
        close.getStyleClass().addAll("btn-outline", "btn-sm");
        close.setOnAction(e -> hideSearch());
        HBox top = new HBox(8, summary, spacer, close);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(4);
        for (ChatEntry match : matches) {
            list.getChildren().add(resultRow(match));
        }
        ScrollPane listPane = new ScrollPane(list);
        listPane.setFitToWidth(true);
        listPane.setMaxHeight(150);

        searchResults.getChildren().setAll(top);
        if (!matches.isEmpty()) {
            searchResults.getChildren().add(listPane);
        }
        searchResults.setVisible(true);
        searchResults.setManaged(true);
    }

    /** One matching message with its sender and time; choosing it jumps to the message (A2 steps 3-4). */
    private Node resultRow(ChatEntry match) {
        Label line = new Label(ChatText.header(conversation, match) + "    "
                + Formats.preview(match.content(), ChatText.PREVIEW_LENGTH));
        line.getStyleClass().add("chat-text");
        HBox row = new HBox(line);
        row.getStyleClass().add("chat-result");
        row.setOnMouseClicked(e -> jumpTo(match.position()));
        return row;
    }

    private void jumpTo(int position) {
        if (position < 0 || position >= rows.size()) {
            return;
        }
        if (highlighted != null) {
            highlighted.getStyleClass().remove("chat-highlight");
        }
        highlighted = rows.get(position);
        highlighted.getStyleClass().add("chat-highlight");

        followNewest = false;
        Bounds line = highlighted.getBoundsInParent();
        historyPane.setVvalue(ChatText.scrollPosition(line.getMinY(), line.getHeight(),
                history.getHeight(), historyPane.getViewportBounds().getHeight()));
    }

    private void hideSearch() {
        searchResults.getChildren().clear();
        searchResults.setVisible(false);
        searchResults.setManaged(false);
        if (highlighted != null) {
            highlighted.getStyleClass().remove("chat-highlight");
            highlighted = null;
        }
    }
}
