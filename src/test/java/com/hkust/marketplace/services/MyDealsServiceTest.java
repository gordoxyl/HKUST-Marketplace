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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UC7 Manage My Deals. The class is a test driver for {@link MyDealsService}, with a
 * {@link TestClock} as the stub for time (Lecture 14). How the tests were designed
 * (Lectures 12-14):
 *
 * <p><b>Thread tests</b> follow a flow through every object it touches: marking a purchase and
 * a claim complete (basic flow), returning a borrowed item (A2), and one deal from the
 * buyer's offer through the seller's acceptance to completion, across three use cases.
 *
 * <p><b>State-based tests</b> put a deal into each of its states (Pending, Countered, Declined,
 * Accepted, Completed) and apply both events, "mark complete" and "return": Special
 * Requirement 1 allows them in exactly one state.
 *
 * <p><b>Equivalence partitions</b> of the deal type: a price offer or claim is marked complete,
 * a borrow request is returned; each action is tried on the types it is not for. And of the
 * owner: the buyer's own deal, another buyer's, none at all.
 *
 * <p><b>Boundary values</b> of the moment of a self-return (Special Requirement 3):
 * one second before the due date (allowed), the due date itself and one second after (refused).
 */
class MyDealsServiceTest {

    private static final LocalDateTime NOW = Fixtures.START;

