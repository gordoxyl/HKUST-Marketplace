package com.hkust.marketplace.ui;

import com.hkust.marketplace.services.ChatEntry;
import com.hkust.marketplace.services.Conversation;

/**
 * The wording of the shared chat screens ({@link ChatView}, {@link ChatList}), kept apart
 * from the JavaFX code so it can be unit-tested.
 */
public final class ChatText {

    /** The longest preview of a message on the Chats screen and in search results. */
    static final int PREVIEW_LENGTH = 70;

    private ChatText() {
        // Utility class
    }

    /** The page title of a chat: "Chat: Desk Lamp". */
    public static String title(Conversation conversation) {
        return "Chat: " + conversation.listing().getTitle();
    }

    /** What the chat is about and with whom: "Sell · HK$ 180 · with Charlie Lee". */
    public static String subtitle(Conversation conversation) {
        return conversation.listing().getType().getDisplayName() + " · " + conversation.listing().getPriceDisplay()
                + " · with " + conversation.otherPartyName();
    }

    /** "You" for the viewer's own messages and offers, otherwise the sender's full name. */
    public static String who(Conversation conversation, ChatEntry entry) {
        return conversation.isSentByViewer(entry) ? "You" : entry.senderName();
    }

    /** The sender and time shown above every message and offer (UC6 step 2): "You · 09 Nov 2026 14:05". */
    public static String header(Conversation conversation, ChatEntry entry) {
        return who(conversation, entry) + " · " + Formats.dateTime(entry.time());
    }

    /** The message itself, or what an offer proposed, such as "Price offer: HK$ 160". */
    public static String body(ChatEntry entry) {
        return entry.isOffer() ? OfferText.summary(entry.offer()) : entry.content();
    }

    /** One line about the chat's newest message or offer, for the Chats screen: "You: Is it still available?". */
    public static String preview(Conversation conversation) {
        return conversation.lastEntry()
                .map(last -> who(conversation, last) + ": " + Formats.preview(body(last), PREVIEW_LENGTH))
                .orElse("No messages yet");
    }

    /**
     * The result of a search (UC6 A2 step 2): "No messages contain 'lamp'.",
     * "1 message contains 'lamp'." or "3 messages contain 'lamp'.".
     */
    public static String searchSummary(String keyword, int matches) {
        String quoted = "'" + keyword.strip() + "'";
        if (matches <= 0) {
            return "No messages contain " + quoted + ".";
        }
        return matches == 1 ? "1 message contains " + quoted + "." : matches + " messages contain " + quoted + ".";
    }

    /**
     * Where to scroll the history so that one line is in the middle of the visible part, with
     * the messages before and after it around it (UC6 A2 step 4).
     * @param top            the line's distance from the top of the history
     * @param height         the line's height
     * @param contentHeight  the height of the whole history
     * @param viewportHeight the height of the part that is visible
     * @return the scroll position from 0 (top) to 1 (bottom); 0 if the whole history is visible
     */
    public static double scrollPosition(double top, double height, double contentHeight, double viewportHeight) {
        double scrollable = contentHeight - viewportHeight;
        if (scrollable <= 0) {
            return 0;
        }
        double offset = top + height / 2 - viewportHeight / 2;
        return Math.max(0, Math.min(1, offset / scrollable));
    }
}
