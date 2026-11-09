package com.hkust.marketplace.ui;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the required-text check used by "ask for a reason" dialogs. */
class TextInputsTest {

    @Test
    void required_trimsTheText() {
        assertEquals(Optional.of("Spam listing"), TextInputs.required("  Spam listing \n"));
    }

    @Test
    void required_refusesNothingTyped() {
        // Equivalence classes: no value, empty, only spaces
        assertEquals(Optional.empty(), TextInputs.required(null));
        assertEquals(Optional.empty(), TextInputs.required(""));
        assertEquals(Optional.empty(), TextInputs.required("   \t"));
    }

    @Test
    void required_acceptsASingleCharacter() {
        assertEquals(Optional.of("x"), TextInputs.required("x")); // boundary: shortest valid input
    }
}
