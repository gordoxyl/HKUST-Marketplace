package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.AdminTestSupport;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for Task 3.8 Seller Ratings Overview: view with filters (6.5) and hide review (6.6). */
class AdminReputationServiceTest {

    private static final LocalDateTime DAY0 = Fixtures.START;

    private AdminTestSupport support;
    private AdminReputationService service;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();               // admin ada, seller sam, buyer bea
        Fixtures.seller(support.store, "sue");
        Fixtures.buyer(support.store, "ben");
        service = new AdminReputationService(support.store);
        support.loginAsAdmin();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    // ---------- helpers ----------

    /**
     * Completes a deal and adds a review. The listing is created live each time so each
     * offer targets a different listing, satisfying the one-review-per-deal rule.
     */
    private Review review(String seller, int rating) {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, seller, 100));
        Offer offer = support.store.addOffer(new PriceOffer(listing.getId(), "bea", 100));
        offer.accept(support.store.now());
        offer.complete(support.store.now());
        return support.store.addReview(new Review(offer.getId(), rating, "comment"));
    }

    private List<Notification> notificationsOf(String username) {
        return support.store.getNotificationsFor(username).stream()
                .filter(n -> n.getCategory() == NotificationCategory.ANNOUNCEMENT).toList();
    }
    // ---------- Task 3.8: list and filters (6.5) ----------

    @Test
    void task3_8_noFilter_returnsEveryReview_includingHiddenOnes() {
        Review r1 = review("sam", 5);
        Review r2 = review("sam", 3);
        review("sue", 4);
        support.store.transaction(() -> r1.setHidden(true));            // hidden review must still appear

        List<Review> result = service.findReviews(ReviewFilter.none());

        assertEquals(3, result.size());
        assertTrue(result.contains(r1));                                // admin sees hidden
    }

    @Test
    void task3_8_nullFilterIsTheSameAsNoFilter() {
        Review r = review("sam", 5);

        assertSame(r, service.findReviews(null).get(0));
    }

    @Test
    void task3_8_sellerFilter_returnsOnlyThatSellersReviews() {
        Review sams = review("sam", 5);
        Review sues = review("sue", 3);

        assertSameReviews(service.findReviews(ReviewFilter.none().withSeller("sam")), sams);
        assertSameReviews(service.findReviews(ReviewFilter.none().withSeller("SUE")), sues); // case-insensitive
        assertTrue(service.findReviews(ReviewFilter.none().withSeller("nobody")).isEmpty());
    }

    @Test
    void task3_8_ratingFilter_returnsOnlyMatchingScores() {
        Review five = review("sam", 5);
        review("sam", 3);
        review("sue", 5);

        List<Review> result = service.findReviews(ReviewFilter.none().withRating(5));

        assertEquals(2, result.size());
        assertTrue(result.contains(five));
        result.forEach(r -> assertEquals(5, r.getRating()));
    }

    @Test
    void task3_8_ratingFilter_refusesOutOfRange() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.findReviews(ReviewFilter.none().withRating(0)));
        assertNotNull(e.getMessage());

        assertThrows(ServiceException.class,
                () -> service.findReviews(ReviewFilter.none().withRating(6)));
    }

    @Test
    void task3_8_dateRange_isInclusiveOnBothEnds() {
        Review r0 = review("sam", 5);                           // posted at DAY0
        support.clock.advance(Duration.ofDays(1));
        Review r1 = review("sam", 4);                           // posted at DAY0 + 1
        support.clock.advance(Duration.ofDays(1));
        Review r2 = review("sam", 3);                           // posted at DAY0 + 2
        LocalDateTime day1 = DAY0.plusDays(1);
        LocalDateTime day2 = DAY0.plusDays(2);

        assertSameReviews(service.findReviews(ReviewFilter.none().withDateRange(day1, day1)), r1);
        assertSameReviews(service.findReviews(ReviewFilter.none().withDateRange(day1, day2)), r1, r2);
        assertSameReviews(service.findReviews(ReviewFilter.none().withDateRange(null, DAY0)), r0);
        assertSameReviews(service.findReviews(ReviewFilter.none().withDateRange(day2, null)), r2);
        assertTrue(service.findReviews(ReviewFilter.none()
                .withDateRange(DAY0.plusDays(5), DAY0.plusDays(6))).isEmpty());
    }

    @Test
    void task3_8_allFiltersCombine_mustAllMatch() {
        Review match = review("sam", 5);                        // sam, 5 stars, DAY0
        review("sue", 5);                                        // wrong seller
        review("sam", 3);                                        // wrong rating
        support.clock.advance(Duration.ofDays(2));
        review("sam", 5);                                        // too late

        ReviewFilter filter = ReviewFilter.none()
                .withSeller("sam").withRating(5).withDateRange(DAY0, DAY0.plusDays(1));

        assertSameReviews(service.findReviews(filter), match);
    }

    @Test
    void task3_8_noResults_returnsEmptyList_notAnError() {
        assertTrue(service.findReviews(ReviewFilter.none()).isEmpty());

        review("sam", 4);
        assertTrue(service.findReviews(ReviewFilter.none().withRating(1)).isEmpty());
    }

    // ---------- Task 3.8: hide review (6.6) ----------

    @Test
    void task3_8_6_6_basicFlow_hidesReview_recordsAudit_notifiesBuyerAndSeller() {
        Review r = review("sam", 1);
        assertFalse(r.isHidden());
        support.clock.advance(Duration.ofHours(1));

        service.hideReview(r.getId(), "  Offensive content ");

        assertTrue(r.isHidden());

        List<AuditLogEntry> log = support.store.getAuditLog();
        assertEquals(1, log.size());
        AuditLogEntry entry = log.get(0);
        assertEquals("ada", entry.getAdminUsername());
        assertEquals("Hide review", entry.getActionTaken());
        assertEquals("Offensive content", entry.getReason());           // trimmed
        assertNull(entry.getDataSnapshot());
        assertNotNull(entry.getLogReference());
        assertEquals(support.clock.now(), entry.getActionDate());

        List<Notification> toBuyer = notificationsOf("bea");
        assertEquals(1, toBuyer.size());
        assertTrue(toBuyer.get(0).getContent().contains("hidden"));

        List<Notification> toSeller = notificationsOf("sam");
        assertEquals(1, toSeller.size());
        assertTrue(toSeller.get(0).getContent().contains("hidden"));
    }

    @Test
    void task3_8_6_6_hidingAReview_excludesItFromTheSellerAverageRating() {
        review("sam", 5);
        Review low = review("sam", 1);
        assertEquals(3.0, support.store.getAverageRating("sam").getAsDouble());

        service.hideReview(low.getId(), "Spam");

        // DataStore.getAverageRating excludes hidden reviews automatically
        assertEquals(5.0, support.store.getAverageRating("sam").getAsDouble());
        assertEquals(1, support.store.getRatingCount("sam"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void task3_8_A1_reasonRequired_hideWithoutReasonChangesNothing(String reason) {
        Review r = review("sam", 1);

        ServiceException e = assertThrows(ServiceException.class, () -> service.hideReview(r.getId(), reason));

        assertEquals("A reason is required to hide a review.", e.getMessage());
        assertFalse(r.isHidden());
        assertTrue(support.store.getAuditLog().isEmpty());
        assertTrue(notificationsOf("bea").isEmpty());
        assertTrue(notificationsOf("sam").isEmpty());
    }

    @Test
    void task3_8_6_6_hideAlreadyHiddenReview_isRefused() {
        Review r = review("sam", 1);
        service.hideReview(r.getId(), "Spam");
        int auditCountAfterFirst = support.store.getAuditLog().size();

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.hideReview(r.getId(), "Again"));

        assertEquals("This review is already hidden.", e.getMessage());
        assertTrue(r.isHidden());
        assertEquals(auditCountAfterFirst, support.store.getAuditLog().size());
    }

    @Test
    void task3_8_6_6_hideReviewThatDoesNotExist_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.hideReview("R999", "Spam"));

        assertEquals("This review no longer exists.", e.getMessage());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    // ---------- UC2 Special Requirement 3: access denied ----------

    @Test
    void task3_8_noSession_listAndHideAreDenied() {
        Review r = review("sam", 5);
        support.logout();

        assertThrows(AccessDeniedException.class, () -> service.findReviews(ReviewFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.hideReview(r.getId(), "Spam"));
        assertFalse(r.isHidden());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void task3_8_buyerSession_listAndHideAreDenied() {
        Review r = review("sam", 5);
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.findReviews(ReviewFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.hideReview(r.getId(), "Spam"));
        assertFalse(r.isHidden());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    @Test
    void task3_8_sellerSession_listAndHideAreDenied() {
        Review r = review("sam", 5);
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.findReviews(ReviewFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.hideReview(r.getId(), "Spam"));
        assertFalse(r.isHidden());
        assertTrue(support.store.getAuditLog().isEmpty());
    }

    // ---------- Task 3.8 N1: findReviewsForSeller coverage ----------

    @Test
    void task3_8_findReviewsForSeller_exactSellerMatch_doesNotMatchPrefix() {
        Fixtures.seller(support.store, "samuel");
        Review sams = review("sam", 5);
        review("samuel", 3);

        assertSameReviews(service.findReviewsForSeller("sam", ReviewFilter.none()), sams);
    }

    @Test
    void task3_8_findReviewsForSeller_sellerMatchIsCaseInsensitive() {
        Review sams = review("sam", 5);

        assertSameReviews(service.findReviewsForSeller("SAM", ReviewFilter.none()), sams);
        assertSameReviews(service.findReviewsForSeller("Sam", ReviewFilter.none()), sams);
    }

    @Test
    void task3_8_findReviewsForSeller_includesHiddenReviews() {
        Review hidden = review("sam", 2);
        support.store.transaction(() -> hidden.setHidden(true));

        assertTrue(service.findReviewsForSeller("sam", ReviewFilter.none()).contains(hidden));
    }

    @Test
    void task3_8_findReviewsForSeller_rating0_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.findReviewsForSeller("sam", ReviewFilter.none().withRating(0)));
        assertEquals("The rating filter must be between 1 and 5.", e.getMessage());
    }

    @Test
    void task3_8_findReviewsForSeller_rating6_isRefused() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.findReviewsForSeller("sam", ReviewFilter.none().withRating(6)));
        assertEquals("The rating filter must be between 1 and 5.", e.getMessage());
    }

    @Test
    void task3_8_findReviewsForSeller_rating1And5_areAccepted() {
        Review one = review("sam", 1);
        Review five = review("sam", 5);

        assertSameReviews(service.findReviewsForSeller("sam", ReviewFilter.none().withRating(1)), one);
        assertSameReviews(service.findReviewsForSeller("sam", ReviewFilter.none().withRating(5)), five);
    }

    @Test
    void task3_8_findReviewsForSeller_dateRange_isInclusiveAtBothEnds() {
        LocalDateTime start = support.clock.now();
        Review atStart = review("sam", 5);

        support.clock.advance(Duration.ofDays(1));
        Review inMiddle = review("sam", 4);

        support.clock.advance(Duration.ofDays(1));
        LocalDateTime end = support.clock.now();
        Review atEnd = review("sam", 3);

        List<Review> result = service.findReviewsForSeller("sam",
                ReviewFilter.none().withDateRange(start, end));

        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of(atStart, inMiddle, atEnd)));
    }

    @Test
    void task3_8_findReviewsForSeller_nullFilter_returnsAllForSellerOnly() {
        Review r1 = review("sam", 5);
        Review r2 = review("sam", 3);
        review("sue", 4);

        assertSameReviews(service.findReviewsForSeller("sam", null), r1, r2);
    }

    @Test
    void task3_8_findReviewsForSeller_sortedNewestFirst() {
        Review older = review("sam", 5);
        support.clock.advance(Duration.ofHours(1));
        Review newer = review("sam", 3);

        assertEquals(List.of(newer, older),
                service.findReviewsForSeller("sam", ReviewFilter.none()));
    }

    @Test
    void task3_8_findReviewsForSeller_noSession_isDenied() {
        support.logout();
        assertThrows(AccessDeniedException.class,
                () -> service.findReviewsForSeller("sam", ReviewFilter.none()));
    }

    @Test
    void task3_8_findReviewsForSeller_buyerSession_isDenied() {
        support.loginAsBuyer();
        assertThrows(AccessDeniedException.class,
                () -> service.findReviewsForSeller("sam", ReviewFilter.none()));
    }

    @Test
    void task3_8_findReviewsForSeller_sellerSession_isDenied() {
        support.loginAsSeller();
        assertThrows(AccessDeniedException.class,
                () -> service.findReviewsForSeller("sam", ReviewFilter.none()));
    }
    // ---------- helpers ----------

    private static void assertSameReviews(List<Review> actual, Review... expected) {
        assertEquals(expected.length, actual.size(), "number of reviews");
        assertEquals(Set.of(expected), Set.copyOf(actual));
    }
}