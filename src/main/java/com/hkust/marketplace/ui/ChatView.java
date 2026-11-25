package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.ChatEntry;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.Conversation;
import com.hkust.marketplace.services.OfferAction;
import com.hkust.marketplace.services.ServiceException;
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

/**
 * The chat screen, the same in the buyer and seller portals (UC6 Chat About Listing; Tasks
 * 1.5 and 2.5): the history of messages and offers with sender and time, a box to write a
 * message, and a search of the history that jumps to the chosen message and highlights it.
 * A chat is shown in the chat screen, {@link ChatList}, beside the user's other chats:
 * <pre>{@code
 * Conversation chat = chatService.openForOffer(offer.getId());       // or openForListing(listingId)
 * new ChatList(chatService, host, onOpen).openFrom(chat, "Back to Offers & Requests", back);
 * }</pre>
 *
 * <p><b>Deal actions.</b> The offers in the history only show what happened and their status.
 * What can be done with them is in one row under Send, the same in both portals, and a button
 * is there only while it is that person's turn (assumptions register 71):
 * <ul>
 *   <li>the buyer portal adds Make Offer with {@link #setOnMakeOffer} and, while the seller
 *       has countered, Agree to Counter with {@link #setOnAgreeToCounter} (UC5 step 1). Agree
 *       to Counter opens {@code OfferChoiceDialog}, which lists the countered offers and
 *       returns the one chosen;</li>
 *   <li>the seller portal adds Respond in Offers &amp; Requests with
 *       {@link #setOnRespondToOffers}: a link, not an answer. The seller answers offers only on
 *       Offers &amp; Requests (Task 2.5; Manage Offers &amp; Requests 3.3-3.5), so the chat does
 *       not repeat that screen's actions.</li>
 * </ul>
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
    /** The seller's link to Offers &amp; Requests; null while it is not offered. */
    private RespondToOffers respondToOffers;
    /** Told when the user's own action has changed the history, such as the chat screen's list beside it. */
    private Runnable historyChanged = () -> { };

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
     * What the seller's Respond in Offers &amp; Requests does: the seller portal shows its Manage
     * Offers &amp; Requests screen with these offers marked ({@link Scrolling#JUMP_HIGHLIGHT}) and
     * the newest one scrolled into view ({@link Scrolling#bringIntoView}), as a chat search
     * result is (UC6 A2 step 4). The seller answers them there (Task 2.5; assumptions register 71).
     */
    @FunctionalInterface
    public interface RespondToOffers {

        /**
         * Pre: the viewer is this chat's seller, and {@code waiting} is not empty: this chat's open
         * offers (Pending or Countered) of its one buyer on its one listing, newest first, as
         * {@link Conversation#offersToActOn()} lists them.
         * Post: the chat itself has changed nothing.
         * @param waiting the offers the seller is to answer
         */
        void respond(List<Offer> waiting);
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
     * Gives the chat the seller's link to Offers &amp; Requests. While the chat has open offers,
     * the seller sees "Respond in Offers &amp; Requests (n)", which calls {@code respond} with
     * them. The chat never shows the seller Accept, Decline or Counter. Without the link, or
     * when the viewer is the buyer, nothing is shown.
     */
    public void setOnRespondToOffers(RespondToOffers respond) {
        this.respondToOffers = respond;
        showActions();
    }

    /**
     * What else follows when the user's own action changes this chat's history: a message sent,
     * or an offer made with Make Offer or Agree to Counter. The chat screen ({@link ChatList})
     * reloads its list of chats, so this chat moves to the top (assumptions register 90).
     */
    public void setOnHistoryChanged(Runnable changed) {
        this.historyChanged = changed;
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
                if (reload()) {
                    historyChanged.run();
                }
            }));
        }
        if (agreeToCounter != null && waiting > 0) {
            dealActions.getChildren().add(actionButton(ChatText.agreeButton(waiting), this::chooseCounterAndAgree));
        }
    }

    /** The link to Offers &amp; Requests while the chat has open offers ({@code waiting} of them). */
    private void addSellerActions(int waiting) {
        if (respondToOffers != null && waiting > 0) {
            dealActions.getChildren().add(actionButton(ChatText.respondButton(waiting),
                    () -> respondToOffers.respond(conversation.offersToActOn())));
        }
    }

    private static Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("btn-outline");
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * Lists the offers the seller countered, runs the buyer's Agree to Counter on the chosen one
     * and reloads the chat. When the buyer cancels the Make Offer form or it is refused (agree
     * returns false), the list opens again, until the buyer cancels it, an offer is submitted, or
     * no countered offer is left.
     */
    private void chooseCounterAndAgree() {
        while (true) {
            List<Offer> countered = conversation.offersToActOn();
            if (countered.isEmpty()) {
                return;
            }
            Optional<Offer> chosen = OfferChoiceDialog.show(conversation, countered);
            if (chosen.isEmpty()) {
                return; // cancelled
            }
            // Precondition of AgreeToCounter (Lecture 10): the dialog lists only countered offers
            assert conversation.actionsFor(chosen.get()).contains(OfferAction.AGREE_TO_COUNTER)
                    : "the chosen offer is countered";
            boolean submitted;
            try {
                submitted = agreeToCounter.agree(chosen.get());
            } catch (ServiceException e) { // a safety net: Make Offer shows its own problems
                Dialogs.problem(e);
                submitted = false;
            }
            if (!reload()) {
                return;
            }
            if (submitted) {
                historyChanged.run(); // a new offer in the history
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
        historyChanged.run();
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
            highlighted.getStyleClass().remove(Scrolling.JUMP_HIGHLIGHT);
        }
        highlighted = rows.get(position);
        highlighted.getStyleClass().add(Scrolling.JUMP_HIGHLIGHT);

        followNewest = false;
        Scrolling.bringIntoView(historyPane, highlighted);
    }

    private void hideSearch() {
        searchResults.getChildren().clear();
        searchResults.setVisible(false);
        searchResults.setManaged(false);
        if (highlighted != null) {
            highlighted.getStyleClass().remove(Scrolling.JUMP_HIGHLIGHT);
            highlighted = null;
        }
    }
}
