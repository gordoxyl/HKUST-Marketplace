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
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Chats screen, the same in the buyer and seller portals: every chat of the logged-in
 * user, the one with the newest message or offer first; choosing one opens it in a
 * {@link ChatView}.
 * <pre>{@code
 * show("Chats", new ChatList(new ChatService(), this));   // "this" is the dashboard (ContentHost)
 * }</pre>
 * The use cases open a chat from a listing, a deal or an incoming offer (UC6 step 1). This
 * screen is our addition: it is how a seller reaches a chat that a buyer started with a
 * message and no offer (see the assumptions in the supplementary notes).
 */
public class ChatList extends ScrollPane implements Refreshable {

    private final ChatService service;
    private final ContentHost host;
    private final Consumer<ChatView> onOpen;
    private final VBox list = new VBox(12);

    public ChatList(ChatService service, ContentHost host) {
        this(service, host, view -> { });
    }

    /**
     * @param onOpen called with each chat screen this list opens, before it is shown, so a
     *               portal can add its own options, such as the buyer's
     *               {@link ChatView#setOnMakeOffer Make Offer}
     */
    public ChatList(ChatService service, ContentHost host, Consumer<ChatView> onOpen) {
        this.service = service;
        this.host = host;
        this.onOpen = onOpen;
        list.setPadding(new Insets(4));
        setContent(list);
        setFitToWidth(true);
        refresh();
    }

    /** Reloads the list of chats. */
    @Override
    public final void refresh() {
        list.getChildren().clear();
        List<Conversation> chats;
        try {
            chats = service.getMyConversations();
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
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
        Label subtitle = new Label(ChatText.subtitle(chat));
        subtitle.getStyleClass().add("listing-card-line");
        Label preview = new Label(ChatText.preview(chat));
        preview.getStyleClass().add("chat-text");
        VBox about = new VBox(3, title, subtitle, preview);
        HBox.setHgrow(about, Priority.ALWAYS);

        Label time = new Label(chat.lastEntry().map(last -> Formats.dateTime(last.time())).orElse(""));
        time.getStyleClass().add("listing-card-date");
        time.setMinWidth(Region.USE_PREF_SIZE);
        Button open = new Button("Open Chat");
        open.getStyleClass().addAll("btn-primary", "btn-sm");
        open.setMinWidth(Region.USE_PREF_SIZE);
        open.setOnAction(e -> open(chat));

        HBox item = new HBox(14, about, time, open);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("card");
        item.setPadding(new Insets(14, 16, 14, 16));
        item.setCursor(Cursor.HAND);
        item.setOnMouseClicked(e -> open(chat));
        return item;
    }

    private void open(Conversation chat) {
        ChatView view = new ChatView(service, chat);
        onOpen.accept(view);
        host.show(ChatText.title(chat), view);
    }
}
