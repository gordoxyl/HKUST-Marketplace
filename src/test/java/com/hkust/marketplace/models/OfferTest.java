package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the Offer status rules and the PriceOffer, BorrowRequest and Claim subclasses. */
class OfferTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    private final Listing sale = new SaleListing("sam", "Book", Category.TEXTBOOKS, ItemCondition.USED,
            "desc", MeetupLocation.LIBRARY, 100);
    private final Listing lend = new LendListing("sam", "Calculator", Category.ELECTRONICS, ItemCondition.USED,
            "desc", MeetupLocation.LIBRARY, Duration.ofDays(7));
    private final Listing giveaway = new GiveawayListing("sam", "Lamp", Category.FURNITURE, ItemCondition.USED,
            "desc", MeetupLocation.LIBRARY);

    @Test
    void eachOfferType_matchesOnlyItsListingType() {
        Offer price = new PriceOffer("L1", "bea", 50);
        Offer borrow = new BorrowRequest("L1", "bea", NOW, NOW.plusDays(1));
        Offer claim = new Claim("L1", "bea");

        assertTrue(price.canBeMadeOn(sale));
        assertFalse(price.canBeMadeOn(lend));
        assertTrue(borrow.canBeMadeOn(lend));
        assertFalse(borrow.canBeMadeOn(giveaway));
        assertTrue(claim.canBeMadeOn(giveaway));
        assertFalse(claim.canBeMadeOn(sale));
    }

    @Test
    void newOffer_isPendingAndCurrent() {
        Offer offer = new PriceOffer("L1", "bea", 50);

        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertTrue(offer.isOpen());
        assertTrue(offer.isCurrent());
        assertFalse(offer.isCompleted());
        assertNull(offer.getAcceptedDate());
    }

    @Test
    void acceptThenComplete_recordsDatesOnce() {
        Offer offer = new Claim("L1", "bea");

        offer.accept(NOW);
        assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
        assertEquals(NOW, offer.getAcceptedDate());
        assertTrue(offer.isCurrent());

        offer.complete(NOW.plusHours(1));
        assertTrue(offer.isCompleted());
        assertFalse(offer.isCurrent());
        assertThrows(IllegalStateException.class, () -> offer.complete(NOW.plusHours(2)));
    }

    @Test
    void invalidTransitions_areRefused() {
        Offer pending = new PriceOffer("L1", "bea", 50);
        assertThrows(IllegalStateException.class, () -> pending.complete(NOW)); // not accepted

        Offer declined = new PriceOffer("L1", "bea", 50);
        declined.decline();
        assertThrows(IllegalStateException.class, () -> declined.accept(NOW));
        assertThrows(IllegalStateException.class, declined::decline);
        assertFalse(declined.isCurrent());

        Offer accepted = new PriceOffer("L1", "bea", 50);
        accepted.accept(NOW);
        assertThrows(IllegalStateException.class, accepted::decline);
        assertThrows(IllegalStateException.class, () -> accepted.accept(NOW));
    }

    @Test
    void counteredOffer_canBeDeclinedButNotAccepted() {
        PriceOffer offer = new PriceOffer("L1", "bea", 50);

        offer.counter(80);
        assertEquals(OfferStatus.COUNTERED, offer.getStatus());
        assertEquals(80, offer.getCounterPrice());
        // UC5 Special Requirement 4: the buyer agrees to a counter by making a new offer
        assertThrows(IllegalStateException.class, () -> offer.accept(NOW));
        assertThrows(IllegalStateException.class, () -> offer.counter(90));

        offer.decline();
        assertEquals(OfferStatus.DECLINED, offer.getStatus());
    }

    @Test
    void priceOffer_rejectsPricesThatAreNotPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new PriceOffer("L1", "bea", 0));
        assertThrows(IllegalArgumentException.class, () -> new PriceOffer("L1", "bea", -5));
        assertThrows(IllegalArgumentException.class, () -> new PriceOffer("L1", "bea", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PriceOffer("L1", "bea", Double.POSITIVE_INFINITY));

        PriceOffer offer = new PriceOffer("L1", "bea", 50);
        assertThrows(IllegalArgumentException.class, () -> offer.counter(0));
        assertEquals(OfferStatus.PENDING, offer.getStatus()); // a bad counter changes nothing
    }

    @Test
    void borrowRequest_needsAtLeastTheMinimumBorrowTime() {
        assertDoesNotThrow(() -> new BorrowRequest("L1", "bea", NOW, NOW.plusSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new BorrowRequest("L1", "bea", NOW, NOW.plusSeconds(4)));
        assertThrows(IllegalArgumentException.class, () -> new BorrowRequest("L1", "bea", NOW, NOW.minusDays(1)));
        assertThrows(NullPointerException.class, () -> new BorrowRequest("L1", "bea", NOW, null));
    }

    @Test
    void borrowRequest_autoReturnCompletesOnTheDueDate() {
        BorrowRequest request = new BorrowRequest("L1", "bea", NOW, NOW.plusDays(3));
        request.accept(NOW);

        request.autoReturn();

        assertEquals(NOW.plusDays(3), request.getDueDate());
        assertEquals(request.getDueDate(), request.getCompletedDate());
        assertTrue(request.isAutoReturned());
        assertThrows(IllegalStateException.class, request::autoReturn); // returned only once
    }

    @Test
    void borrowRequest_counterStoresTheNewDates() {
        BorrowRequest request = new BorrowRequest("L1", "bea", NOW, NOW.plusDays(3));

        request.counter(NOW.plusDays(1), NOW.plusDays(2));

        assertEquals(OfferStatus.COUNTERED, request.getStatus());
        assertEquals(NOW.plusDays(1), request.getCounterStartDate());
        assertEquals(NOW.plusDays(2), request.getCounterEndDate());
    }
}
