package com.hkust.marketplace.services;

/**
 * A use-case rule was not met, such as an alternative flow ("the listing is no longer
 * available"). The message is written for the user, so the screen can show it as it is:
 * <pre>{@code
 * try {
 *     service.doSomething(...);
 * } catch (ServiceException e) {
 *     showError(e.getMessage());
 * }
 * }</pre>
 *
 * Services check input and rules first and throw this for problems the user can fix or
 * should be told about (the "barricade"). Models throw IllegalArgumentException or
 * IllegalStateException instead, for states that should never happen if the services did
 * their checks; those indicate a bug.
 */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }
}
