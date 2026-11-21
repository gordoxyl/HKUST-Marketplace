package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.RatingSummary;
import com.hkust.marketplace.services.ReviewForm;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the wording of ratings and reviews (UC8 step 7 and A3), of the buyer's review form
 * (UC10) and of the Browse screen (UC8 A1).
 */
class ReviewTextTest {

    private static Review review(int rating, String comment) {
        Review review = new Review("O1", rating, comment);
        review.markAdded("R1", LocalDateTime.of(2026, 11, 9, 14, 5));
        return review;
    }

    @Test
    void ratingHeading_showsTheAverage_orSaysThereAreNoRatings() {
        assertEquals("Seller rating: 4.5 / 5 (2 ratings)", ReviewText.ratingHeading(new RatingSummary(2, 4.5)));
        assertEquals("Seller rating: No ratings yet", ReviewText.ratingHeading(RatingSummary.NONE));
    }

    @Test
    void review_showsItsStarsAndDate_andItsComment() {
        Review review = review(4, "Smooth deal");

        assertEquals("★★★★☆ · 09 Nov 2026", ReviewText.header(review));
        assertEquals("Smooth deal", ReviewText.comment(review));
    }

    @Test
    void ratingWithoutAComment_saysSo() {
        assertEquals("No comment.", ReviewText.comment(review(5, null)));
        assertEquals("No comment.", ReviewText.comment(review(5, "   "))); // a blank comment is stored as none
    }

    @Test
    void sellersReply_isShownOnlyWhenThereIsOne() {
        Review review = review(3, "Late");
        assertEquals(Optional.empty(), ReviewText.reply(review));

        review.reply("Sorry about that.");
        assertEquals(Optional.of("Seller's reply: Sorry about that."), ReviewText.reply(review));
    }

    @Test
    void browseResultSummary_countsTheListings_orSaysNoneMatch() {
        assertEquals(BrowseText.NO_LISTINGS, BrowseText.resultSummary(0)); // UC8 A1
        assertEquals("1 listing", BrowseText.resultSummary(1));
        assertEquals("6 listings", BrowseText.resultSummary(6));
    }

    // ---------- Review Seller (UC10) ----------

    private static final ReviewForm FORM = new ReviewForm("O1", "Calculus Textbook", "Sam Lee");

    @Test
    void reviewForm_namesTheSellerAndTheListing() { // step 3
        assertEquals("Rate and review Sam Lee\nfor 'Calculus Textbook'", ReviewText.formHeader(FORM));
    }

    @Test
    void chosenRating_showsTheStars_fromOneToFive_orAsksForThem() { // step 4; boundary values 1 and 5
        assertEquals("Choose 1 to 5 stars.", ReviewText.chosenRating(null));
        assertEquals("★☆☆☆☆ 1 of 5", ReviewText.chosenRating(1));
        assertEquals("★★★★★ 5 of 5", ReviewText.chosenRating(5));
    }

    @Test
    void submittedReview_isConfirmed() { // step 10
        assertEquals("Thank you. Your review of Sam Lee was submitted. It now shows on the seller's profile "
                + "and on each of their listings.", ReviewText.submitted(FORM));
    }

    @Test
    void sellerBio_isShown_orTheProfileSaysThereIsNone() {
        assertEquals("Final year student", BrowseText.bio(" Final year student "));
        assertEquals("This seller has not written a bio.", BrowseText.bio(null));
        assertEquals("This seller has not written a bio.", BrowseText.bio("  "));
    }
}
