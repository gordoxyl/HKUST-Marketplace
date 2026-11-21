package com.hkust.marketplace.services;

/**
 * The date range starts after it ends (UC11 A2). The buyer fixes it by choosing another date,
 * so a screen that searches on every change can show the message next to the dates instead of
 * in a dialog, which would interrupt the buyer at each step. A screen catches this type to tell
 * it apart from other problems:
 * <pre>{@code
 * try {
 *     entries = history.find(query);
 * } catch (InvalidDateRangeException e) {
 *     rangeProblem.setText(e.getMessage());   // A2: shown under the dates; the results stay
 * } catch (ServiceException e) {
 *     Dialogs.problem(e);                     // anything else, as on every screen
 * }
 * }</pre>
 */
public class InvalidDateRangeException extends ServiceException {

    public InvalidDateRangeException(String message) {
        super(message);
    }
}
