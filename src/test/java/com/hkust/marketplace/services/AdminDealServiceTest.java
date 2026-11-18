package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.AdminTestSupport;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for Task 3.5 All Deals Record: list with filters (A1 no results), and CSV export. */
class AdminDealServiceTest {

    private static final LocalDateTime DAY0 = Fixtures.START;

    private AdminTestSupport support;
    private AdminDealService service;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();             // admin ada, seller sam, buyer bea
        Fixtures.seller(support.store, "sue");
        Fixtures.buyer(support.store, "ben");
        service = new AdminDealService(support.store);
        support.loginAsAdmin();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    // ---------- helpers ----------

    /** Same offers regardless of order, each exactly once. */
    private static void assertSameDeals(List<Offer> actual, Offer... expected) {
        assertEquals(expected.length, actual.size(), "number of deals");
        assertEquals(Set.of(expected), Set.copyOf(actual));
    }

    /** A pending PriceOffer on a live sale listing. */
    private Offer pendingOffer(String seller, String buyer, double price) {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, seller, price));
        return support.store.addOffer(new PriceOffer(listing.getId(), buyer, price));
    }

    /** A completed (sold) PriceOffer. Returns the offer after completion. */
    private Offer completedSale(String seller, String buyer, double price) {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, seller, price));
        Offer offer = support.store.addOffer(new PriceOffer(listing.getId(), buyer, price));
        offer.accept(support.store.now());
        listing.acceptDeal();
        offer.complete(support.store.now());
        listing.completeDeal();
        return offer;
    }

    /** A completed (given) Claim. */
    private Offer completedClaim(String seller, String buyer) {
        GiveawayListing listing = Fixtures.live(Fixtures.giveaway(support.store, seller));
        Offer claim = support.store.addOffer(new Claim(listing.getId(), buyer));
        claim.accept(support.store.now());
        listing.acceptDeal();
        claim.complete(support.store.now());
        listing.completeDeal();
        return claim;
    }

    /** An accepted BorrowRequest (not yet returned). */
    private BorrowRequest acceptedBorrow(String seller, String buyer) {
        LendListing listing = Fixtures.live(Fixtures.lend(support.store, seller, Duration.ofDays(7)));
        BorrowRequest request = (BorrowRequest) support.store.addOffer(
                new BorrowRequest(listing.getId(), buyer, support.store.now(),
                        support.store.now().plusDays(7)));
        request.accept(support.store.now());
        listing.acceptDeal();
        return request;
    }

    /** A declined PriceOffer. */
    private Offer declinedOffer(String seller, String buyer) {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, seller, 100));
        Offer offer = support.store.addOffer(new PriceOffer(listing.getId(), buyer, 90));
        offer.decline();
        return offer;
    }

    // ---------- Task 3.5: list without filters ----------

    @Test
    void task3_5_noFilter_returnsEveryOfferOfEveryTypeAndStatus() {
        Offer sold = completedSale("sam", "bea", 100);
        Offer given = completedClaim("sue", "bea");
        Offer borrowed = acceptedBorrow("sam", "bea");
        Offer pending = pendingOffer("sue", "ben", 50);
        Offer declined = declinedOffer("sam", "bea");

        assertSameDeals(service.findDeals(DealFilter.none()), sold, given, borrowed, pending, declined);
    }

    @Test
    void task3_5_nullFilterIsTheSameAsNoFilter() {
        Offer offer = completedSale("sam", "bea", 100);

        assertSameDeals(service.findDeals(null), offer);
    }

    // ---------- Task 3.5: filters ----------

    @Test
    void task3_5_statusFilter_returnsOnlyMatchingStatus() {
        Offer sold = completedSale("sam", "bea", 100);
        Offer pending = pendingOffer("sam", "bea", 80);
        Offer declined = declinedOffer("sue", "ben");

        assertSameDeals(service.findDeals(DealFilter.none().withStatus(OfferStatus.PENDING)), pending);
        assertSameDeals(service.findDeals(DealFilter.none().withStatus(OfferStatus.DECLINED)), declined);
        // A completed PriceOffer has no special status — still PENDING internally before accept, then moves to ACCEPTED
        // After complete() the offer has a completedDate but getStatus() stays ACCEPTED
        assertSameDeals(service.findDeals(DealFilter.none().withStatus(OfferStatus.ACCEPTED)), sold);
        assertTrue(service.findDeals(DealFilter.none().withStatus(OfferStatus.COUNTERED)).isEmpty());
    }

    @Test
    void task3_5_typeFilter_returnsOnlyMatchingListingType() {
        Offer sellOffer = pendingOffer("sam", "bea", 100);
        Offer lendRequest = acceptedBorrow("sue", "ben");
        Offer claimOffer = completedClaim("sam", "bea");

        assertSameDeals(service.findDeals(DealFilter.none().withType(ListingType.SELL)), sellOffer);
        assertSameDeals(service.findDeals(DealFilter.none().withType(ListingType.LEND)), lendRequest);
        assertSameDeals(service.findDeals(DealFilter.none().withType(ListingType.GIVEAWAY)), claimOffer);
    }

    @Test
    void task3_5_sellerFilter_caseInsensitiveUsernameMatch() {
        Offer samsDeal = pendingOffer("sam", "bea", 100);
        Offer suesDeal = pendingOffer("sue", "bea", 50);

        assertSameDeals(service.findDeals(DealFilter.none().withSeller("SAM")), samsDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withSeller("sue")), suesDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withSeller("")), samsDeal, suesDeal);
        assertTrue(service.findDeals(DealFilter.none().withSeller("nobody")).isEmpty());
    }

    @Test
    void task3_5_buyerFilter_caseInsensitiveUsernameMatch() {
        Offer beaDeal = pendingOffer("sam", "bea", 100);
        Offer benDeal = pendingOffer("sam", "ben", 80);

        assertSameDeals(service.findDeals(DealFilter.none().withBuyer("BEA")), beaDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withBuyer("ben")), benDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withBuyer("")), beaDeal, benDeal);
        assertTrue(service.findDeals(DealFilter.none().withBuyer("nobody")).isEmpty());
    }

    @Test
    void task3_5_dateRange_filterOnOfferDateInclusiveOnBothEnds() {
        Offer first = pendingOffer("sam", "bea", 100);           // offered at DAY0
        support.clock.advance(Duration.ofDays(1));
        Offer second = pendingOffer("sam", "bea", 80);           // offered at DAY0 + 1
        support.clock.advance(Duration.ofDays(1));
        Offer third = pendingOffer("sam", "bea", 60);            // offered at DAY0 + 2
        LocalDateTime day1 = DAY0.plusDays(1);
        LocalDateTime day2 = DAY0.plusDays(2);

        assertSameDeals(service.findDeals(DealFilter.none().withDateRange(day1, day1)), second);
        assertSameDeals(service.findDeals(DealFilter.none().withDateRange(day1, day2)), second, third);
        assertSameDeals(service.findDeals(DealFilter.none().withDateRange(null, DAY0)), first);
        assertSameDeals(service.findDeals(DealFilter.none().withDateRange(day2, null)), third);
        assertTrue(service.findDeals(DealFilter.none()
                .withDateRange(DAY0.plusDays(5), DAY0.plusDays(6))).isEmpty());
    }

    @Test
    void task3_5_allFiltersCombine_mustAllMatch() {
        Offer match = pendingOffer("sam", "bea", 100);           // sam, bea, SELL, DAY0
        pendingOffer("sue", "bea", 80);                          // wrong seller
        pendingOffer("sam", "ben", 60);                          // wrong buyer
        support.clock.advance(Duration.ofDays(2));
        pendingOffer("sam", "bea", 50);                          // too late

        DealFilter filter = DealFilter.none()
                .withSeller("sam").withBuyer("bea")
                .withType(ListingType.SELL).withDateRange(DAY0, DAY0.plusDays(1));

        assertSameDeals(service.findDeals(filter), match);
    }

    // ---------- Task 3.5 A1: no results ----------

    @Test
    void task3_5_A1_noMatch_returnsEmptyList_notAnError() {
        assertTrue(service.findDeals(DealFilter.none()).isEmpty());   // nothing stored at all

        pendingOffer("sam", "bea", 100);
        assertTrue(service.findDeals(DealFilter.none().withStatus(OfferStatus.ACCEPTED)).isEmpty());
        assertTrue(service.findDeals(DealFilter.none().withBuyer("nobody")).isEmpty());
    }

    // ---------- Task 3.5: CSV export ----------

    @Test
    void task3_5_exportCsv_correctHeadersAndRows() {
        Offer sold = completedSale("sam", "bea", 100);

        List<Offer> deals = service.findDeals(DealFilter.none());
        String csv = service.exportCsv(deals);
        String[] lines = csv.split("\n");

        assertEquals("Deal ID,Item Title,Buyer,Seller,Type,Start Date,Complete Date,Status", lines[0]);
        assertEquals(2, lines.length);                           // header + 1 row
        assertTrue(lines[1].contains(sold.getId()));
        assertTrue(lines[1].contains("bea"));
        assertTrue(lines[1].contains("sam"));
        assertTrue(lines[1].contains("Sell"));
        assertTrue(lines[1].contains("Accepted"));
    }

    @Test
    void task3_5_exportCsv_titleWithCommaIsQuoted() {
        SaleListing tricky = Fixtures.live(Fixtures.sale(support.store, "sam", 50));
        support.store.transaction(() -> tricky.setTitle("Bag, used"));
        Offer offer = support.store.addOffer(new PriceOffer(tricky.getId(), "bea", 50));

        String csv = service.exportCsv(List.of(offer));
        String dataRow = csv.split("\n")[1];

        assertTrue(dataRow.contains("\"Bag, used\""));
    }

    @Test
    void task3_5_exportCsv_emptyList_returnsHeaderOnly() {
        String csv = service.exportCsv(List.of());
        String[] lines = csv.split("\n");

        assertEquals(1, lines.length);
        assertEquals("Deal ID,Item Title,Buyer,Seller,Type,Start Date,Complete Date,Status", lines[0]);
    }

    // ---------- Access control (UC2 Special Requirement 3) ----------

    @Test
    void task3_5_noSession_findAndExportAreDenied() {
        Offer offer = pendingOffer("sam", "bea", 100);
        support.logout();

        assertThrows(AccessDeniedException.class, () -> service.findDeals(DealFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.exportCsv(List.of(offer)));
    }

    @Test
    void task3_5_buyerSession_findAndExportAreDenied() {
        Offer offer = pendingOffer("sam", "bea", 100);
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.findDeals(DealFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.exportCsv(List.of(offer)));
    }

    @Test
    void task3_5_sellerSession_findAndExportAreDenied() {
        Offer offer = pendingOffer("sam", "bea", 100);
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.findDeals(DealFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.exportCsv(List.of(offer)));
    }
}