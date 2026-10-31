package com.hkust.marketplace.services;

/**
 * Outcome of {@link AuthService#login}. Each failure matches one alternative
 * flow of UC2 Log In, and carries the message to show the user.
 */
public enum LoginResult {
    SUCCESS("Login successful."),
    /** UC2 A1 */
    MISSING_FIELDS("Please enter both username and password."),
    /** UC2 A2: one message for both cases, so it never reveals whether the username exists. */
    INVALID_CREDENTIALS("Incorrect username or password."),
    /** UC2 A3 */
    WRONG_ACCOUNT_TYPE("This account cannot log in to this portal. Please use the portal for your account type."),
    /** UC2 A4 */
    DEACTIVATED("This account has been deactivated. Please contact the admin.");

    private final String message;

    LoginResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
