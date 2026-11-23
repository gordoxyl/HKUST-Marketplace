package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for UC8 Browse Marketplace: the basic flow (show, refine, open a
 * listing), A1 (nothing found), A2 (invalid price range), A3 (seller profile) and the special
 * requirements. The marketplace below is posted one minute apart, oldest first:
 * <pre>
 * book        sale      HK$ 180   Textbooks    Used      sam   "Calculus Textbook"
 * charger     sale      HK$ 250   Electronics  Like New  sam   "Laptop Charger"
 * lamp        giveaway  (HK$ 0)   Furniture    Used      sue   "Desk Lamp" (description mentions a textbook)
 * calculator  lend      no price  Electronics  Like New  sue   "Graphing Calculator"
 * pen         sale      HK$ 60    Stationery   New       sue   "Fountain Pen"
 * chair       sale      HK$ 90    Furniture    Used      sam   "Old Chair"   Reserved: live, but not available
 * </pre>
 */
class BrowseServiceTest {

    private TestClock clock;
    private DataStore store;
    private BrowseService browse;
    private Seller sam;
    private Buyer bea;
    private Buyer ben;
    private Listing book;
    private Listing charger;
    private Listing lamp;
    private Listing calculator;
    private Listing pen;
    private Listing chair;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        browse = new BrowseService(store);
        sam = Fixtures.seller(store, "sam");
        Fixtures.seller(store, "sue"); // only her account is needed: the tests name her by username
        bea = Fixtures.buyer(store, "bea");
        ben = Fixtures.buyer(store, "ben");

        book = post(new SaleListing("sam", "Calculus Textbook", Category.TEXTBOOKS, ItemCondition.USED,
                "For MATH 1013, some notes inside", MeetupLocation.LIBRARY, 180));
        charger = post(new SaleListing("sam", "Laptop Charger", Category.ELECTRONICS, ItemCondition.LIKE_NEW,
                "Works with most laptops", MeetupLocation.LIBRARY, 250));
        lamp = post(new GiveawayListing("sue", "Desk Lamp", Category.FURNITURE, ItemCondition.USED,
                "Bright LED, good for reading a TEXTBOOK at night", MeetupLocation.LIBRARY));
        calculator = post(new LendListing("sue", "Graphing Calculator", Category.ELECTRONICS, ItemCondition.LIKE_NEW,
                "TI-84", MeetupLocation.LIBRARY, Duration.ofDays(7)));
        pen = post(new SaleListing("sue", "Fountain Pen", Category.STATIONERY, ItemCondition.NEW,
                null, MeetupLocation.LIBRARY, 60));
        chair = post(new SaleListing("sam", "Old Chair", Category.FURNITURE, ItemCondition.USED,
                "Wooden", MeetupLocation.LIBRARY, 90));
        chair.acceptDeal(); // Reserved

        SessionManager.login(bea);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** Adds the listing one minute after the previous one and approves it. */
    private Listing post(Listing listing) {
        clock.advance(Duration.ofMinutes(1));
        store.addListing(listing);
        listing.approve();
        return listing;
    }

    private static BrowseQuery keyword(String keyword) {
        return new BrowseQuery(keyword, null, null, null, "", "", ListingSort.NEWEST);
    }

    private static BrowseQuery priced(String min, String max) {
        return new BrowseQuery("", null, null, null, min, max, ListingSort.NEWEST);
    }

    private static BrowseQuery sorted(ListingSort sort) {
        return new BrowseQuery("", null, null, null, "", "", sort);
    }

    /** Sells a new listing of Sam's to the buyer and has the buyer review Sam, one day later. */
    private Review reviewOfSam(String buyer, int rating, String comment) {
        clock.advance(Duration.ofDays(1));
        SaleListing sold = Fixtures.live(Fixtures.sale(store, "sam", 10));
        Offer deal = store.addOffer(new PriceOffer(sold.getId(), buyer, 10));
        deal.accept(store.now());
        sold.acceptDeal();
        deal.complete(store.now());
        sold.completeDeal();
        return store.addReview(new Review(deal.getId(), rating, comment));
    }

    // ---------- Show Listings (steps 1-2; Special Requirement 1) ----------