    private TestClock clock;
    private DataStore store;
    private MyDealsService deals;
    private DealRules rules;
    private Seller sam;
    private Buyer bea;
    private Buyer ben;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        deals = new MyDealsService(store);
        rules = new DealRules(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        ben = Fixtures.buyer(store, "ben");
        SessionManager.login(bea);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    // ---------- Deals in a chosen state, reached through the state machines ----------

    private Offer priceOffer(String buyer) {
        return store.addOffer(new PriceOffer(Fixtures.live(Fixtures.sale(store, "sam", 100)).getId(), buyer, 90));
    }

    private Offer claim(String buyer) {
        return store.addOffer(new Claim(Fixtures.live(Fixtures.giveaway(store, "sam")).getId(), buyer));
    }

    /** A borrow request for the week that starts in an hour. */
    private BorrowRequest borrowRequest(String buyer) {
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        return (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), buyer, NOW.plusHours(1), NOW.plusHours(1).plusDays(7)));
    }

    /** The seller accepts the deal (Manage Offers and Requests). */
    private <O extends Offer> O accepted(O offer) {
        rules.accept(offer);
        return offer;
    }

    /** Moves a pending deal into the state, through the same events the app uses. */
    private Offer moveTo(Offer offer, String state) {
        return Fixtures.moveDealTo(rules, offer, state);
    }

    private List<Notification> boardOf(User user) {
        return store.getNotificationsFor(user.getUsername());
    }

    // ================= Thread tests: the basic flow and A2 =================

    @Test
    void markingAPurchaseComplete_datesTheDeal_sellsTheListing_andNotifiesTheSeller() {
        Offer purchase = accepted(priceOffer("bea"));
        Listing book = store.getListingOf(purchase);
        int before = boardOf(sam).size();
        clock.advance(Duration.ofDays(2));

        Deal done = deals.markComplete(purchase.getId()); // steps 5-8, after the buyer confirmed

        assertEquals(NOW.plusDays(2), purchase.getCompletedDate());   // step 6: today
        assertEquals(ListingStatus.SOLD, book.getStatus());           // step 6
        assertEquals(ListingStatus.SOLD, store.getListingsBySeller("sam").get(0).getStatus()); // step 7: My Listings
        assertEquals(before + 1, boardOf(sam).size());                // step 8
        assertEquals("Buyer bea marked the deal for 'Book' complete.", boardOf(sam).get(0).getContent());
        assertEquals(NotificationCategory.DEAL_COMPLETED, boardOf(sam).get(0).getCategory());

        assertFalse(done.isCurrent());                                // step 7: shown as completed on My Deals
        assertFalse(done.canBeMarkedComplete());
        assertTrue(done.canBeReviewed());                             // step 9: the buyer can now rate the seller
        assertEquals(List.of(purchase), deals.getMyDeals().stream().map(Deal::offer).toList());
    }

    @Test
    void markingAClaimComplete_makesTheListingGiven() {
        Offer claim = accepted(claim("bea"));

        deals.markComplete(claim.getId());

        assertTrue(claim.isCompleted());
        assertEquals(ListingStatus.GIVEN, store.getListingOf(claim).getStatus());
    }

    @Test
    void returningABorrowedItem_datesTheDeal_makesTheListingLiveAgain_andNotifiesTheSeller() {
        BorrowRequest request = accepted(borrowRequest("bea"));
        Listing calculator = store.getListingOf(request);
        assertEquals(ListingStatus.LENT, calculator.getStatus());
        int before = boardOf(sam).size();
        clock.advance(Duration.ofDays(3));

        Deal done = deals.returnItem(request.getId()); // A2 steps 2-4

        assertEquals(NOW.plusDays(3), request.getCompletedDate());    // today, not the due date
        assertFalse(request.isAutoReturned());
        assertEquals(ListingStatus.LIVE, calculator.getStatus());
        assertTrue(store.isAvailable(calculator));                    // it can be borrowed again
        assertEquals(before + 1, boardOf(sam).size());
        assertEquals("Buyer bea returned 'Calculator'.", boardOf(sam).get(0).getContent());
        assertFalse(done.canBeReturned());
        assertTrue(done.canBeReviewed());
    }

    @Test
    void returningAnItemWhoseListingTheAdminRemoved_leavesTheListingRemoved() {
        // Postcondition: "the listing is Live again unless the admin removed it"
        BorrowRequest request = accepted(borrowRequest("bea"));
        Listing calculator = store.getListingOf(request);
        store.transaction(calculator::remove);

        deals.returnItem(request.getId());

        assertTrue(request.isCompleted());
        assertEquals(ListingStatus.REMOVED, calculator.getStatus());
    }

    @Test
    void oneDeal_fromOfferThroughAcceptanceToCompletion_acrossThreeUseCases() {
        // UC5 (buyer) -> Manage Offers and Requests (seller) -> UC7 (buyer)
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer offer = new OfferService(store).submitPriceOffer(book.getId(), "90");
        assertEquals("Pending", stateOnMyDeals(offer));
        assertFalse(deals.getMyDeals().get(0).canBeMarkedComplete()); // not accepted yet

        rules.accept(offer);
        assertEquals("Accepted", stateOnMyDeals(offer));
        assertTrue(deals.getMyDeals().get(0).canBeMarkedComplete());

        deals.markComplete(offer.getId());
        assertEquals("Completed", stateOnMyDeals(offer));
        assertEquals(ListingStatus.SOLD, book.getStatus());
    }

    private String stateOnMyDeals(Offer offer) {
        Deal deal = deals.getMyDeals().stream().filter(d -> d.offer() == offer).findFirst().orElseThrow();
        return deal.offer().isCompleted() ? "Completed" : deal.offer().getStatus().getDisplayName();
    }

    @Test
    void completion_survivesARestart(@TempDir Path folder) {
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed(); // the demo data: Alice's offer on the calculus textbook is accepted
        SessionManager.login(saved.findUser("alice"));
        MyDealsService savedDeals = new MyDealsService(saved);
        Deal accepted = savedDeals.getMyDeals().get(0);
        assertTrue(accepted.canBeMarkedComplete());

        savedDeals.markComplete(accepted.offer().getId());

        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(file));
        restarted.loadOrSeed();
        assertTrue(restarted.findOffer(accepted.offer().getId()).isCompleted());
        assertEquals(ListingStatus.SOLD, restarted.findListing(accepted.listing().getId()).getStatus());
    }

    // ================= Show Deals (step 2) and A1 =================

    @Test
    void myDeals_listsCurrentDealsFirst_thenCompletedOnes_eachNewestFirst_withoutDeclinedOnes() {
        Offer oldPending = priceOffer("bea");                       // 12:00
        clock.advance(Duration.ofMinutes(1));
        Offer firstDone = moveTo(priceOffer("bea"), "COMPLETED");   // completed 12:01
        clock.advance(Duration.ofMinutes(1));
        Offer declined = moveTo(priceOffer("bea"), "DECLINED");
        clock.advance(Duration.ofMinutes(1));
        Offer countered = moveTo(priceOffer("bea"), "COUNTERED");   // 12:03
        clock.advance(Duration.ofMinutes(1));
        Offer secondDone = moveTo(claim("bea"), "COMPLETED");       // completed 12:04
        clock.advance(Duration.ofMinutes(1));
        Offer accepted = accepted(borrowRequest("bea"));            // 12:05
        priceOffer("ben");                                          // someone else's deal

        List<Deal> shown = deals.getMyDeals();

        assertEquals(List.of(accepted, countered, oldPending, secondDone, firstDone),
                shown.stream().map(Deal::offer).toList());
        assertEquals(List.of(true, true, true, false, false), shown.stream().map(Deal::isCurrent).toList());
        assertFalse(shown.stream().anyMatch(deal -> deal.offer() == declined));
    }

    @Test
    void eachDeal_showsItsListingSellerTermsAndWhatCanBeDoneWithIt() {
        PriceOffer countered = (PriceOffer) moveTo(priceOffer("bea"), "COUNTERED");
        clock.advance(Duration.ofMinutes(1));
        BorrowRequest borrowed = accepted(borrowRequest("bea")); // the newer deal, so it is listed first

        Deal borrowDeal = deals.getMyDeals().get(0);
        Deal counterDeal = deals.getMyDeals().get(1);

        assertSame(countered, counterDeal.offer());
        assertEquals("Book", counterDeal.listing().getTitle());
        assertEquals("Seller sam", counterDeal.sellerName());
        assertTrue(counterDeal.hasCounter());                       // the buyer can agree through Make Offer (SR4)
        assertEquals(95, countered.getCounterPrice());
        assertTrue(counterDeal.dueDate().isEmpty());
        assertFalse(counterDeal.canBeMarkedComplete());
        assertFalse(counterDeal.canBeReviewed());

        assertSame(borrowed, borrowDeal.offer());
        assertEquals(borrowed.getProposedEndDate(), borrowDeal.dueDate().orElseThrow()); // Special Requirement 2
        assertTrue(borrowDeal.canBeReturned());
        assertFalse(borrowDeal.canBeMarkedComplete());              // a borrowed item is returned, not marked complete
        assertFalse(borrowDeal.hasCounter());
    }

    @Test
    void borrowRequestThatIsNotAcceptedYet_showsNoDueDate() {
        borrowRequest("bea");

        Deal pending = deals.getMyDeals().get(0);

        assertTrue(pending.dueDate().isEmpty());
        assertFalse(pending.canBeReturned());
    }

    @Test
    void reviewedDeal_cannotBeReviewedAgain() {
        Offer purchase = moveTo(priceOffer("bea"), "COMPLETED");
        assertTrue(deals.getMyDeals().get(0).canBeReviewed());

        store.addReview(new Review(purchase.getId(), 5, "Great"));

        assertFalse(deals.getMyDeals().get(0).canBeReviewed());
    }

    @Test
    void buyerWithNoDeals_seesAnEmptyList() {
        priceOffer("ben");

        assertTrue(deals.getMyDeals().isEmpty()); // A1: the screen then says there are no deals yet
    }

    @Test
    void buyerWithOnlyDeclinedDeals_seesAnEmptyList() {
        moveTo(priceOffer("bea"), "DECLINED");

        assertTrue(deals.getMyDeals().isEmpty());
    }

    // ================= Special Requirement 1: state-based =================

    @ParameterizedTest(name = "a {0} price offer")
    @ValueSource(strings = {"PENDING", "COUNTERED", "DECLINED", "COMPLETED"})
    void onlyAnAcceptedPurchaseThatIsNotCompleted_canBeMarkedComplete(String state) {
        Offer offer = moveTo(priceOffer("bea"), state);
        LocalDateTime completedBefore = offer.getCompletedDate();
        ListingStatus listingBefore = store.getListingOf(offer).getStatus();
        int notificationsBefore = boardOf(sam).size();

        ServiceException refused = assertThrows(ServiceException.class, () -> deals.markComplete(offer.getId()));

        assertEquals("Only an accepted deal that is not yet completed can be marked complete.", refused.getMessage());
        assertEquals(completedBefore, offer.getCompletedDate());   // unchanged, also for a deal completed before
        assertEquals(listingBefore, store.getListingOf(offer).getStatus());
        assertEquals(notificationsBefore, boardOf(sam).size());
    }

    @ParameterizedTest(name = "a {0} borrow request")
    @ValueSource(strings = {"PENDING", "COUNTERED", "DECLINED", "COMPLETED"})
    void onlyAnAcceptedBorrowRequestThatIsNotReturned_canBeReturned(String state) {
        BorrowRequest request = (BorrowRequest) moveTo(borrowRequest("bea"), state);
        LocalDateTime completedBefore = request.getCompletedDate();
        ListingStatus listingBefore = store.getListingOf(request).getStatus();

        assertThrows(ServiceException.class, () -> deals.returnItem(request.getId()));

        assertEquals(completedBefore, request.getCompletedDate());
        assertEquals(listingBefore, store.getListingOf(request).getStatus());
    }

    @Test
    void dealIsCompletedOnlyOnce() {
        Offer purchase = accepted(priceOffer("bea"));
        deals.markComplete(purchase.getId());
        LocalDateTime first = purchase.getCompletedDate();
        clock.advance(Duration.ofDays(1));

        assertThrows(ServiceException.class, () -> deals.markComplete(purchase.getId()));

        assertEquals(first, purchase.getCompletedDate());
    }

    // ================= Special Requirement 1: the action must fit the deal type =================

    @Test
    void borrowedItem_isReturned_notMarkedComplete() {
        BorrowRequest request = accepted(borrowRequest("bea"));

        ServiceException refused = assertThrows(ServiceException.class, () -> deals.markComplete(request.getId()));

        assertEquals("A borrowed item is completed by returning it, not by marking it complete.", refused.getMessage());
        assertFalse(request.isCompleted());
        assertEquals(ListingStatus.LENT, store.getListingOf(request).getStatus());
    }

    @Test
    void purchaseOrClaim_isMarkedComplete_notReturned() {
        for (Offer deal : List.of(accepted(priceOffer("bea")), accepted(claim("bea")))) {
            ServiceException refused = assertThrows(ServiceException.class, () -> deals.returnItem(deal.getId()));

            assertEquals("Only a borrowed item can be returned. Mark this deal complete instead.", refused.getMessage());
            assertFalse(deal.isCompleted());
            assertEquals(ListingStatus.RESERVED, store.getListingOf(deal).getStatus());
        }
    }

    // ================= Special Requirement 3: boundary at the due date =================

    @ParameterizedTest(name = "{0} s from the due date: self-return allowed = {1}")
    @CsvSource({"-86400, true", "-1, true", "0, false", "1, false", "86400, false"})
    void selfReturn_isOfferedOnlyBeforeTheDueDate(long secondsFromDueDate, boolean allowed) {
        BorrowRequest request = accepted(borrowRequest("bea"));
        clock.set(request.getDueDate().plusSeconds(secondsFromDueDate));

        assertEquals(allowed, deals.getMyDeals().get(0).canBeReturned()); // the button follows the same rule

        if (allowed) {
            deals.returnItem(request.getId());
            assertTrue(request.isCompleted());
        } else {
            ServiceException refused = assertThrows(ServiceException.class, () -> deals.returnItem(request.getId()));
            assertEquals(MyDealsService.PERIOD_ENDED, refused.getMessage());
            assertFalse(request.isCompleted()); // left for Process Borrowing Deadlines (UC12)
            assertEquals(ListingStatus.LENT, store.getListingOf(request).getStatus());
        }
    }

    @Test
    void itemCanBeReturned_beforeItsBorrowPeriodHasStarted() {
        BorrowRequest request = accepted(borrowRequest("bea")); // starts in an hour

        deals.returnItem(request.getId());

        assertEquals(NOW, request.getCompletedDate());
    }

    // ================= Whose deal it is =================

    @Test
    void anotherBuyersDeal_cannotBeCompletedOrReturned() {
        Offer bensPurchase = accepted(priceOffer("ben"));
        BorrowRequest bensLoan = accepted(borrowRequest("ben"));

        assertEquals("This is not one of your deals.",
                assertThrows(ServiceException.class, () -> deals.markComplete(bensPurchase.getId())).getMessage());
        assertThrows(ServiceException.class, () -> deals.returnItem(bensLoan.getId()));
        assertFalse(bensPurchase.isCompleted());
        assertFalse(bensLoan.isCompleted());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"O999", ""})
    void dealThatDoesNotExist_cannotBeCompletedOrReturned(String offerId) {
        assertThrows(ServiceException.class, () -> deals.markComplete(offerId));
        assertThrows(ServiceException.class, () -> deals.returnItem(offerId));
    }

    // ================= After an automatic return: the deals to prompt a review for (client answer #3) =================

    @Test
    void autoReturnedDealsToReview_areOnlyTheBuyersOwnAutomaticReturns_withoutAReview() {
        BorrowRequest waiting = accepted(borrowRequest("bea"));
        BorrowRequest reviewed = accepted(borrowRequest("bea"));
        BorrowRequest selfReturned = accepted(borrowRequest("bea"));
        accepted(borrowRequest("ben"));
        moveTo(priceOffer("bea"), "COMPLETED");          // completed, but not a loan
        deals.returnItem(selfReturned.getId());          // returned by the buyer: the prompt came then
        clock.set(waiting.getDueDate());
        new BorrowingDeadlineService(store).process();   // UC12: the three other loans are returned
        store.addReview(new Review(reviewed.getId(), 5, "Good."));

        assertEquals(List.of(waiting), deals.getAutoReturnedDealsToReview().stream().map(Deal::offer).toList());
    }

    @Test
    void autoReturnedDealsToReview_listTheMostRecentlyReturnedFirst() {
        BorrowRequest first = accepted(borrowRequest("bea"));
        LendListing projector = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        BorrowRequest second = accepted((BorrowRequest) store.addOffer(
                new BorrowRequest(projector.getId(), "bea", NOW.plusHours(2), NOW.plusDays(9))));
        BorrowingDeadlineService timer = new BorrowingDeadlineService(store);
        clock.set(first.getDueDate());
        timer.process();
        clock.set(second.getDueDate());
        timer.process();

        assertEquals(List.of(second, first), deals.getAutoReturnedDealsToReview().stream().map(Deal::offer).toList());
    }

    // ================= Precondition: the Buyer is logged in =================

    @Test
    void onlyABuyerHasMyDeals() {
        Offer purchase = accepted(priceOffer("bea"));
        BorrowRequest loan = accepted(borrowRequest("bea"));
        for (User other : List.of(sam, Fixtures.admin(store, "ada"))) {
            SessionManager.login(other);
            assertThrows(AccessDeniedException.class, deals::getMyDeals, other.getUsername());
            assertThrows(AccessDeniedException.class, () -> deals.markComplete(purchase.getId()));
            assertThrows(AccessDeniedException.class, () -> deals.returnItem(loan.getId()));
            assertThrows(AccessDeniedException.class, deals::getAutoReturnedDealsToReview);
        }
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, deals::getMyDeals);
        assertThrows(AccessDeniedException.class, () -> deals.markComplete(purchase.getId()));
        assertFalse(purchase.isCompleted());
        assertFalse(loan.isCompleted());
    }

    @Test
    void deactivatedBuyer_cannotCompleteADeal() {
        Offer purchase = accepted(priceOffer("bea"));
        store.transaction(() -> bea.setActive(false));

        assertThrows(AccessDeniedException.class, () -> deals.markComplete(purchase.getId()));
        assertFalse(purchase.isCompleted());
    }
}
