package com.hkust.marketplace.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A screen opened from another screen: a Back button above the content, the same in every
 * portal. For example a chat opened from a listing, or a listing opened from Browse:
 * <pre>{@code
 * Node listings = ...;                                           // the screen to go back to
 * host.show(title, new SubScreen("Back to listings", () -> host.show("Browse Marketplace", listings), detail));
 * }</pre>
 * Going back shows the earlier screen as it was, so what the user typed or chose there is kept.
 */
public class SubScreen extends VBox implements Refreshable {

    private final Node content;

    /**
     * @param backLabel where Back leads, such as "Back to listings"
     * @param back      shows the earlier screen again
     * @param content   the screen itself; it gets all the space below the button
     */
    public SubScreen(String backLabel, Runnable back, Node content) {
        super(10);
        this.content = content;
        setPadding(new Insets(4));
        Button backButton = new Button("← " + backLabel);
        backButton.getStyleClass().addAll("btn-outline", "btn-sm");
        backButton.setOnAction(e -> back.run());
        VBox.setVgrow(content, Priority.ALWAYS);
        getChildren().addAll(backButton, content);
    }

    /** Reloads the screen it holds, if that screen can reload. */
    @Override
    public void refresh() {
        if (content instanceof Refreshable screen) {
            screen.refresh();
        }
    }
}
