package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;

import java.time.Clock;

/**
 * A tiny program that {@code AppStoreStartupTest} runs in its own JVM, because the bug it
 * checks (regression log R3) happens only the first time Java loads DataStore.
 * <ul>
 *   <li>{@code new}: makes a store the way every test does.</li>
 *   <li>{@code app}: gets the app's store, as the app does at start-up.</li>
 * </ul>
 */
public final class StoreProbe {

    private StoreProbe() {
        // Program entry point only
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("app")) {
            DataStore store = DataStore.getInstance();
            System.out.println("app store users=" + store.getAllUsers().size());
        } else {
            DataStore store = new DataStore(Clock.systemDefaultZone());
            System.out.println("new store users=" + store.getAllUsers().size());
        }
    }
}
