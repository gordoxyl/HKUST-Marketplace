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

    // ---------- A typed date and time (UC5 step 4: the borrow period) ----------

    private static final java.time.LocalDate DAY = java.time.LocalDate.of(2026, 11, 12);

    @org.junit.jupiter.params.ParameterizedTest(name = "\"{0}\" is {1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "14:30,      2026-11-12T14:30:00",
            "14:30:05,   2026-11-12T14:30:05",
            "9:05,       2026-11-12T09:05:00",
            "00:00,      2026-11-12T00:00:00",
            "23:59:59,   2026-11-12T23:59:59",
            "' 14:30 ',  2026-11-12T14:30:00"})
    void dateTime_joinsTheChosenDateAndTheTypedTime(String time, java.time.LocalDateTime expected) {
        assertEquals(Optional.of(expected), TextInputs.dateTime(DAY, time));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "time \"{0}\"")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "", "   ", "noon", "14", "14:3", "14.30", "24:00", "23:60", "14:30:60", "14:30:5", "2pm", "-1:00"})
    void dateTime_isEmpty_whenTheTimeIsNotATimeOfDay(String time) {
        assertEquals(Optional.empty(), TextInputs.dateTime(DAY, time));
    }

    @Test
    void dateTime_isEmpty_whenTheDateOrTheTimeIsMissing() {
        assertEquals(Optional.empty(), TextInputs.dateTime(null, "14:30"));
        assertEquals(Optional.empty(), TextInputs.dateTime(DAY, null));
    }
}
