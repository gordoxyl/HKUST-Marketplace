package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.hkust.marketplace.services.OfferAction.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what each party can do with an offer now (assumptions register 71): the seller's
 * buttons on Offers &amp; Requests, the only place where offers are answered (Task 2.5), and
 * the buyer's Agree to Counter in the chat.
 *
 * <p><b>State-based tests</b> (Lecture 13): each kind of offer is moved into each state of the
 * Offer state machine through the model's own events, and the actions for the seller and for
 * the buyer are checked in each.
 *
 * <p><b>A contract test against the seller's service</b> (Lecture 14, integration): in every
 * state where an offer can be answered, each answer that Offers &amp; Requests enables succeeds
 * with {@link SellerOfferService} and each one it disables is refused. If either side changes
 * its rules, this test fails instead of the screen offering a button that cannot work.
 */
class OfferActionTest {

    private static final LocalDateTime NOW = Fixtures.START;

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** A new offer of the kind, moved into the state through the model's events. */
    private static Offer offer(String kind, String state) {
        Offer offer = switch (kind) {
            case "PRICE" -> new PriceOffer("L1", "bea", 150);
            case "BORROW" -> new BorrowRequest("L1", "bea", NOW.plusHours(1), NOW.plusDays(2));
            case "CLAIM" -> new Claim("L1", "bea");
            default -> throw new IllegalArgumentException(kind);
        };
        return moveTo(offer, state);
    }

    private static Offer moveTo(Offer offer, String state) {
        switch (state) {
            case "PENDING" -> { }
            case "COUNTERED" -> {
                if (offer instanceof PriceOffer price) {
                    price.counter(170);
                } else {
                    ((BorrowRequest) offer).counter(NOW.plusDays(1), NOW.plusDays(3));
                }
            }
            case "ACCEPTED" -> offer.accept(NOW);
            case "DECLINED" -> offer.decline();
            case "COMPLETED" -> {
                offer.accept(NOW);
                offer.complete(NOW.plusDays(1));
            }
            default -> throw new IllegalArgumentException(state);
        }
        return offer;
    }

    private static List<OfferAction> actions(String names) {
        return names.isBlank() ? List.of() : Arrays.stream(names.trim().split("\\s+")).map(OfferAction::valueOf).toList();
    }

    // ================= State-based: the actions in each state =================

    @ParameterizedTest(name = "{0} {1}: seller [{2}], buyer [{3}]")
    @CsvSource({
            "PRICE,  PENDING,   ACCEPT DECLINE COUNTER, ''",
            "PRICE,  COUNTERED, DECLINE,                AGREE_TO_COUNTER",
            "PRICE,  ACCEPTED,  '',                     ''",
            "PRICE,  DECLINED,  '',                     ''",
            "PRICE,  COMPLETED, '',                     ''",
            "BORROW, PENDING,   ACCEPT DECLINE COUNTER, ''",
            "BORROW, COUNTERED, DECLINE,                AGREE_TO_COUNTER",
            "BORROW, ACCEPTED,  '',                     ''",
            "BORROW, DECLINED,  '',                     ''",
            "BORROW, COMPLETED, '',                     ''",
            "CLAIM,  PENDING,   ACCEPT DECLINE,         ''", // a claim cannot be countered (Task 2.5)
            "CLAIM,  ACCEPTED,  '',                     ''",
            "CLAIM,  DECLINED,  '',                     ''",
            "CLAIM,  COMPLETED, '',                     ''"
    })
    void theActionsOnAnOffer_followItsState(String kind, String state, String seller, String buyer) {
        Offer offer = offer(kind, state);

        assertEquals(actions(seller), OfferAction.allowedFor(offer, false));
        assertEquals(actions(buyer), OfferAction.allowedFor(offer, true));
    }

    @Test
    void theSellersButtons_areAcceptDeclineAndCounter_withoutCounterOnAGiveaway() {
        assertEquals(List.of(ACCEPT, DECLINE, COUNTER), OfferAction.sellerButtons(ListingType.SELL));
        assertEquals(List.of(ACCEPT, DECLINE, COUNTER), OfferAction.sellerButtons(ListingType.LEND));
        assertEquals(List.of(ACCEPT, DECLINE), OfferAction.sellerButtons(ListingType.GIVEAWAY)); // claims cannot be countered
    }

    // ================= Contract with the seller's service =================

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"PRICE, PENDING", "PRICE, COUNTERED", "BORROW, PENDING", "BORROW, COUNTERED", "CLAIM, PENDING"})
    void everyAnswerOffersAndRequestsEnables_isOneTheSellersServiceAllows_andNoOther(String kind, String state) {
        for (OfferAction answer : List.of(ACCEPT, DECLINE, COUNTER)) {
            DataStore store = Fixtures.store(Fixtures.clock()); // a fresh deal for each answer
            Seller sam = Fixtures.seller(store, "sam");
            Fixtures.buyer(store, "bea");
            Offer offer = store.addOffer(newOfferOn(store, kind));
            moveTo(offer, state);
            SessionManager.login(sam);
            boolean offered = OfferAction.allowedFor(offer, false).contains(answer);

            boolean worked = tryAnswer(new SellerOfferService(store), offer, answer);

            assertEquals(offered, worked, kind + " " + state + ": " + answer);
        }
    }

    /** An offer of the kind on a live listing of the matching type, by bea on sam's listing. */
    private static Offer newOfferOn(DataStore store, String kind) {
        return switch (kind) {
            case "PRICE" -> new PriceOffer(Fixtures.live(Fixtures.sale(store, "sam", 200)).getId(), "bea", 150);
            case "BORROW" -> new BorrowRequest(Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7))).getId(),
                    "bea", NOW.plusHours(1), NOW.plusDays(2));
            case "CLAIM" -> new Claim(Fixtures.live(Fixtures.giveaway(store, "sam")).getId(), "bea");
            default -> throw new IllegalArgumentException(kind);
        };
    }

    /** Does what the seller's answer would do, through the seller's service; false if it was refused. */
    private static boolean tryAnswer(SellerOfferService service, Offer offer, OfferAction answer) {
        try {
            switch (answer) {
                case ACCEPT -> service.accept(offer.getId());
                case DECLINE -> service.decline(offer.getId());
                case COUNTER -> {
                    if (offer instanceof BorrowRequest) {
                        service.counterBorrow(offer.getId(), NOW.plusDays(1), NOW.plusDays(4));
                    } else {
                        service.counterPrice(offer.getId(), 175); // a claim is refused here
                    }
                }
                case AGREE_TO_COUNTER -> throw new IllegalArgumentException("not a seller's answer");
            }
            return true;
        } catch (ServiceException e) {
            return false;
        }
    }
}
