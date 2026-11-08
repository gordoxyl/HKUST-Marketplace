package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The private chat between one buyer and the seller of one listing (ChatThread in the
 * domain class diagram; UC6). The thread holds its messages in order; its offers are the
 * offers with the same buyer and listing, kept in DataStore.
 */
public class ChatThread {

    private String id;
    private final String buyerUsername;
    private final String listingId;
    private final String sellerUsername;
    private LocalDateTime createdAt;
    private final List<Message> messages = new ArrayList<>();

    /** Only the buyer starts a thread (UC6 Special Requirement 1). */
    public ChatThread(String buyerUsername, Listing listing) {
        this.buyerUsername = Objects.requireNonNull(buyerUsername, "buyerUsername");
        this.listingId = Objects.requireNonNull(listing.getId(), "listing id");
        this.sellerUsername = listing.getSellerUsername();
    }

    /** Null until the thread is added to DataStore. */
    public String getId() { return id; }

    /** Called once by DataStore when the thread is added: gives it its id and creation time. */
    public void markAdded(String id, LocalDateTime createdAt) {
        if (this.id != null) {
            throw new IllegalStateException("Chat thread already has id " + this.id);
        }
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getBuyerUsername() { return buyerUsername; }
    public String getListingId() { return listingId; }
    public String getSellerUsername() { return sellerUsername; }

    /** Null until the thread is added to DataStore. */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** True for the thread's buyer and the listing's seller, the only people who may read or write it. */
    public boolean isParticipant(String username) {
        return buyerUsername.equalsIgnoreCase(username) || sellerUsername.equalsIgnoreCase(username);
    }

    /** All messages, oldest first. Messages are never edited or deleted (UC6 Special Requirement 2). */
    public List<Message> getMessages() {
        return List.copyOf(messages);
    }

    /**
     * Appends a message.
     * @throws IllegalArgumentException if the message is empty, or the sender is not the
     *         thread's buyer or the listing's seller ({the sender of a Message must be ...})
     */
    public void addMessage(Message message) {
        if (!isParticipant(message.getSenderUsername())) {
            throw new IllegalArgumentException("Only the buyer and the seller can write in this chat.");
        }
        if (message.getContent().isBlank()) {
            throw new IllegalArgumentException("A message cannot be empty.");
        }
        messages.add(message);
    }
}
