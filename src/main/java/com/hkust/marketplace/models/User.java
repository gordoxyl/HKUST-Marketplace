package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * A marketplace account (User in the domain class diagram).
 * Every user is exactly one of {@link Buyer}, {@link Seller} or {@link Admin} ({complete, disjoint}).
 *
 * The username is stored in lower case, because usernames are unique ignoring case (UC1 A1),
 * and it cannot be changed (UC3 Special Requirement 1).
 * Only a hash of the password is kept, never the password itself (UC1 Special Requirement 1).
 */
public abstract class User {

    private final String username;
    private String fullName;
    private String passwordHash;
    private boolean active = true;
    private final LocalDateTime createdAt = LocalDateTime.now();

    protected User(String username, String fullName, String passwordHash) {
        this.username = Objects.requireNonNull(username, "username").toLowerCase(Locale.ROOT);
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    /** The account type, which decides the only portal this user can log in to (UC2). */
    public abstract UserRole getRole();

    public String getUsername() { return username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = Objects.requireNonNull(fullName, "fullName"); }

    /** A hash made by PasswordHasher, never a plaintext password. */
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash"); }

    /** False once the admin deactivates the account; it then cannot log in (UC2 A4). */
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return fullName + " (@" + username + ")";
    }
}
