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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Task 3.5 All Deals Record: the list of deals (accepted offers only) with filters,
 * VIEW DETAILS, the CSV export and the "No deals found" case (A1).
 */
class AdminDealServiceTest {

    private static final DateTimeFormatter CSV_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH);

    private static final LocalDateTime DAY0 = Fixtures.START;
    private static final String CSV_HEADER =
            "Item Title,Buyer,Seller,Deal Type,Start Date,Return/Complete Date,Status";

    private AdminTestSupport support;
    private AdminDealService service;
    private DealRules rules;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();             // admin ada, seller sam, buyer bea
        Fixtures.seller(support.store, "sue");
        Fixtures.buyer(support.store, "ben");
        service = new AdminDealService(support.store);
        rules = new DealRules(support.store);
        support.loginAsAdmin();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    // ---------- helpers ----------

    /** The records' offers must be exactly the expected offers, in any order, each once. */
    private static void assertSameDeals(List<DealRecord> actual, Offer... expected) {
        assertEquals(expected.length, actual.size(), "number of deals");
        assertEquals(Set.of(expected), Set.copyOf(actual.stream().map(DealRecord::offer).toList()));
    }

    private static DealRecord recordOf(List<DealRecord> records, Offer offer) {
        return records.stream().filter(r -> r.offer() == offer).findFirst().orElseThrow();
    }

    /** A pending price offer on a live sale listing (not a deal). */
    private PriceOffer pendingOffer(String seller, String buyer, double price) {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, seller, price));
        return (PriceOffer) support.store.addOffer(new PriceOffer(listing.getId(), buyer, price));
    }

    /** An accepted sale (in progress): accepted at the store's current time. */
    private Offer acceptedSale(String seller, String buyer, double price) {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, seller, price));
        Offer offer = support.store.addOffer(new PriceOffer(listing.getId(), buyer, price));
        offer.accept(support.store.now());
        listing.acceptDeal();
        return offer;
    }

    /** Marks an accepted sale or claim complete, as the buyer would (UC7). */
    private void complete(Offer deal) {
        rules.complete(deal);
    }

    /** An accepted giveaway claim (in progress). */
    private Offer acceptedClaim(String seller, String buyer) {
        GiveawayListing listing = Fixtures.live(Fixtures.giveaway(support.store, seller));
        Offer claim = support.store.addOffer(new Claim(listing.getId(), buyer));
        claim.accept(support.store.now());
        listing.acceptDeal();
        return claim;
    }

    /** An accepted borrow request (in progress) that starts one day from now and lasts 7 days. */
    private BorrowRequest acceptedBorrow(String seller, String buyer) {
        LendListing listing = Fixtures.live(Fixtures.lend(support.store, seller, Duration.ofDays(14)));
        LocalDateTime start = support.store.now().plusDays(1);
        BorrowRequest request = (BorrowRequest) support.store.addOffer(
                new BorrowRequest(listing.getId(), buyer, start, start.plusDays(7)));
        request.accept(support.store.now());
        listing.acceptDeal();
        return request;
    }

    private Offer declinedOffer(String seller, String buyer) {
        PriceOffer offer = pendingOffer(seller, buyer, 90);
        offer.decline();
        return offer;
    }

    private Offer counteredOffer(String seller, String buyer) {
        PriceOffer offer = pendingOffer(seller, buyer, 90);
        offer.counter(120);
        return offer;
    }

    // ---------- Task 3.5: only accepted offers are deals ----------

    @Test
    void task3_5_noFilter_listsEveryAcceptedDeal_inProgressOrCompleted() {
        Offer inProgressSale = acceptedSale("sam", "bea", 100);
        Offer completedSale = acceptedSale("sam", "ben", 80);
        complete(completedSale);
        Offer claim = acceptedClaim("sue", "bea");
        BorrowRequest loan = acceptedBorrow("sue", "ben");

        assertSameDeals(service.findDeals(DealFilter.none()), inProgressSale, completedSale, claim, loan);
    }

    @Test
    void task3_5_nullFilterIsTheSameAsNoFilter() {
        Offer deal = acceptedSale("sam", "bea", 100);

        assertSameDeals(service.findDeals(null), deal);
    }

    @Test
    void task3_5_pendingCounteredAndDeclinedOffers_areNotListed() {
        pendingOffer("sam", "bea", 100);
        counteredOffer("sam", "ben");
        declinedOffer("sue", "bea");
        Offer deal = acceptedSale("sue", "ben", 60);

        assertSameDeals(service.findDeals(DealFilter.none()), deal);
    }

    @Test
    void task3_5_onlyNonDealOffers_returnsAnEmptyList() {
        pendingOffer("sam", "bea", 100);
        counteredOffer("sam", "ben");
        declinedOffer("sue", "bea");

        assertTrue(service.findDeals(DealFilter.none()).isEmpty());
    }

    @Test
    void task3_5_otherOpenOffersDeclinedByAnAcceptance_areNotListed() {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, "sam", 100));
        Offer first = support.store.addOffer(new PriceOffer(listing.getId(), "bea", 90));
        Offer second = support.store.addOffer(new PriceOffer(listing.getId(), "ben", 95));
        rules.accept(first);                                    // declines the second one

        assertEquals(OfferStatus.DECLINED, second.getStatus());
        assertSameDeals(service.findDeals(DealFilter.none()), first);
    }

    // ---------- Task 3.5: start date (client answer #4) ----------

    @Test
    void task3_5_saleDeal_startDateIsTheAcceptedDate_notTheOfferDate() {
        SaleListing listing = Fixtures.live(Fixtures.sale(support.store, "sam", 100));
        Offer offer = support.store.addOffer(new PriceOffer(listing.getId(), "bea", 90));
        support.clock.advance(Duration.ofDays(2));
        offer.accept(support.store.now());
        listing.acceptDeal();

        DealRecord record = service.findDeals(DealFilter.none()).get(0);

        assertEquals(offer.getAcceptedDate(), record.startDate());
        assertEquals(DAY0.plusDays(2), record.startDate());
        assertNotEquals(offer.getOfferDate(), record.startDate());
    }

    @Test
    void task3_5_giveawayDeal_startDateIsTheAcceptedDate() {
        GiveawayListing listing = Fixtures.live(Fixtures.giveaway(support.store, "sam"));
        Offer claim = support.store.addOffer(new Claim(listing.getId(), "bea"));
        support.clock.advance(Duration.ofHours(5));
        claim.accept(support.store.now());
        listing.acceptDeal();

        DealRecord record = service.findDeals(DealFilter.none()).get(0);

        assertEquals(claim.getAcceptedDate(), record.startDate());
        assertEquals(DAY0.plusHours(5), record.startDate());
        assertEquals(ListingType.GIVEAWAY, record.dealType());
    }

    @Test
    void task3_5_loanDeal_startDateIsTheProposedStartOfTheAcceptedRequest() {
        BorrowRequest loan = acceptedBorrow("sam", "bea");

        DealRecord record = service.findDeals(DealFilter.none()).get(0);

        assertEquals(loan.getProposedStartDate(), record.startDate());
        assertEquals(DAY0.plusDays(1), record.startDate());
        assertNotEquals(loan.getAcceptedDate(), record.startDate());
        assertEquals(ListingType.LEND, record.dealType());
    }

    // ---------- Task 3.5: row contents and state ----------

    @Test
    void task3_5_record_carriesTheDisplayedFields() {
        Offer deal = acceptedSale("sam", "bea", 100);

        DealRecord record = service.findDeals(DealFilter.none()).get(0);

        assertSame(deal, record.offer());
        assertEquals("Book", record.listing().getTitle());
        assertEquals("bea", record.buyerUsername());
        assertEquals("sam", record.sellerUsername());
        assertEquals(ListingType.SELL, record.dealType());
        assertNull(record.completedDate());
    }

    @Test
    void task3_5_saleAndClaim_areInProgressUntilCompleted_thenCompleted() {
        Offer sale = acceptedSale("sam", "bea", 100);
        Offer claim = acceptedClaim("sue", "ben");

        List<DealRecord> before = service.findDeals(DealFilter.none());
        assertEquals(DealState.IN_PROGRESS, recordOf(before, sale).state());
        assertEquals(DealState.IN_PROGRESS, recordOf(before, claim).state());
        assertNull(recordOf(before, sale).completedDate());

        support.clock.advance(Duration.ofDays(1));
        complete(sale);
        complete(claim);

        List<DealRecord> after = service.findDeals(DealFilter.none());
        assertEquals(DealState.COMPLETED, recordOf(after, sale).state());
        assertEquals(DealState.COMPLETED, recordOf(after, claim).state());
        assertEquals(DAY0.plusDays(1), recordOf(after, sale).completedDate());
    }

    @Test
    void task3_5_loan_isInProgressUntilReturned_thenReturned() {
        BorrowRequest loan = acceptedBorrow("sam", "bea");
        assertEquals(DealState.IN_PROGRESS, service.findDeals(DealFilter.none()).get(0).state());

        support.clock.advance(Duration.ofDays(2));
        rules.returnItem(loan, false);                           // the buyer returns it

        DealRecord record = service.findDeals(DealFilter.none()).get(0);
        assertEquals(DealState.RETURNED, record.state());
        assertEquals(DAY0.plusDays(2), record.completedDate());
    }

    @Test
    void task3_5_autoReturnedLoan_alsoShowsReturned() {
        BorrowRequest loan = acceptedBorrow("sam", "bea");
        support.clock.advance(Duration.ofDays(9));
        rules.returnItem(loan, true);                            // the timer returns it

        DealRecord record = service.findDeals(DealFilter.none()).get(0);

        assertTrue(loan.isAutoReturned());
        assertEquals(DealState.RETURNED, record.state());
        assertEquals(loan.getDueDate(), record.completedDate());
    }

    // ---------- Task 3.5: filters ----------

    @Test
    void task3_5_statusFilter_inProgressCompletedReturned() {
        Offer inProgress = acceptedSale("sam", "bea", 100);
        Offer completed = acceptedSale("sam", "ben", 80);
        complete(completed);
        BorrowRequest returned = acceptedBorrow("sue", "bea");
        rules.returnItem(returned, false);
        BorrowRequest activeLoan = acceptedBorrow("sue", "ben");

        assertSameDeals(service.findDeals(DealFilter.none().withStatus(DealState.IN_PROGRESS)),
                inProgress, activeLoan);
        assertSameDeals(service.findDeals(DealFilter.none().withStatus(DealState.COMPLETED)), completed);
        assertSameDeals(service.findDeals(DealFilter.none().withStatus(DealState.RETURNED)), returned);
    }

    @Test
    void task3_5_typeFilter_sellLendGiveaway() {
        Offer sale = acceptedSale("sam", "bea", 100);
        BorrowRequest loan = acceptedBorrow("sue", "ben");
        Offer claim = acceptedClaim("sam", "bea");

        assertSameDeals(service.findDeals(DealFilter.none().withType(ListingType.SELL)), sale);
        assertSameDeals(service.findDeals(DealFilter.none().withType(ListingType.LEND)), loan);
        assertSameDeals(service.findDeals(DealFilter.none().withType(ListingType.GIVEAWAY)), claim);
    }

    @Test
    void task3_5_sellerFilter_matchesPartOfTheUsername_ignoringCase() {
        Offer samsDeal = acceptedSale("sam", "bea", 100);
        Offer suesDeal = acceptedSale("sue", "bea", 50);

        assertSameDeals(service.findDeals(DealFilter.none().withSeller("SAM")), samsDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withSeller("su")), suesDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withSeller("s")), samsDeal, suesDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withSeller("  ")), samsDeal, suesDeal);
        assertTrue(service.findDeals(DealFilter.none().withSeller("nobody")).isEmpty());
    }

    @Test
    void task3_5_buyerFilter_matchesPartOfTheUsername_ignoringCase() {
        Offer beaDeal = acceptedSale("sam", "bea", 100);
        Offer benDeal = acceptedSale("sam", "ben", 80);

        assertSameDeals(service.findDeals(DealFilter.none().withBuyer("BEA")), beaDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withBuyer("be")), beaDeal, benDeal);
        assertSameDeals(service.findDeals(DealFilter.none().withBuyer("")), beaDeal, benDeal);
        assertTrue(service.findDeals(DealFilter.none().withBuyer("nobody")).isEmpty());
    }

    @Test
    void task3_5_dateRange_appliesToTheStartDate_inclusiveOnBothEnds() {
        Offer first = acceptedSale("sam", "bea", 100);           // start DAY0
        support.clock.advance(Duration.ofDays(1));
        Offer second = acceptedSale("sam", "bea", 80);           // start DAY0 + 1
        support.clock.advance(Duration.ofDays(1));
        Offer third = acceptedSale("sam", "bea", 60);            // start DAY0 + 2
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
    void task3_5_dateRange_justOutsideTheBoundaryIsExcluded() {
        Offer deal = acceptedSale("sam", "bea", 100);            // start DAY0

        assertTrue(service.findDeals(DealFilter.none()
                .withDateRange(DAY0.plusNanos(1), DAY0.plusDays(1))).isEmpty());
        assertTrue(service.findDeals(DealFilter.none()
                .withDateRange(DAY0.minusDays(1), DAY0.minusNanos(1))).isEmpty());
        assertSameDeals(service.findDeals(DealFilter.none().withDateRange(DAY0, DAY0)), deal);
    }

    @Test
    void task3_5_dateRange_usesTheLoansAgreedStart_notTheDayItWasAccepted() {
        BorrowRequest loan = acceptedBorrow("sam", "bea");       // accepted DAY0, starts DAY0 + 1

        assertTrue(service.findDeals(DealFilter.none().withDateRange(DAY0, DAY0)).isEmpty());
        assertSameDeals(service.findDeals(DealFilter.none()
                .withDateRange(DAY0.plusDays(1), DAY0.plusDays(1))), loan);
    }

    @Test
    void task3_5_allFiltersCombine_mustAllMatch() {
        Offer match = acceptedSale("sam", "bea", 100);           // sam, bea, SELL, in progress, DAY0
        acceptedSale("sue", "bea", 80);                          // wrong seller
        acceptedSale("sam", "ben", 60);                          // wrong buyer
        Offer completedOther = acceptedSale("sam", "bea", 50);
        complete(completedOther);                                // wrong state
        acceptedClaim("sam", "bea");                             // wrong type
        support.clock.advance(Duration.ofDays(3));
        acceptedSale("sam", "bea", 40);                          // too late

        DealFilter filter = DealFilter.none()
                .withSeller("sam").withBuyer("bea")
                .withType(ListingType.SELL).withStatus(DealState.IN_PROGRESS)
                .withDateRange(DAY0, DAY0.plusDays(1));

        assertSameDeals(service.findDeals(filter), match);
    }

    @Test
    void task3_5_dealsAreListedNewestStartDateFirst() {
        Offer oldest = acceptedSale("sam", "bea", 100);
        support.clock.advance(Duration.ofHours(1));
        Offer middle = acceptedSale("sam", "bea", 80);
        support.clock.advance(Duration.ofHours(1));
        Offer newest = acceptedSale("sam", "bea", 60);

        List<Offer> offers = service.findDeals(DealFilter.none()).stream().map(DealRecord::offer).toList();

        assertEquals(List.of(newest, middle, oldest), offers);
    }

    // ---------- Task 3.5 A1: no deals ----------

    @Test
    void task3_5_A1_noMatch_returnsAnEmptyList_notAnError() {
        assertTrue(service.findDeals(DealFilter.none()).isEmpty());   // nothing stored at all

        acceptedSale("sam", "bea", 100);
        assertTrue(service.findDeals(DealFilter.none().withStatus(DealState.RETURNED)).isEmpty());
        assertTrue(service.findDeals(DealFilter.none().withBuyer("nobody")).isEmpty());
    }

    // ---------- Task 3.5 step 4.1: VIEW DETAILS ----------

    @Test
    void task3_5_viewDetails_ofASale() {
        Offer deal = acceptedSale("sam", "bea", 90);

        DealDetails details = service.getDealDetails(deal.getId());

        assertSame(deal, details.offer());
        assertEquals("Book", details.listing().getTitle());
        assertEquals("bea", details.buyerUsername());
        assertEquals("Buyer bea", details.buyerFullName());
        assertNull(details.residenceHall());
        assertEquals("sam", details.sellerUsername());
        assertEquals("Seller sam", details.sellerFullName());
        assertNull(details.sellerBio());
        assertEquals(ListingType.SELL, details.dealType());
        assertEquals(90.0, details.amount());
        assertNull(details.borrowStart());
        assertNull(details.borrowEnd());
        assertEquals(deal.getAcceptedDate(), details.startDate());
        assertNull(details.completedDate());
        assertEquals(DealState.IN_PROGRESS, details.state());
        assertFalse(details.reviewed());
        assertFalse(details.reviewHidden());
    }

    @Test
    void task3_5_viewDetails_ofALoan_showsTheBorrowPeriod_andNoAmount() {
        BorrowRequest loan = acceptedBorrow("sam", "bea");

        DealDetails details = service.getDealDetails(loan.getId());

        assertEquals(ListingType.LEND, details.dealType());
        assertNull(details.amount());
        assertEquals(loan.getProposedStartDate(), details.borrowStart());
        assertEquals(loan.getProposedEndDate(), details.borrowEnd());
        assertEquals(loan.getProposedStartDate(), details.startDate());
    }

    @Test
    void task3_5_viewDetails_showsResidenceHallAndBio_whenTheyExist() {
        Buyer hallBuyer = new Buyer("hal", "Hal Hall", "hash", "Hall 3");
        support.store.addUser(hallBuyer);
        Seller bioSeller = new Seller("sia", "Sia Seller", "hash");
        bioSeller.setBio("Sells books");
        support.store.addUser(bioSeller);
        Offer deal = acceptedSale("sia", "hal", 60);

        DealDetails details = service.getDealDetails(deal.getId());

        assertEquals("Hall 3", details.residenceHall());
        assertEquals("Sells books", details.sellerBio());
        assertEquals("Hal Hall", details.buyerFullName());
        assertEquals("Sia Seller", details.sellerFullName());
    }

    @Test
    void task3_5_viewDetails_reviewedAndReviewHiddenFlags() {
        Offer deal = acceptedSale("sam", "bea", 100);
        complete(deal);
        assertFalse(service.getDealDetails(deal.getId()).reviewed());

        Review review = support.store.addReview(new Review(deal.getId(), 4, "ok"));
        DealDetails reviewed = service.getDealDetails(deal.getId());
        assertTrue(reviewed.reviewed());
        assertFalse(reviewed.reviewHidden());
        assertEquals(DealState.COMPLETED, reviewed.state());

        support.store.transaction(() -> review.setHidden(true));
        DealDetails hidden = service.getDealDetails(deal.getId());
        assertTrue(hidden.reviewed());
        assertTrue(hidden.reviewHidden());
    }

    @Test
    void task3_5_viewDetails_unknownOffer_isRefused() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.getDealDetails("O999"));

        assertEquals("This deal no longer exists.", e.getMessage());
    }

    @Test
    void task3_5_viewDetails_ofANonDeal_isRefused() {
        Offer pending = pendingOffer("sam", "bea", 100);
        Offer countered = counteredOffer("sam", "ben");
        Offer declined = declinedOffer("sue", "bea");

        for (Offer notADeal : List.of(pending, countered, declined)) {
            ServiceException e = assertThrows(ServiceException.class,
                    () -> service.getDealDetails(notADeal.getId()));
            assertEquals("This deal no longer exists.", e.getMessage());
        }
    }

    // ---------- Task 3.5 step 4.2: CSV export ----------

    @Test
    void task3_5_exportCsv_hasTheHeaderAndOneRowPerDeal() {
        acceptedSale("sam", "bea", 100);

        String[] lines = service.exportCsv(service.findDeals(DealFilter.none())).split("\n");

        assertEquals(CSV_HEADER, lines[0]);
        assertEquals(2, lines.length);

        assertEquals("Book,bea,sam,Sale," + DAY0.format(CSV_FMT) + ",,In progress", lines[1]);
    }

    @Test
    void task3_5_exportCsv_completedSaleAndReturnedLoan_showTheirDatesAndStatus() {
        Offer sale = acceptedSale("sam", "bea", 100);
        support.clock.advance(Duration.ofDays(1));
        complete(sale);
        BorrowRequest loan = acceptedBorrow("sue", "ben");
        support.clock.advance(Duration.ofDays(2));
        rules.returnItem(loan, false);

        String csv = service.exportCsv(service.findDeals(DealFilter.none()));

        String expectedSale = "Book,bea,sam,Sale,"
                + DAY0.format(CSV_FMT) + ","
                + DAY0.plusDays(1).format(CSV_FMT) + ",Completed";

        String expectedLoan = "Calculator,ben,sue,Loan,"
                + DAY0.plusDays(2).format(CSV_FMT) + ","
                + DAY0.plusDays(3).format(CSV_FMT) + ",Returned";

        assertTrue(csv.contains(expectedSale), "CSV missing sale row: " + expectedSale);
        assertTrue(csv.contains(expectedLoan), "CSV missing loan row: " + expectedLoan);
    }
    @Test
    void task3_5_exportCsv_escapesEveryTextField() {
        Fixtures.buyer(support.store, "a,b");
        Fixtures.seller(support.store, "s\"q");
        Offer deal = acceptedSale("s\"q", "a,b", 50);
        support.store.getListingOf(deal).setTitle("Bag, \"used\"\nnew");

        String csv = service.exportCsv(service.findDeals(DealFilter.none()));

        assertTrue(csv.contains("\"Bag, \"\"used\"\"\nnew\""));
        assertTrue(csv.contains("\"a,b\""));
        assertTrue(csv.contains("\"s\"\"q\""));
    }

    @Test
    void task3_5_exportCsv_emptyList_returnsOnlyTheHeader() {
        String[] lines = service.exportCsv(List.of()).split("\n");

        assertEquals(1, lines.length);
        assertEquals(CSV_HEADER, lines[0]);
    }

    @Test
    void task3_5_exportCsv_ofAFilteredList_containsOnlyThoseDeals() {
        acceptedSale("sam", "bea", 100);
        acceptedSale("sue", "ben", 50);

        List<DealRecord> filtered = service.findDeals(DealFilter.none().withSeller("sue"));
        String[] lines = service.exportCsv(filtered).split("\n");

        assertEquals(2, lines.length);
        assertTrue(lines[1].contains(",ben,sue,"));
        assertFalse(service.exportCsv(filtered).contains(",bea,sam,"));
    }

    // ---------- Access control (UC2 Special Requirement 3) ----------

    @Test
    void task3_5_noSession_findDetailsAndExportAreDenied() {
        Offer deal = acceptedSale("sam", "bea", 100);
        List<DealRecord> records = service.findDeals(DealFilter.none());
        support.logout();

        assertThrows(AccessDeniedException.class, () -> service.findDeals(DealFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.getDealDetails(deal.getId()));
        assertThrows(AccessDeniedException.class, () -> service.exportCsv(records));
    }

    @Test
    void task3_5_buyerSession_findDetailsAndExportAreDenied() {
        Offer deal = acceptedSale("sam", "bea", 100);
        List<DealRecord> records = service.findDeals(DealFilter.none());
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.findDeals(DealFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.getDealDetails(deal.getId()));
        assertThrows(AccessDeniedException.class, () -> service.exportCsv(records));
    }

    @Test
    void task3_5_sellerSession_findDetailsAndExportAreDenied() {
        Offer deal = acceptedSale("sam", "bea", 100);
        List<DealRecord> records = service.findDeals(DealFilter.none());
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.findDeals(DealFilter.none()));
        assertThrows(AccessDeniedException.class, () -> service.getDealDetails(deal.getId()));
        assertThrows(AccessDeniedException.class, () -> service.exportCsv(records));
    }
}