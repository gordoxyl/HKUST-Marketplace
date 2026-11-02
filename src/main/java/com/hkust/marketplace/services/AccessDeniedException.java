package com.hkust.marketplace.services;

/**
 * Thrown when the logged-in user may not call a service method: nobody is logged in,
 * the account is deactivated, or the account type is wrong (UC2 Special Requirement 3).
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
