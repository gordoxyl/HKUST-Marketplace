package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.services.SellerOfferService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Driver tests (Lecture 14) for the seller's answers inside a chat (Task 2.5). The chat itself
 * is not needed: each test calls {@code accept}, {@code decline} or {@code counter} as the chat
 * will, and a stub {@link SellerOfferAnswers.SellerPrompts} plays the seller, so no JavaFX
 * dialog opens. The states are reached through the models' own events (state-based, Lecture 13).
 */
class SellerOfferAnswersTest {

    private static final String BAD_PRICE =
            "The counter price must be a positive amount, such as 150 or 99.50.";
    private static final String NO_PERIOD =
            "Please enter both the start and the end of the counter period, each with a date and a time.";

    private TestClock clock;
    private DataStore store;
    private SellerOfferService service;
    private StubPrompts prompts;
    private SellerOfferAnswers answers;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        service = new SellerOfferService(store);
        prompts = new StubPrompts();
        answers = new SellerOfferAnswers(service, prompts);
        Seller sam = Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        SessionManager.login(sam);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    private SaleListing liveSale() {
        return Fixtures.live(Fixtures.sale(store, "sam", 100));
    }

    private PriceOffer priceOffer(Listing listing, String buyer, double price) {
        return (PriceOffer) store.addOffer(new PriceOffer(listing.getId(), buyer, price));
    }

    private BorrowRequest borrowRequest(Lending listing) {
        return (BorrowRequest) store.addOffer(new BorrowRequest(listing.id(),
                "bea", clock.now().plusHours(1), clock.now().plusHours(2)));
    }

    /** A lend listing and its id, so the helper's signature stays short. */
    private record Lending(String id) {
    }

    private Lending liveLend() {
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        return new Lending(calculator.getId());
    }

    private static String time(LocalDateTime moment) {
        return String.format("%02d:%02d:%02d", moment.getHour(), moment.getMinute(), moment.getSecond());
    }

    private static SellerOfferAnswers.SellerPrompts.Period period(LocalDateTime start, LocalDateTime end) {
        return new SellerOfferAnswers.SellerPrompts.Period(start.toLocalDate(), time(start),
                end.toLocalDate(), time(end));
    }

    // ---------- Accept ----------

    @Test
    void acceptingAPendingOffer_reservesTheListing_andDeclinesTheOthers() {
        SaleListing listing = liveSale();
        PriceOffer winner = priceOffer(listing, "bea", 80);
        PriceOffer other = priceOffer(listing, "ben", 90);

        assertTrue(answers.accept(winner));

        assertEquals(OfferStatus.ACCEPTED, winner.getStatus());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertEquals(OfferStatus.DECLINED, other.getStatus());
        assertTrue(prompts.infos.contains("Offer accepted"));
        assertTrue(prompts.confirmations.get(0).startsWith("Accept price offer"));
    }

    @Test
    void acceptingABorrowRequest_lendsTheItem() {
        Lending calculator = liveLend();
        BorrowRequest request = borrowRequest(calculator);

        assertTrue(answers.accept(request));

        assertEquals(OfferStatus.ACCEPTED, request.getStatus());
        assertEquals(ListingStatus.LENT, store.findListing(calculator.id()).getStatus());
        assertTrue(prompts.confirmations.get(0).contains("Lent"));
    }

    @Test
    void cancellingTheConfirmation_changesNothing() {
        SaleListing listing = liveSale();
        PriceOffer offer = priceOffer(listing, "bea", 80);
        prompts.confirmAnswer = false;

        assertFalse(answers.accept(offer));

        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertTrue(prompts.problems.isEmpty());
        assertTrue(prompts.infos.isEmpty());
    }

    @Test
    void acceptingACounteredOffer_failsWithTheServicesMessage() {
        PriceOffer offer = priceOffer(liveSale(), "bea", 80);
        offer.counter(95);

        assertFalse(answers.accept(offer));

        assertEquals(List.of("Only a pending price offer can be accepted."), prompts.problems);
        assertEquals(OfferStatus.COUNTERED, offer.getStatus());
    }

    @Test
    void acceptingAnOfferThatIsNoLongerOpen_failsWithTheServicesMessage() {
        PriceOffer offer = priceOffer(liveSale(), "bea", 80);
        offer.decline();

        assertFalse(answers.accept(offer));

        assertEquals(1, prompts.problems.size());
        assertEquals("Only a pending price offer can be accepted.", prompts.problems.get(0));
    }

    @Test
    void aBorrowRequestWhosePeriodHasEnded_cannotBeAccepted_butCanBeCounteredWithNewDates() {
        // Regression R8: the item would be lent and returned at once, so the service refuses.
        Lending calculator = liveLend();
        BorrowRequest request = borrowRequest(calculator);
        clock.advance(Duration.ofHours(3));

        assertFalse(answers.accept(request));
        assertTrue(prompts.problems.get(0).startsWith("The borrow period of this request has already ended."));
        assertEquals(OfferStatus.PENDING, request.getStatus());

        LocalDateTime start = clock.now().plusHours(1);
        prompts.periods.add(period(start, start.plusDays(1)));
        assertTrue(answers.counter(request));
        assertEquals(OfferStatus.COUNTERED, request.getStatus());
    }

