package com.hkust.marketplace.models;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the rules built into listings, users, chat threads, reviews, reports and admin records. */
class ModelRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    private SaleListing book() {
        return new SaleListing("sam", "Book", Category.TEXTBOOKS, ItemCondition.USED, "desc", MeetupLocation.LIBRARY, 100);
    }

    // ---------- Users ----------

    @Test
    void users_haveTheirRoleAndLowerCaseUsername() {
        assertEquals(UserRole.BUYER, new Buyer("Bea", "Bea", "hash", null).getRole());
        assertEquals(UserRole.SELLER, new Seller("sam", "Sam", "hash").getRole());
        assertEquals(UserRole.ADMIN, new Admin("ada", "Ada", "hash").getRole());
        assertEquals("bea", new Buyer("Bea", "Bea", "hash", null).getUsername());
    }

    @Test
    void seller_blankBioIsNoBio() {
        Seller seller = new Seller("sam", "Sam", "hash");
        seller.setBio("   ");
        assertNull(seller.getBio());
    }

    // ---------- Listings ----------

    @Test
    void listings_reportTheirTypeAndPriceText() {
        assertEquals(ListingType.SELL, book().getType());
        assertEquals("HK$ 100", book().getPriceDisplay());
        assertEquals("HK$ 99.50", SaleListing.formatPrice(99.5));
        assertEquals("Free", new GiveawayListing("sam", "Lamp", Category.FURNITURE, ItemCondition.USED,
                "desc", MeetupLocation.LIBRARY).getPriceDisplay());
    }

    /** Regression R7: the seller is stored the way accounts store their username. */
    @Test
    void listing_storesTheSellersUsernameInLowerCaseWithoutSurroundingSpaces() {
        assertEquals("sam", new SaleListing(" Sam ", "Book", Category.TEXTBOOKS, ItemCondition.USED,
                "desc", MeetupLocation.LIBRARY, 100).getSellerUsername());
    }

    @Test
    void newListing_isPendingReviewWithNoViews() {
        Listing listing = book();
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertEquals(0, listing.getViewCount());
        assertNull(listing.getId());
    }

    @Test
    void saleListing_rejectsAPriceThatIsNotAPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> new SaleListing("sam", "Book", Category.TEXTBOOKS,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY, 0));
        assertThrows(IllegalArgumentException.class, () -> book().setPrice(-1));
        assertThrows(IllegalArgumentException.class, () -> book().setPrice(Double.POSITIVE_INFINITY));
    }

    @Test
    void lendListing_needsAtLeastTheMinimumBorrowTime() {
        assertThrows(IllegalArgumentException.class, () -> new LendListing("sam", "Calc", Category.ELECTRONICS,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY, Duration.ofSeconds(4)));
        LendListing lend = new LendListing("sam", "Calc", Category.ELECTRONICS,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY, Duration.ofSeconds(5));
        assertEquals("5 seconds", lend.getDurationDisplay());
        assertEquals("1 day 2 hours", LendListing.formatDuration(Duration.ofHours(26)));
        assertEquals("0 seconds", LendListing.formatDuration(Duration.ZERO)); // boundary value
    }

    @Test
    void listing_holdsAtMostSixPhotosAndTheFirstIsTheThumbnail() {
        Listing listing = book();
        assertNull(listing.getThumbnail());

        listing.setPhotos(List.of("a.png", "b.png"));
        assertEquals("a.png", listing.getThumbnail());

        List<String> seven = Collections.nCopies(7, "x.png");
        assertThrows(IllegalArgumentException.class, () -> listing.setPhotos(seven));
    }

    @Test
    void listing_isAddedOnlyOnce_andGetsItsPostedDateThen() {
        Listing listing = book();
        assertNull(listing.getPostedDate());

        listing.markAdded("L1", NOW);

        assertEquals(NOW, listing.getPostedDate());
        assertThrows(IllegalStateException.class, () -> listing.markAdded("L2", NOW));
    }

    // ---------- Chat ----------

    @Test
    void chatThread_onlyTheBuyerAndSellerCanWrite() {
        Listing listing = book();
        listing.markAdded("L1", NOW);
        ChatThread thread = new ChatThread("bea", listing);

        thread.addMessage(new Message("bea", "Is it available?", NOW));
        thread.addMessage(new Message("sam", "Yes", NOW.plusMinutes(1)));

        assertEquals(2, thread.getMessages().size());
        assertThrows(IllegalArgumentException.class, () -> thread.addMessage(new Message("eve", "Hi", NOW)));
        assertThrows(IllegalArgumentException.class, () -> thread.addMessage(new Message("bea", "  ", NOW)));
    }

    // ---------- Reviews ----------

    @Test
    void review_ratingMustBeOneToFive() {
        assertThrows(IllegalArgumentException.class, () -> new Review("O1", 0, null));
        assertThrows(IllegalArgumentException.class, () -> new Review("O1", 6, null));
        assertNull(new Review("O1", 5, " ").getComment()); // comment is optional
    }

    @Test
    void review_allowsOnlyOneNonEmptyReply() {
        Review review = new Review("O1", 4, "Good");

        assertThrows(IllegalArgumentException.class, () -> review.reply(" "));
        assertThrows(IllegalArgumentException.class, () -> review.reply(null));
        review.reply("Thanks!");
        assertEquals("Thanks!", review.getSellerReply());
        assertThrows(IllegalStateException.class, () -> review.reply("Again"));
    }

    // ---------- Reports and admin records ----------

    @Test
    void report_needsReasonAndDetails() {
        assertThrows(IllegalArgumentException.class, () -> new Report("L1", "bea", null, "details"));
        assertThrows(IllegalArgumentException.class, () -> new Report("L1", "bea", ReportReason.OTHER, " "));
        assertThrows(IllegalArgumentException.class, () -> new Report("L1", "bea", ReportReason.OTHER, null));
    }

    // ---------- Ids: DataStore gives each record its id once, when it is added ----------

    @Test
    void records_areGivenTheirIdOnlyOnce() {
        Listing listing = book();
        listing.markAdded("L1", NOW);
        Offer offer = new PriceOffer("L1", "bea", 50);
        ChatThread thread = new ChatThread("bea", listing);
        Notification notification = new Notification("bea", NotificationCategory.ANNOUNCEMENT, "Welcome");
        Review review = new Review("O1", 5, "Good");
        Report report = new Report("L1", "bea", ReportReason.OTHER, "details");
        User user = new Buyer("bea", "Bea", "hash", null);

        offer.markAdded("O1", NOW);
        thread.markAdded("T1", NOW);
        notification.markAdded("N1", NOW);
        review.markAdded("R1", NOW);
        report.markAdded("P1");
        user.markAdded(NOW);

        assertThrows(IllegalStateException.class, () -> offer.markAdded("O2", NOW));
        assertThrows(IllegalStateException.class, () -> thread.markAdded("T2", NOW));
        assertThrows(IllegalStateException.class, () -> notification.markAdded("N2", NOW));
        assertThrows(IllegalStateException.class, () -> review.markAdded("R2", NOW));
        assertThrows(IllegalStateException.class, () -> report.markAdded("P2"));
        assertThrows(IllegalStateException.class, () -> user.markAdded(NOW));
        assertEquals("O1", offer.getId());
    }

    @Test
    void listingActionRecord_requiresReasonForRejectTakedownAndRemove() {
        assertDoesNotThrow(() -> new ListingActionRecord("L1", "admin", ListingAction.APPROVE_LISTING, null));
        for (ListingAction action : List.of(ListingAction.REJECT_LISTING, ListingAction.TAKEDOWN_LISTING,
                ListingAction.REMOVE_LISTING)) {
            assertThrows(IllegalArgumentException.class, () -> new ListingActionRecord("L1", "admin", action, " "));
        }
    }
}
