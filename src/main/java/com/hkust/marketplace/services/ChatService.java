package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * UC6 Chat About Listing, shared by the Buyer and the Seller (Tasks 1.5 and 2.5): the private
 * chat between one buyer and the seller of one listing.
 *
 * <p>How a screen uses it:
 * <pre>{@code
 * Conversation chat = chatService.openForListing(listing.getId());   // buyer, from the listing detail
 * Conversation chat = chatService.openForOffer(offer.getId());       // buyer from My Deals, seller from an offer
 * chat = chatService.send(chat, "Is it still available?");           // returns the updated chat
 * List<ChatEntry> matches = chatService.search(chat, "available");   // A2
 * }</pre>
 *
 * <p>Rules enforced here, for every call, whatever the screen does:
 * <ul>
 *   <li>Only the chat's buyer and the listing's seller can read, write or search it
 *       (Special Requirement 1). The admin cannot.</li>
 *   <li>Only the buyer starts a chat, with the first message or offer, and only on a live
 *       listing, one that Browse shows. An existing chat stays open to both of them
 *       whatever happens to the listing later, so its history is retained.</li>
 *   <li>An empty message is refused (A1). The other party is notified of every message
 *       (step 6; Special Requirement 4 for sellers).</li>
 *   <li>There is no way to edit or delete a message (Special Requirement 2). A chat is
 *       deleted only together with a participant's account, by the admin
 *       ({@code DataStore.deleteAccount}).</li>
 * </ul>
 */
public class ChatService {

    private final DataStore store;
    private final NotificationService notifications;

    public ChatService() {
        this(DataStore.getInstance());
    }

    public ChatService(DataStore store) {
        this.store = store;
        this.notifications = new NotificationService(store);
    }

    // ========== Opening a chat (UC6 steps 1-2) ==========

    /**
     * The logged-in buyer's chat about a listing, opened from the listing detail screen. If
     * the buyer has not started one yet, the chat is empty, and nothing is stored until the
     * first message.
     * @throws ServiceException if the listing does not exist, or the buyer has no chat about
     *         it and it is not on the marketplace
     */
    public Conversation openForListing(String listingId) {
        Buyer buyer = AccessControl.requireBuyer();
        return load(buyer, listingId, buyer.getUsername());
    }

    /**
     * The chat an offer, borrow request or claim belongs to: opened by the buyer from My Deals
     * (UC7 A4), or by the seller from the incoming offer (Manage Offers &amp; Requests 3.6).
     * @throws ServiceException      if the offer does not exist
     * @throws AccessDeniedException if the logged-in user is neither the offer's buyer nor the listing's seller
     */
    public Conversation openForOffer(String offerId) {
        User viewer = requireChatUser();
        Offer offer = store.findOffer(offerId);
        if (offer == null) {
            throw new ServiceException("This offer no longer exists.");
        }
        return load(viewer, offer.getListingId(), offer.getBuyerUsername());
    }

    /**
     * Every chat of the logged-in buyer or seller, the one with the newest message or offer
     * first. A seller sees the chats buyers started on the seller's listings.
     */
    public List<Conversation> getMyConversations() {
        User viewer = requireChatUser();
        return store.read(() -> {
            List<ChatThread> threads = viewer instanceof Buyer
                    ? store.getThreadsForBuyer(viewer.getUsername())
                    : store.getThreadsForSeller(viewer.getUsername());
            return threads.stream()
                    .map(thread -> conversation(viewer, store.findListing(thread.getListingId()),
                            thread.getBuyerUsername(), thread))
                    .sorted(Comparator.comparing(ChatService::lastActivity).reversed()) // stable: equal times keep their order
                    .toList();
        });
    }

    /** The same chat with its current history, for example after the other party wrote. */
    public Conversation reload(Conversation conversation) {
        return load(requireChatUser(), conversation.listing().getId(), conversation.buyerUsername());
    }

    // ========== Writing a message (UC6 steps 3-6) ==========

    /**
     * Sends a message in the chat as the logged-in participant: stores it with the sender and
     * the current time, creating the chat thread first if this is the buyer's first message
     * on the listing, and notifies the other party.
     * @param text the message; spaces and line breaks around it are dropped
     * @return the chat with the new message
     * @throws ServiceException if the message is empty (A1); nothing is stored then
     */
    public Conversation send(Conversation conversation, String text) {
        User sender = requireChatUser();
        String listingId = conversation.listing().getId();
        String buyerUsername = conversation.buyerUsername();
        return store.transaction(() -> {
            Listing listing = requireParticipant(sender, listingId, buyerUsername);
            String content = text == null ? "" : text.strip();
            if (content.isEmpty()) {
                throw new ServiceException("Please write a message before sending."); // A1
            }
            ChatThread thread = threadIfStarted(sender, listing, buyerUsername);
            if (thread == null) {
                thread = store.getOrCreateThread(buyerUsername, listingId); // the buyer's first message (step 5)
            }
            thread.addMessage(new Message(sender.getUsername(), content, store.now()));

            String otherParty = sender instanceof Buyer ? listing.getSellerUsername() : thread.getBuyerUsername();
            notifications.send(otherParty, NotificationCategory.NEW_MESSAGE,
                    sender.getFullName() + " sent you a message about '" + listing.getTitle() + "'.");
            return conversation(sender, listing, buyerUsername, thread);
        });
    }

