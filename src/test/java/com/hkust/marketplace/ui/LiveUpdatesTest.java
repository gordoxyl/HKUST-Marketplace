package com.hkust.marketplace.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for telling the open dashboard that the timer changed the data (UC12 step 4). */
class LiveUpdatesTest {

    @AfterEach
    void forget() {
        LiveUpdates.clear();
    }

    @Test
    void openDashboard_isToldEachTimeTheDataChanges() {
        AtomicInteger refreshes = new AtomicInteger();
        LiveUpdates.setOnDataChanged(() -> true, refreshes::incrementAndGet);

        assertTrue(LiveUpdates.dataChanged());
        assertTrue(LiveUpdates.dataChanged());

        assertEquals(2, refreshes.get());
    }

    @Test
    void noDashboardOpen_isNotAnError() {
        assertFalse(LiveUpdates.dataChanged()); // for example on the welcome screen
    }

    @Test
    void newDashboard_replacesTheOneBefore() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        LiveUpdates.setOnDataChanged(() -> true, first::incrementAndGet);
        LiveUpdates.setOnDataChanged(() -> true, second::incrementAndGet); // the next login

        LiveUpdates.dataChanged();

        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void dashboardThatIsNoLongerShown_isNotTold_andIsForgotten() {
        AtomicBoolean shown = new AtomicBoolean(true);
        AtomicInteger refreshes = new AtomicInteger();
        LiveUpdates.setOnDataChanged(shown::get, refreshes::incrementAndGet);
        shown.set(false); // the user logged out

        assertFalse(LiveUpdates.dataChanged());
        shown.set(true);
        assertFalse(LiveUpdates.dataChanged()); // forgotten, even if it would say it is shown again

        assertEquals(0, refreshes.get());
    }

    @Test
    void registrationNeedsBothParts() {
        assertThrows(NullPointerException.class, () -> LiveUpdates.setOnDataChanged(null, () -> { }));
        assertThrows(NullPointerException.class, () -> LiveUpdates.setOnDataChanged(() -> true, null));
    }
}
