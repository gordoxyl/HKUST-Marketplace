package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.SameMoment;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UC9 Report Listing. The class is a test driver for {@link ReportService} (Lecture
 * 14). How the tests were designed (Lectures 12-14):
 *
 * <p><b>Equivalence partitions and boundary values</b> of what the form sends:
 * <pre>
 *   user:     buyer | seller | admin | nobody logged in | deactivated buyer            only an active buyer
 *   listing:  one the buyer can open | none with that id                               the precondition
 *   history:  first report | same buyer again | another buyer | same buyer, other listing
 *                                                                                      A1 only for "again" (SR3)
 *   reason:   none (null) | each of the six                                            A2 without one
 *   details:  none (null) | empty | only spaces and line breaks | 1 character | text with spaces around it
 *                                                                                      A2 unless there is text
 * </pre>
 * The checks follow the order of the flow of events: a listing that cannot be reported is
 * reported before a missing reason or details.
 *
 * <p><b>State-based tests</b> put a listing into each of its states through the Listing state
 * machine, with and without a deal by the buyer. A listing can be reported exactly when the
 * buyer can open it in UC8, and both use cases are checked against the same table
 * (assumption 80).
 *
 * <p><b>Basis paths</b> (white box, Lecture 12) of {Check Report} and {Check Details}, which
 * have six decisions, so V(G) = 7: no such listing (P1), a listing the buyer cannot open (P2),
 * reported already (P3), no reason (P4), no details (P5), blank details (P6), and a report
 * recorded (P7). Each path's test is marked with it.
 *
 * <p><b>Thread tests</b> follow a report from the detail screen to every admin's notification
 * board and the admin's view of the listing, check that the seller is told nothing (Special
 * Requirement 4), and that a report survives a restart.
 *
 * <p><b>The critical case</b> (Lecture 14): one report submitted many times at the same moment
 * is recorded once (Special Requirement 3).
 */
class ReportServiceTest {

    private static final LocalDateTime NOW = Fixtures.START;
    private static final String DETAILS = "Asked me to pay before we meet.";

    private DataStore store;
    private ReportService reports;
    private DealRules rules;
    private Seller sam;
    private Buyer bea;
    private Buyer ben;
    private SaleListing pen;

