package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.AdminTestSupport;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for Manage Marketplace Listings steps 2-4 (list and filters), 5.1 (view) and A2 (no results). */
class AdminListingQueryTest {

    private AdminTestSupport support;
    private AdminListingService service;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();               // admin ada, seller sam, buyer bea
        Fixtures.seller(support.store, "sue");
        Fixtures.buyer(support.store, "ben");
        service = new AdminListingService(support.store);
        support.loginAsAdmin();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    /** Same listings, in any order, each exactly once. */
    private static void assertSameListings(List<Listing> actual, Listing... expected) {
        assertEquals(expected.length, actual.size(), "number of listings");
        assertEquals(Set.of(expected), Set.copyOf(actual));
    }

    private void report(Listing listing, String buyer, ReportReason reason) {
        assertTrue(support.store.addReport(new Report(listing.getId(), buyer, reason, "details")));
    }

    private SaleListing sale(String seller) {
        return Fixtures.sale(support.store, seller, 100);
    }

    // ---------- Steps 2-4: list and filters ----------

    @Test
    void manageMarketplaceListings_step2_noFilter_listsEveryListingOfEveryStatus() {
        SaleListing pending = support.pendingSale();
        SaleListing live = support.liveSale();
        SaleListing removed = Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.REMOVED);
        SaleListing sold = Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.SOLD);
        LendListing lend = Fixtures.lend(support.store, "sam", Duration.ofDays(7));
        GiveawayListing lamp = Fixtures.giveaway(support.store, "sue");

        assertSameListings(service.findListings(ListingFilter.none()), pending, live, removed, sold, lend, lamp);
    }

    @Test
    void manageMarketplaceListings_step2_nullFilterIsTheSameAsNoFilter() {
        SaleListing pending = support.pendingSale();
        SaleListing live = support.liveSale();

        assertSameListings(service.findListings(null), pending, live);
    }

    @Test
    void manageMarketplaceListings_step3_statusFilter_showsOnlyThatStatus() {
        SaleListing pending = support.pendingSale();
        SaleListing live = support.liveSale();
        SaleListing removed = Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.REMOVED);

        assertSameListings(service.findListings(ListingFilter.none().withStatus(ListingStatus.PENDING_REVIEW)), pending);
        assertSameListings(service.findListings(ListingFilter.none().withStatus(ListingStatus.LIVE)), live);
        assertSameListings(service.findListings(ListingFilter.none().withStatus(ListingStatus.REMOVED)), removed);
        assertTrue(service.findListings(ListingFilter.none().withStatus(ListingStatus.SOLD)).isEmpty());
    }

    @Test
    void manageMarketplaceListings_step3_categoryAndTypeFilters() {
        SaleListing book = support.pendingSale();                                   // Textbooks, Sell
        LendListing calculator = Fixtures.lend(support.store, "sam", Duration.ofDays(7)); // Electronics, Lend
        GiveawayListing lamp = Fixtures.giveaway(support.store, "sam");             // Furniture, Giveaway

        assertSameListings(service.findListings(ListingFilter.none().withCategory(Category.ELECTRONICS)), calculator);
        assertSameListings(service.findListings(ListingFilter.none().withCategory(Category.FURNITURE)), lamp);
        assertSameListings(service.findListings(ListingFilter.none().withType(ListingType.SELL)), book);
        assertSameListings(service.findListings(ListingFilter.none().withType(ListingType.LEND)), calculator);
        assertSameListings(service.findListings(ListingFilter.none().withType(ListingType.GIVEAWAY)), lamp);
    }

    @Test
    void manageMarketplaceListings_step3_sellerFilter_matchesUsernameOrFullName_ignoringCase() {
        SaleListing sams = sale("sam");
        SaleListing sues = sale("sue");

        assertSameListings(service.findListings(ListingFilter.none().withSeller("SAM")), sams);       // username
        assertSameListings(service.findListings(ListingFilter.none().withSeller(" seller sue ")), sues); // full name
        assertSameListings(service.findListings(ListingFilter.none().withSeller("")), sams, sues);     // blank = all
        assertTrue(service.findListings(ListingFilter.none().withSeller("nobody")).isEmpty());
    }

    @Test
    void manageMarketplaceListings_step3_combinedFilters_mustAllMatch() {
        SaleListing liveBook = support.liveSale();                                   // sam, Textbooks, Live
        support.pendingSale();                                                       // sam, Textbooks, Pending
        LendListing liveCalc = Fixtures.live(Fixtures.lend(support.store, "sam", Duration.ofDays(7)));

        ListingFilter filter = ListingFilter.none()
                .withStatus(ListingStatus.LIVE).withCategory(Category.TEXTBOOKS).withSeller("sam");

        assertSameListings(service.findListings(filter), liveBook);
        assertSameListings(service.findListings(ListingFilter.none().withStatus(ListingStatus.LIVE)), liveBook, liveCalc);
    }

    // ---------- Reported filter ----------

    @Test
    void manageMarketplaceListings_step3_reportedFilter_listsEachReportedListingOnce() {
        SaleListing reported = support.liveSale();
        SaleListing clean = support.liveSale();
        report(reported, "bea", ReportReason.SCAM_OR_FRAUD);
        report(reported, "ben", ReportReason.OTHER);        // two reports, one listing

        List<Listing> result = service.findListings(ListingFilter.none().withReportedOnly(true));

        assertSameListings(result, reported);
        assertFalse(result.contains(clean));
    }

    @Test
    void manageMarketplaceListings_step3_reportedFilter_includesEveryStatus() {
        SaleListing pending = support.pendingSale();
        SaleListing removed = Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.REMOVED);
        SaleListing sold = Fixtures.moveTo(Fixtures.sale(support.store, "sam", 100), ListingStatus.SOLD);
        support.liveSale();                                  // not reported
        report(pending, "bea", ReportReason.OTHER);
        report(removed, "bea", ReportReason.OFFENSIVE_CONTENT);
        report(sold, "bea", ReportReason.SPAM_OR_DUPLICATE);

        assertSameListings(service.findListings(ListingFilter.none().withReportedOnly(true)), pending, removed, sold);
    }

    @Test
    void manageMarketplaceListings_step3_reportedFilter_combinesWithOtherFilters() {
        SaleListing reportedLive = support.liveSale();
        SaleListing reportedPending = support.pendingSale();
        SaleListing unreportedLive = support.liveSale();
        LendListing reportedLend = Fixtures.live(Fixtures.lend(support.store, "sue", Duration.ofDays(7)));
        report(reportedLive, "bea", ReportReason.OTHER);
        report(reportedPending, "bea", ReportReason.OTHER);
        report(reportedLend, "ben", ReportReason.OTHER);

        // reported AND live
        assertSameListings(service.findListings(
                        ListingFilter.none().withReportedOnly(true).withStatus(ListingStatus.LIVE)),
                reportedLive, reportedLend);
        // reported AND live AND seller sue
        assertSameListings(service.findListings(
                        ListingFilter.none().withReportedOnly(true).withStatus(ListingStatus.LIVE).withSeller("sue")),
                reportedLend);
        // not reported-only: the unreported listing is back
        assertTrue(service.findListings(ListingFilter.none().withStatus(ListingStatus.LIVE))
                .contains(unreportedLive));
    }

    // ---------- Date range ----------

    @Test
    void manageMarketplaceListings_step3_dateRange_isInclusiveOnBothEnds() {
        LocalDate day0 = Fixtures.START.toLocalDate();
        SaleListing first = sale("sam");                       // posted day 0
        support.clock.advance(Duration.ofDays(1));
        SaleListing second = sale("sam");                      // posted day 1
        support.clock.advance(Duration.ofDays(1));
        SaleListing third = sale("sam");                       // posted day 2

        assertSameListings(service.findListings(
                ListingFilter.none().withDateRange(day0.plusDays(1), day0.plusDays(1))), second);
        assertSameListings(service.findListings(
                ListingFilter.none().withDateRange(day0, day0.plusDays(1))), first, second);
        assertSameListings(service.findListings(
                ListingFilter.none().withDateRange(day0.plusDays(1), null)), second, third);
        assertSameListings(service.findListings(
                ListingFilter.none().withDateRange(null, day0)), first);
        assertTrue(service.findListings(
                ListingFilter.none().withDateRange(day0.plusDays(3), day0.plusDays(4))).isEmpty());
    }

    @Test
    void manageMarketplaceListings_A2_invalidDateRange_isRefused() {
        LocalDate day0 = Fixtures.START.toLocalDate();
        sale("sam");

        ServiceException e = assertThrows(ServiceException.class, () -> service.findListings(
                ListingFilter.none().withDateRange(day0.plusDays(2), day0)));

        assertEquals("The date range is invalid.", e.getMessage());
    }

    // ---------- Order and no results ----------

    @Test
    void manageMarketplaceListings_step2_listsNewestPostedFirst() {
        SaleListing oldest = sale("sam");
        support.clock.advance(Duration.ofHours(1));
        SaleListing middle = sale("sam");
        support.clock.advance(Duration.ofHours(1));
        SaleListing newest = sale("sam");

        assertEquals(List.of(newest, middle, oldest), service.findListings(ListingFilter.none()));
    }

    @Test
    void manageMarketplaceListings_A2_noMatch_returnsEmptyList_notAnError() {
        assertTrue(service.findListings(ListingFilter.none()).isEmpty());   // nothing stored at all

        support.pendingSale();
        assertTrue(service.findListings(ListingFilter.none().withStatus(ListingStatus.GIVEN)).isEmpty());
        assertTrue(service.findListings(ListingFilter.none().withReportedOnly(true)).isEmpty());
    }

    // ---------- 5.1 View ----------

    @Test
    void manageMarketplaceListings_5_1_view_showsListingSellerNameAndReports() {
        SaleListing book = support.liveSale();
        report(book, "bea", ReportReason.SCAM_OR_FRAUD);
        report(book, "ben", ReportReason.MISLEADING_DESCRIPTION);
        SaleListing other = support.liveSale();
        report(other, "bea", ReportReason.OTHER);

        ListingDetails details = service.getListingDetails(book.getId());

        assertSame(book, details.listing());
        assertEquals("Seller sam", details.sellerFullName());
        assertEquals(2, details.reports().size());
        assertTrue(details.reports().stream().allMatch(r -> r.getListingId().equals(book.getId())));
        assertEquals(Set.of("bea", "ben"),
                Set.copyOf(details.reports().stream().map(Report::getBuyerUsername).toList()));
    }

    @Test
    void manageMarketplaceListings_3_7_view_listingWithoutReports_hasAnEmptyReportList() {
        SaleListing book = support.pendingSale();

        ListingDetails details = service.getListingDetails(book.getId());

        assertTrue(details.reports().isEmpty());
    }

    @Test
    void manageMarketplaceListings_3_7_view_ofDeletedSeller_showsTheUsername() {
        SaleListing book = support.liveSale();
        support.store.deleteAccount("sam");   // listing stays as archive

        ListingDetails details = service.getListingDetails(book.getId());

        assertEquals("sam", details.sellerFullName());
    }

    @Test
    void manageMarketplaceListings_3_7_view_unknownListing_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.getListingDetails("L999"));

        assertEquals("This listing no longer exists.", e.getMessage());
    }

    // ---------- UC2 Special Requirement 3: access denied ----------

    @Test
    void manageMarketplaceListings_UC2_SR3_noSession_listAndViewAreDenied() {
        SaleListing book = support.liveSale();
        support.logout();

        assertThrows(AccessDeniedException.class, () -> service.findListings(ListingFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.getListingDetails(book.getId()));
    }

    @Test
    void manageMarketplaceListings_UC2_SR3_buyerSession_listAndViewAreDenied() {
        SaleListing book = support.liveSale();
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.findListings(ListingFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.getListingDetails(book.getId()));
    }

    @Test
    void manageMarketplaceListings_UC2_SR3_sellerSession_listAndViewAreDenied() {
        SaleListing book = support.liveSale();
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.findListings(ListingFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.getListingDetails(book.getId()));
    }
}