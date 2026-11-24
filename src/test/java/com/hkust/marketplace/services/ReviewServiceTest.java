package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.SameMoment;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UC10 Review Seller. The class is a test driver for {@link ReviewService}, with a
 * {@link TestClock} as the stub for time (Lecture 14). How the tests were designed
 * (Lectures 13-14):
 *
 * <p><b>Thread tests</b> follow a review through every object it touches (the deal, the review,
 * the seller's rating and notification board), starting from each way a deal is completed: a
 * purchase marked complete, an item the buyer returned and an item the timer returned (client
 * answer #3), and one deal from the buyer's offer to its review, across four use cases.
 *
 * <p><b>State-based tests</b> put a deal into each of its states (Pending, Countered, Declined,
 * Accepted, Completed, and Completed with a review) and try to review it: Special Requirement 1
 * allows it in exactly one.
 *
 * <p><b>Equivalence partitions and boundary values</b> of what the form sends:
 * <pre>
 *   rating:  none (null) | below 1 (-1, 0) | 1 to 5 (1, 3, 5) | above 5 (6)         A2 outside 1 to 5
 *   comment: none (null) | empty | only spaces and line breaks | text (1 character) required: client answer #8
 *   deal:    the buyer's own | another buyer's | none with that id                   A1 unless the buyer's own
 *   user:    buyer | seller | admin | nobody logged in | deactivated buyer           only an active buyer
 * </pre>
 * The checks follow the order of the flow of events: a deal that cannot be reviewed is
 * reported before a missing rating, and a missing rating before a missing comment.
 *
 * <p><b>The critical case</b> (Lecture 14): one deal submitted many times at the same moment
 * gets one review (Special Requirement 1).
 */
class ReviewServiceTest {

    private static final LocalDateTime NOW = Fixtures.START;

    private TestClock clock;
    private DataStore store;
    private ReviewService reviews;
    private MyDealsService deals;
    private DealRules rules;
    private Seller sam;
    private Buyer bea;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        reviews = new ReviewService(store);
        deals = new MyDealsService(store);
        rules = new DealRules(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        SessionManager.login(bea);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    // ---------- Deals ----------

    private Offer priceOffer(String buyer) {
        return store.addOffer(new PriceOffer(Fixtures.live(Fixtures.sale(store, "sam", 100)).getId(), buyer, 90));
    }

    /** A purchase that its buyer has marked complete, ready to be reviewed. */
    private Offer completedPurchase(String buyer) {
        return Fixtures.moveDealTo(rules, priceOffer(buyer), "COMPLETED");
    }

    /** Bea's borrow request on the calculator for the week that starts in an hour, accepted by the seller. */
    private BorrowRequest acceptedLoan(LendListing calculator) {
        BorrowRequest loan = (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), "bea", store.now().plusHours(1), store.now().plusHours(1).plusDays(7)));
        rules.accept(loan);
        return loan;
    }

    private LendListing calculator() {
        return Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
    }

    private List<Notification> boardOf(User user) {
        return store.getNotificationsFor(user.getUsername());
    }

    // ================= Thread tests: the basic flow, from each way a deal is completed =================

    @Test
    void reviewingACompletedPurchase_recordsTheReview_countsItInTheRating_andNotifiesTheSeller() {
        Offer purchase = completedPurchase("bea");
        int before = boardOf(sam).size();
        clock.advance(Duration.ofDays(1));

        ReviewForm form = reviews.start(purchase.getId());                        // steps 2-3
        Review review = reviews.submit(purchase.getId(), 4, "  Smooth deal.  ");  // steps 5-9

        assertEquals(new ReviewForm(purchase.getId(), "Book", "Seller sam"), form); // step 3
        assertEquals(4, review.getRating());
        assertEquals("Smooth deal.", review.getComment());                          // step 7, without the spaces
        assertEquals(NOW.plusDays(1), review.getReviewDate());                      // step 7: today
        assertSame(review, store.findReviewForOffer(purchase.getId()));
        assertEquals(OptionalDouble.of(4.0), store.getAverageRating("sam"));        // step 8
        assertEquals(List.of(review), store.getReviewsForSeller("sam"));            // step 8: profile and listings
        List<Notification> board = boardOf(sam);
        assertEquals(before + 1, board.size());                                     // step 9
        assertEquals(NotificationCategory.REVIEW_RECEIVED, board.get(0).getCategory());
        assertEquals("Buyer bea rated you 4 out of 5 stars for 'Book': \"Smooth deal.\"", board.get(0).getContent());
        assertFalse(deals.getMyDeals().get(0).canBeReviewed());                      // its Rate Seller button goes
    }

    @Test
    void anItemTheBuyerReturned_canBeReviewed() { // client answer #3: a loan is completed once it is returned
        BorrowRequest loan = acceptedLoan(calculator());
        deals.returnItem(loan.getId());

        Review review = reviews.submit(loan.getId(), 5, "Worked perfectly.");

        assertSame(review, store.findReviewForOffer(loan.getId()));
        assertEquals(1, store.getRatingCount("sam"));
    }

    @Test
    void anItemTheTimerReturned_isOfferedForReview_untilItIsReviewed() { // UC12 then UC10, client answer #3
        BorrowRequest loan = acceptedLoan(calculator());
        clock.set(loan.getDueDate());
        new BorrowingDeadlineService(store).process(); // UC12 step 3: returned automatically

        assertEquals(List.of(loan), deals.getAutoReturnedDealsToReview().stream().map(Deal::offer).toList());

        reviews.submit(loan.getId(), 3, "Fine, but the meet-up was late.");

        assertTrue(deals.getAutoReturnedDealsToReview().isEmpty());
        assertEquals(OptionalDouble.of(3.0), store.getAverageRating("sam"));
    }

    @Test
    void oneDeal_fromTheBuyersOfferToItsReview_acrossFourUseCases() {
        Listing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer offer = new OfferService(store).submitPriceOffer(book.getId(), "95"); // UC5 Make Offer
        rules.accept(offer);                                                        // the seller accepts (Manage Offers)
        deals.markComplete(offer.getId());                                          // UC7, step 9 prompts the review

        reviews.submit(offer.getId(), 5, "Exactly as described.");                  // UC10

        assertEquals(ListingStatus.SOLD, book.getStatus());
        assertEquals(OptionalDouble.of(5.0), store.getAverageRating("sam"));
    }

    // ================= State-based tests: which deals can be reviewed (Special Requirement 1) =================

    @ParameterizedTest(name = "a deal that is {0} cannot be reviewed")
    @ValueSource(strings = {"PENDING", "COUNTERED", "DECLINED", "ACCEPTED"})
    void aDealThatIsNotCompleted_cannotBeReviewed(String state) {
        Offer offer = Fixtures.moveDealTo(rules, priceOffer("bea"), state);

        DealNotReviewableException atStart = assertThrows(DealNotReviewableException.class,
                () -> reviews.start(offer.getId()));
        DealNotReviewableException atSubmit = assertThrows(DealNotReviewableException.class,
                () -> reviews.submit(offer.getId(), 5, "Good."));

        assertEquals(ReviewService.NOT_COMPLETED, atStart.getMessage()); // A1
        assertEquals(ReviewService.NOT_COMPLETED, atSubmit.getMessage());
        assertNull(store.findReviewForOffer(offer.getId()));
    }

    @Test
    void aCompletedDeal_isReviewedOnce_andTheReviewStaysAsSubmitted() {
        Offer purchase = completedPurchase("bea");
        reviews.submit(purchase.getId(), 5, "Great seller.");

        DealNotReviewableException again = assertThrows(DealNotReviewableException.class,
                () -> reviews.submit(purchase.getId(), 1, "Changed my mind."));
        DealNotReviewableException restart = assertThrows(DealNotReviewableException.class,
                () -> reviews.start(purchase.getId()));

        assertEquals(ReviewService.ALREADY_REVIEWED, again.getMessage());   // A1
        assertEquals(ReviewService.ALREADY_REVIEWED, restart.getMessage());
        Review kept = store.findReviewForOffer(purchase.getId());
        assertEquals(5, kept.getRating());                                  // Special Requirement 3
        assertEquals("Great seller.", kept.getComment());
        assertEquals(1, store.getRatingCount("sam"));
    }

    @Test
    void borrowingTheSameItemTwice_givesTwoDealsToReview() { // client answer #8: one review per completed deal
        LendListing calculator = calculator();
        BorrowRequest first = acceptedLoan(calculator);
        deals.returnItem(first.getId());                     // the calculator is Live again
        BorrowRequest second = acceptedLoan(calculator);
        deals.returnItem(second.getId());

        reviews.submit(first.getId(), 4, "Good the first time.");
        reviews.submit(second.getId(), 2, "Not charged the second time.");

        assertEquals(2, store.getRatingCount("sam"));
        assertEquals(OptionalDouble.of(3.0), store.getAverageRating("sam"));
    }

    // ================= Equivalence partitions and boundary values: the rating (A2) =================

    @ParameterizedTest(name = "rating {0} is refused")
    @NullSource
    @ValueSource(ints = {-1, 0, 6})
    void aMissingOrOutOfRangeRating_isRefused_andNothingIsRecorded(Integer rating) {
        Offer purchase = completedPurchase("bea");
        int before = boardOf(sam).size();

        ServiceException e = assertThrows(ServiceException.class, () -> reviews.submit(purchase.getId(), rating, "Good."));

        assertEquals(ReviewService.RATING_REQUIRED, e.getMessage());
        assertFalse(e instanceof DealNotReviewableException, "the buyer corrects the form ({Enter Review})");
        assertNull(store.findReviewForOffer(purchase.getId()));
        assertEquals(before, boardOf(sam).size());
    }

    @ParameterizedTest(name = "rating {0} is recorded")
    @ValueSource(ints = {1, 3, 5})
    void aRatingFromOneToFive_isRecorded(int rating) {
        Offer purchase = completedPurchase("bea");

        assertEquals(rating, reviews.submit(purchase.getId(), rating, "Good.").getRating());
    }

    // ================= Equivalence partitions: the written review (client answer #8) =================

    @ParameterizedTest(name = "comment [{0}] is refused")
    @NullSource
    @ValueSource(strings = {"", "   ", " \n\t "})
    void anEmptyWrittenReview_isRefused_andNothingIsRecorded(String comment) {
        Offer purchase = completedPurchase("bea");

        ServiceException e = assertThrows(ServiceException.class, () -> reviews.submit(purchase.getId(), 4, comment));

        assertEquals(ReviewService.COMMENT_REQUIRED, e.getMessage());
        assertFalse(e instanceof DealNotReviewableException, "the buyer corrects the form ({Enter Review})");
        assertNull(store.findReviewForOffer(purchase.getId()));
    }

    @Test
    void aWrittenReviewOfOneCharacter_orOfSeveralLines_isKeptAsWritten() {
        Offer first = completedPurchase("bea");
        Offer second = completedPurchase("bea");

        assertEquals("k", reviews.submit(first.getId(), 4, "k").getComment()); // the shortest that counts
        assertEquals("Good item.\nWould buy again.",
                reviews.submit(second.getId(), 5, " Good item.\nWould buy again. ").getComment());
    }

    // ================= The order of the checks follows the flow of events =================

    @Test
    void aDealThatCannotBeReviewed_isReportedBeforeAMissingRating() { // A1 ends the use case whatever was typed
        Offer pending = priceOffer("bea");

        DealNotReviewableException e = assertThrows(DealNotReviewableException.class,
                () -> reviews.submit(pending.getId(), null, ""));

        assertEquals(ReviewService.NOT_COMPLETED, e.getMessage());
    }

    @Test
    void aMissingRating_isReportedBeforeAMissingComment() { // step 6 checks the rating
        Offer purchase = completedPurchase("bea");

        ServiceException e = assertThrows(ServiceException.class, () -> reviews.submit(purchase.getId(), null, ""));

        assertEquals(ReviewService.RATING_REQUIRED, e.getMessage());
    }

    // ================= Equivalence partitions: whose deal, and who is logged in =================

    @ParameterizedTest(name = "deal [{0}]")
    @NullSource
    @ValueSource(strings = {"BEN", "O999"})
    void anotherBuyersDeal_orNoSuchDeal_cannotBeReviewed(String which) {
        Offer bensPurchase = completedPurchase("ben");
        String offerId = "BEN".equals(which) ? bensPurchase.getId() : which;

        DealNotReviewableException atStart = assertThrows(DealNotReviewableException.class, () -> reviews.start(offerId));
        DealNotReviewableException atSubmit = assertThrows(DealNotReviewableException.class,
                () -> reviews.submit(offerId, 5, "Good."));

        assertEquals(ReviewService.NOT_YOURS, atStart.getMessage());
        assertEquals(ReviewService.NOT_YOURS, atSubmit.getMessage());
        assertNull(store.findReviewForOffer(bensPurchase.getId()));
    }

    @Test
    void onlyABuyer_canReviewASeller() {
        Offer purchase = completedPurchase("bea");
        for (User other : List.of(sam, Fixtures.admin(store, "ada"))) {
            SessionManager.login(other);
            assertThrows(AccessDeniedException.class, () -> reviews.start(purchase.getId()), other.getUsername());
            assertThrows(AccessDeniedException.class, () -> reviews.submit(purchase.getId(), 5, "Good."));
        }
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> reviews.submit(purchase.getId(), 5, "Good."));
        assertNull(store.findReviewForOffer(purchase.getId()));
    }

    @Test
    void aDeactivatedBuyer_cannotReview() {
        Offer purchase = completedPurchase("bea");
        store.transaction(() -> bea.setActive(false));

        assertThrows(AccessDeniedException.class, () -> reviews.submit(purchase.getId(), 5, "Good."));
        assertNull(store.findReviewForOffer(purchase.getId()));
    }

    // ================= The critical case: the same deal submitted at the same moment =================

    @Test
    void reviewsOfOneDealSubmittedAtTheSameMoment_onlyTheFirstIsRecorded() throws Exception {
        Offer purchase = completedPurchase("bea");
        int attempts = 8;
        List<Callable<Boolean>> submissions = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            int stars = 1 + i % Review.MAX_RATING;
            submissions.add(() -> {
                try {
                    reviews.submit(purchase.getId(), stars, "Attempt " + stars);
                    return true;
                } catch (DealNotReviewableException e) {
                    return false; // already reviewed by a faster attempt
                }
            });
        }
        List<Boolean> submitted;
        try (SameMoment moment = new SameMoment(attempts)) {
            submitted = moment.run(submissions);
        }
        int recorded = Collections.frequency(submitted, true);

        assertEquals(1, recorded);
        assertEquals(1, store.getRatingCount("sam"));
    }
}
