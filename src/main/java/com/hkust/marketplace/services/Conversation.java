package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The chat between one buyer and the seller of one listing, as one of them sees it (UC6
 * step 2): the listing, the two people, and the history in the order it was sent. It is a
 * copy made at one moment; {@link ChatService} returns a new one after each message.
 *
 * <p>A buyer who has not written or made an offer yet gets a conversation with no entries.
 * The chat thread itself is created by the buyer's first message (UC6 step 5) or first offer
 * (UC5 step 7).
 *
 * @param listing        the listing the chat is about
 * @param buyerUsername  the buyer in this chat; with the listing, this identifies the chat
 * @param buyerName      the buyer's full name
 * @param sellerName     the full name of the listing's seller
 * @param viewerUsername the logged-in participant this copy was made for
 * @param entries        messages and offers, oldest first
 */
public record Conversation(Listing listing, String buyerUsername, String buyerName, String sellerName,
                           String viewerUsername, List<ChatEntry> entries) {

    public Conversation {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(buyerUsername, "buyerUsername");
        Objects.requireNonNull(buyerName, "buyerName");
        Objects.requireNonNull(sellerName, "sellerName");
        Objects.requireNonNull(viewerUsername, "viewerUsername");
        entries = List.copyOf(entries);
    }

    /** True if this copy was made for the buyer, false if for the listing's seller. */
    public boolean viewerIsBuyer() {
        return viewerUsername.equalsIgnoreCase(buyerUsername);
    }

    /** The full name of the person the viewer is chatting with. */
    public String otherPartyName() {
        return viewerIsBuyer() ? sellerName : buyerName;
    }

    /** True if the viewer sent this message or made this offer. */
    public boolean isSentByViewer(ChatEntry entry) {
        return entry.senderUsername().equalsIgnoreCase(viewerUsername);
    }

    /** The newest message or offer, or empty if nothing has been sent yet. */
    public Optional<ChatEntry> lastEntry() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(entries.size() - 1));
    }
}
