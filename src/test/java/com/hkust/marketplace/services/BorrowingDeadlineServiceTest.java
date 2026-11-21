package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.data.Storage;
import com.hkust.marketplace.data.StoreSnapshot;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.SameMoment;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UC12 Process Borrowing Deadlines. The class is the test driver; a
 * {@link TestClock} is the stub for the passage of time, so a deadline days away is reached
 * without waiting (Lecture 14). How the tests were designed (Lectures 13-14):
 *
 * <p><b>Equivalence partitions and boundary values</b> of the three moments the use case
 * depends on:
 * <pre>
 * Reminder time (SR2)   period of 48 hours or longer: 24 hours before the due date
 *                       period shorter than 48 hours: halfway through the period
 *   boundary at 48 h:   47:59:59 (halfway), 48:00:00 and 48:00:01 (24 hours before)
 * Sending the reminder  before the reminder time: nothing    at or after it: one reminder
 *   boundary:           reminder time - 1 s, the reminder time, reminder time + 1 s
 * Returning the item    before the due date: nothing         at or after it: returned
 *   boundary:           due date - 1 s, the due date, due date + 1 s
 * </pre>
 *
 * <p><b>State-based tests</b>: a borrow request in every state it can be in is left alone,
 * except the one state the use case names: accepted and not returned.
 *
 * <p><b>Thread tests</b> follow the basic flow and A1 through every object: the request, its
 * listing, both notification boards, and the buyer's My Deals.
 *
 * <p>The critical step (Special Requirement 5, returned only once) is run from several
 * threads at the same moment.
 */
class BorrowingDeadlineServiceTest {

    private static final LocalDateTime START = Fixtures.START;

