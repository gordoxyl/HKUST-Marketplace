package com.hkust.marketplace.ui;

/**
 * A screen that can reload its data while it is open, for example after the borrowing-deadline
 * timer returned an item (UC12 step 4: the change shows on the open My Deals screen). See
 * {@link LiveUpdates}.
 */
public interface Refreshable {

    /** Reloads what the screen shows, keeping what the user typed or chose on it. */
    void refresh();
}
