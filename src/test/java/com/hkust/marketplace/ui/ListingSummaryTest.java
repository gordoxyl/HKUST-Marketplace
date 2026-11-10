package com.hkust.marketplace.ui;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what the shared listing card shows (C6; UC8 step 2 and Special Requirement 1,
 * Tasks 1.3, 2.4 and 3.2). State-based testing (Lecture 14): each listing is first moved to
 * the status under test through the state machine, then the card's text is checked for each
 * kind of viewer.
 */
class ListingSummaryTest {

    private DataStore store;
    private Seller sam;

    @BeforeEach
    void setUp() {
        store = Fixtures.store(Fixtures.clock());
        sam = Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
    }

    private ListingSummary summary(Listing listing, UserRole viewer) {
        return ListingSummary.of(listing, viewer, store);
    }

    /** A Pending Review listing of a type that can reach the status, moved there. */
    private Listing listingIn(ListingStatus status) {
        Listing listing = switch (status) {
            case LENT -> Fixtures.lend(store, "sam", Duration.ofDays(7));
            case GIVEN -> Fixtures.giveaway(store, "sam");
            default -> Fixtures.sale(store, "sam", 100);
        };
        return Fixtures.moveTo(listing, status);
    }

    // ---------- What the buyer sees on Browse (UC8 step 2) ----------

    @Test
    void buyer_seesEveryBrowseField_forAnAvailableSale() {
        ListingSummary card = summary(Fixtures.live(Fixtures.sale(store, "sam", 180)), UserRole.BUYER);

        assertEquals("Book", card.title());
        assertEquals("Sell", card.typeText());
        assertEquals("HK$ 180", card.priceText());
        assertEquals("Textbooks · Used", card.detailsText());
        assertEquals(Optional.of("Seller: Seller sam"), card.sellerText());
        assertEquals("Meet-up: Library", card.locationText());
        assertEquals("Posted: 02 Nov 2026", card.dateText());
        assertEquals(ListingSummary.AVAILABLE, card.badgeText());
        assertEquals("badge-live", card.badgeStyle());
        assertTrue(card.available());
        assertFalse(card.greyedOut());
        assertEquals(Optional.empty(), card.photosText());
        assertEquals(Optional.empty(), card.thumbnail()); // no photo: the card shows "No photo"
    }