    @Test
    void browse_showsTheLiveListings_newestFirst_unavailableOnesIncluded() {
        Fixtures.sale(store, "sam", 10);                                  // Pending Review
        Fixtures.moveTo(Fixtures.sale(store, "sam", 10), ListingStatus.REJECTED);
        Fixtures.moveTo(Fixtures.sale(store, "sam", 10), ListingStatus.SOLD);
        Fixtures.moveTo(Fixtures.giveaway(store, "sue"), ListingStatus.GIVEN);
        Fixtures.moveTo(Fixtures.sale(store, "sam", 10), ListingStatus.REMOVED);

        List<Listing> shown = browse.search(BrowseQuery.ALL);

        assertEquals(List.of(chair, pen, calculator, lamp, charger, book), shown);
        assertFalse(store.isAvailable(chair)); // Reserved: shown greyed out, but shown
    }

    @Test
    void lentListing_andAGiveawayWithAPendingClaim_areStillShown() {
        calculator.acceptDeal(); // Lent
        store.addOffer(new Claim(lamp.getId(), "ben"));

        List<Listing> shown = browse.search(BrowseQuery.ALL);

        assertTrue(shown.contains(calculator));
        assertTrue(shown.contains(lamp));
        assertFalse(store.isAvailable(lamp));
    }

    @Test
    void listingsOfADeactivatedSeller_areNotShown() {
        store.transaction(() -> sam.setActive(false));

        assertEquals(List.of(pen, calculator, lamp), browse.search(BrowseQuery.ALL));
    }

    // ---------- Refine and Show Results (steps 3-4) ----------

    @Test
    void keyword_isLookedForInTitleAndDescription_ignoringCase() {
        assertEquals(List.of(lamp, book), browse.search(keyword("textbook"))); // lamp: description; book: title
        assertEquals(List.of(lamp, book), browse.search(keyword("  TextBook ")));
        assertEquals(List.of(calculator), browse.search(keyword("ti-84")));
        assertEquals(List.of(pen), browse.search(keyword("fountain"))); // a listing with no description is still searched
    }

    @Test
    void noKeyword_matchesEveryListing() {
        assertEquals(6, browse.search(keyword("")).size());
        assertEquals(6, browse.search(keyword("   ")).size());
        assertEquals(6, browse.search(new BrowseQuery(null, null, null, null, null, null, null)).size());
    }

    @Test
    void filters_narrowTheResults_byCategoryTypeAndCondition() {
        assertEquals(List.of(calculator, charger),
                browse.search(new BrowseQuery("", Category.ELECTRONICS, null, null, "", "", ListingSort.NEWEST)));
        assertEquals(List.of(lamp),
                browse.search(new BrowseQuery("", null, ListingType.GIVEAWAY, null, "", "", ListingSort.NEWEST)));
        assertEquals(List.of(pen),
                browse.search(new BrowseQuery("", null, null, ItemCondition.NEW, "", "", ListingSort.NEWEST)));
    }

    @Test
    void aListingMustMatchEveryChosenFilter() {
        // Furniture + Sell + Used: the chair. The lamp is Furniture and Used, but a giveaway.
        assertEquals(List.of(chair), browse.search(
                new BrowseQuery("", Category.FURNITURE, ListingType.SELL, ItemCondition.USED, "", "", ListingSort.NEWEST)));
        // With the keyword too: "textbook" is only in the lamp's description, so nothing is left
        assertTrue(browse.search(
                new BrowseQuery("textbook", Category.FURNITURE, ListingType.SELL, ItemCondition.USED, "", "", ListingSort.NEWEST))
                .isEmpty());
    }

    @Test
    void priceRange_keepsThePricesInside_theBoundsIncluded() {
        assertEquals(List.of(chair, book), browse.search(priced("90", "180")));   // both bounds are prices
        assertEquals(List.of(book), browse.search(priced("90.01", "249.99")));
        assertEquals(List.of(book), browse.search(priced("180", "180")));         // minimum = maximum is allowed
        assertEquals(List.of(charger, book), browse.search(priced("100", "")));   // only a minimum
        assertEquals(List.of(chair, pen, lamp), browse.search(priced("", "90"))); // only a maximum
    }

