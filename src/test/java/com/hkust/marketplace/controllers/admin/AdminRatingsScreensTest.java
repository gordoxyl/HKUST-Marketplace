package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.testsupport.OffScreenApp;
import com.hkust.marketplace.testsupport.ScreenData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The admin's Seller Ratings screens (Task 3.8), through the real screens off screen. */
class AdminRatingsScreensTest {

    private static OffScreenApp app;
    private static ScreenData data;

    @BeforeAll
    static void open() {
        app = OffScreenApp.start();
        data = new ScreenData(app.store());
    }

    @AfterEach
    void reset() {
        app.reset();
    }

    @Test
    void aReviewWithAReply_showsTheSellersReplyUnderTheComment() {
        data.seller("replyseller", "Reply Seller");
        data.buyer("replybuyer", "Reply Buyer");
        PriceOffer deal = data.priceOffer("replybuyer", data.sale("replyseller", "Reply Mug", 50), 45);
        data.accept(deal);
        data.complete(deal);
        Review review = app.store().addReview(new Review(deal.getId(), 4, "Nice mug")); // the buyer's review (UC10)
        app.store().transaction(() -> review.reply("Thanks for buying!"));               // and the seller's reply
        app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
        app.press("Seller Ratings");
        app.press("View Reviews", "Reply Seller");

        assertTrue(app.shows("Nice mug"));
        assertTrue(app.shows("Seller's reply: Thanks for buying!")); // Manage Reviews 3.1.5: wherever the review is shown
        assertEquals(List.of(), app.cutTexts());
    }
}
