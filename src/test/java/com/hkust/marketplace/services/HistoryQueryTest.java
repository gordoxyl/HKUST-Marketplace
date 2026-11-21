package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HistoryQuery}, what the Deal History screen sends (UC11 step 3).
 * Equivalence partitions of the seller text (Lecture 13): nothing (null), blank, and text with
 * spaces around it.
 */
class HistoryQueryTest {

    @ParameterizedTest(name = "seller [{0}] means no search")
    @NullSource
    @ValueSource(strings = {"", "   ", " \t "})
    void noSellerText_isStoredAsBlank(String seller) {
        assertEquals("", new HistoryQuery(null, null, null, seller).seller());
    }

    @Test
    void sellerText_losesTheSpacesAroundIt_butKeepsItsCaseAndInnerSpaces() {
        assertEquals("Charlie Lee", new HistoryQuery(null, null, null, "  Charlie Lee ").seller());
    }

    @Test
    void theDatesAndTheCategory_areKeptAsChosen() { // their order is the service's check (A2)
        LocalDate from = LocalDate.of(2026, 11, 13);
        LocalDate to = LocalDate.of(2026, 11, 12);

        HistoryQuery query = new HistoryQuery(from, to, Category.ELECTRONICS, "sam");

        assertEquals(from, query.from());
        assertEquals(to, query.to());
        assertEquals(Category.ELECTRONICS, query.category());
    }

    @Test
    void all_hasNoFilter() { // step 2: every completed deal
        assertNull(HistoryQuery.ALL.from());
        assertNull(HistoryQuery.ALL.to());
        assertNull(HistoryQuery.ALL.category());
        assertEquals("", HistoryQuery.ALL.seller());
    }
}
