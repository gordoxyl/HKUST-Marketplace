package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for how offers, borrow requests and claims are worded on every screen. State-based
 * (Lecture 14): each offer is moved through its states and the wording is checked in each.
 */
class OfferTextTest {

    private static final LocalDateTime START = Fixtures.START.plusHours(1);
    private static final LocalDateTime END = START.plusDays(7);

    @Test
    void priceOffer_showsItsPrice_andTheSellersCounter() {
        PriceOffer offer = new PriceOffer("L1", "bea", 160);

        assertEquals("Price offer", OfferText.kind(offer));
        assertEquals("Price offer: HK$ 160", OfferText.summary(offer));
        assertEquals(Optional.empty(), OfferText.counter(offer));

        offer.counter(170.5);
        assertEquals(Optional.of("Seller's counter: HK$ 170.50"), OfferText.counter(offer));
        assertEquals("Price offer: HK$ 160", OfferText.summary(offer)); // what the buyer proposed stays
    }

    @Test
    void borrowRequest_showsItsPeriod_andTheSellersCounter() {
        BorrowRequest request = new BorrowRequest("L1", "bea", START, END);

        assertEquals("Borrow request: 02 Nov 2026 13:00 – 09 Nov 2026 13:00", OfferText.summary(request));
        assertEquals(Optional.empty(), OfferText.counter(request));

        request.counter(START.plusDays(1), END.plusDays(1));
        assertEquals(Optional.of("Seller's counter: 03 Nov 2026 13:00 – 10 Nov 2026 13:00"), OfferText.counter(request));
    }

    @Test
    void claim_hasNoTermsAndNoCounter() {
        Claim claim = new Claim("L1", "bea");

        assertEquals("Claim", OfferText.summary(claim));
        assertEquals(Optional.empty(), OfferText.counter(claim));
    }

    @Test
    void status_followsTheOfferThroughItsStates() {
        Offer pending = new PriceOffer("L1", "bea", 160);
        assertStatus("Pending", "badge-pending", pending);

        PriceOffer countered = new PriceOffer("L1", "bea", 160);
        countered.counter(170);
        assertStatus("Countered", "badge-sold", countered);

        Offer declined = new PriceOffer("L1", "bea", 160);
        declined.decline();
        assertStatus("Declined", "badge-rejected", declined);

        Offer accepted = new PriceOffer("L1", "bea", 160);
        accepted.accept(START);
        assertStatus("Accepted", "badge-live", accepted);

        accepted.complete(END); // marked complete, or the item returned
        assertStatus("Completed", "badge-sold", accepted);
    }

    private static void assertStatus(String status, String style, Offer offer) {
        assertEquals(status, OfferText.status(offer));
        assertEquals(style, OfferText.badgeStyle(offer));
    }
}
