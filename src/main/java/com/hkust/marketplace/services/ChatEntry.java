package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Offer;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One line in a chat's history (UC6 step 2): a free-text message, or a price offer, borrow
 * request or claim the buyer made on the listing, which appears in the history alongside the
 * messages (UC6 Special Requirement 3). Exactly one of {@code content} and {@code offer} is set.
 *
 * @param position       the line's place in the history, counted from 0; a search result uses
 *                       it to jump to the line (UC6 A2)
 * @param senderUsername who sent it; for an offer, the buyer
 * @param senderName     the sender's full name, for display
 * @param time           when the message was sent, or when the offer was made
 * @param content        the message text; null for an offer
 * @param offer          the offer, with its current status; null for a message
 */
public record ChatEntry(int position, String senderUsername, String senderName, LocalDateTime time,
                        String content, Offer offer) {

    public ChatEntry {
        Objects.requireNonNull(senderUsername, "senderUsername");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(time, "time");
        if ((content == null) == (offer == null)) {
            throw new IllegalArgumentException("A chat entry is either a message or an offer.");
        }
    }

    /** True for an offer, borrow request or claim; false for a message. */
    public boolean isOffer() {
        return offer != null;
    }
}