    @BeforeEach
    void setUp() {
        store = Fixtures.store(Fixtures.clock());
        reports = new ReportService(store);
        rules = new DealRules(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        ben = Fixtures.buyer(store, "ben");
        Fixtures.admin(store, "ada"); // two admins, so that "every admin" is tested
        Fixtures.admin(store, "al");
        pen = Fixtures.live(Fixtures.sale(store, "sam", 10));
        SessionManager.login(bea);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    // ---------- Listings in a chosen state, reached through the Listing state machine ----------

    /** A new listing of the type the status needs: a lend listing for Lent, a giveaway for Given, otherwise a sale. */
    private Listing newListingFor(ListingStatus status) {
        return switch (status) {
            case LENT -> Fixtures.lend(store, "sam", Duration.ofDays(7));
            case GIVEN -> Fixtures.giveaway(store, "sam");
            default -> Fixtures.sale(store, "sam", 50);
        };
    }

    /** A listing in the status that the buyer bea has no deal on. */
    private Listing withoutBeasDeal(ListingStatus status) {
        return Fixtures.moveTo(newListingFor(status), status);
    }

    /** A listing in the status that bea has a deal on: her offer on the live listing, then the events that lead there. */
    private Listing withBeasDeal(ListingStatus status) {
        Listing listing = Fixtures.live(newListingFor(status));
        Offer offer = store.addOffer(beasOfferOn(listing));
        switch (status) {
            case RESERVED, LENT -> Fixtures.moveDealTo(rules, offer, "ACCEPTED");
            case SOLD, GIVEN -> Fixtures.moveDealTo(rules, offer, "COMPLETED");
            case REMOVED -> store.transaction(listing::remove); // the admin removes it after her offer
            default -> { } // Live: her offer is still pending
        }
        return listing;
    }

    private Offer beasOfferOn(Listing listing) {
        return switch (listing.getType()) {
            case SELL -> new PriceOffer(listing.getId(), "bea", 45);
            case LEND -> new BorrowRequest(listing.getId(), "bea", NOW.plusHours(1), NOW.plusDays(1));
            case GIVEAWAY -> new Claim(listing.getId(), "bea");
        };
    }

    /** What the user's notification board says about reported listings. */
    private List<String> reportNews(String username) {
        return store.getNotificationsFor(username).stream()
                .filter(n -> n.getCategory() == NotificationCategory.LISTING_REPORTED)
                .map(Notification::getContent).toList();
    }

    private void assertNothingRecordedOrSent() {
        assertTrue(store.getAllReports().isEmpty(), "no report");
        assertTrue(reportNews("ada").isEmpty(), "no notification");
        assertTrue(reportNews("al").isEmpty(), "no notification");
    }

    // ================= Basic flow =================

    @Test
    void theForm_namesTheListing_andOffersTheSixReasons_inTheSpecsOrder() { // steps 2-3, Special Requirement 1
        ReportForm form = reports.start(pen.getId());

        assertEquals(pen.getId(), form.listingId());
        assertEquals("Book", form.listingTitle());
        assertEquals(List.of("Prohibited item", "Scam or fraud", "Misleading description", "Offensive content",
                "Spam or duplicate", "Other"), form.reasons().stream().map(ReportReason::getDisplayName).toList());
        assertNothingRecordedOrSent(); // opening the form changes nothing
    }

    @Test
    void aReport_isRecordedWithItsReasonAndDetails_andEveryAdminIsNotified() { // P7, steps 5-8
        Report report = reports.submit(pen.getId(), ReportReason.SCAM_OR_FRAUD, DETAILS);

        assertEquals(List.of(report), store.getReportsForListing(pen.getId()));
        assertEquals("bea", report.getBuyerUsername());
        assertEquals(ReportReason.SCAM_OR_FRAUD, report.getReason());
        assertEquals(DETAILS, report.getDetails());
        assertTrue(store.hasReported("bea", pen.getId()));
        String news = "'Book' by Seller sam was reported (Scam or fraud) and needs moderation.";
        assertEquals(List.of(news), reportNews("ada"));
        assertEquals(List.of(news), reportNews("al"));
    }

    // ================= Equivalence partitions and boundary values =================

    @Test
    void onlyABuyer_canReportAListing() {
        for (User user : List.of(sam, store.findUser("ada"))) {
            SessionManager.login(user);
            assertThrows(AccessDeniedException.class, () -> reports.start(pen.getId()));
            assertThrows(AccessDeniedException.class, () -> reports.submit(pen.getId(), ReportReason.OTHER, DETAILS));
        }
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> reports.start(pen.getId()));
        assertThrows(AccessDeniedException.class, () -> reports.submit(pen.getId(), ReportReason.OTHER, DETAILS));
        assertNothingRecordedOrSent();
    }

    @Test
    void aDeactivatedBuyer_cannotReportAListing() {
        store.transaction(() -> bea.setActive(false));

        assertThrows(AccessDeniedException.class, () -> reports.start(pen.getId()));
        assertThrows(AccessDeniedException.class, () -> reports.submit(pen.getId(), ReportReason.OTHER, DETAILS));
        assertNothingRecordedOrSent();
    }

    @Test
    void aListingThatDoesNotExist_cannotBeReported() { // P1
        ListingNotReportableException atStart = assertThrows(ListingNotReportableException.class,
                () -> reports.start("L999"));
        ListingNotReportableException atSubmit = assertThrows(ListingNotReportableException.class,
                () -> reports.submit("L999", ReportReason.OTHER, DETAILS));

        assertEquals(ListingAccess.NOT_AVAILABLE, atStart.getMessage());
        assertEquals(ListingAccess.NOT_AVAILABLE, atSubmit.getMessage());
        assertNothingRecordedOrSent();
    }

    @Test
    void aListingReportedBefore_cannotBeReportedAgain() { // P3, A1, Special Requirement 3
        reports.submit(pen.getId(), ReportReason.SCAM_OR_FRAUD, DETAILS);

        ListingNotReportableException atStart = assertThrows(ListingNotReportableException.class,
                () -> reports.start(pen.getId()));
        ListingNotReportableException atSubmit = assertThrows(ListingNotReportableException.class,
                () -> reports.submit(pen.getId(), ReportReason.OTHER, "Reporting it again."));

        assertEquals(ReportService.ALREADY_REPORTED, atStart.getMessage());
        assertEquals(ReportService.ALREADY_REPORTED, atSubmit.getMessage());
        assertEquals(1, store.getReportsForListing(pen.getId()).size());
        assertEquals(1, reportNews("ada").size());
    }

    @Test
    void anotherBuyer_canReportTheSameListing_andTheBuyer_anotherListing() { // Special Requirement 3 is per buyer and listing
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 50));
        reports.submit(pen.getId(), ReportReason.SCAM_OR_FRAUD, DETAILS);
        reports.submit(book.getId(), ReportReason.OTHER, DETAILS);
        SessionManager.login(ben);
        reports.submit(pen.getId(), ReportReason.MISLEADING_DESCRIPTION, DETAILS);

