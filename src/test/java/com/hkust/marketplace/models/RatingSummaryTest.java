package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RatingSummary}, a seller's rating count and average as one value.
 * Equivalence partitions and boundary values (Lecture 13) of the pair it holds:
 * <pre>
 *   count 0:  average 0 (valid) | any other average, even 0.5 (impossible: an average without ratings)
 *   count 1+: average 1 and 5, the ends (valid) | 0.99 and 5.01, just outside (impossible) | 0 (impossible)
 *   count -1: impossible
 * </pre>
 */
class RatingSummaryTest {

    @Test
    void noRatings_haveNoAverage() {
        assertTrue(RatingSummary.NONE.isEmpty());
        assertEquals(0, RatingSummary.NONE.count());
        assertEquals(RatingSummary.NONE, RatingSummary.of(List.of()));
    }

    @ParameterizedTest(name = "{0} ratings averaging {1}")
    @CsvSource({"1, 1.0", "1, 5.0", "2, 3.5", "7, 4.29"})
    void aCountWithAnAverageFromOneToFive_isARating(int count, double average) {
        RatingSummary rating = new RatingSummary(count, average);

        assertFalse(rating.isEmpty());
        assertEquals(count, rating.count());
        assertEquals(average, rating.average());
    }

    @ParameterizedTest(name = "{0} ratings averaging {1} cannot be")
    @CsvSource({"-1, 0", "0, 4.0", "0, 0.5", "1, 0.99", "1, 5.01", "3, 0"})
    void anImpossiblePair_isRefused(int count, double average) {
        assertThrows(IllegalArgumentException.class, () -> new RatingSummary(count, average));
    }

    @Test
    void ofReviews_countsThemAndAveragesTheirRatings() {
        List<Review> reviews = List.of(new Review("O1", 4, "Good"), new Review("O2", 5, "Great"));

        assertEquals(new RatingSummary(2, 4.5), RatingSummary.of(reviews));
    }
}