    // ========== Searching the history (UC6 A2) ==========

    /**
     * The messages in the chat that contain the keyword, oldest first, each with its sender,
     * time and position in the history. Upper and lower case are treated as the same. Offers
     * are not searched: they have no text.
     * @return the matching messages; empty if no message contains the keyword
     * @throws ServiceException if no keyword is given
     */
    public List<ChatEntry> search(Conversation conversation, String keyword) {
        Conversation current = reload(conversation);
        String wanted = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            throw new ServiceException("Please enter a keyword to search for.");
        }
        return current.entries().stream()
                .filter(entry -> !entry.isOffer() && entry.content().toLowerCase(Locale.ROOT).contains(wanted))
                .toList();
    }

    // ========== Rules ==========

    /** Chat is for buyers and sellers; the admin takes no part in it. */
    private static User requireChatUser() {
        return AccessControl.requireRole(UserRole.BUYER, UserRole.SELLER);
    }

    /** The chat between the buyer and the listing as the viewer sees it, after checking that the viewer may. */
    private Conversation load(User viewer, String listingId, String buyerUsername) {
        return store.read(() -> {
            Listing listing = requireParticipant(viewer, listingId, buyerUsername);
            return conversation(viewer, listing, buyerUsername, threadIfStarted(viewer, listing, buyerUsername));
        });
    }

    /**
     * Special Requirement 1: the viewer must be the chat's buyer or the listing's seller.
     * @return the listing
     */
    private Listing requireParticipant(User viewer, String listingId, String buyerUsername) {
        Listing listing = store.findListing(listingId);
        if (listing == null) {
            throw new ServiceException("This listing no longer exists.");
        }
        boolean isBuyer = viewer instanceof Buyer && viewer.getUsername().equalsIgnoreCase(buyerUsername);
        boolean isSeller = viewer instanceof Seller && viewer.getUsername().equalsIgnoreCase(listing.getSellerUsername());
        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("Only the buyer and the listing's seller can open this chat.");
        }
        return listing;
    }

    /**
     * The chat thread, or null if the buyer has not started it yet. In that case only the
     * buyer may go on, and only on a live listing: the chat is started from the listing detail
     * screen, which shows live listings only (UC6 step 1 and precondition).
     */
    private ChatThread threadIfStarted(User viewer, Listing listing, String buyerUsername) {
        ChatThread thread = store.findThread(buyerUsername, listing.getId());
        if (thread == null) {
            if (!(viewer instanceof Buyer)) {
                throw new ServiceException("There is no chat with this buyer about this listing.");
            }
            if (!store.isLive(listing)) {
                throw new ServiceException("This listing is not on the marketplace, so a new chat cannot be started.");
            }
        }
        return thread;
    }

    /** @param thread null for a chat the buyer has not started yet, which has no history */
    private Conversation conversation(User viewer, Listing listing, String buyerUsername, ChatThread thread) {
        return new Conversation(listing, buyerUsername, store.getFullName(buyerUsername),
                store.getFullName(listing.getSellerUsername()), viewer.getUsername(),
                thread != null ? history(thread) : List.of());
    }

    /**
     * The thread's messages and offers in the order they were sent (UC6 step 2, Special
     * Requirement 3). An offer made at the same moment as a message is listed before it.
     */
    private List<ChatEntry> history(ChatThread thread) {
        record Line(String sender, LocalDateTime time, String content, Offer offer) { }

        List<Line> lines = new ArrayList<>();
        for (Offer offer : store.getOffersInThread(thread)) {
            lines.add(new Line(offer.getBuyerUsername(), offer.getOfferDate(), null, offer));
        }
        for (Message message : thread.getMessages()) {
            lines.add(new Line(message.getSenderUsername(), message.getSentAt(), message.getContent(), null));
        }
        lines.sort(Comparator.comparing(Line::time)); // stable, so equal times keep the order above

        List<ChatEntry> entries = new ArrayList<>();
        for (Line line : lines) {
            entries.add(new ChatEntry(entries.size(), line.sender(), store.getFullName(line.sender()),
                    line.time(), line.content(), line.offer()));
        }
        return entries;
    }

    /** When the chat last had a message or offer; a chat with neither sorts last. */
    private static LocalDateTime lastActivity(Conversation conversation) {
        return conversation.lastEntry().map(ChatEntry::time).orElse(LocalDateTime.MIN);
    }
}
