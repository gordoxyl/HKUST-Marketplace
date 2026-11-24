package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Offer;
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

    // ---------- Deal actions beside Send (assumptions register 71) ----------

    /** The buyer's deal action while the seller has countered, and the title of its dialog. */
    public static final String AGREE_TO_COUNTER = "Agree to Counter";
    /** The button in that dialog that agrees to the chosen counter. */
    public static final String AGREE = "Agree";

    /** The buyer's button while the seller has countered: "Agree to Counter (1)". */
    public static String agreeButton(int countered) {
        return AGREE_TO_COUNTER + " (" + countered + ")";
    }

    /**
     * The seller's button while the chat has open offers: "Respond in Offers &amp; Requests (2)".
     * It is a link: the seller answers offers only on Offers &amp; Requests (Task 2.5).
     */
    public static String respondButton(int waiting) {
        return "Respond in Offers & Requests (" + waiting + ")";
    }

    /**
     * The heading of the Agree to Counter dialog: whose counters, and the listing. "Agree to a
     * counter from Sam Lee" with "'Calculus Textbook', newest first" under it; the order is
     * mentioned only when there is more than one counter.
     */
    public static String choiceHeading(Conversation conversation, int counters) {
        return "Agree to a counter from " + conversation.otherPartyName() + "\n'" + conversation.listing().getTitle()
                + "'" + (counters > 1 ? ", newest first" : "");
    }

    /**
     * One countered offer in that dialog: the buyer's terms, the seller's counter, and when the
     * offer was made: "HK$ 150 → HK$ 170 · 18 Nov 2026 22:13".
     */
    public static String choice(Offer offer) {
        return OfferText.terms(offer) + OfferText.counterTerms(offer).map(counter -> " → " + counter).orElse("")
                + " · " + Formats.dateTime(offer.getOfferDate());
    }
}