    private TestClock clock;
    private DataStore store;
    private BorrowingDeadlineService deadlines;
    private DealRules rules;
    private Seller sam;
    private Buyer bea;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        deadlines = new BorrowingDeadlineService(store);
        rules = new DealRules(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** A borrow request by Bea for a period that starts now, not answered yet. */
    private BorrowRequest requested(Duration period) {
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        return (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), "bea", clock.now(), clock.now().plus(period)));
    }

    /** An item Bea borrows from now for the period: the request, accepted by the seller. */
    private BorrowRequest borrowed(Duration period) {
        BorrowRequest request = requested(period);
        rules.accept(request);
        return request;
    }

    private List<Notification> boardOf(User user, NotificationCategory category) {
        return store.getNotificationsFor(user.getUsername()).stream()
                .filter(notification -> notification.getCategory() == category).toList();
    }

    // ================= Special Requirement 2: the reminder time =================

    @ParameterizedTest(name = "a period of {0} is reminded {1} after it starts")
    @CsvSource({
            "PT72H,        PT48H",         // 24 hours before the due date
            "PT48H0M1S,    PT24H0M1S",     // just above the boundary
            "PT48H,        PT24H",         // the boundary: 24 hours before, which is also halfway
            "PT47H59M59S,  PT23H59M59.5S", // just below the boundary: halfway
            "PT24H,        PT12H",
            "PT10S,        PT5S",
            "PT5S,         PT2.5S"})       // the shortest period there is
    void reminderTime_is24HoursBeforeTheDueDate_orHalfwayForAShortPeriod(Duration period, Duration afterStart) {
        BorrowRequest request = requested(period);

        assertEquals(START.plus(afterStart), request.getReminderTime());
    }

    // ================= {Send Reminders} (step 2) =================

    @ParameterizedTest(name = "{0} s from the reminder time: reminder sent = {1}")
    @CsvSource({"-1, false", "0, true", "1, true"})
    void reminder_isSentOnceTheReminderTimeIsReached(long secondsFromReminderTime, boolean sent) {
        BorrowRequest request = borrowed(Duration.ofDays(7));
        clock.set(request.getReminderTime().plusSeconds(secondsFromReminderTime));

        DeadlineReport report = deadlines.process();

        assertEquals(sent, request.isReminderSent()); // "records that the reminder was sent"
        assertEquals(sent ? 1 : 0, boardOf(bea, NotificationCategory.DUE_DATE_REMINDER).size());
        assertEquals(sent ? List.of(request) : List.of(), report.reminded());
        assertEquals(sent, report.hasChanges());
        assertFalse(request.isCompleted()); // only reminded, not returned
    }

    @Test
    void reminder_namesTheItemAndTheDueDate_andGoesToTheBuyerOnly() {
        BorrowRequest request = borrowed(Duration.ofDays(7)); // due 09 Nov 2026 12:00
        int samBefore = store.getNotificationsFor("sam").size();
        clock.set(request.getReminderTime());

        deadlines.process();

        Notification reminder = boardOf(bea, NotificationCategory.DUE_DATE_REMINDER).get(0);
        assertEquals("Reminder: 'Calculator' is due on 09 Nov 2026 12:00:00.", reminder.getContent());
        assertEquals(request.getReminderTime(), reminder.getTimestamp());
        assertEquals(samBefore, store.getNotificationsFor("sam").size());
    }

    /** Regression R9: the reminder names the due time to the second, as My Deals does. */
    @Test
    void reminderOfAShortLoan_namesTheDueTimeToTheSecond() {
        clock.advance(Duration.ofSeconds(44));
        BorrowRequest request = borrowed(Duration.ofMinutes(5)); // 12:00:44 to 12:05:44, like the practice HDMI Cable
        clock.set(request.getReminderTime());

        deadlines.process();

        assertEquals("Reminder: 'Calculator' is due on 02 Nov 2026 12:05:44.",
                boardOf(bea, NotificationCategory.DUE_DATE_REMINDER).get(0).getContent());
    }

    @Test
    void eachBorrowRequest_getsAtMostOneReminder() {
        // Special Requirement 3
        BorrowRequest request = borrowed(Duration.ofDays(7));
        clock.set(request.getReminderTime());
        deadlines.process();

        clock.advance(Duration.ofHours(1));
        DeadlineReport second = deadlines.process();
        clock.advance(Duration.ofHours(1));
        deadlines.process();

        assertEquals(1, boardOf(bea, NotificationCategory.DUE_DATE_REMINDER).size());
        assertFalse(second.hasChanges());
    }

    @Test
    void shortBorrow_isRemindedHalfwayThrough() {
        BorrowRequest request = borrowed(Duration.ofSeconds(10));

        clock.advance(Duration.ofSeconds(4));
        deadlines.process();
        assertFalse(request.isReminderSent());

        clock.advance(Duration.ofSeconds(1)); // 5 s: halfway
        deadlines.process();
        assertTrue(request.isReminderSent());
        assertFalse(request.isCompleted());
    }

    // ================= {Return Item} (step 3) =================

    @ParameterizedTest(name = "{0} s from the due date: returned = {1}")
    @CsvSource({"-1, false", "0, true", "1, true", "86400, true"})
    void item_isReturnedOnceItsDueDateHasCome(long secondsFromDueDate, boolean returned) {
        BorrowRequest request = borrowed(Duration.ofDays(7));
        clock.set(request.getDueDate().plusSeconds(secondsFromDueDate));

        DeadlineReport report = deadlines.process();

        assertEquals(returned, request.isCompleted());
        assertEquals(returned, request.isAutoReturned());
        assertEquals(returned ? ListingStatus.LIVE : ListingStatus.LENT, store.getListingOf(request).getStatus());
        assertEquals(returned ? List.of(request) : List.of(), report.returned());
        if (returned) {
            // "records the due date as the deal's completion date", however late the check ran
            assertEquals(request.getDueDate(), request.getCompletedDate());
        }
    }

    @Test
    void autoReturn_makesTheListingAvailableAgain_andNotifiesBuyerAndSeller() {
        BorrowRequest request = borrowed(Duration.ofDays(7));
        Listing calculator = store.getListingOf(request);
        clock.set(request.getDueDate());

        deadlines.process();

        assertTrue(store.isAvailable(calculator)); // it can be borrowed again
        List<Notification> beaBoard = boardOf(bea, NotificationCategory.DEAL_COMPLETED);
        List<Notification> samBoard = boardOf(sam, NotificationCategory.DEAL_COMPLETED);
        assertEquals("'Calculator' was returned automatically because the borrowing period ended. "
                + "You can rate the seller from My Deals.", beaBoard.get(0).getContent()); // client answer #3
        assertEquals("'Calculator', borrowed by Buyer bea, was returned automatically because the borrowing period ended.",
                samBoard.get(0).getContent());

        // Step 4: My Deals shows the deal as completed and auto-returned
        SessionManager.login(bea);
        Deal onMyDeals = new MyDealsService(store).getMyDeals().get(0);
        assertFalse(onMyDeals.isCurrent());
        assertFalse(onMyDeals.canBeReturned());
        assertTrue(((BorrowRequest) onMyDeals.offer()).isAutoReturned());
        assertTrue(onMyDeals.canBeReviewed());
    }

    @Test
    void shortestBorrow_isReturnedAfterFiveSeconds() {
        // Special Requirement 1: deadlines are met even with the 5-second minimum borrow time
        BorrowRequest request = borrowed(BorrowRequest.MIN_BORROW_TIME);

        clock.advance(Duration.ofSeconds(4));
        deadlines.process();
        assertFalse(request.isCompleted());
        assertTrue(request.isReminderSent()); // reminded halfway, at 2.5 s

        clock.advance(Duration.ofSeconds(1));
        deadlines.process();
        assertTrue(request.isAutoReturned());
    }

    @Test
    void itemWhoseReminderTimeAndDueDateBothPassed_isRemindedAndThenReturned_inOneRun() {
        // The app was closed for the whole period; the postcondition still promises one reminder
        BorrowRequest request = borrowed(Duration.ofDays(7));
        clock.advance(Duration.ofDays(30));

        DeadlineReport report = deadlines.process();

        assertEquals(List.of(request), report.reminded());
        assertEquals(List.of(request), report.returned());
        assertEquals(request.getDueDate(), request.getCompletedDate());
        List<Notification> beaBoard = store.getNotificationsFor("bea"); // newest first
        assertEquals(NotificationCategory.DEAL_COMPLETED, beaBoard.get(0).getCategory());
        assertEquals(NotificationCategory.DUE_DATE_REMINDER, beaBoard.get(1).getCategory());
    }

    @Test
    void severalItems_areEachHandledAtTheirOwnTime() {
        BorrowRequest shortLoan = borrowed(Duration.ofHours(2));
        BorrowRequest longLoan = borrowed(Duration.ofDays(7));
        clock.advance(Duration.ofHours(2));

        DeadlineReport report = deadlines.process();

        assertEquals(List.of(shortLoan), report.returned());
        assertTrue(shortLoan.isAutoReturned());
        assertFalse(longLoan.isCompleted());
        assertFalse(longLoan.isReminderSent());
        assertEquals(ListingStatus.LENT, store.getListingOf(longLoan).getStatus());
    }

    // ================= A1: Listing Removed While Lent =================

    @Test
    void itemWhoseListingTheAdminRemoved_isReturned_butTheListingStaysRemoved_andTheNextItemIsHandled() {
        BorrowRequest removedLoan = borrowed(Duration.ofDays(2));
        BorrowRequest otherLoan = borrowed(Duration.ofDays(2));
        store.transaction(store.getListingOf(removedLoan)::remove);
        clock.advance(Duration.ofDays(2));

        DeadlineReport report = deadlines.process();

        assertTrue(removedLoan.isAutoReturned());
        assertEquals(removedLoan.getDueDate(), removedLoan.getCompletedDate());
        assertEquals(ListingStatus.REMOVED, store.getListingOf(removedLoan).getStatus());   // A1 step 1
        assertEquals(2, boardOf(bea, NotificationCategory.DEAL_COMPLETED).size());          // "including the notifications"
        assertEquals(2, boardOf(sam, NotificationCategory.DEAL_COMPLETED).size());
        assertTrue(otherLoan.isAutoReturned());                                             // A1 step 2
        assertEquals(ListingStatus.LIVE, store.getListingOf(otherLoan).getStatus());
        assertEquals(2, report.returned().size());
    }

    // ================= Only an accepted request that has not been returned (state-based) =================

    @ParameterizedTest(name = "a {0} borrow request")
    @ValueSource(strings = {"PENDING", "COUNTERED", "DECLINED"})
    void requestThatWasNeverAccepted_isLeftAlone_howeverLateItIs(String state) {
        BorrowRequest request = requested(Duration.ofDays(2));
        switch (state) {
            case "COUNTERED" -> request.counter(START.plusDays(1), START.plusDays(3));
            case "DECLINED" -> request.decline();
            default -> { }
        }
        clock.advance(Duration.ofDays(30));

        DeadlineReport report = deadlines.process();

        assertFalse(report.hasChanges());
        assertFalse(request.isReminderSent());
        assertFalse(request.isCompleted());
        assertEquals(ListingStatus.LIVE, store.getListingOf(request).getStatus());
        assertTrue(store.getNotificationsFor("bea").isEmpty());
    }

    @Test
    void itemTheBuyerAlreadyReturned_isNotReturnedAgain() {
        // Special Requirement 5: a borrowed item is returned only once
        BorrowRequest request = borrowed(Duration.ofDays(7));
        clock.advance(Duration.ofDays(1));
        SessionManager.login(bea);
        new MyDealsService(store).returnItem(request.getId()); // the buyer's own return (UC7 A2)
        LocalDateTime returnedAt = request.getCompletedDate();
        clock.advance(Duration.ofDays(30));

        DeadlineReport report = deadlines.process();

        assertFalse(report.hasChanges());
        assertEquals(returnedAt, request.getCompletedDate());
        assertFalse(request.isAutoReturned());
        assertFalse(request.isReminderSent()); // returned before the reminder time: no reminder either
        assertTrue(boardOf(bea, NotificationCategory.DUE_DATE_REMINDER).isEmpty());
    }

    @Test
    void autoReturnedItem_isNotReturnedOrRemindedAgain() {
        BorrowRequest request = borrowed(Duration.ofDays(2));
        clock.advance(Duration.ofDays(2));
        deadlines.process();
        int beaNotifications = store.getNotificationsFor("bea").size();

        clock.advance(Duration.ofDays(1));
        DeadlineReport again = deadlines.process();

        assertFalse(again.hasChanges());
        assertEquals(beaNotifications, store.getNotificationsFor("bea").size());
        assertEquals(request.getDueDate(), request.getCompletedDate()); // the first return stays as it was
    }

    @Test
    void purchasesAndClaims_haveNoDeadline() {
        Offer purchase = store.addOffer(new PriceOffer(Fixtures.live(Fixtures.sale(store, "sam", 100)).getId(), "bea", 90));
        rules.accept(purchase);
        clock.advance(Duration.ofDays(365));

        assertFalse(deadlines.process().hasChanges());
        assertFalse(purchase.isCompleted());
        assertEquals(ListingStatus.RESERVED, store.getListingOf(purchase).getStatus());
    }

    @Test
    void itemLentAgainAfterAnAutoReturn_getsItsOwnReminderAndReturn() {
        BorrowRequest first = borrowed(Duration.ofHours(2));
        Listing calculator = store.getListingOf(first);
        clock.advance(Duration.ofHours(2));
        deadlines.process();

        BorrowRequest second = (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), "ben", clock.now(), clock.now().plusHours(2)));
        rules.accept(second);
        clock.advance(Duration.ofHours(2));
        DeadlineReport report = deadlines.process();

        assertEquals(List.of(second), report.returned());
        assertEquals(ListingStatus.LIVE, calculator.getStatus());
    }

    // ================= Nothing to do =================

    @Test
    void runWithNoDeadlineReached_changesNothing_andDoesNotSave() {
        CountingStorage storage = new CountingStorage();
        DataStore saving = new DataStore(clock, storage);
        Fixtures.seller(saving, "sam");
        Fixtures.buyer(saving, "bea");
        LendListing calculator = Fixtures.live(Fixtures.lend(saving, "sam", Duration.ofDays(7)));
        new DealRules(saving).accept(saving.addOffer(
                new BorrowRequest(calculator.getId(), "bea", clock.now(), clock.now().plusDays(7))));
        int savesBefore = storage.saves;

        for (int second = 0; second < 60; second++) { // a minute of checks
            clock.advance(Duration.ofSeconds(1));
            assertSame(DeadlineReport.NOTHING, new BorrowingDeadlineService(saving).process());
        }

        assertEquals(savesBefore, storage.saves); // the timer does not rewrite the save file every second
    }

    @Test
    void emptyMarketplace_hasNothingToDo() {
        assertSame(DeadlineReport.NOTHING, new BorrowingDeadlineService(Fixtures.store(Fixtures.clock())).process());
    }

    /** A storage double that only counts saves (a test spy). */
    private static class CountingStorage implements Storage {
        int saves;

        @Override public Optional<StoreSnapshot> load() { return Optional.empty(); }
        @Override public void save(StoreSnapshot snapshot) { saves++; }
        @Override public String setAsideUnreadable() { return ""; }
        @Override public void saveAuditSnapshot(String logReference, String snapshot) { }
        @Override public Optional<String> loadAuditSnapshot(String logReference) { return Optional.empty(); }
        @Override public void savePhoto(String photoId, byte[] image) { }
        @Override public Optional<byte[]> loadPhoto(String photoId) { return Optional.empty(); }
    }

    // ================= Special Requirement 5: returned only once (the critical step) =================

    @Test
    void checksRunningAtTheSameMoment_returnTheItemOnlyOnce() throws Exception {
        BorrowRequest request = borrowed(Duration.ofHours(2));
        clock.advance(Duration.ofHours(2));
        int threads = 8;
        List<Callable<DeadlineReport>> timerRuns = Collections.nCopies(threads, deadlines::process);
        List<DeadlineReport> runs;
        try (SameMoment moment = new SameMoment(threads)) {
            runs = moment.run(timerRuns);
        }
        int returns = runs.stream().mapToInt(run -> run.returned().size()).sum();

        assertEquals(1, returns);
        assertEquals(1, boardOf(bea, NotificationCategory.DEAL_COMPLETED).size());
        assertEquals(1, boardOf(bea, NotificationCategory.DUE_DATE_REMINDER).size());
        assertEquals(request.getDueDate(), request.getCompletedDate());
    }

    // ================= Accounts that are gone =================

    @Test
    void itemBorrowedByADeletedBuyer_isStillRemindedSilently_andReturned() {
        BorrowRequest request = borrowed(Duration.ofDays(2));
        store.deleteAccount("bea");
        clock.advance(Duration.ofDays(2));

        DeadlineReport report = deadlines.process();

        assertTrue(request.isAutoReturned());
        assertEquals(ListingStatus.LIVE, store.getListingOf(request).getStatus());
        assertEquals(1, report.reminded().size()); // recorded, although nobody is left to tell
        assertEquals(1, boardOf(sam, NotificationCategory.DEAL_COMPLETED).size());
    }

    // ================= Saving =================

    @Test
    void itemThatBecameDueWhileTheAppWasClosed_isReturnedWhenItStartsAgain(@TempDir Path folder) {
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed(); // the demo data: Bob's borrow request for the calculator is pending
        BorrowRequest request = (BorrowRequest) saved.getOffersByBuyer("bob").stream()
                .filter(Offer::isOpen).findFirst().orElseThrow();
        new DealRules(saved).accept(request);
        String id = request.getId();

        clock.advance(Duration.ofDays(30)); // the app is closed all this time
        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(file));
        restarted.loadOrSeed();
        DeadlineReport report = new BorrowingDeadlineService(restarted).process(); // the timer's first check

        BorrowRequest reloaded = (BorrowRequest) restarted.findOffer(id);
        assertEquals(1, report.returned().size());
        assertTrue(reloaded.isAutoReturned());
        assertEquals(reloaded.getDueDate(), reloaded.getCompletedDate());
        assertEquals(ListingStatus.LIVE, restarted.getListingOf(reloaded).getStatus());

        DataStore again = new DataStore(clock, new EncryptedJsonStorage(file)); // and the return was saved
        again.loadOrSeed();
        assertTrue(((BorrowRequest) again.findOffer(id)).isAutoReturned());
        assertTrue(((BorrowRequest) again.findOffer(id)).isReminderSent());
    }
}
