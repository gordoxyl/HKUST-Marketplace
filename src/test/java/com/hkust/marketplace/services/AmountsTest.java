package com.hkust.marketplace.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for typed amounts of money, by equivalence partition (Lecture 13): the valid
 * partition "digits with at most two decimals", and the invalid ones around it.
 */
class AmountsTest {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({"0, 0", "0.00, 0", "0.01, 0.01", "7, 7", "180, 180", "99.5, 99.5", "99.50, 99.5",
            "007, 7", "' 60 ', 60", "1000000, 1000000"})
    void plainAmount_isRead(String typed, double expected) {
        assertEquals(OptionalDouble.of(expected), Amounts.parse(typed));
    }

    @ParameterizedTest(name = "\"{0}\" is not an amount")
    @NullSource
    @ValueSource(strings = {
            "", "   ",                         // nothing
            "-1", "+1", "-0",                  // a sign
            "$10", "HK$ 10", "1,000", "10%",   // a symbol or separator
            "abc", "12abc", "1e3", "0x10", "10d", "NaN", "Infinity", // letters
            "12.", ".5", "1.234", "1.2.3", "1 000"}) // not digits with at most two decimals
    void anythingElse_isNotAnAmount(String typed) {
        assertEquals(OptionalDouble.empty(), Amounts.parse(typed));
    }

    @Test
    void amountTooLargeForANumber_isNotAnAmount() {
        assertEquals(OptionalDouble.empty(), Amounts.parse("9".repeat(400)));
    }

    @Test
    void plain_showsCentsOnlyWhenThereAreSome() {
        assertEquals("180", Amounts.plain(180));
        assertEquals("99.50", Amounts.plain(99.5));
        assertEquals("0.01", Amounts.plain(0.01));
        assertEquals("0", Amounts.plain(0));
    }

    @Test
    void plain_canBeReadBack() {
        for (double amount : new double[] {0.01, 99.5, 180, 1234567.89}) {
            assertEquals(OptionalDouble.of(amount), Amounts.parse(Amounts.plain(amount)));
        }
    }
}
