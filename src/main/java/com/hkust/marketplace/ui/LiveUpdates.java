package com.hkust.marketplace.ui;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Tells the open dashboard that the data changed without the user doing anything, so the
 * screen it shows can reload. Today that is the borrowing-deadline timer (UC12): when it sends
 * a reminder or returns an item, the buyer's open My Deals or notification board shows it at
 * once (UC12 step 4), and a seller's open My Listings can show the listing Live again.
 *
 * <p>A dashboard registers itself when it opens, and so replaces the dashboard before it:
 * <pre>{@code
 * LiveUpdates.setOnDataChanged(() -> contentPane.getScene() != null, this::refreshCurrentScreen);
 * }</pre>
 * The app calls {@link #dataChanged()} on the JavaFX thread. A dashboard that is no longer on
 * screen (the user logged out) is told nothing and forgotten.
 *
 * <p>This class has no JavaFX code, so its rules are unit-tested.
 */
public final class LiveUpdates {

    private static BooleanSupplier stillShown;
    private static Runnable onDataChanged;

    private LiveUpdates() {
        // Utility class
    }

    /**
     * Registers the dashboard that is now open, replacing any earlier one.
     * @param stillShown    true while that dashboard is on screen
     * @param onDataChanged reloads the screen the dashboard shows
     */
    public static synchronized void setOnDataChanged(BooleanSupplier stillShown, Runnable onDataChanged) {
        LiveUpdates.stillShown = Objects.requireNonNull(stillShown, "stillShown");
        LiveUpdates.onDataChanged = Objects.requireNonNull(onDataChanged, "onDataChanged");
    }

    /**
     * The data changed: the open dashboard reloads its screen. Call it on the JavaFX thread.
     * @return true if a dashboard was told; false if none is open
     */
    public static boolean dataChanged() {
        Runnable action;
        synchronized (LiveUpdates.class) {
            if (onDataChanged == null) {
                return false;
            }
            if (!stillShown.getAsBoolean()) {
                clear(); // logged out: forget it, so it can be freed
                return false;
            }
            action = onDataChanged;
        }
        action.run();
        return true;
    }

    /** Forgets the registered dashboard. */
    public static synchronized void clear() {
        stillShown = null;
        onDataChanged = null;
    }
}
