package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UC5 Make Offer. The class is a test driver for {@link OfferService}; a
 * {@link TestClock} stands in for the real clock (a stub), and the store keeps its data in
 * memory (Lecture 14). The tests are grouped by how they were designed (Lectures 12-14):
 *
 * <p><b>Thread tests</b> follow one use-case flow through all the objects it touches (service,
 * store, chat thread, notification board), starting with the basic flow of each offer type.
 *
 * <p><b>Equivalence partitions and boundary values</b> for what the buyer types:
 * <pre>
 * Price (A2)            invalid: nothing typed | not a number | not positive      valid: positive amount
 *   boundary at 0:      -0.01 (invalid), 0 (invalid), 0.01 (valid)
 * Start of period (A3)  invalid: missing | in the past                             valid: now or later
 *   boundary at now:    now - 1 s (invalid), now (valid), now + 1 s (valid)
 * Length of period (A3) invalid: shorter than 5 s (also 0 and negative)            valid: 5 s or longer
 *   boundary at 5 s:    4 s and 4.999999999 s (invalid), 5 s (valid), 6 s (valid)
 * Offer type            valid: the type that fits the listing    invalid: either of the other two
 * </pre>
 *
 * <p><b>State-based tests</b> put the listing into each state first, through the state machine,
 * and then apply the event "make an offer": only an available listing accepts it (A1).
 *
 * <p><b>Basis paths</b> of {@code checkPeriod} (cyclomatic complexity 5): start missing, end
 * missing, start in the past, period too short, and the valid path.
 */
class OfferServiceTest {

    private static final LocalDateTime NOW = Fixtures.START;

