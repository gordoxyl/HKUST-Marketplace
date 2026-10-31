package com.hkust.marketplace.services;

/**
 * Validation rules for account details, shared by buyers, sellers and admins.
 * Follows UC1 Register as Buyer (alternative flows A1-A4 and Special Requirements 2-3);
 * Register as Seller and UC3 Manage Profile use the same rules.
 *
 * Each method returns an error message to show the user, or null if the value is valid.
 * Checking that a username is not already taken needs the user store, so that check
 * is done in {@link AuthService}.
 */
public final class AccountValidator {

    public static final int MIN_PASSWORD_LENGTH = 8;

    /** The password rules, for showing next to the password field. */
    public static final String PASSWORD_RULES =
            "At least " + MIN_PASSWORD_LENGTH + " characters, with at least one uppercase letter, "
            + "one lowercase letter, one digit and one special character (such as ! @ # $ %). "
            + "ASCII characters only.";

    private AccountValidator() {
        // Utility class
    }

    /** UC1 A1: the username must not be empty and may contain only ASCII characters. */
    public static String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            return "Username cannot be empty.";
        }
        if (!isPrintableAscii(username)) {
            return "Username can only contain English letters, digits and ASCII symbols.";
        }
        return null;
    }

    /** UC1 A2: the full name must not be empty. Any characters are allowed (Special Requirement 3). */
    public static String validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Full name cannot be empty.";
        }
        return null;
    }

    /** UC1 A3: the password must be ASCII only and meet the strength rules (Special Requirement 2). */
    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }
        if (!isPrintableAscii(password)) {
            return "Password can only contain English letters, digits and ASCII symbols.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                hasUpper = true;
            } else if (c >= 'a' && c <= 'z') {
                hasLower = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if (c != ' ') {
                // Any other printable ASCII character is a symbol such as ! @ # $ %
                hasSpecial = true;
            }
        }
        if (!hasUpper) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!hasLower) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!hasDigit) {
            return "Password must contain at least one digit.";
        }
        if (!hasSpecial) {
            return "Password must contain at least one special character (such as ! @ # $ %).";
        }
        return null;
    }

    /** UC1 A4: the confirmation password must match the password. */
    public static String validateConfirmation(String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            return "The two passwords do not match.";
        }
        return null;
    }

    /** True if every character is printable ASCII (space to ~). */
    private static boolean isPrintableAscii(String s) {
        for (char c : s.toCharArray()) {
            if (c < ' ' || c > '~') {
                return false;
            }
        }
        return true;
    }
}
