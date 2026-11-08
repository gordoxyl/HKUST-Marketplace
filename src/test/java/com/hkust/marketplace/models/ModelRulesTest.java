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

    @Test
    void newListing_isPendingReviewWithNoViews() {
        Listing listing = book();
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertEquals(0, listing.getViewCount());
        assertNull(listing.getId());
    }

    @Test
    void saleListing_rejectsNonPositivePrice() {
        assertThrows(IllegalArgumentException.class, () -> new SaleListing("sam", "Book", Category.TEXTBOOKS,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY, 0));
        assertThrows(IllegalArgumentException.class, () -> book().setPrice(-1));
    }

    @Test
    void lendListing_needsAtLeastTheMinimumBorrowTime() {
        assertThrows(IllegalArgumentException.class, () -> new LendListing("sam", "Calc", Category.ELECTRONICS,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY, Duration.ofSeconds(4)));
        LendListing lend = new LendListing("sam", "Calc", Category.ELECTRONICS,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY, Duration.ofSeconds(5));
        assertEquals("5 seconds", lend.getDurationDisplay());
        assertEquals("1 day 2 hours", LendListing.formatDuration(Duration.ofHours(26)));
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
        review.reply("Thanks!");
        assertEquals("Thanks!", review.getSellerReply());
        assertThrows(IllegalStateException.class, () -> review.reply("Again"));
    }

    // ---------- Reports and admin records ----------

    @Test
    void report_needsReasonAndDetails() {
        assertThrows(IllegalArgumentException.class, () -> new Report("L1", "bea", null, "details"));
        assertThrows(IllegalArgumentException.class, () -> new Report("L1", "bea", ReportReason.OTHER, " "));
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