    private TestClock clock;
    private DataStore store;
    private OfferService offers;
    private NotificationService notifications;
    private Seller sam;
    private Buyer bea;
    private Buyer ben;
    private SaleListing book;
    private LendListing calculator;
    private GiveawayListing lamp;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        offers = new OfferService(store);
        notifications = new NotificationService(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        ben = Fixtures.buyer(store, "ben");
        book = Fixtures.live(Fixtures.sale(store, "sam", 180));
        calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        SessionManager.login(bea);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** Logs the user in and returns their notification board. */
    private List<Notification> boardOf(User user) {
        SessionManager.login(user);
        return notifications.getMyNotifications();
    }

    /** Checks that a refused attempt recorded nothing and told nobody. */
    private void assertNothingRecorded() {
        assertTrue(store.getAllOffers().isEmpty(), "no offer is recorded");
        assertTrue(store.getNotificationsFor("sam").isEmpty(), "the seller is not notified");
        assertTrue(store.getThreadsForBuyer("bea").isEmpty(), "no chat thread is created");
    }

    // ================= Thread tests: the basic flow of each offer type =================

    @Test
    void priceOffer_isRecordedAsPendingInTheBuyersChat_andTheSellerIsNotified() {
        OfferForm form = offers.start(book.getId());          // steps 1-4
        assertSame(book, form.listing());
        assertEquals("180", form.price());                    // the buyer corrects the asking price

        Offer offer = offers.submitPriceOffer(book.getId(), "150"); // steps 5-9

        PriceOffer recorded = assertInstanceOf(PriceOffer.class, offer);
        assertEquals(150, recorded.getOfferedPrice());
        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertEquals("bea", offer.getBuyerUsername());
        assertEquals(NOW, offer.getOfferDate());
        ChatThread thread = store.findThread("bea", book.getId());
        assertEquals(List.of(offer), store.getOffersInThread(thread)); // step 7: in the buyer's chat thread

        List<Notification> samBoard = boardOf(sam);           // step 8
        assertEquals(1, samBoard.size());
        assertEquals(NotificationCategory.OFFER_RECEIVED, samBoard.get(0).getCategory());
        assertEquals("Buyer bea made a price offer of HK$ 150 for 'Book'.", samBoard.get(0).getContent());
        assertTrue(boardOf(bea).isEmpty());
        assertTrue(store.isAvailable(book));                  // a price offer does not reserve the listing
    }

    @Test
    void borrowRequest_isRecordedWithItsPeriod_andTheSellerIsNotified() {
        OfferForm form = offers.start(calculator.getId());
        assertEquals(NOW.plusMinutes(1), form.start());       // suggested: starts in a minute...
        assertEquals(NOW.plusMinutes(1).plusDays(7), form.end()); // ...and lasts the listing's lending duration
        assertEquals("", form.price());

        Offer offer = offers.submitBorrowRequest(calculator.getId(), form.start(), form.end());

        BorrowRequest request = assertInstanceOf(BorrowRequest.class, offer);
        assertEquals(form.start(), request.getProposedStartDate());
        assertEquals(form.end(), request.getProposedEndDate());
        assertEquals(OfferStatus.PENDING, request.getStatus());
        assertEquals("Buyer bea sent a borrow request for 'Calculator'.", boardOf(sam).get(0).getContent());
    }

    @Test
    void claim_needsNoInput_andMakesTheGiveawayUnavailable() {
        OfferForm form = offers.start(lamp.getId());          // step 3: nothing to enter
        assertEquals("", form.price());
        assertNull(form.start());
        assertNull(form.end());

        Offer offer = offers.submitClaim(lamp.getId());

        assertInstanceOf(Claim.class, offer);
        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertEquals("Buyer bea claimed 'Lamp'.", boardOf(sam).get(0).getContent());
        assertFalse(store.isAvailable(lamp));                 // Special Requirement 1: one pending claim at a time
    }

    @Test
    void suggestedPrice_keepsTheCents() {
        SaleListing pen = Fixtures.live(Fixtures.sale(store, "sam", 99.5));

        assertEquals("99.50", offers.start(pen.getId()).price());
    }

    @Test
    void offerAppearsInTheChatHistory_andAnExistingChatIsUsed() {
        ChatService chat = new ChatService(store);
        chat.send(chat.openForListing(book.getId()), "Would you take less?");
        clock.advance(Duration.ofMinutes(1));

        Offer offer = offers.submitPriceOffer(book.getId(), "150");

        Conversation history = chat.openForListing(book.getId());
        assertEquals(2, history.entries().size());
        assertSame(offer, history.lastEntry().orElseThrow().offer());
        assertEquals(1, store.getThreadsForBuyer("bea").size()); // the thread the buyer started, not a second one
    }

    @Test
    void offerThenAcceptance_acrossBothRoles_endsWithAReservedListing() {
        // A thread through two use cases: Make Offer (buyer) and Manage Offers & Requests (seller)
        Offer offer = offers.submitPriceOffer(book.getId(), "150");

        new DealRules(store).accept(offer); // the seller's action

        assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
        assertEquals(ListingStatus.RESERVED, book.getStatus());
        assertEquals(1, boardOf(bea).size());
        SessionManager.login(ben);
        assertThrows(ListingUnavailableException.class, () -> offers.start(book.getId())); // A1 for everyone else
    }

    @Test
    void offer_survivesARestart(@TempDir Path folder) {
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed();
        Listing charger = saved.getLiveListings().stream()
                .filter(l -> l.getTitle().startsWith("MacBook")).findFirst().orElseThrow();
        SessionManager.login(saved.findUser("alice"));

        Offer offer = new OfferService(saved).submitPriceOffer(charger.getId(), "200");

        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(file));
        restarted.loadOrSeed();
        PriceOffer reloaded = assertInstanceOf(PriceOffer.class, restarted.findOffer(offer.getId()));
        assertEquals(200, reloaded.getOfferedPrice());
        assertEquals(OfferStatus.PENDING, reloaded.getStatus());
        assertNotNull(restarted.findThread("alice", charger.getId()));
    }

    // ================= A1: Listing Not Available (state-based) =================

