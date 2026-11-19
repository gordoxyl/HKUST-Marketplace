package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what a chat offers to do (assumptions register 71): which of its offers the viewer
 * can act on, newest first. Equivalence partitions of the history's lines: messages, offers
 * that are open (Pending or Countered), and offers that are finished (Accepted, Declined), each
 * seen by the seller and by the buyer.
 */
class ConversationTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 18, 22, 0);

    private final SaleListing book = new SaleListing("sam", "Calculus Textbook", Category.TEXTBOOKS,
            ItemCondition.USED, "A book", MeetupLocation.LIBRARY, 180);
    private final List<ChatEntry> entries = new ArrayList<>();

    private Offer offer(double price) {
        return new PriceOffer("L1", "bea", price);
    }

    /** Adds the offer to the history, after everything added before it. */
    private Offer said(Offer offer) {
        entries.add(new ChatEntry(entries.size(), "bea", "Bea Chan", TIME.plusMinutes(entries.size()), null, offer));
        return offer;
    }

    private void message(String sender, String text) {
        entries.add(new ChatEntry(entries.size(), sender, sender, TIME.plusMinutes(entries.size()), text, null));
    }

    private Conversation seenBy(String viewer) {
        return new Conversation(book, "bea", "Bea Chan", "Sam Lee", viewer, entries);
    }

    @Test
    void theSeller_actsOnTheOpenOffers_newestFirst() {
        Offer accepted = said(offer(120));
        accepted.accept(TIME);
        message("bea", "Would you take less?");
        Offer countered = said(offer(150));
        ((PriceOffer) countered).counter(170);
        Offer declined = said(offer(100));
        declined.decline();
        Offer pending = said(offer(160));

        assertEquals(List.of(pending, countered), seenBy("sam").offersToActOn());
        assertEquals(List.of(OfferAction.ACCEPT, OfferAction.DECLINE, OfferAction.COUNTER), seenBy("sam").actionsFor(pending));
        assertEquals(List.of(OfferAction.DECLINE), seenBy("sam").actionsFor(countered));
    }

    @Test
    void theBuyer_actsOnlyOnTheOffersTheSellerCountered() {
        said(offer(160));
        Offer first = said(offer(140));
        ((PriceOffer) first).counter(165);
        Offer second = said(offer(150));
        ((PriceOffer) second).counter(170);

        assertEquals(List.of(second, first), seenBy("bea").offersToActOn());
        assertEquals(List.of(OfferAction.AGREE_TO_COUNTER), seenBy("bea").actionsFor(second));
    }

    @Test
    void aChatWithoutOpenOffers_hasNothingToActOn() {
        assertTrue(seenBy("sam").offersToActOn().isEmpty()); // no history at all
        message("bea", "Is it still available?");
        Offer declined = said(offer(100));
        declined.decline();

        assertTrue(seenBy("sam").offersToActOn().isEmpty());
        assertTrue(seenBy("bea").offersToActOn().isEmpty());
    }

    @Test
    void theOffersFollowTheirStatus_whenTheChatIsLoadedAgain() {
        Offer pending = said(offer(160));
        assertEquals(List.of(pending), seenBy("sam").offersToActOn());

        pending.accept(TIME); // answered, for example from Offers & Requests

        assertTrue(seenBy("sam").offersToActOn().isEmpty());
    }
}
