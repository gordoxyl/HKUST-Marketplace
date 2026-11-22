package com.hkust.marketplace.services;

import java.util.function.IntPredicate;

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
        // The kinds of character, in the order the rules are listed
        if (!contains(password, AccountValidator::isUppercase)) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!contains(password, AccountValidator::isLowercase)) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!contains(password, AccountValidator::isDigit)) {
            return "Password must contain at least one digit.";
        }
        if (!contains(password, AccountValidator::isSymbol)) {
            return "Password must contain at least one special character (such as ! @ # $ %).";
        }
        return null;
    }

    /**
     * What a user is told about a password that is refused (UC1 A3, UC3 A2): what is wrong with
     * it, then the password rules. Registration and profile changes both use it.
     * @return null if the password is acceptable
     */
    public static String validatePasswordShowingRules(String password) {
        String error = validatePassword(password);
        return error == null ? null : error + " Password rules: " + PASSWORD_RULES;
    }

    /** True if at least one character of the text passes the test. */
    private static boolean contains(String text, IntPredicate test) {
        return text.chars().anyMatch(test);
    }

    private static boolean isUppercase(int c) {
        return c >= 'A' && c <= 'Z';
    }

    private static boolean isLowercase(int c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Any other printable ASCII character except the space is a symbol, such as ! @ # $ %.
     * Only called after {@link #isPrintableAscii} has passed.
     */
    private static boolean isSymbol(int c) {
        return c != ' ' && !isUppercase(c) && !isLowercase(c) && !isDigit(c);
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