    @ParameterizedTest(name = "a {0} listing in status {1}")
    @CsvSource({
            "SELL,     PENDING_REVIEW", "SELL,     REJECTED", "SELL,     RESERVED",
            "SELL,     SOLD",           "SELL,     REMOVED",
            "LEND,     LENT",           "LEND,     REMOVED",
            "GIVEAWAY, RESERVED",       "GIVEAWAY, GIVEN"})
    void listingThatIsNotLive_acceptsNoOffer(ListingType type, ListingStatus status) {
        Listing listing = Fixtures.moveTo(switch (type) {
            case SELL -> Fixtures.sale(store, "sam", 50);
            case LEND -> Fixtures.lend(store, "sam", Duration.ofDays(1));
            case GIVEAWAY -> Fixtures.giveaway(store, "sam");
        }, status);

        ListingUnavailableException atStart = assertThrows(ListingUnavailableException.class,
                () -> offers.start(listing.getId()));                   // {Check Availability}
        assertEquals("This listing is no longer available.", atStart.getMessage());
        assertThrows(ListingUnavailableException.class, () -> submitFor(listing)); // {Record Offer}
        assertNothingRecorded();
    }

    /** Submits the offer type that fits the listing, with valid terms. */
    private Offer submitFor(Listing listing) {
        return switch (listing.getType()) {
            case SELL -> offers.submitPriceOffer(listing.getId(), "10");
            case LEND -> offers.submitBorrowRequest(listing.getId(), NOW.plusHours(1), NOW.plusHours(2));
            case GIVEAWAY -> offers.submitClaim(listing.getId());
        };
    }

    @Test
    void giveawayWithAPendingClaim_acceptsNoSecondClaim_fromAnotherBuyerOrTheSameOne() {
        Offer first = offers.submitClaim(lamp.getId());

        assertThrows(ListingUnavailableException.class, () -> offers.submitClaim(lamp.getId())); // the same buyer
        SessionManager.login(ben);
        assertThrows(ListingUnavailableException.class, () -> offers.start(lamp.getId()));
        assertThrows(ListingUnavailableException.class, () -> offers.submitClaim(lamp.getId()));
        assertEquals(List.of(first), store.getOffersForListing(lamp.getId()));
    }

    @Test
    void giveaway_canBeClaimedAgain_afterTheSellerDeclinesTheClaim() {
        Offer first = offers.submitClaim(lamp.getId());
        store.transaction(first::decline);
        SessionManager.login(ben);

        Offer second = offers.submitClaim(lamp.getId());

        assertEquals(OfferStatus.PENDING, second.getStatus());
        assertEquals(OfferStatus.DECLINED, first.getStatus());
    }

    @Test
    void listingOfADeactivatedSeller_acceptsNoOffer() {
        store.transaction(() -> sam.setActive(false));

        assertThrows(ListingUnavailableException.class, () -> offers.start(book.getId()));
        assertThrows(ListingUnavailableException.class, () -> offers.submitPriceOffer(book.getId(), "150"));
        assertTrue(store.getAllOffers().isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"L999", ""})
    void listingThatDoesNotExist_acceptsNoOffer(String listingId) {
        assertThrows(ListingUnavailableException.class, () -> offers.start(listingId));
        assertThrows(ListingUnavailableException.class, () -> offers.submitClaim(listingId));
    }

    @Test
    void listingTakenWhileTheBuyerFilledInTheForm_isRefusedAtTheFinalCheck() {
        // The second check of step 7: available at {Check Availability}, not any more at {Record Offer}
        offers.start(book.getId());
        Offer bensOffer = store.addOffer(new PriceOffer(book.getId(), "ben", 170));
        new DealRules(store).accept(bensOffer); // the seller accepted Ben in the meantime

        assertThrows(ListingUnavailableException.class, () -> offers.submitPriceOffer(book.getId(), "175"));

        assertEquals(List.of(bensOffer), store.getOffersForListing(book.getId()));
    }

    // ================= A2: Invalid Price (equivalence partitions and boundary values) =================

