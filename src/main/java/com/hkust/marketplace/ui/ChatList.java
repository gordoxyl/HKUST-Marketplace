package com.hkust.marketplace.ui;

import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.Conversation;
import com.hkust.marketplace.services.ServiceException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * The chat screen, the same in the buyer and seller portals (UC6 Chat About Listing; Tasks 1.5
 * and 2.5): every chat of the logged-in user in a list, the one with the newest message or
 * offer first, and the chosen chat ({@link ChatView}) beside the list, as in a desktop
 * messenger, with a light grey line between them. The list can be hidden, to give the chat
 * the whole width, and shown again (assumptions register 90).
 * <pre>{@code
 * show("Chats", new ChatList(chatService, this, onOpen));                         // the Chats page
 * new ChatList(chatService, this, onOpen).openFrom(chat, "Back to listing", back); // from another screen
 * }</pre>
 * Every chat opens here, with Back above it ({@link SubScreen}): "Back to Chats" when it was
 * chosen on the Chats page (assumptions register 89), otherwise back to the screen it was
 * opened from. The use cases open a chat from a listing, a deal or an incoming offer (UC6 step
 * 1); the Chats page is our addition, and it is how a seller reaches a chat that a buyer
 * started with a message and no offer (see the assumptions in the supplementary notes).
 *
 * <p>{@code onOpen} gets every chat this screen opens, before it is shown, so a portal can add
 * its own options to it: the buyer's Make Offer and Agree to Counter, the seller's link to
 * Offers &amp; Requests.
 */
public class ChatList extends VBox implements Refreshable {

    /** The Chats page's title, which the page shows again when its chat is closed. */
    private static final String CHATS_TITLE = "Chats";
    private static final String BACK_TO_CHATS = "Back to Chats";
    private static final String HIDE_LIST = "◀ Hide chats";
    private static final String SHOW_LIST = "▶ Show chats";
    private static final double LIST_WIDTH = 340;

    private final ChatService service;
    private final ContentHost host;
    private final Consumer<ChatView> onOpen;
    private final VBox list = new VBox(10);
    private final ScrollPane listPane = new ScrollPane(list);
    /** The light grey line between the list and the chat; it hides with the list. */
    private final Region divider = new Region();
    private final StackPane chatPane = new StackPane();
    private final Button toggle = new Button(HIDE_LIST);
    /** The chats as last loaded, newest first. */
    private List<Conversation> chats = List.of();
    /** The chat shown beside the list; null while none is open. */
    private ChatView open;
    /** Where Back leads from an open chat: this page's list, unless {@link #openFrom} named another screen. */
    private String backLabel = BACK_TO_CHATS;
    private Runnable back = this::closeChat;

    public ChatList(ChatService service, ContentHost host) {
        this(service, host, view -> { });
    }

    /**
     * @param onOpen called with each chat screen this list opens, before it is shown, so a
     *               portal can add its own options, such as the buyer's
     *               {@link ChatView#setOnMakeOffer Make Offer}
     */
    public ChatList(ChatService service, ContentHost host, Consumer<ChatView> onOpen) {
        super(10);
        this.service = service;
        this.host = host;
        this.onOpen = onOpen;
        setPadding(new Insets(4));

        list.setPadding(new Insets(4));
        listPane.setFitToWidth(true);
        listPane.setPrefWidth(LIST_WIDTH);
        listPane.setMinWidth(LIST_WIDTH);
        toggle.getStyleClass().addAll("btn-outline", "btn-sm");
        toggle.setOnAction(e -> showList(!listPane.isVisible()));
        divider.getStyleClass().add("chat-divider");
        HBox.setHgrow(chatPane, Priority.ALWAYS);
        HBox split = new HBox(12, listPane, divider, chatPane);
        VBox.setVgrow(split, Priority.ALWAYS);
        getChildren().addAll(toggle, split);

        showNoChat();
        refresh();
    }

