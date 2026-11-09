package com.hkust.marketplace.ui;

import javafx.scene.Node;

/**
 * Somewhere a screen can be shown: each portal's dashboard, whose main area shows one screen
 * at a time. Shared components (the notification board, profile, chat) receive the dashboard
 * as a ContentHost, so they can open another screen with its data, for example a listing's
 * chat thread from a notification:
 * <pre>{@code
 * host.show("Chat: Desk Lamp", new ChatView(host, thread));
 * }</pre>
 */
public interface ContentHost {

    /** Shows the content in the main area, under the given page title. */
    void show(String title, Node content);
}