    @ParameterizedTest(name = "price \"{0}\"")
    @NullSource
    @ValueSource(strings = {
            "", "   ",                                         // nothing typed
            "abc", "12abc", "1,000", "$150", "1e3", "150.", ".5", "150.999", "NaN", "Infinity", "+5", // not a number
            "0", "0.00", "-0.01", "-1", "-150"})               // not positive; 0 and -0.01 are the boundary
    void invalidPrice_isRefused_andNothingIsRecorded(String price) {
        ServiceException refused = assertThrows(ServiceException.class,
                () -> offers.submitPriceOffer(book.getId(), price));

        assertEquals(OfferService.INVALID_PRICE, refused.getMessage());
        assertFalse(refused instanceof ListingUnavailableException); // the buyer can correct it (back to Enter Terms)
        assertNothingRecorded();
    }

    @ParameterizedTest(name = "price \"{0}\" is HK$ {1}")
    @CsvSource({"0.01, 0.01", "1, 1", "150, 150", "99.5, 99.5", "99.50, 99.5", "' 150 ', 150", "1000000, 1000000"})
    void positiveAmount_isAccepted(String price, double expected) {
        PriceOffer offer = (PriceOffer) offers.submitPriceOffer(book.getId(), price);

        assertEquals(expected, offer.getOfferedPrice());
    }

    @Test
    void priceTooLargeToBeANumber_isRefused() {
        String digits = "9".repeat(400);

        assertEquals(OfferService.INVALID_PRICE,
                assertThrows(ServiceException.class, () -> offers.submitPriceOffer(book.getId(), digits)).getMessage());
    }

    @Test
    void invalidPrice_isReportedBeforeTheAvailabilityIsCheckedAgain() {
        // {Check Terms} comes before {Record Offer}: the buyer first hears about the price
        store.transaction(book::remove);

        ServiceException refused = assertThrows(ServiceException.class,
                () -> offers.submitPriceOffer(book.getId(), "free"));

        assertEquals(OfferService.INVALID_PRICE, refused.getMessage());
    }

    // ================= A3: Invalid Borrow Period (basis paths and boundary values) =================

    @Test
    void periodWithAMissingDate_isRefused() {
        // Basis paths 1 and 2: the start is missing; the end is missing
        for (LocalDateTime[] period : new LocalDateTime[][] {
                {null, NOW.plusDays(1)}, {NOW.plusHours(1), null}, {null, null}}) {
            ServiceException refused = assertThrows(ServiceException.class,
                    () -> offers.submitBorrowRequest(calculator.getId(), period[0], period[1]));
            assertEquals(OfferService.PERIOD_INCOMPLETE, refused.getMessage());
        }
        assertNothingRecorded();
    }

    @Test
    void periodStartingInThePast_isRefused_atTheBoundaryOfNow() {
        // Basis path 3, and the boundary at "now": one second earlier is in the past
        ServiceException refused = assertThrows(ServiceException.class,
                () -> offers.submitBorrowRequest(calculator.getId(), NOW.minusSeconds(1), NOW.plusDays(1)));

        assertEquals(OfferService.PERIOD_STARTS_IN_PAST, refused.getMessage());
        assertNothingRecorded();
    }

    @ParameterizedTest(name = "starting {0} s from now")
    @ValueSource(longs = {0, 1, 3600})
    void periodStartingNowOrLater_isAccepted(long secondsFromNow) {
        LocalDateTime start = NOW.plusSeconds(secondsFromNow);

        Offer offer = offers.submitBorrowRequest(calculator.getId(), start, start.plusDays(1));

        assertEquals(OfferStatus.PENDING, offer.getStatus());
    }

    @ParameterizedTest(name = "a period of {0} ns")
    @ValueSource(longs = {4_000_000_000L, 4_999_999_999L, 0, -1_000_000_000L, -86_400_000_000_000L})
    void periodShorterThanFiveSeconds_isRefused(long nanos) {
        // Basis path 4, and the boundary at the minimum borrow time (Special Requirement 3)
        LocalDateTime start = NOW.plusHours(1);

        ServiceException refused = assertThrows(ServiceException.class,
                () -> offers.submitBorrowRequest(calculator.getId(), start, start.plusNanos(nanos)));

        assertEquals("The borrow period must end at least 5 seconds after it starts.", refused.getMessage());
        assertNothingRecorded();
    }

