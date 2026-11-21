package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HistoryText}, the wording of Deal History (UC11). How the tests were
 * designed (Lectures 12-13):
 *
 * <p><b>Boundary values</b> of the number of deals: 0 (A1), 1 and 2 (singular and plural).
 *
 * <p><b>Equivalence partitions</b> of the deal (Special Requirement 3): a price offer, a borrow
 * request, a claim, and a kind of deal the screen does not know. The last is also the fourth
 * basis path of {@link HistoryText#dealType}.
 */
class HistoryTextTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Test
    void noDeals_givesTheA1Message() {
        assertEquals("No deals match. A deal appears here once it is completed.", HistoryText.resultSummary(0));
        assertEquals(HistoryText.NO_DEALS, HistoryText.resultSummary(0));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"1, 1 deal", "2, 2 deals", "12, 12 deals"})
    void someDeals_areCounted(int count, String summary) {
        assertEquals(summary, HistoryText.resultSummary(count));
    }

    @Test
    void eachKindOfDeal_isNamedFromTheBuyersSide() {
        assertEquals("Bought", HistoryText.dealType(new PriceOffer("L1", "bea", 90)));
        assertEquals("Borrowed", HistoryText.dealType(new BorrowRequest("L2", "bea", NOW, NOW.plusDays(1))));
        assertEquals("Received", HistoryText.dealType(new Claim("L3", "bea")));
    }

    @Test
    void anUnknownKindOfDeal_isRefused_insteadOfShownUnderAWrongName() { // fail fast (Lecture 11)
        Offer swap = new Offer("L4", "bea") {
            @Override
            public boolean canBeMadeOn(Listing listing) {
                return false;
            }

            @Override
            public String getKindName() {
                return "swap";
            }
        };

        assertThrows(IllegalArgumentException.class, () -> HistoryText.dealType(swap));
    }
}
