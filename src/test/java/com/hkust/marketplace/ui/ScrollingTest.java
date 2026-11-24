package com.hkust.marketplace.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for where a long screen scrolls to show one item in the middle: the chat search's jump
 * (UC6 A2 step 4) and the offers a chat links to on Offers &amp; Requests (assumptions register 71).
 *
 * <p><b>Equivalence classes and boundary values</b> (Lecture 13): content that can scroll and
 * content that fits; an item in the middle, at the very top and at the very bottom, and centred
 * exactly on the top edge.
 */
class ScrollingTest {

    private static final double EXACT = 1e-9;

    @Test
    void position_putsTheItemInTheMiddle_andStaysBetweenTopAndBottom() {
        // Content 1000 high seen through a window 200 high: 800 can be scrolled
        assertEquals(0.5, Scrolling.position(480, 40, 1000, 200), EXACT);   // item centred at 500
        assertEquals(0.0, Scrolling.position(0, 40, 1000, 200), EXACT);     // first item: cannot go above the top
        assertEquals(1.0, Scrolling.position(960, 40, 1000, 200), EXACT);   // last item: cannot go below the bottom
        assertEquals(0.0, Scrolling.position(80, 40, 1000, 200), EXACT);    // centred exactly at the top edge
        assertEquals(0.125, Scrolling.position(180, 40, 1000, 200), EXACT);
    }

    @Test
    void position_isTheTop_whenTheWholeContentIsVisible() {
        assertEquals(0.0, Scrolling.position(50, 40, 200, 200), EXACT);     // content exactly as high as the window
        assertEquals(0.0, Scrolling.position(50, 40, 120, 200), EXACT);     // content lower than the window
    }
}