    @ParameterizedTest(name = "a period of {0} s")
    @ValueSource(longs = {5, 6, 604_800})
    void periodOfFiveSecondsOrLonger_isAccepted(long seconds) {
        // Basis path 5: the valid path, at and above the boundary
        LocalDateTime start = NOW.plusHours(1);

        BorrowRequest request = (BorrowRequest) offers.submitBorrowRequest(calculator.getId(), start, start.plusSeconds(seconds));

        assertEquals(start.plusSeconds(seconds), request.getDueDate());
    }

    @Test
    void periodWithTwoProblems_reportsTheStartFirst() {
        // In the past and too short: A3 names the problems in this order
        ServiceException refused = assertThrows(ServiceException.class,
                () -> offers.submitBorrowRequest(calculator.getId(), NOW.minusHours(1), NOW.minusHours(1).plusSeconds(1)));

        assertEquals(OfferService.PERIOD_STARTS_IN_PAST, refused.getMessage());
    }

    @Test
    void nowIsReadWhenTheRequestIsSubmitted_notWhenTheFormWasOpened() {
        OfferForm form = offers.start(calculator.getId());   // suggests a start one minute from now
        clock.advance(Duration.ofMinutes(2));                // the buyer waited too long

        assertThrows(ServiceException.class,
                () -> offers.submitBorrowRequest(calculator.getId(), form.start(), form.end()));
    }

    // ================= The offer type must fit the listing type =================

    @Test
    void offerOfAnotherType_isRefused() {
        ServiceException priceOnLend = assertThrows(ServiceException.class,
                () -> offers.submitPriceOffer(calculator.getId(), "50"));
        assertEquals("A price offer cannot be made on a lend listing.", priceOnLend.getMessage());
        assertThrows(ServiceException.class, () -> offers.submitPriceOffer(lamp.getId(), "50"));
        assertThrows(ServiceException.class,
                () -> offers.submitBorrowRequest(book.getId(), NOW.plusHours(1), NOW.plusHours(2)));
        assertThrows(ServiceException.class,
                () -> offers.submitBorrowRequest(lamp.getId(), NOW.plusHours(1), NOW.plusHours(2)));
        assertThrows(ServiceException.class, () -> offers.submitClaim(book.getId()));
        assertThrows(ServiceException.class, () -> offers.submitClaim(calculator.getId()));
        assertNothingRecorded();
    }

    // ================= Special Requirement 2: one atomic step =================