        assertEquals(2, store.getReportsForListing(pen.getId()).size());
        assertEquals(1, store.getReportsForListing(book.getId()).size());
    }

    @ParameterizedTest
    @EnumSource(ReportReason.class)
    void eachOfTheSixReasons_isRecorded(ReportReason reason) {
        assertEquals(reason, reports.submit(pen.getId(), reason, DETAILS).getReason());
    }

    @Test
    void noReason_isRefused_andNothingIsRecordedOrSent() { // P4, A2
        ServiceException e = assertThrows(ServiceException.class, () -> reports.submit(pen.getId(), null, DETAILS));

        assertEquals(ReportService.REASON_AND_DETAILS_REQUIRED, e.getMessage());
        assertEquals(ServiceException.class, e.getClass(), "A2 goes back to the form, the use case goes on");
        assertNothingRecordedOrSent();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  \n\t "})
    void missingEmptyOrBlankDetails_areRefused_andNothingIsRecordedOrSent(String details) { // P5, P6, A2
        ServiceException e = assertThrows(ServiceException.class,
                () -> reports.submit(pen.getId(), ReportReason.OTHER, details));

        assertEquals(ReportService.REASON_AND_DETAILS_REQUIRED, e.getMessage());
        assertEquals(ServiceException.class, e.getClass(), "A2 goes back to the form, the use case goes on");
        assertNothingRecordedOrSent();
    }

    @Test
    void detailsOfOneCharacter_orOfSeveralLines_areKept_withoutTheSpacesAroundThem() { // assumption 82
        assertEquals("x", reports.submit(pen.getId(), ReportReason.OTHER, "x").getDetails());
        SessionManager.login(ben);
        assertEquals("Two lines\nof text",
                reports.submit(pen.getId(), ReportReason.OTHER, "  Two lines\nof text  ").getDetails());
    }

    @Test
    void aListingThatCannotBeReported_isReportedBeforeAnIncompleteForm() { // the use case ends whatever was typed
        ListingNotReportableException missing = assertThrows(ListingNotReportableException.class,
                () -> reports.submit("L999", null, DETAILS));
        assertEquals(ListingAccess.NOT_AVAILABLE, missing.getMessage());

        reports.submit(pen.getId(), ReportReason.OTHER, DETAILS);
        ListingNotReportableException again = assertThrows(ListingNotReportableException.class,
                () -> reports.submit(pen.getId(), ReportReason.OTHER, "   "));
        assertEquals(ReportService.ALREADY_REPORTED, again.getMessage());
    }

    // ================= State-based tests: the Listing state machine, with and without a deal =================

    @ParameterizedTest(name = "{0}, a deal by the buyer: {1}")
    @CsvSource({"LIVE, false", "LIVE, true", "RESERVED, false", "RESERVED, true", "LENT, false", "LENT, true",
            "SOLD, true", "GIVEN, true", "REMOVED, true"})
    void aListingTheBuyerCanOpen_canBeReported(ListingStatus status, boolean beasDeal) { // assumption 80
        Listing listing = beasDeal ? withBeasDeal(status) : withoutBeasDeal(status);

        assertDoesNotThrow(() -> new BrowseService(store).openListing(listing.getId()), "UC8 opens it");
        assertEquals(listing.getId(), reports.start(listing.getId()).listingId());
        reports.submit(listing.getId(), ReportReason.OTHER, DETAILS);
        assertTrue(store.hasReported("bea", listing.getId()));
    }

    @ParameterizedTest(name = "{0}, no deal by the buyer")
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "REJECTED", "SOLD", "GIVEN", "REMOVED"})
    void aListingTheBuyerCannotOpen_cannotBeReported(ListingStatus status) { // P2, the precondition
        Listing listing = withoutBeasDeal(status);

        assertThrows(ServiceException.class, () -> new BrowseService(store).openListing(listing.getId()),
                "UC8 does not open it");
        ListingNotReportableException e = assertThrows(ListingNotReportableException.class,
                () -> reports.start(listing.getId()));
        assertEquals(ListingAccess.NOT_AVAILABLE, e.getMessage());
    }

    @Test
    void aDeactivatedSellersListing_canBeReported_onlyByABuyerWithADealOnIt() { // not live (glossary), assumption 80
        Listing withDeal = withBeasDeal(ListingStatus.LIVE);
        store.transaction(() -> sam.setActive(false));

        ListingNotReportableException e = assertThrows(ListingNotReportableException.class,
                () -> reports.start(pen.getId()));
        assertEquals(ListingAccess.NOT_AVAILABLE, e.getMessage());
        assertEquals(withDeal.getId(), reports.start(withDeal.getId()).listingId());
    }

    @Test
    void aListingRemovedWhileTheFormIsOpen_isRefusedAtSubmit() { // assumption 81: the precondition no longer holds
        ReportForm form = reports.start(pen.getId());
        store.transaction(pen::remove); // the admin removes it meanwhile; bea has no deal on it

        ListingNotReportableException e = assertThrows(ListingNotReportableException.class,
                () -> reports.submit(form.listingId(), ReportReason.OTHER, DETAILS));
        assertEquals(ListingAccess.NOT_AVAILABLE, e.getMessage());
        assertNothingRecordedOrSent();
    }

    @Test
    void aReportSentFromAnotherWindowWhileTheFormIsOpen_endsTheUseCaseAtSubmit() { // assumption 81: A1 again
        ReportForm first = reports.start(pen.getId());
        ReportForm second = reports.start(pen.getId());
        reports.submit(first.listingId(), ReportReason.SCAM_OR_FRAUD, DETAILS);

        ListingNotReportableException e = assertThrows(ListingNotReportableException.class,
                () -> reports.submit(second.listingId(), ReportReason.OTHER, DETAILS));
        assertEquals(ReportService.ALREADY_REPORTED, e.getMessage());
        assertEquals(1, store.getAllReports().size());
    }

    // ================= The admins' notification (step 8) =================

    @Test
    void theNotification_namesTheListingItsSellerAndTheReason() { // assumption 83
        assertEquals("'Book' by Diana Ng was reported (Scam or fraud) and needs moderation.",
                ReportService.news(pen, "Diana Ng", ReportReason.SCAM_OR_FRAUD));
    }

    @Test
    void aDeletedSellersListing_isNamedWithTheSellersUsername() { // client answer #6: the deleted seller's deals are kept
        Listing bought = withBeasDeal(ListingStatus.SOLD);
        store.deleteAccount("sam");

        reports.submit(bought.getId(), ReportReason.SCAM_OR_FRAUD, DETAILS);

        assertEquals(List.of("'Book' by sam was reported (Scam or fraud) and needs moderation."), reportNews("ada"));
    }

    // ================= Thread tests =================

    @Test
    void aReport_fromTheDetailScreen_toTheAdminsBoard_andTheAdminsViewOfTheListing() { // UC8, UC9, UC4, Manage Listings
        new BrowseService(store).openListing(pen.getId());                   // UC8 step 6: bea views the listing
        ReportForm form = reports.start(pen.getId());                         // UC9 steps 1-3
        reports.submit(form.listingId(), ReportReason.MISLEADING_DESCRIPTION, "Says 65W, the photo shows 30W.");

        SessionManager.login(store.findUser("ada"));                          // UC4, the admin's board
        List<Notification> board = new NotificationService(store).getMyNotifications();
        assertEquals(1, board.size());
        assertEquals(NotificationCategory.LISTING_REPORTED, board.get(0).getCategory());
        assertEquals("'Book' by Seller sam was reported (Misleading description) and needs moderation.",
                board.get(0).getContent());

        AdminListingService moderation = new AdminListingService(store);     // Manage Marketplace Listings 2 and 5.1
        assertEquals(List.of(pen), moderation.findListings(ListingFilter.none().withReportedOnly(true)));
        List<Report> seen = moderation.getListingDetails(pen.getId()).reports();
        assertEquals(1, seen.size());
        assertEquals(ReportReason.MISLEADING_DESCRIPTION, seen.get(0).getReason());
        assertEquals("Says 65W, the photo shows 30W.", seen.get(0).getDetails());
        assertEquals("bea", seen.get(0).getBuyerUsername());                  // only the admin sees who reported
    }

    @Test
    void theSeller_isToldNothing_andNoNotificationNamesTheReporter() { // Special Requirement 4
        int samsBefore = store.getNotificationsFor("sam").size();

        reports.submit(pen.getId(), ReportReason.OFFENSIVE_CONTENT, DETAILS);

        assertEquals(samsBefore, store.getNotificationsFor("sam").size());
        List<String> everyNotification = store.getAllUsers().stream()
                .flatMap(user -> store.getNotificationsFor(user.getUsername()).stream())
                .map(Notification::getContent).toList();
        Pattern reporter = Pattern.compile("\\bbea\\b", Pattern.CASE_INSENSITIVE);
        assertFalse(everyNotification.isEmpty(), "the admins were notified");
        assertTrue(everyNotification.stream().noneMatch(text -> reporter.matcher(text).find()),
                everyNotification::toString);
    }

    @Test
    void everyAdmin_isNotifiedOnceForEachReport_andTheBuyersAreNot() { // step 8, assumption 83
        reports.submit(pen.getId(), ReportReason.SCAM_OR_FRAUD, DETAILS);
        SessionManager.login(ben);
        reports.submit(pen.getId(), ReportReason.SPAM_OR_DUPLICATE, DETAILS);

        assertEquals(2, reportNews("ada").size());
        assertEquals(2, reportNews("al").size());
        assertTrue(store.getNotificationsFor("bea").isEmpty(), "step 9 is a message on the screen");
        assertTrue(store.getNotificationsFor("ben").isEmpty());
    }

    @Test
    void aReport_survivesARestart_andStillBlocksASecondReport(@TempDir Path folder) { // Special Requirement 3 across runs
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(Fixtures.clock(), new EncryptedJsonStorage(file));
        saved.loadOrSeed();
        Fixtures.seller(saved, "sam");
        Buyer reporter = Fixtures.buyer(saved, "bea");
        Listing lamp = Fixtures.live(Fixtures.giveaway(saved, "sam"));
        SessionManager.login(reporter);
        new ReportService(saved).submit(lamp.getId(), ReportReason.PROHIBITED_ITEM, DETAILS);

        DataStore restarted = new DataStore(Fixtures.clock(), new EncryptedJsonStorage(file));
        restarted.loadOrSeed();

        assertTrue(restarted.hasReported("bea", lamp.getId()));
        ListingNotReportableException e = assertThrows(ListingNotReportableException.class,
                () -> new ReportService(restarted).start(lamp.getId()));
        assertEquals(ReportService.ALREADY_REPORTED, e.getMessage());
    }

    // ================= The critical case: one report submitted at the same moment =================

    @Test
    void reportsOfOneListingSubmittedAtTheSameMoment_onlyTheFirstIsRecorded() throws Exception {
        int attempts = 10;
        List<Callable<String>> submissions = Collections.nCopies(attempts, () -> {
            try {
                reports.submit(pen.getId(), ReportReason.SCAM_OR_FRAUD, DETAILS);
                return "recorded";
            } catch (ListingNotReportableException e) {
                return e.getMessage(); // reported by a faster attempt
            }
        });
        List<String> outcomes;
        try (SameMoment moment = new SameMoment(attempts)) {
            outcomes = moment.run(submissions);
        }

        assertEquals(1, Collections.frequency(outcomes, "recorded"));
        assertEquals(attempts - 1, Collections.frequency(outcomes, ReportService.ALREADY_REPORTED));
        assertEquals(1, store.getAllReports().size());
        assertEquals(1, reportNews("ada").size());
        assertEquals(1, reportNews("al").size());
    }
}
