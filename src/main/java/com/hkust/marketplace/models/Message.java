package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Objects;

/** One free-text message in a chat thread (Message in the domain class diagram). Messages cannot be edited (UC6 Special Requirement 2). */
public class Message {

    private final String senderUsername;
    private final String content;
    private final LocalDateTime sentAt;

    public Message(String senderUsername, String content) {
        this.senderUsername = Objects.requireNonNull(senderUsername, "senderUsername");
        this.content = Objects.requireNonNull(content, "content");
        this.sentAt = LocalDateTime.now();
    }

    public String getSenderUsername() { return senderUsername; }
    public String getContent() { return content; }
    public LocalDateTime getSentAt() { return sentAt; }
}