    @Test
    void claimsSubmittedAtTheSameMoment_onlyTheFirstSucceeds() throws Exception {
        // The giveaway is the critical case (Lecture 14: regression-test critical subsystems)
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    offers.submitClaim(lamp.getId());
                    return true;
                } catch (ListingUnavailableException e) {
                    return false;
                }
            }));
        }

        go.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(10, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertEquals(1, succeeded);
        assertEquals(1, store.getOffersForListing(lamp.getId()).size());
        assertEquals(1, store.getNotificationsFor("sam").size());
    }

    @Test
    void twoBuyersClaimOneAfterTheOther_theSecondIsToldItIsNoLongerAvailable() {
        offers.submitClaim(lamp.getId());
        SessionManager.login(ben);

        assertThrows(ListingUnavailableException.class, () -> offers.submitClaim(lamp.getId()));
        assertTrue(store.getThreadsForBuyer("ben").isEmpty());
    }

    // ================= Special Requirements 4 and 5: counters and earlier offers =================

    @Test
    void agreeingToAPriceCounter_startsWithTheCounterPrice_andMakesANewOffer() {
        PriceOffer first = (PriceOffer) offers.submitPriceOffer(book.getId(), "150");
        store.transaction(() -> first.counter(170)); // the seller's counter

        OfferForm form = offers.startFromCounter(first.getId());
        assertEquals("170", form.price());           // step 4: the system fills in the counter's price
        assertSame(book, form.listing());
        Offer agreed = offers.submitPriceOffer(book.getId(), form.price());

        assertEquals(170, ((PriceOffer) agreed).getOfferedPrice());
        assertEquals(OfferStatus.PENDING, agreed.getStatus());    // for the seller to accept or decline
        assertEquals(OfferStatus.COUNTERED, first.getStatus());   // the earlier offer stays as a record
        assertEquals(List.of(first, agreed), store.getOffersInThread(store.findThread("bea", book.getId())));
    }

    @Test
    void agreeingToADateCounter_startsWithTheCounterDates() {
        BorrowRequest first = (BorrowRequest) offers.submitBorrowRequest(
                calculator.getId(), NOW.plusDays(1), NOW.plusDays(8));
        store.transaction(() -> first.counter(NOW.plusDays(2), NOW.plusDays(5)));

        OfferForm form = offers.startFromCounter(first.getId());

        assertEquals(NOW.plusDays(2), form.start());
        assertEquals(NOW.plusDays(5), form.end());
        assertEquals("", form.price());
    }

    @Test
    void onlyTheBuyersOwnCounteredOffer_canBeAgreedTo() {
        Offer pending = offers.submitPriceOffer(book.getId(), "150");
        PriceOffer bensOffer = (PriceOffer) store.addOffer(new PriceOffer(book.getId(), "ben", 100));
        bensOffer.counter(160);

        assertEquals("The seller has not countered this price offer.",
                assertThrows(ServiceException.class, () -> offers.startFromCounter(pending.getId())).getMessage());
        assertEquals("This is not one of your offers.",
                assertThrows(ServiceException.class, () -> offers.startFromCounter(bensOffer.getId())).getMessage());
        assertThrows(ServiceException.class, () -> offers.startFromCounter("O999"));
        assertThrows(ServiceException.class, () -> offers.startFromCounter(null));
    }

    @Test
    void counterOnAListingThatIsNoLongerAvailable_cannotBeAgreedTo() {
        PriceOffer first = (PriceOffer) offers.submitPriceOffer(book.getId(), "150");
        store.transaction(() -> {
            first.counter(170);
            book.remove(); // the admin removed the listing
        });

        assertThrows(ListingUnavailableException.class, () -> offers.startFromCounter(first.getId()));
    }

    @Test
    void newOffer_leavesTheEarlierOffersAsTheyWere() {
        Offer first = offers.submitPriceOffer(book.getId(), "100");
        Offer second = offers.submitPriceOffer(book.getId(), "120");
        store.transaction(first::decline);

        Offer third = offers.submitPriceOffer(book.getId(), "140");

        assertEquals(OfferStatus.DECLINED, first.getStatus());
        assertEquals(OfferStatus.PENDING, second.getStatus()); // not withdrawn by the third
        assertEquals(OfferStatus.PENDING, third.getStatus());
        assertEquals(3, store.getOffersByBuyer("bea").size());
        assertEquals(3, store.getNotificationsFor("sam").size());
    }

    // ================= Precondition: the Buyer is logged in =================

    @Test
    void onlyABuyerCanMakeAnOffer() {
        Offer beasOffer = offers.submitPriceOffer(book.getId(), "150");
        for (User other : List.of(sam, Fixtures.admin(store, "ada"))) {
            SessionManager.login(other);
            assertThrows(AccessDeniedException.class, () -> offers.start(book.getId()), other.getUsername());
            assertThrows(AccessDeniedException.class, () -> offers.startFromCounter(beasOffer.getId()));
            assertThrows(AccessDeniedException.class, () -> offers.submitPriceOffer(book.getId(), "150"));
            assertThrows(AccessDeniedException.class,
                    () -> offers.submitBorrowRequest(calculator.getId(), NOW.plusHours(1), NOW.plusDays(1)));
            assertThrows(AccessDeniedException.class, () -> offers.submitClaim(lamp.getId()));
        }
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> offers.submitClaim(lamp.getId()));
        assertEquals(List.of(beasOffer), store.getAllOffers());
    }

    @Test
    void deactivatedBuyer_cannotMakeAnOffer() {
        store.transaction(() -> bea.setActive(false));

        assertThrows(AccessDeniedException.class, () -> offers.submitClaim(lamp.getId()));
        assertTrue(store.getAllOffers().isEmpty());
    }
}
