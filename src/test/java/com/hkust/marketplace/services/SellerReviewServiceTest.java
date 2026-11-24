package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2.9 Review and Feedback Handling: the seller's list of reviews and rating, replying once
 * and telling the buyer, and flagging an inappropriate review for the admin (Manage Reviews).
 */
class SellerReviewServiceTest {

    private TestClock clock;
    private DataStore store;
    private SellerReviewService service;
    private Seller sam;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        service = new SellerReviewService(store);
        sam = Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        SessionManager.login(sam);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** A completed sale deal for {@code sellerName}, reviewed by {@code buyer}. */
    private Review reviewedDeal(String sellerName, String buyer, int rating, String comment) {
        SaleListing listing = Fixtures.live(Fixtures.sale(store, sellerName, 100));
        PriceOffer offer = (PriceOffer) store.addOffer(new PriceOffer(listing.getId(), buyer, 90));
        DealRules rules = new DealRules(store);
        rules.accept(offer);
        rules.complete(offer);
        return store.addReview(new Review(offer.getId(), rating, comment));
    }

    // ---------- Step 1: the list and the rating ----------

    @Test
    void reviews_areThisSellersVisibleOnesOnly() {
        Review mine = reviewedDeal("sam", "bea", 5, "Very smooth");
        Fixtures.seller(store, "sue");
        reviewedDeal("sue", "ben", 4, "Fine");

        assertEquals(List.of(mine), service.getMyReviews());
    }

    @Test
    void aHiddenReview_isNotShownAndNotCountedInTheRating() {
        Review review = reviewedDeal("sam", "bea", 5, "Very smooth");
        review.setHidden(true);

        assertTrue(service.getMyReviews().isEmpty());
        assertTrue(service.getMyRating().isEmpty());
    }

    @Test
    void theRatingIsTheCountAndAverageOfTheShownReviews() {
        reviewedDeal("sam", "bea", 4, "Good");
        reviewedDeal("sam", "ben", 5, "Great");

        assertEquals(2, service.getMyRating().count());
        assertEquals(4.5, service.getMyRating().average(), 0.0001);
    }

    // ---------- Steps 3-5: reply ----------

    @Test
    void replying_savesTheReply_andNotifiesTheBuyer() {
        Review review = reviewedDeal("sam", "bea", 5, "Very smooth");

        service.reply(review.getId(), "  Thank you, enjoy the book!  ");

        assertEquals("Thank you, enjoy the book!", review.getSellerReply());
        List<com.hkust.marketplace.models.Notification> board = store.getNotificationsFor("bea");
        assertTrue(board.stream().anyMatch(n -> n.getCategory() == NotificationCategory.REVIEW_REPLY
                && n.getContent().contains("Seller sam replied to your review for 'Book'")
                && n.getContent().contains("Thank you, enjoy the book!")));
    }

    @Test
    void aReviewCanHaveOnlyOneReply() {
        Review review = reviewedDeal("sam", "bea", 5, "Very smooth");
        service.reply(review.getId(), "Thanks!");

        assertEquals("Only one reply is allowed per review.",
                assertThrows(ServiceException.class,
                        () -> service.reply(review.getId(), "One more thing")).getMessage());
        assertEquals("Thanks!", review.getSellerReply());
    }

    @Test
    void anEmptyReply_isRefused_andNothingIsSaved() {
        Review review = reviewedDeal("sam", "bea", 5, "Very smooth");

        for (String bad : new String[]{null, "", "   "}) {
            assertEquals("The reply cannot be empty.",
                    assertThrows(ServiceException.class,
                            () -> service.reply(review.getId(), bad)).getMessage());
        }
        assertNull(review.getSellerReply());
    }

    @Test
    void replyingToAReviewFromADeletedBuyer_worksAndSendsNoNotice() {
        Review review = reviewedDeal("sam", "bea", 5, "Very smooth");
        store.deleteAccount("bea");

        service.reply(review.getId(), "Thanks anyway");

        assertEquals("Thanks anyway", review.getSellerReply());
        assertEquals(DataStore.DELETED_USER, store.getReviewerName(review));
        assertTrue(store.getNotificationsFor("bea").isEmpty());
    }

    // ---------- Step 6: flag ----------

    @Test
    void flagging_marksTheReviewForTheAdmin_andTellsTheAdmins() {
        Fixtures.admin(store, "ada");
        Review review = reviewedDeal("sam", "bea", 1, "Rude comment");

        service.flag(review.getId());

        assertTrue(review.isFlagged());
        assertTrue(store.getNotificationsFor("ada").stream().anyMatch(n ->
                n.getCategory() == NotificationCategory.ANNOUNCEMENT
                        && n.getContent().equals("@sam flagged a review of 'Book' as inappropriate.")));
    }

    @Test
    void aReviewCanBeFlaggedOnlyOnce() {
        Review review = reviewedDeal("sam", "bea", 1, "Rude comment");
        service.flag(review.getId());

        assertEquals("This review is already awaiting moderation.",
                assertThrows(ServiceException.class, () -> service.flag(review.getId())).getMessage());
        assertTrue(review.isFlagged());
    }

    // ---------- Ownership and access ----------

    @Test
    void anotherSellersReview_isRefused() {
        Fixtures.seller(store, "sue");
        Review hers = reviewedDeal("sue", "ben", 4, "Fine");

        String expected = "This review is not on one of your listings.";
        assertEquals(expected,
                assertThrows(ServiceException.class, () -> service.reply(hers.getId(), "Thanks")).getMessage());
        assertEquals(expected,
                assertThrows(ServiceException.class, () -> service.flag(hers.getId())).getMessage());
        assertNull(hers.getSellerReply());
        assertFalse(hers.isFlagged());
    }

    @Test
    void aMissingReview_isReported() {
        String expected = "This review is not on one of your listings.";
        assertEquals(expected,
                assertThrows(ServiceException.class, () -> service.reply("no-such-id", "Thanks")).getMessage());
        assertEquals(expected,
                assertThrows(ServiceException.class, () -> service.flag("no-such-id")).getMessage());
    }

    @Test
    void onlyALoggedInSellerCanManageReviews() {
        Review review = reviewedDeal("sam", "bea", 5, "Very smooth");

        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> service.getMyReviews());
        assertThrows(AccessDeniedException.class, () -> service.getMyRating());
        assertThrows(AccessDeniedException.class, () -> service.reply(review.getId(), "Thanks"));
        assertThrows(AccessDeniedException.class, () -> service.flag(review.getId()));

        Buyer bea = (Buyer) store.findUser("bea");
        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class, () -> service.getMyReviews());
        assertThrows(AccessDeniedException.class, () -> service.reply(review.getId(), "Thanks"));
        assertThrows(AccessDeniedException.class, () -> service.flag(review.getId()));

        Admin ada = Fixtures.admin(store, "ada");
        SessionManager.login(ada);
        assertThrows(AccessDeniedException.class, () -> service.getMyReviews());
        assertThrows(AccessDeniedException.class, () -> service.reply(review.getId(), "Thanks"));

        assertNull(review.getSellerReply());
        assertFalse(review.isFlagged());
    }
}
