package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.SameMoment;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UC11 View Deal History. The class is a test driver for {@link DealHistoryService},
 * with a {@link TestClock} as the stub for time (Lecture 14). How the tests were designed
 * (Lectures 12-14):
 *
 * <p><b>Thread tests</b> follow a deal from the event that completes it into the history: a
 * purchase from the buyer's offer through the seller's acceptance to Mark Complete (UC5, UC7),
 * an item the buyer returned (UC7 A2) and an item the timer returned late (UC12).
 *
 * <p><b>State-based tests</b> put each kind of deal into each of its states through the Offer
 * state machine, and complete deals through each of its completing events: only a Completed
 * deal is in the history (Special Requirement 1), whatever becomes of its listing afterwards.
 *
 * <p><b>Equivalence partitions and boundary values</b> of the query (step 4):
 * <pre>
 *   user:     buyer | seller | admin | nobody logged in | deactivated buyer      only an active buyer
 *   from, to: none | the deal's day | the day before | the day after             both ends included
 *   order:    from before to | from = to (one day) | from one day after to      A2 only for the last
 *   time:     12 Nov 23:59:59 and 13 Nov 00:00:00 against "to 12 Nov"           the day counts, not the time
 *   category: none | the deal's | another
 *   seller:   none | blank | the whole name | part of it in another case | spaces around | no match
 *   account:  the seller active | deactivated | deleted (its username is shown and searched)
 * </pre>
 *
 * <p><b>Basis paths</b> (Lecture 12) of {@link DealHistoryService#inRange}: V(G) = 5, one test
 * per path.
 *
 * <p><b>The critical case</b> (Lecture 14): the history read while the timer returns many items
 * at once sees all of them returned or none, never half of them (active class, Lecture 15).
 */
class DealHistoryServiceTest {

    private static final LocalDateTime NOW = Fixtures.START; // Mon 2 Nov 2026 12:00
    private static final LocalDateTime DEAL_DAY = LocalDateTime.of(2026, 11, 12, 10, 0);
    private static final LocalDate NOV_11 = LocalDate.of(2026, 11, 11);
    private static final LocalDate NOV_12 = LocalDate.of(2026, 11, 12);
    private static final LocalDate NOV_13 = LocalDate.of(2026, 11, 13);

    private TestClock clock;
    private DataStore store;
    private DealHistoryService history;
    private DealRules rules;
    private Seller sam;
    private Buyer bea;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        history = new DealHistoryService(store);
        rules = new DealRules(store);
        sam = Fixtures.seller(store, "sam");
        Fixtures.seller(store, "sue");
        bea = Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        SessionManager.login(bea);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    // ---------- Deals, made and completed through the same events as in the app ----------

    private Offer purchase(String buyer, String seller, Category category) {
        SaleListing listing = Fixtures.live(Fixtures.sale(store, seller, 100));
        listing.setCategory(category);
        return store.addOffer(new PriceOffer(listing.getId(), buyer, 90));
    }

    /** Bea's claim on a new giveaway of the seller's. */
    private Offer beasClaim(String seller) {
        return store.addOffer(new Claim(Fixtures.live(Fixtures.giveaway(store, seller)).getId(), "bea"));
    }

    /** Bea's borrow request on a new lend listing of Sam's, for the week that starts an hour before the moment. */
    private BorrowRequest beasLoanAround(LocalDateTime moment) {
        LendListing listing = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        return beaBorrows(listing, moment.minusHours(1), moment.plusDays(6));
    }

    /** Bea's borrow request on the listing, for the period. */
    private BorrowRequest beaBorrows(LendListing listing, LocalDateTime start, LocalDateTime end) {
        return (BorrowRequest) store.addOffer(new BorrowRequest(listing.getId(), "bea", start, end));
    }

    /** The seller accepts the deal an hour before the moment, and it is marked complete at the moment (UC7). */
    private <O extends Offer> O completedAt(O deal, LocalDateTime moment) {
        clock.set(moment.minusHours(1));
        rules.accept(deal);
        clock.set(moment);
        rules.complete(deal);
        return deal;
    }

    /** The seller accepts the loan an hour before the moment, and the buyer returns it at the moment (UC7 A2). */
    private BorrowRequest returnedAt(BorrowRequest loan, LocalDateTime moment) {
        clock.set(moment.minusHours(1));
        rules.accept(loan);
        clock.set(moment);
        rules.returnItem(loan, false);
        return loan;
    }

    private List<HistoryEntry> all() {
        return history.find(HistoryQuery.ALL);
    }

    private List<HistoryEntry> find(LocalDate from, LocalDate to, Category category, String seller) {
        return history.find(new HistoryQuery(from, to, category, seller));
    }

    /** The deals of the rows, in the order shown. */
    private static List<Offer> deals(List<HistoryEntry> rows) {
        return rows.stream().map(HistoryEntry::deal).toList();
    }

    // ================= Thread tests: from the completing event into the history =================

    @Test
    void aPurchase_fromTheBuyersOfferToTheHistory_acrossThreeUseCases() {
        SaleListing desk = Fixtures.live(Fixtures.sale(store, "sam", 300));
        Offer offer = new OfferService(store).submitPriceOffer(desk.getId(), "280"); // UC5, as Bea
        clock.advance(Duration.ofHours(1));
        rules.accept(offer);                                                          // Sam accepts it
        clock.advance(Duration.ofDays(1));
        new MyDealsService(store).markComplete(offer.getId());                        // UC7, as Bea

        List<HistoryEntry> rows = all();                                              // UC11 step 2

        assertEquals(1, rows.size());
        HistoryEntry row = rows.get(0);
        assertSame(offer, row.deal());
        assertEquals("Book", row.listing().getTitle());                   // Item Title
        assertEquals("Seller sam", row.sellerName());                    // Seller
        assertEquals(NOW.plusHours(1).plusDays(1), row.dealDate());     // Deal Date: the day it was marked complete (SR2)
        assertInstanceOf(PriceOffer.class, row.deal());                   // Deal Type: Bought (SR3; wording in HistoryTextTest)
        assertTrue(row.deal().isCompleted());                             // Outcome: Completed (SR4; wording in OfferTextTest)
    }

    @Test
    void anItemTheBuyerReturned_isInTheHistory_datedOnTheDayItWasReturned() { // UC7 A2, then UC11 SR2
        BorrowRequest loan = beasLoanAround(DEAL_DAY);
        clock.set(DEAL_DAY.minusHours(1));
        rules.accept(loan);
        clock.set(DEAL_DAY);
        new MyDealsService(store).returnItem(loan.getId()); // as Bea, before the due date

        HistoryEntry row = all().get(0);

        assertSame(loan, row.deal());
        assertEquals(DEAL_DAY, row.dealDate());
        assertFalse(loan.isAutoReturned()); // Outcome: Returned
    }

    @Test
    void anItemTheTimerReturnedLate_isDatedOnItsDueDate_notWhenTheTimerRan() { // UC12 step 3, then UC11 SR2 and SR4
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        LocalDateTime due = NOW.plusMinutes(10);
        BorrowRequest loan = beaBorrows(calculator, NOW.plusMinutes(5), due);
        rules.accept(loan);
        clock.set(due.plusHours(1)); // the app was closed when the item fell due

        new BorrowingDeadlineService(store).process();

        HistoryEntry row = all().get(0);
        assertSame(loan, row.deal());
        assertEquals(due, row.dealDate());
        assertTrue(loan.isAutoReturned()); // Outcome: Auto-returned
    }

    @Test
    void aDealCompletedLater_isInTheNextResult() { // SR1: worked out each time, never stored
        Offer first = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY);
        assertEquals(List.of(first), deals(all()));

        Offer second = completedAt(beasClaim("sue"), DEAL_DAY.plusDays(1));

        assertEquals(List.of(second, first), deals(all()));
    }

    @Test
    void borrowingTheSameItemTwice_givesTwoRows() { // two deals, as two reviews in client answer #8
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        BorrowRequest firstLoan = returnedAt(
                beaBorrows(calculator, DEAL_DAY.minusHours(1), DEAL_DAY.plusDays(6)), DEAL_DAY);
        BorrowRequest secondLoan = returnedAt(
                beaBorrows(calculator, DEAL_DAY.plusDays(3).minusHours(1), DEAL_DAY.plusDays(9)), DEAL_DAY.plusDays(3));

        assertEquals(List.of(secondLoan, firstLoan), deals(all()));
    }

    // ================= State-based tests (Offer and Listing state machines) =================

    @ParameterizedTest(name = "a {0} that is {1} is not in the history")
    @CsvSource({"PRICE, PENDING", "PRICE, COUNTERED", "PRICE, DECLINED", "PRICE, ACCEPTED",
            "LOAN, PENDING", "LOAN, COUNTERED", "LOAN, DECLINED", "LOAN, ACCEPTED",
            "CLAIM, PENDING", "CLAIM, DECLINED", "CLAIM, ACCEPTED"})
    void aDealThatIsNotCompleted_isNotInTheHistory(String kind, String state) {
        Offer deal = switch (kind) {
            case "PRICE" -> purchase("bea", "sam", Category.TEXTBOOKS);
            case "LOAN" -> beasLoanAround(NOW.plusHours(2));
            default -> beasClaim("sam");
        };

        Fixtures.moveDealTo(rules, deal, state);

        assertTrue(all().isEmpty()); // A1
    }

    @Test
    void eachEventThatCompletesADeal_putsItInTheHistory() {
        Offer bought = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY);
        Offer received = completedAt(beasClaim("sam"), DEAL_DAY.plusHours(1));
        BorrowRequest returned = returnedAt(beasLoanAround(DEAL_DAY.plusHours(2)), DEAL_DAY.plusHours(2));
        BorrowRequest autoReturned = beasLoanAround(DEAL_DAY.plusHours(3));
        clock.set(DEAL_DAY.plusHours(3));
        rules.accept(autoReturned);
        rules.returnItem(autoReturned, true); // the timer's event: completed on its due date

        List<HistoryEntry> rows = all();

        assertEquals(List.of(autoReturned, returned, received, bought), deals(rows));
        assertEquals(autoReturned.getDueDate(), rows.get(0).dealDate());
        assertTrue(autoReturned.isAutoReturned());
        assertFalse(returned.isAutoReturned());
        assertInstanceOf(Claim.class, rows.get(2).deal());
        assertInstanceOf(PriceOffer.class, rows.get(3).deal());
    }

    @Test
    void aCompletedDeal_staysInTheHistory_whateverHappensToItsListingLater() {
        BorrowRequest loan = returnedAt(beasLoanAround(DEAL_DAY), DEAL_DAY);
        Listing calculator = store.getListingOf(loan);
        assertEquals(ListingStatus.LIVE, calculator.getStatus()); // lent out, returned, Live again
        calculator.remove();                                       // then the admin takes it down (Task 3.7)
        Offer bought = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY.plusDays(1));

        List<HistoryEntry> rows = all();

        assertEquals(ListingStatus.REMOVED, calculator.getStatus());
        assertEquals(ListingStatus.SOLD, store.getListingOf(bought).getStatus());
        assertEquals(List.of(bought, loan), deals(rows));
    }

    // ================= Who can see a history =================

    @Test
    void onlyAnActiveBuyer_canSeeADealHistory() { // the precondition
        SessionManager.login(sam);
        assertThrows(AccessDeniedException.class, this::all);

        SessionManager.login(Fixtures.admin(store, "ada"));
        assertThrows(AccessDeniedException.class, this::all);

        SessionManager.logout();
        assertThrows(AccessDeniedException.class, this::all);

        SessionManager.login(bea);
        bea.setActive(false);
        assertThrows(AccessDeniedException.class, this::all);
    }

    @Test
    void anotherBuyersCompletedDeal_isNotInTheHistory() {
        completedAt(purchase("ben", "sam", Category.TEXTBOOKS), DEAL_DAY);

        assertTrue(all().isEmpty());

        SessionManager.login(store.findUser("ben"));
        assertEquals(1, all().size()); // it is in Ben's
    }

    // ================= The date range (step 4, A2) =================

    @ParameterizedTest(name = "from {0} to {1}: listed = {2}")
    @CsvSource(nullValues = "-", value = {
            "-,          -,          true",  // no range
            "2026-11-12, -,          true",  // from the deal's day
            "2026-11-11, -,          true",  // from the day before
            "2026-11-13, -,          false", // from the day after
            "-,          2026-11-12, true",  // to the deal's day
            "-,          2026-11-13, true",  // to the day after
            "-,          2026-11-11, false", // to the day before
            "2026-11-12, 2026-11-12, true",  // a one-day range
            "2026-11-11, 2026-11-13, true"})
    void theDateRange_includesBothOfItsEnds(LocalDate from, LocalDate to, boolean listed) {
        Offer deal = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY); // 12 Nov 10:00

        List<HistoryEntry> rows = find(from, to, null, "");

        assertEquals(listed ? List.of(deal) : List.of(), deals(rows));
    }

    @Test
    void theRange_comparesTheDayOfTheDealDate_notItsTime() {
        Offer lastSecond = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), LocalDateTime.of(2026, 11, 12, 23, 59, 59));
        Offer nextDay = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), LocalDateTime.of(2026, 11, 13, 0, 0, 0));

        assertEquals(List.of(lastSecond), deals(find(null, NOV_12, null, "")));
        assertEquals(List.of(nextDay), deals(find(NOV_13, null, null, "")));
    }

    @Test
    void aStartAfterTheEnd_isRefused_beforeAnyDealIsRead() { // A2, one day over; an empty history would otherwise be A1
        // Its own type, so the screen can show it under the dates instead of in a dialog
        InvalidDateRangeException refused = assertThrows(InvalidDateRangeException.class, () -> find(NOV_13, NOV_12, null, ""));

        assertEquals("The date range is invalid. Choose a start date on or before the end date.", refused.getMessage());
    }

    // ================= Category and seller (step 4) =================

    @Test
    void theCategory_keepsOnlyTheDealsWhoseListingIsInIt() {
        Offer charger = completedAt(purchase("bea", "sam", Category.ELECTRONICS), DEAL_DAY);
        Offer book = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY.plusHours(1));

        assertEquals(List.of(book, charger), deals(find(null, null, null, "")));
        assertEquals(List.of(charger), deals(find(null, null, Category.ELECTRONICS, "")));
        assertTrue(find(null, null, Category.FURNITURE, "").isEmpty()); // A1
    }

    @ParameterizedTest(name = "seller [{0}] finds Sam's deal only")
    @ValueSource(strings = {"Seller sam", "SAM", "  sam ", "r sa"})
    void partOfTheSellersName_inAnyCase_findsTheirDeals(String typed) {
        Offer samsDeal = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY);
        completedAt(purchase("bea", "sue", Category.TEXTBOOKS), DEAL_DAY.plusHours(1));

        assertEquals(List.of(samsDeal), deals(find(null, null, null, typed)));
    }

    @ParameterizedTest(name = "seller [{0}] is no search")
    @NullSource
    @ValueSource(strings = {"", "   "})
    void noSellerText_findsTheDealsOfEverySeller(String typed) {
        completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY);
        completedAt(purchase("bea", "sue", Category.TEXTBOOKS), DEAL_DAY.plusHours(1));

        assertEquals(2, find(null, null, null, typed).size());
    }

    @Test
    void aSellerNameThatMatchesNoDeal_givesNoDeals() { // A1
        completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY);

        assertTrue(find(null, null, null, "xyz").isEmpty());
    }

    @Test
    void aDeactivatedSeller_keepsTheirName_andADeletedOne_isShownAndFoundByUsername() {
        Offer samsDeal = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY);
        Offer suesDeal = completedAt(purchase("bea", "sue", Category.TEXTBOOKS), DEAL_DAY.plusHours(1));
        sam.setActive(false);
        store.deleteAccount("sue");

        List<HistoryEntry> rows = all();

        assertEquals(List.of(suesDeal, samsDeal), deals(rows)); // both deals stay
        assertEquals("sue", rows.get(0).sellerName());          // the account is gone, so its username is shown
        assertEquals("Seller sam", rows.get(1).sellerName());   // a deactivated account still has its name
        assertEquals(List.of(suesDeal), deals(find(null, null, null, "sue")));
    }

    @Test
    void theFilters_combine_soADealMustMatchEveryOneThatIsSet() { // step 4
        Offer match = completedAt(purchase("bea", "sam", Category.ELECTRONICS), DEAL_DAY);
        completedAt(purchase("bea", "sam", Category.ELECTRONICS), DEAL_DAY.plusDays(2));  // after the range
        completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY.plusHours(1));   // another category
        completedAt(purchase("bea", "sue", Category.ELECTRONICS), DEAL_DAY.plusHours(2)); // another seller

        assertEquals(List.of(match), deals(find(NOV_11, NOV_13, Category.ELECTRONICS, "sam")));
    }

    // ================= The order (step 2) and the postcondition =================

    @Test
    void theHistory_isNewestDealDateFirst() {
        Offer tenth = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY.minusDays(2));
        Offer fourteenth = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY.plusDays(2));
        Offer twelfth = completedAt(purchase("bea", "sam", Category.TEXTBOOKS), DEAL_DAY);

        assertEquals(List.of(fourteenth, twelfth, tenth), deals(all()));
    }

    @Test
    void dealsCompletedAtTheSameMoment_showTheNewestOfferFirst() {
        clock.set(DEAL_DAY.minusDays(3));
        Offer older = purchase("bea", "sam", Category.TEXTBOOKS);
        clock.set(DEAL_DAY.minusDays(2));
        Offer newer = purchase("bea", "sam", Category.TEXTBOOKS);
        completedAt(older, DEAL_DAY);
        completedAt(newer, DEAL_DAY);

        assertEquals(List.of(newer, older), deals(all()));
    }

    @Test
    void readingTheHistory_changesNothing(@TempDir Path folder) throws Exception { // the postcondition
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed(); // the demo data: Bob bought the chemistry textbook from Charlie
        SessionManager.login(saved.findUser("bob"));
        byte[] before = Files.readAllBytes(file);

        List<HistoryEntry> rows = new DealHistoryService(saved).find(HistoryQuery.ALL);

        assertEquals(1, rows.size());
        assertEquals("Organic Chemistry Textbook", rows.get(0).listing().getTitle());
        assertEquals("Charlie Lee", rows.get(0).sellerName());
        assertArrayEquals(before, Files.readAllBytes(file)); // nothing changed, so nothing was saved
    }

    // ================= Basis paths of inRange (Lecture 12) =================

    @ParameterizedTest(name = "{0}: from {1} to {2} -> {3}")
    @CsvSource(nullValues = "-", value = {
            "P1 no range,                        -,          -,          true",
            "P2 before the start,                2026-11-13, -,          false",
            "P3 from the start and no end,       2026-11-12, -,          true",
            "P4 from the start but after the end, 2026-11-12, 2026-11-11, false",
            "P5 no start and up to the end,      -,          2026-11-12, true"})
    void inRange_eachBasisPath(String path, LocalDate from, LocalDate to, boolean inside) {
        assertEquals(inside, DealHistoryService.inRange(DEAL_DAY, from, to), path);
    }

    // ================= The critical case: the timer at work while the history is read =================

    @Test
    void theHistoryReadWhileTheTimerReturnsItems_seesAllOfThemReturnedOrNone() throws Exception {
        int loans = 20;
        LocalDateTime due = NOW.plusMinutes(10);
        for (int i = 0; i < loans; i++) {
            LendListing listing = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
            rules.accept(beaBorrows(listing, NOW.plusMinutes(5), due));
        }
        clock.set(due); // all of them fall due at the same moment

        try (SameMoment moment = new SameMoment(2)) {
            Future<DeadlineReport> timer = moment.submit(() -> new BorrowingDeadlineService(store).process()); // UC12, one transaction
            Future<List<Integer>> reader = moment.submit(() -> {
                List<Integer> sizes = new ArrayList<>();
                for (int read = 0; read < 200; read++) {
                    sizes.add(history.find(HistoryQuery.ALL).size());
                }
                return sizes;
            });
            moment.go();
            SameMoment.resultOf(timer);
            List<Integer> sizes = SameMoment.resultOf(reader);

            assertTrue(sizes.stream().allMatch(size -> size == 0 || size == loans), sizes.toString());
        }
        assertEquals(loans, all().size());
    }
}