    @Test
    void forThePriceRange_aGiveawayCountsAsZero_andLendListingsAreLeftOut() {
        // Special Requirement 4
        assertEquals(List.of(lamp), browse.search(priced("0", "0")));
        assertEquals(List.of(pen, lamp), browse.search(priced("", "60")));
        assertFalse(browse.search(priced("0", "100000")).contains(calculator));
        assertTrue(browse.search(BrowseQuery.ALL).contains(calculator)); // shown again without a price range
    }

    @ParameterizedTest
    @EnumSource(ListingSort.class)
    void sortOrder_keepsTheSameListings(ListingSort sort) {
        assertEquals(6, browse.search(sorted(sort)).size());
    }

    @Test
    void sortByPrice_putsThePricedListingsInOrder_andTheLendListingsAfterThem() {
        // Special Requirement 4: the giveaway counts as HK$0, the lend listing has no price
        assertEquals(List.of(lamp, pen, chair, book, charger, calculator),
                browse.search(sorted(ListingSort.PRICE_LOW_TO_HIGH)));
        assertEquals(List.of(charger, book, chair, pen, lamp, calculator),
                browse.search(sorted(ListingSort.PRICE_HIGH_TO_LOW)));
    }

    @Test
    void sortByPrice_putsEqualPrices_andTheUnpriced_newestFirst() {
        Listing secondLamp = post(new GiveawayListing("sue", "Floor Lamp", Category.FURNITURE, ItemCondition.USED,
                "Tall", MeetupLocation.LIBRARY));
        Listing secondCalculator = post(new LendListing("sue", "Old Calculator", Category.ELECTRONICS,
                ItemCondition.USED, "Casio", MeetupLocation.LIBRARY, Duration.ofDays(3)));

        List<Listing> lowToHigh = browse.search(sorted(ListingSort.PRICE_LOW_TO_HIGH));
        List<Listing> highToLow = browse.search(sorted(ListingSort.PRICE_HIGH_TO_LOW));

        assertEquals(List.of(secondLamp, lamp), lowToHigh.subList(0, 2));
        assertEquals(List.of(secondCalculator, calculator), lowToHigh.subList(6, 8));
        assertEquals(List.of(secondLamp, lamp, secondCalculator, calculator), highToLow.subList(4, 8));
    }

    @Test
    void keywordFiltersPriceRangeAndSort_workTogether() {
        List<Listing> results = browse.search(new BrowseQuery("a", null, ListingType.SELL, null, "50", "200",
                ListingSort.PRICE_HIGH_TO_LOW));

        assertEquals(List.of(book, chair, pen), results); // "a" is in all three titles; the charger costs too much
    }

    // ---------- A1: No Listings Found ----------

    @Test
    void nothingMatches_givesAnEmptyResult() {
        assertTrue(browse.search(keyword("unicorn")).isEmpty());
        assertTrue(browse.search(priced("1000", "")).isEmpty());
    }

    @Test
    void emptyMarketplace_givesAnEmptyResult() {
        BrowseService empty = new BrowseService(Fixtures.store(Fixtures.clock()));

        assertTrue(empty.search(BrowseQuery.ALL).isEmpty());
    }

    // ---------- A2: Invalid Price Range ----------

