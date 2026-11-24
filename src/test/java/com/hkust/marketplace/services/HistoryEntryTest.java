package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HistoryEntry}, one row of Deal History (UC11 step 2). State-based
 * (Lecture 13): a deal is put into each of its states through the Offer state machine, and the
 * record accepts it only once it is Completed (Special Requirement 1).
 */
class HistoryEntryTest {

    private DataStore store;
    private DealRules rules;

    @BeforeEach
    void setUp() {
        store = Fixtures.store(Fixtures.clock());
        rules = new DealRules(store);
        Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
    }

    private Offer purchase() {
        return store.addOffer(new PriceOffer(Fixtures.live(Fixtures.sale(store, "sam", 100)).getId(), "bea", 90));
    }

    @ParameterizedTest(name = "a {0} deal is not a row of the history")
    @ValueSource(strings = {"PENDING", "COUNTERED", "DECLINED", "ACCEPTED"})
    void aDealThatIsNotCompleted_isRefused(String state) {
        Offer deal = Fixtures.moveDealTo(rules, purchase(), state);
        Listing listing = store.getListingOf(deal);

        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry(deal, listing, "Seller sam"));
    }

    @Test
    void aCompletedDeal_isARow_datedOnItsCompletionDate() { // Special Requirement 2
        Offer deal = Fixtures.moveDealTo(rules, purchase(), "COMPLETED");

        HistoryEntry entry = new HistoryEntry(deal, store.getListingOf(deal), "Seller sam");

        assertNotNull(deal.getCompletedDate());
        assertEquals(deal.getCompletedDate(), entry.dealDate());
    }

    @Test
    void everyPart_isRequired() {
        Offer deal = Fixtures.moveDealTo(rules, purchase(), "COMPLETED");
        Listing listing = store.getListingOf(deal);

        assertThrows(NullPointerException.class, () -> new HistoryEntry(null, listing, "Seller sam"));
        assertThrows(NullPointerException.class, () -> new HistoryEntry(deal, null, "Seller sam"));
        assertThrows(NullPointerException.class, () -> new HistoryEntry(deal, listing, null));
    }
}
