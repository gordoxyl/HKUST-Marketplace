package com.hkust.marketplace.ui;

import java.util.Optional;

/**
 * Checks for text typed into dialogs and forms. Kept apart from the JavaFX dialogs so the
 * rules can be unit-tested.
 */
public final class TextInputs {

    private TextInputs() {
        // Utility class
    }

    /**
     * The text without surrounding spaces, or empty if nothing was typed. Used for required
     * inputs such as a rejection or removal reason, which must not be blank (Manage
     * Marketplace Listings Special Requirement 1; Manage All User Accounts Special Requirement 1).
     */
    public static Optional<String> required(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text.trim());
    }
}