    @ParameterizedTest(name = "minimum \"{0}\", maximum \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "200  | 100",    // the minimum is greater than the maximum
            "0.02 | 0.01",
            "-1   | 100",    // negative
            "10   | -5",
            "-0   | 10",
            "abc  | 100",    // not a number
            "10   | cheap",
            "1e3  | ''",
            "12.  | ''",
            ".5   | ''",
            "$10  | ''",
            "1.234| ''",    // more than two decimals
            "1,000| ''",
            "''   | NaN"})
    void invalidPriceRange_isRefused(String min, String max) {
        ServiceException refused = assertThrows(ServiceException.class, () -> browse.search(priced(min, max)));

        assertEquals(BrowseService.INVALID_PRICE_RANGE, refused.getMessage());
    }

    @ParameterizedTest(name = "minimum \"{0}\", maximum \"{1}\"")
    @CsvSource(delimiter = '|', value = {"0 | 0", "' 60 ' | 60.00", "007 | 100", "'' | ''", "99999999 | ''"})
    void validPriceRange_isAccepted(String min, String max) {
        assertDoesNotThrow(() -> browse.search(priced(min, max)));
    }

    // ---------- Select Listing and Show Detail (steps 5-7; Special Requirement 2) ----------

    @Test
    void openingAListing_addsOneViewPerOpening_andReturnsWhatTheDetailScreenShows() {
        reviewOfSam("ben", 4, "Good");

        ListingDetail detail = browse.openListing(book.getId());

        assertSame(book, detail.listing());
        assertEquals(1, book.getViewCount());
        assertTrue(detail.available());
        assertEquals("Seller sam", detail.seller().fullName());
        assertEquals(4.0, detail.seller().rating().average());
        assertEquals(1, detail.seller().reviews().size());

        browse.openListing(book.getId());
        SessionManager.login(ben);
        browse.openListing(book.getId());
        assertEquals(3, book.getViewCount()); // postcondition: one per opening
        assertEquals(0, charger.getViewCount());
    }

    @Test
    void unavailableListing_canStillBeOpened_butIsNotAvailable() {
        ListingDetail detail = browse.openListing(chair.getId()); // Reserved

        assertFalse(detail.available()); // the action button is disabled
        assertEquals(1, chair.getViewCount());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "REJECTED", "REMOVED", "SOLD"})
    void listingThatIsNotLive_cannotBeOpened_andGetsNoView(ListingStatus status) {
        Listing hidden = Fixtures.moveTo(Fixtures.sale(store, "sam", 10), status);

        ServiceException refused = assertThrows(ServiceException.class, () -> browse.openListing(hidden.getId()));

        assertEquals("This listing is no longer on the marketplace.", refused.getMessage());
        assertEquals(0, hidden.getViewCount());
    }

    @Test
    void reloadingAnOpenListing_showsItsCurrentState_withoutCountingAView() {
        browse.openListing(lamp.getId());
        store.addOffer(new Claim(lamp.getId(), "ben")); // another buyer claimed it meanwhile

        ListingDetail reloaded = browse.reloadListing(lamp.getId());

        assertFalse(reloaded.available());
        assertEquals(1, lamp.getViewCount()); // still only the opening
        Listing hidden = Fixtures.moveTo(Fixtures.sale(store, "sam", 10), ListingStatus.REMOVED);
        assertThrows(ServiceException.class, () -> browse.reloadListing(hidden.getId()));
        assertThrows(ServiceException.class, () -> browse.reloadListing("L999"));
    }

    @Test
    void unknownListing_cannotBeOpened() {
        assertThrows(ServiceException.class, () -> browse.openListing("L999"));
        assertThrows(ServiceException.class, () -> browse.openListing(null));
    }

    @Test
    void buyerCanReopenAListingTheyHaveADealOn_whateverItsStatus_otherBuyersCannot() {
        // Special Requirement 2: the detail screen is also opened from My Deals
        Offer deal = store.addOffer(new PriceOffer(book.getId(), "bea", 150));
        deal.accept(store.now());
        book.acceptDeal();
        deal.complete(store.now());
        book.completeDeal(); // Sold: no longer live

        ListingDetail detail = browse.openListing(book.getId());

        assertFalse(detail.available());
        assertEquals(1, book.getViewCount());
        SessionManager.login(ben);
        assertThrows(ServiceException.class, () -> browse.openListing(book.getId()));
        assertEquals(1, book.getViewCount());
    }

    // ---------- A3: View Seller Profile; Special Requirement 5 ----------

    @Test
    void sellerProfile_showsNameBioAverageRating_andReviewsNewestFirstWithTheReply() {
        sam.setBio("Final year student");
        Review first = reviewOfSam("ben", 5, "Smooth deal");
        first.reply("Thank you!");
        Review second = reviewOfSam("bea", 2, null);

        SellerProfile profile = browse.getSellerProfile(" SAM ");

        assertEquals("sam", profile.username());
        assertEquals("Seller sam", profile.fullName());
        assertEquals("Final year student", profile.bio());
        assertEquals(3.5, profile.rating().average());
        assertEquals(2, profile.rating().count());
        assertEquals(List.of(second, first), profile.reviews()); // newest first
        assertEquals("Thank you!", profile.reviews().get(1).getSellerReply());
        assertNull(profile.reviews().get(0).getComment());
    }

    @Test
    void sellerWithNoReviews_hasNoAverageRating() {
        SellerProfile profile = browse.getSellerProfile("sue");

        assertTrue(profile.rating().isEmpty());
        assertEquals(0, profile.rating().count());
        assertTrue(profile.reviews().isEmpty());
        assertNull(profile.bio());
    }

    @Test
    void reviewHiddenByTheAdmin_isNeitherShownNorCounted() {
        Review kept = reviewOfSam("ben", 5, "Great");
        Review hidden = reviewOfSam("bea", 1, "Rude words");
        store.transaction(() -> hidden.setHidden(true));

        SellerProfile profile = browse.getSellerProfile("sam");

        assertEquals(List.of(kept), profile.reviews());
        assertEquals(5.0, profile.rating().average());
        assertEquals(1, profile.rating().count());
        assertEquals(List.of(kept), browse.openListing(book.getId()).seller().reviews()); // the same on the detail screen
    }

    @ParameterizedTest
    @ValueSource(strings = {"bea", "nobody", ""})
    void usernameThatIsNotASellers_hasNoProfile(String username) {
        ServiceException refused = assertThrows(ServiceException.class, () -> browse.getSellerProfile(username));

        assertEquals("This seller's profile is not available.", refused.getMessage());
    }

    @Test
    void noUsername_hasNoProfile() {
        assertThrows(ServiceException.class, () -> browse.getSellerProfile(null));
    }

    @Test
    void sellerWhoseAccountWasDeleted_isShownByUsername_withTheReviewsThatWereKept() {
        Review kept = reviewOfSam("ben", 4, "Good");
        Offer deal = store.addOffer(new PriceOffer(book.getId(), "bea", 150));
        deal.accept(store.now());
        book.acceptDeal();
        store.deleteAccount("sam"); // the listings and deals stay as records

        ListingDetail detail = browse.openListing(book.getId()); // Bea reopens her deal's listing

        assertEquals("sam", detail.seller().fullName());
        assertNull(detail.seller().bio());
        assertEquals(List.of(kept), detail.seller().reviews());
        assertEquals("sam", browse.getSellerProfile("sam").fullName());
    }

    // ---------- Precondition: the Buyer is logged in ----------

    @Test
    void onlyABuyerCanBrowse() {
        for (User other : List.of(sam, Fixtures.admin(store, "ada"))) {
            SessionManager.login(other);
            assertThrows(AccessDeniedException.class, () -> browse.search(BrowseQuery.ALL), other.getUsername());
            assertThrows(AccessDeniedException.class, () -> browse.openListing(book.getId()), other.getUsername());
            assertThrows(AccessDeniedException.class, () -> browse.reloadListing(book.getId()), other.getUsername());
            assertThrows(AccessDeniedException.class, () -> browse.getSellerProfile("sam"), other.getUsername());
        }
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> browse.search(BrowseQuery.ALL));
        assertThrows(AccessDeniedException.class, () -> browse.openListing(book.getId()));
        assertEquals(0, book.getViewCount());
    }

    @Test
    void deactivatedBuyer_cannotBrowse() {
        store.transaction(() -> bea.setActive(false));

        assertThrows(AccessDeniedException.class, () -> browse.search(BrowseQuery.ALL));
    }

    // ---------- The query ----------

    @Test
    void query_storesNothingTypedAsBlank_andKnowsWhetherAPriceRangeIsSet() {
        BrowseQuery nothing = new BrowseQuery(null, null, null, null, null, null, null);
        assertEquals("", nothing.keyword());
        assertEquals(ListingSort.NEWEST, nothing.sort());
        assertFalse(nothing.hasPriceRange());
        assertFalse(BrowseQuery.ALL.hasPriceRange());

        assertTrue(priced("10", "").hasPriceRange());
        assertTrue(priced("", "10").hasPriceRange());
        assertFalse(priced("  ", " ").hasPriceRange());
        assertEquals("Price: low to high", ListingSort.PRICE_LOW_TO_HIGH.toString());
    }
}
