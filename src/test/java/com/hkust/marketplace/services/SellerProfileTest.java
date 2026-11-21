package com.hkust.marketplace.services;

import com.hkust.marketplace.models.RatingSummary;
import com.hkust.marketplace.models.Review;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SellerProfile}: the rating it shows always counts exactly the reviews it shows
 * (UC8 step 7 and A3), so the heading and the list below it can never disagree.
 */
class SellerProfileTest {

    private static final List<Review> TWO_REVIEWS = List.of(new Review("O1", 4, "Good"), new Review("O2", 5, "Great"));

    @Test
    void aRatingCountedFromTheReviewsShown_isAccepted() {
        SellerProfile profile = new SellerProfile("sam", "Seller sam", null, RatingSummary.of(TWO_REVIEWS), TWO_REVIEWS);

        assertEquals(new RatingSummary(2, 4.5), profile.rating());
        assertEquals(TWO_REVIEWS, profile.reviews());
    }

    @Test
    void aRatingThatDoesNotCountTheReviewsShown_isRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new SellerProfile("sam", "Seller sam", null, RatingSummary.NONE, TWO_REVIEWS));
        assertThrows(IllegalArgumentException.class,
                () -> new SellerProfile("sam", "Seller sam", null, new RatingSummary(3, 4.0), TWO_REVIEWS));
    }
}
