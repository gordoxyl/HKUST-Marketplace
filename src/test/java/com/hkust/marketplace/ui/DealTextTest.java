package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.services.Deal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the wording of My Deals (UC7 step 2). State-based (Lecture 13): a deal is moved
 * through its states, and the dates and due date it shows are checked in each.
 */
class DealTextTest {

    private static final LocalDateTime MADE = LocalDateTime.of(2026, 11, 9, 14, 5);

    private final SaleListing book = new SaleListing("sam", "Calculus Textbook", Category.TEXTBOOKS,
            ItemCondition.USED, "A book", MeetupLocation.LIBRARY, 180);

    private static <O extends Offer> O made(O offer) {
        offer.markAdded("O1", MADE);
        return offer;
    }

    private Deal deal(Offer offer) {
        return new Deal(offer, book, "Sam Lee", false, false, false);
    }

    @Test
    void about_namesTheSellerAndTheDealType() {
        assertEquals("Seller: Sam Lee · Price offer", DealText.about(deal(made(new PriceOffer("L1", "bea", 160)))));
        assertEquals("Seller: Sam Lee · Borrow request",
                DealText.about(deal(made(new BorrowRequest("L1", "bea", MADE, MADE.plusDays(7))))));
    }

    @Test
    void dates_growAsThePurchaseMovesOn() {
        PriceOffer offer = made(new PriceOffer("L1", "bea", 160));
        assertEquals("Made 09 Nov 2026", DealText.dates(deal(offer)));

        offer.accept(MADE.plusDays(1));
        assertEquals("Made 09 Nov 2026 · Accepted 10 Nov 2026", DealText.dates(deal(offer)));

        offer.complete(MADE.plusDays(3));
        assertEquals("Made 09 Nov 2026 · Accepted 10 Nov 2026 · Completed 12 Nov 2026", DealText.dates(deal(offer)));
    }

    @Test
    void borrowedItem_showsItsDueDateUntilItIsReturned() {
        BorrowRequest request = made(new BorrowRequest("L1", "bea", MADE.plusDays(1), MADE.plusDays(8)));
        assertEquals(Optional.empty(), DealText.due(deal(request))); // not accepted yet: no due date

        request.accept(MADE.plusHours(1));
        assertEquals(Optional.of("Due 17 Nov 2026 14:05"), DealText.due(deal(request)));

        request.complete(MADE.plusDays(2)); // returned by the buyer
        assertEquals(Optional.empty(), DealText.due(deal(request)));
        assertTrue(DealText.dates(deal(request)).endsWith("· Returned 11 Nov 2026"));
    }

    @Test
    void itemReturnedWhenThePeriodEnded_saysSo() {
        BorrowRequest request = made(new BorrowRequest("L1", "bea", MADE.plusDays(1), MADE.plusDays(8)));
        request.accept(MADE.plusHours(1));
        request.autoReturn(); // UC12: the completion date is the due date

        assertTrue(DealText.dates(deal(request)).endsWith("· Returned automatically 17 Nov 2026"));
    }

    @Test
    void purchase_hasNoDueDate() {
        PriceOffer offer = made(new PriceOffer("L1", "bea", 160));
        offer.accept(MADE.plusDays(1));

        assertEquals(Optional.empty(), DealText.due(deal(offer)));
    }

    @Test
    void questionsAndMessages_nameTheListingAndTheSeller() {
        Deal purchase = deal(made(new PriceOffer("L1", "bea", 160)));

        assertEquals("Mark your price offer for 'Calculus Textbook' complete? Do this once you have received "
                + "the item. It cannot be undone.", DealText.confirmComplete(purchase));       // UC7 step 4
        assertEquals("Return 'Calculus Textbook' to Sam Lee now? This ends the borrowing and cannot be undone.",
                DealText.confirmReturn(purchase));                                             // A2 step 1
        assertEquals("'Calculus Textbook' was returned. Sam Lee has been notified.", DealText.returned(purchase));
        assertEquals("The deal for 'Calculus Textbook' is complete, and Sam Lee has been notified. "
                + "Would you like to rate and review the seller now?", DealText.ratePrompt(purchase)); // step 9
    }
}
