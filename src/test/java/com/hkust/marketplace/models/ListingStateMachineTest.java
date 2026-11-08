package com.hkust.marketplace.models;

import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * State-based tests for the Listing state machine (docs/state-machines.md; Lecture 14):
 * each test puts a listing in a state, applies one event, and checks the resulting state.
 * Every allowed transition is tested, and every event is tried in the states that must refuse it.
 */
class ListingStateMachineTest {

    private static SaleListing sale() {
        return new SaleListing("sam", "Book", Category.TEXTBOOKS, ItemCondition.USED, "desc", MeetupLocation.LIBRARY, 100);
    }

    private static LendListing lend() {
        return new LendListing("sam", "Calculator", Category.ELECTRONICS, ItemCondition.USED, "desc",
                MeetupLocation.LIBRARY, Duration.ofDays(7));
    }

    private static GiveawayListing giveaway() {
        return new GiveawayListing("sam", "Lamp", Category.FURNITURE, ItemCondition.USED, "desc", MeetupLocation.LIBRARY);
    }

    /** A listing in the given status, of a type that can reach it. */
    private static Listing listingIn(ListingStatus status) {
        Listing listing = switch (status) {
            case LENT -> lend();
            case GIVEN -> giveaway();
            default -> sale();
        };
        return Fixtures.moveTo(listing, status);
    }

    /** Applies the event and checks it is refused and leaves the status unchanged. */
    private static void assertRefused(ListingStatus from, Consumer<Listing> event) {
        Listing listing = listingIn(from);
        assertThrows(IllegalStateException.class, () -> event.accept(listing), "from " + from);
        assertEquals(from, listing.getStatus(), "a refused event must not change the status");
    }

    // ---------- Allowed transitions ----------

    @Test
    void newListing_isPendingReview() {
        assertEquals(ListingStatus.PENDING_REVIEW, sale().getStatus());
    }

    @Test
    void adminReview_approvesOrRejects() {
        Listing approved = sale();
        approved.approve();
        assertEquals(ListingStatus.LIVE, approved.getStatus());

        Listing rejected = sale();
        rejected.reject();
        assertEquals(ListingStatus.REJECTED, rejected.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "LIVE", "REJECTED"})
    void sellerEdit_resubmitsForReview(ListingStatus from) {
        Listing listing = listingIn(from);
        listing.resubmit();
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "LIVE", "REJECTED"})
    void sellerDelete_removes(ListingStatus from) {
        Listing listing = listingIn(from);
        listing.withdraw();
        assertEquals(ListingStatus.REMOVED, listing.getStatus());
    }

    @Test
    void saleDeal_goesReservedThenSold() {
        Listing listing = Fixtures.moveTo(sale(), ListingStatus.LIVE);
        listing.acceptDeal();
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        listing.completeDeal();
        assertEquals(ListingStatus.SOLD, listing.getStatus());
    }

    @Test
    void giveawayDeal_goesReservedThenGiven() {
        Listing listing = Fixtures.moveTo(giveaway(), ListingStatus.LIVE);
        listing.acceptDeal();
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        listing.completeDeal();
        assertEquals(ListingStatus.GIVEN, listing.getStatus());
    }

    @Test
    void lendDeal_goesLentThenLiveAndCanBeLentAgain() {
        Listing listing = Fixtures.moveTo(lend(), ListingStatus.LIVE);
        listing.acceptDeal();
        assertEquals(ListingStatus.LENT, listing.getStatus());
        listing.completeDeal(); // returned
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        listing.acceptDeal();
        assertEquals(ListingStatus.LENT, listing.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "LIVE", "REJECTED", "RESERVED", "LENT"})
    void adminRemove_removes(ListingStatus from) {
        Listing listing = listingIn(from);
        listing.remove();
        assertEquals(ListingStatus.REMOVED, listing.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "LENT"})
    void dealCompletedAfterAdminRemoval_listingStaysRemoved(ListingStatus from) {
        // UC12 A1 for lent items; the same for reserved items (assumption, to confirm with the client)
        Listing listing = listingIn(from);
        listing.remove();
        listing.completeDeal();
        assertEquals(ListingStatus.REMOVED, listing.getStatus());
    }

    // ---------- Refused events (each event in every state where it is not allowed) ----------

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING_REVIEW")
    void approveAndReject_onlyFromPendingReview(ListingStatus from) {
        assertRefused(from, Listing::approve);
        assertRefused(from, Listing::reject);
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "LENT", "GIVEN", "REMOVED"})
    void lockedListing_cannotBeEditedOrDeleted(ListingStatus from) {
        assertTrue(listingIn(from).isLocked());
        assertRefused(from, Listing::resubmit);
        assertRefused(from, Listing::withdraw);
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "LIVE", "REJECTED"})
    void unlockedListing_isNotLocked(ListingStatus from) {
        assertFalse(listingIn(from).isLocked());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"SOLD", "GIVEN", "REMOVED"})
    void adminRemove_refusedOnceSoldGivenOrRemoved(ListingStatus from) {
        assertRefused(from, Listing::remove);
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "LIVE")
    void acceptDeal_onlyFromLive(ListingStatus from) {
        assertRefused(from, Listing::acceptDeal);
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "LIVE", "REJECTED", "SOLD", "GIVEN"})
    void completeDeal_needsAnAcceptedDeal(ListingStatus from) {
        assertRefused(from, Listing::completeDeal);
    }

    @Test
    void lendListing_cannotBeSoldOrGiven_andSaleListingCannotBeLent() {
        // "Make errors impossible by design" (Lecture 12): these statuses are unreachable
        assertThrows(IllegalArgumentException.class, () -> Fixtures.moveTo(sale(), ListingStatus.LENT));
        assertThrows(IllegalArgumentException.class, () -> Fixtures.moveTo(lend(), ListingStatus.SOLD));
        assertThrows(IllegalArgumentException.class, () -> Fixtures.moveTo(giveaway(), ListingStatus.SOLD));
    }
}