    /**
     * Shows this chat screen as the host's page with the chat open, coming from another screen
     * (UC6 step 1: from a listing, a deal or an incoming offer). Back leads to that screen.
     * @param chat      the chat, as {@link ChatService#openForListing} or {@link ChatService#openForOffer} gives it
     * @param backLabel where Back leads, such as "Back to listing"
     * @param back      shows that screen again
     */
    public void openFrom(Conversation chat, String backLabel, Runnable back) {
        this.backLabel = backLabel;
        this.back = back;
        open(chat);
    }

    /** Reloads the list of chats and the open chat, for example after the timer changed the data. */
    @Override
    public final void refresh() {
        if (reloadList() && open != null) {
            open.refresh();
        }
    }

    /** Loads the chats again and lists them; false if they could not be loaded (the problem was shown). */
    private boolean reloadList() {
        try {
            chats = service.getMyConversations();
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return false;
        }
        showChats();
        return true;
    }

    /** Lists the chats as last loaded, with the open one marked. */
    private void showChats() {
        list.getChildren().clear();
        if (chats.isEmpty()) {
            Label empty = new Label("You have no chats yet. A chat starts when a buyer writes to a seller "
                    + "or makes an offer on a listing.");
            empty.setWrapText(true);
            empty.getStyleClass().add("text-muted");
            list.getChildren().add(empty);
            return;
        }
        for (Conversation chat : chats) {
            list.getChildren().add(item(chat));
        }
    }

    private Node item(Conversation chat) {
        Label title = new Label(chat.listing().getTitle());
        title.getStyleClass().add("listing-card-title");
        title.setWrapText(true);
        Label subtitle = new Label(ChatText.subtitle(chat));
        subtitle.getStyleClass().add("listing-card-line");
        Label preview = new Label(ChatText.preview(chat));
        preview.getStyleClass().add("chat-text");

        Label time = new Label(chat.lastEntry().map(last -> Formats.dateTime(last.time())).orElse(""));
        time.getStyleClass().add("listing-card-date");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button openButton = new Button("Open Chat");
        openButton.getStyleClass().addAll("btn-primary", "btn-sm");
        openButton.setMinWidth(Region.USE_PREF_SIZE);
        openButton.setOnAction(e -> open(chat));
        HBox bottom = new HBox(8, time, spacer, openButton);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox item = new VBox(4, title, subtitle, preview, bottom);
        item.getStyleClass().add("card");
        if (open != null && open.conversation().isSameChat(chat)) {
            item.getStyleClass().add("chat-item-selected");
        }
        item.setPadding(new Insets(12, 14, 12, 14));
        item.setCursor(Cursor.HAND);
        item.setOnMouseClicked(e -> open(chat));
        return item;
    }

    /** Shows the chat beside the list, marks it in the list, and shows the screen with Back above it. */
    private void open(Conversation chat) {
        open = new ChatView(service, chat);
        open.setOnHistoryChanged(this::reloadList); // a message or offer moves this chat to the top
        onOpen.accept(open);
        chatPane.getChildren().setAll(open);
        showToggle(true);
        showChats();
        host.show(ChatText.title(chat), new SubScreen(backLabel, back, this));
    }

    /** "Back to Chats": the Chats page as it first shows, the list with no chat open. */
    private void closeChat() {
        open = null;
        showNoChat();
        showList(true);
        showChats();
        host.show(CHATS_TITLE, this);
    }

    /** No chat open: a hint beside the list instead, and nothing to hide the list for. */
    private void showNoChat() {
        Label hint = new Label("Choose a chat to read and write its messages.");
        hint.getStyleClass().add("text-muted");
        chatPane.getChildren().setAll(hint);
        showToggle(false);
    }

    private void showToggle(boolean shown) {
        toggle.setVisible(shown);
        toggle.setManaged(shown);
    }

    /** Shows or hides the list of chats; while it is hidden, the open chat takes the whole width. */
    private void showList(boolean shown) {
        listPane.setVisible(shown);
        listPane.setManaged(shown);
        divider.setVisible(shown);
        divider.setManaged(shown);
        toggle.setText(shown ? HIDE_LIST : SHOW_LIST);
    }
}