    // ---------- Decline ----------

    @Test
    void decliningACounteredOffer_works() {
        PriceOffer offer = priceOffer(liveSale(), "bea", 80);
        offer.counter(95);

        assertTrue(answers.decline(offer));

        assertEquals(OfferStatus.DECLINED, offer.getStatus());
        assertTrue(prompts.infos.contains("Offer declined"));
    }

    @Test
    void cancellingTheDecline_changesNothing() {
        PriceOffer offer = priceOffer(liveSale(), "bea", 80);
        prompts.confirmAnswer = false;

        assertFalse(answers.decline(offer));

        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertTrue(prompts.infos.isEmpty());
    }

    // ---------- Counter ----------

    @Test
    void counteringAPriceOffer_recordsTheTypedPrice() {
        PriceOffer offer = priceOffer(liveSale(), "bea", 80);
        prompts.prices.add("99.50");

        assertTrue(answers.counter(offer));

        assertEquals(OfferStatus.COUNTERED, offer.getStatus());
        assertEquals(99.50, offer.getCounterPrice());
        assertEquals(Collections.singletonList(null), prompts.pricePrompts);
        assertTrue(prompts.infos.contains("Counter sent"));
    }

    @Test
    void aBadCounterPrice_isShown_andTheFormComesBackWithWhatWasTyped() {
        PriceOffer offer = priceOffer(liveSale(), "bea", 80);
        prompts.prices.addAll(List.of("abc", "0")); // then the stub cancels

        assertFalse(answers.counter(offer));

        assertEquals(List.of(BAD_PRICE, BAD_PRICE), prompts.problems);
        assertEquals(Arrays.asList(null, "abc", "0"), prompts.pricePrompts);
        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertNull(offer.getCounterPrice());
    }

    @Test
    void aCounteredOfferCannotBeCountered_untilTheSellerCancels() {
        PriceOffer offer = priceOffer(liveSale(), "bea", 80);
        offer.counter(95);
        prompts.prices.add("120");

        assertFalse(answers.counter(offer));

        assertTrue(prompts.problems.get(0).contains("Only a pending price offer can be countered."));
        assertEquals(OfferStatus.COUNTERED, offer.getStatus());
    }

    @Test
    void aClaimIsNotCountered_andNoFormOpens() {
        Listing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        Offer claim = store.addOffer(new Claim(lamp.getId(), "bea"));

        assertFalse(answers.counter(claim));

        assertTrue(prompts.pricePrompts.isEmpty());
        assertTrue(prompts.periodPrompts.isEmpty());
    }

    @Test
    void aBadCounterPeriod_isShown_andTheFormComesBackWithWhatWasEntered() {
        Lending calculator = liveLend();
        BorrowRequest request = borrowRequest(calculator);
        LocalDateTime start = clock.now().plusHours(1);
        LocalDateTime end = start.plusDays(1);
        SellerOfferAnswers.SellerPrompts.Period wrongTime = new SellerOfferAnswers.SellerPrompts.Period(
                start.toLocalDate(), "25:99", end.toLocalDate(), time(end));
        prompts.periods.add(wrongTime);
        prompts.periods.add(period(start, end));

        assertTrue(answers.counter(request));

        assertEquals(List.of(NO_PERIOD), prompts.problems);
        assertEquals(Arrays.asList(null, wrongTime), prompts.periodPrompts);
        assertEquals(OfferStatus.COUNTERED, request.getStatus());
        assertEquals(start, request.getCounterStartDate());
    }

    /** A stub seller: answers exactly as the test set it up, and records what it was shown. */
    private static final class StubPrompts implements SellerOfferAnswers.SellerPrompts {

        boolean confirmAnswer = true;
        final Deque<String> prices = new ArrayDeque<>();
        final Deque<Period> periods = new ArrayDeque<>();
        final List<String> pricePrompts = new ArrayList<>();
        final List<Period> periodPrompts = new ArrayList<>();
        final List<String> confirmations = new ArrayList<>();
        final List<String> infos = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        @Override
        public boolean confirm(String title, String message) {
            confirmations.add(title + " - " + message);
            return confirmAnswer;
        }

        @Override
        public Optional<String> askCounterPrice(PriceOffer offer, String typedBefore) {
            pricePrompts.add(typedBefore);
            return Optional.ofNullable(prices.poll());
        }

        @Override
        public Optional<Period> askCounterPeriod(BorrowRequest request, Period before) {
            periodPrompts.add(before);
            return Optional.ofNullable(periods.poll());
        }

        @Override
        public void info(String title, String message) {
            infos.add(title);
        }

        @Override
        public void problem(ServiceException e) {
            problems.add(e.getMessage());
        }
    }
}
