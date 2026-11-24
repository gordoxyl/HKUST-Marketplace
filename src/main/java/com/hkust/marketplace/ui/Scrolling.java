package com.hkust.marketplace.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;

/**
 * Jumping to one item of a long screen and marking it, shared by every portal so the pattern
 * looks and works the same everywhere:
 * <ul>
 *   <li>the chat search jumps to the chosen message and highlights it (UC6 A2 step 4);</li>
 *   <li>the seller's Offers &amp; Requests shows the offers a chat's Respond in Offers &amp;
 *       Requests links to (assumptions register 71).</li>
 * </ul>
 * Where to scroll is worked out in {@link #position}, apart from JavaFX, so it is unit-tested.
 */
public final class Scrolling {

    /** The style class that marks the item jumped to: light yellow, defined in {@code styles.css}. */
    public static final String JUMP_HIGHLIGHT = "jump-highlight";

    private Scrolling() {
        // Utility class
    }

    /**
     * Lays the screen out, then scrolls the pane so the node is in the middle of the visible part,
     * or as near as the content allows. The pane keeps its default scroll range, 0 to 1. Call it
     * once the node is inside the pane's content; a screen just built is laid out first, so it
     * works straight after the pane has been added to the window.
     * @param pane the scroll pane
     * @param node an item inside the pane's content
     */
    public static void bringIntoView(ScrollPane pane, Node node) {
        Parent top = pane.getScene() == null ? pane : pane.getScene().getRoot();
        top.applyCss();
        top.layout();
        Node content = pane.getContent();
        Bounds item = content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        pane.setVvalue(position(item.getMinY(), item.getHeight(),
                content.getLayoutBounds().getHeight(), pane.getViewportBounds().getHeight()));
    }

    /**
     * Where to scroll so that one item is in the middle of the visible part, with what comes
     * before and after it around it.
     * @param top            the item's distance from the top of the content
     * @param height         the item's height
     * @param contentHeight  the height of the whole content
     * @param viewportHeight the height of the part that is visible
     * @return the scroll position from 0 (top) to 1 (bottom); 0 if the whole content is visible
     */
    public static double position(double top, double height, double contentHeight, double viewportHeight) {
        double scrollable = contentHeight - viewportHeight;
        if (scrollable <= 0) {
            return 0;
        }
        double offset = top + height / 2 - viewportHeight / 2;
        return Math.max(0, Math.min(1, offset / scrollable));
    }
}
