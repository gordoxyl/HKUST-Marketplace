package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the position-to-value rule of {@link Choices}, the part that needs no JavaFX
 * window. Boundary values of the position (Lecture 13): nothing selected (-1), "all" (0), the
 * first value (1) and the last value.
 */
class ChoicesTest {

    private static final Category[] VALUES = Category.values();

    @Test
    void nothingSelected_orAll_meansEveryValue() {
        assertNull(Choices.valueAt(-1, VALUES));
        assertNull(Choices.valueAt(0, VALUES));
    }

    @Test
    void theFirstAndTheLastPosition_giveTheFirstAndTheLastValue() {
        assertEquals(VALUES[0], Choices.valueAt(1, VALUES));
        assertEquals(VALUES[VALUES.length - 1], Choices.valueAt(VALUES.length, VALUES));
    }
}
