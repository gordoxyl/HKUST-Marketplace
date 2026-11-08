package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/** One free-text message in a chat thread (Message in the domain class diagram). Messages cannot be edited (UC6 Special Requirement 2). */
public class Message {

    private final String senderUsername;
    private final String content;
    private final LocalDateTime sentAt;

    /** @param sentAt the time it was sent; services pass {@code store.now()} */
    public Message(String senderUsername, String content, LocalDateTime sentAt) {
        this.senderUsername = Objects.requireNonNull(senderUsername, "senderUsername");
        this.content = Objects.requireNonNull(content, "content");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    }

    public String getSenderUsername() { return senderUsername; }
    public String getContent() { return content; }
    public LocalDateTime getSentAt() { return sentAt; }
}
