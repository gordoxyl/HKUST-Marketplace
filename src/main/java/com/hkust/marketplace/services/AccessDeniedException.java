package com.hkust.marketplace.services;

/**
 * Thrown when the logged-in user may not call a service method: nobody is logged in,
 * the account is deactivated, or the account type is wrong (UC2 Special Requirement 3).
 * It is a {@link ServiceException}, so screens show its message like any other rule.
 */
public class AccessDeniedException extends ServiceException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
