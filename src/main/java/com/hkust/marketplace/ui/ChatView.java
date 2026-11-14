package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.services.ChatEntry;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.Conversation;
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
import java.util.function.Consumer;

/**
 * The chat screen, the same in the buyer and seller portals (UC6 Chat About Listing; Tasks
 * 1.5 and 2.5): the history of messages and offers with sender and time, a box to write a
 * message, and a search of the history that jumps to the chosen message and highlights it.
 * <pre>{@code
 * Conversation chat = chatService.openForOffer(offer.getId());       // or openForListing(listingId)
 * host.show(ChatText.title(chat), new ChatView(chatService, chat));
 * }</pre>
 * The buyer portal adds the two ways into Make Offer (UC5 step 1) that start in a chat: the
 * Make Offer option with {@link #setOnMakeOffer}, and agreeing to a seller's counter with
 * {@link #setOnAgreeToCounter}.
 *
 * <p>All rules (who may read and write, empty messages, searching) are in the tested
 * {@link ChatService}, and the wording in {@link ChatText} and {@link OfferText}; this class
 * only lays the chat out.
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
    private final HBox buttons = new HBox(8);
    private Button makeOfferButton;
    /** What "Agree to Counter" on a countered offer does; null while the option is not offered. */
    private Consumer<Offer> agreeToCounter;

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

    /**
     * Adds the Make Offer option for the buyer (UC5 step 1: "the Make Offer option in the
     * listing's chat thread"), named after the listing type: Make Offer, Request Borrow or
     * Claim Item. The action runs Make Offer; afterwards the history is reloaded, so a new
     * offer appears in it. Nothing is added when the viewer is the seller.
     */
    public ChatView setOnMakeOffer(Runnable makeOffer) {
        buttons.getChildren().remove(makeOfferButton);
        if (conversation.viewerIsBuyer()) {
            makeOfferButton = new Button(OfferText.actionText(conversation.listing().getType()));
            makeOfferButton.getStyleClass().add("btn-outline");
            makeOfferButton.setOnAction(e -> {
                makeOffer.run();
                refresh();
            });
            buttons.getChildren().add(makeOfferButton);
        }
        return this;
    }

    /**
     * Adds an Agree to Counter button for the buyer on each of their offers that the seller
     * countered (UC5 step 1: "choosing to agree to a seller's counter on one of their earlier
     * offers"). The action runs Make Offer with the counter's terms filled in; afterwards the
     * history is reloaded. Nothing is added when the viewer is the seller.
     */
    public ChatView setOnAgreeToCounter(Consumer<Offer> agree) {
        agreeToCounter = agree;
        showHistory();
        return this;
    }

    /** Reloads the history, for example after an offer was made or the other party wrote. */
    @Override
    public final void refresh() {
        try {
            conversation = service.reload(conversation);
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        showHistory();
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
        buttons.getChildren().add(send);
        buttons.setAlignment(Pos.BOTTOM_LEFT);

        HBox composer = new HBox(10, input, buttons);
        composer.setAlignment(Pos.BOTTOM_LEFT);
        return composer;
    }

    // ---------- History (UC6 step 2) ----------

    private void showHistory() {
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
        if (agreeToCounter != null && conversation.viewerIsBuyer() && offer.getStatus() == OfferStatus.COUNTERED) {
            Button agree = new Button("Agree to Counter");
            agree.getStyleClass().addAll("btn-outline", "btn-sm");
            agree.setOnAction(e -> {
                agreeToCounter.accept(offer);
                refresh();
            });
            card.getChildren().add(agree);
        }
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
        listPane.setStyle("-fx-background-color: transparent;");

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