    @Test
    void priceText_isBorrowForLendAndFreeForGiveaway() {
        assertEquals("Borrow", summary(Fixtures.lend(store, "sam", Duration.ofDays(7)), UserRole.BUYER).priceText());
        assertEquals("Free", summary(Fixtures.giveaway(store, "sam"), UserRole.BUYER).priceText());
        assertEquals("Lend", summary(Fixtures.lend(store, "sam", Duration.ofDays(7)), UserRole.BUYER).typeText());
        assertEquals("Giveaway", summary(Fixtures.giveaway(store, "sam"), UserRole.BUYER).typeText());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "LENT"})
    void buyer_seesReservedAndLentListingsGreyedOut(ListingStatus status) {
        ListingSummary card = summary(listingIn(status), UserRole.BUYER);

        assertFalse(card.available());
        assertTrue(card.greyedOut());
        assertEquals(status.getDisplayName(), card.badgeText());
        assertEquals("badge-sold", card.badgeStyle());
    }

    @Test
    void buyer_seesAGiveawayWithAPendingClaimAsClaimPending_untilTheClaimIsDeclined() {
        Listing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        Offer claim = store.addOffer(new Claim(lamp.getId(), "bea"));

        ListingSummary pending = summary(lamp, UserRole.BUYER);
        assertTrue(pending.claimPending());
        assertEquals(ListingSummary.CLAIM_PENDING, pending.badgeText());
        assertEquals("badge-pending", pending.badgeStyle());
        assertTrue(pending.greyedOut());

        store.transaction(claim::decline);
        ListingSummary declined = summary(lamp, UserRole.BUYER);
        assertFalse(declined.claimPending());
        assertEquals(ListingSummary.AVAILABLE, declined.badgeText());
        assertFalse(declined.greyedOut());
    }

    @Test
    void aPendingClaimOnASaleIsNotPossible_soOnlyGiveawaysShowClaimPending() {
        Listing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        assertFalse(summary(book, UserRole.BUYER).claimPending());
    }

    @Test
    void deactivatedSeller_isShownToTheBuyerAsSellerDeactivated_butTheAdminSeesTheStatus() {
        Listing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        store.transaction(() -> sam.setActive(false));

        ListingSummary buyerCard = summary(book, UserRole.BUYER);
        assertEquals(ListingSummary.SELLER_DEACTIVATED, buyerCard.badgeText());
        assertEquals("badge-rejected", buyerCard.badgeStyle());
        assertTrue(buyerCard.greyedOut());
        assertFalse(buyerCard.sellerActive());

        ListingSummary adminCard = summary(book, UserRole.ADMIN);
        assertEquals("Live", adminCard.badgeText());
        assertEquals("badge-live", adminCard.badgeStyle());
        assertFalse(adminCard.greyedOut());
    }

    // ---------- What the seller sees on My Listings (Task 2.4) ----------

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "PENDING_REVIEW, badge-pending",
            "LIVE,           badge-live",
            "REJECTED,       badge-rejected",
            "RESERVED,       badge-sold",
            "SOLD,           badge-sold",
            "LENT,           badge-sold",
            "GIVEN,          badge-sold",
            "REMOVED,        badge-rejected"
    })
    void seller_seesTheStatusOfEveryListing_neverGreyedOut(ListingStatus status, String style) {
        ListingSummary card = summary(listingIn(status), UserRole.SELLER);

        assertEquals(status.getDisplayName(), card.badgeText());
        assertEquals(style, card.badgeStyle());
        assertFalse(card.greyedOut());
        assertEquals(Optional.empty(), card.sellerText()); // their own listings
        assertEquals(Optional.empty(), card.photosText());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "REJECTED", "SOLD", "GIVEN", "REMOVED"})
    void availabilityText_ofANonLiveListing_isItsStatus(ListingStatus status) {
        assertEquals(status.getDisplayName(), summary(listingIn(status), UserRole.BUYER).availabilityText());
    }

    // ---------- What the admin sees on Listing Review and Manage All Listings (Task 3.2) ----------

    @Test
    void admin_seesSellerUsername_submittedDateAndTime_andPhotoCount() {
        Listing book = Fixtures.sale(store, "sam", 100);
        ListingSummary card = summary(book, UserRole.ADMIN);

        assertEquals(Optional.of("Seller: Seller sam (@sam)"), card.sellerText());
        assertEquals("Submitted: 02 Nov 2026 12:00", card.dateText());
        assertEquals(Optional.of("No photos"), card.photosText());
        assertEquals("Pending Review", card.badgeText());
        assertFalse(card.greyedOut());

        book.setPhotos(List.of("a.jpg", "b.jpg", "c.jpg"));
        assertEquals(Optional.of("Photos: 3"), summary(book, UserRole.ADMIN).photosText());
    }

    @Test
    void sellerWhoseAccountIsGone_isShownByUsername() {
        Listing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        ListingSummary card = new ListingSummary(book, UserRole.BUYER, "sam", false, false, false);
        assertEquals(Optional.of("Seller: sam"), card.sellerText());
        assertEquals(ListingSummary.SELLER_DEACTIVATED, card.availabilityText());
    }

    @Test
    void summaryNeedsAListingAndAViewer() {
        Listing book = Fixtures.sale(store, "sam", 100);
        assertThrows(NullPointerException.class, () -> new ListingSummary(null, UserRole.BUYER, "sam", true, true, false));
        assertThrows(NullPointerException.class, () -> new ListingSummary(book, null, "sam", true, true, false));
    }

    // ---------- Thumbnail (Task 1.3: the first photo) ----------

    @Test
    void thumbnail_isTheFirstPhoto() {
        Listing book = Fixtures.sale(store, "sam", 100);
        assertEquals(Optional.empty(), summary(book, UserRole.BUYER).thumbnail());

        String first = store.addPhoto(new byte[] {1});
        String second = store.addPhoto(new byte[] {2});
        book.setPhotos(List.of(first, second));

        assertEquals(Optional.of(first), summary(book, UserRole.BUYER).thumbnail());
        assertEquals(Optional.of(first), summary(book, UserRole.ADMIN).thumbnail()); // the same for every viewer
    }
}
