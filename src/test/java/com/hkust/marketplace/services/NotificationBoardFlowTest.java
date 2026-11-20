package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test of UC4 View Notifications across roles, on the data the shared
 * NotificationBoard shows (thread test of the basic flow and A1, Lecture 13): events in one
 * portal put notifications on other users' boards, each user sees only their own, newest
 * first, and marking one as read changes only that one.
 */
class NotificationBoardFlowTest {

    private TestClock clock;
    private DataStore store;
    private NotificationService notifications;
    private DealRules rules;
    private Seller sam;
    private Buyer bea;
    private Buyer ben;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        notifications = new NotificationService(store);
        rules = new DealRules(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        ben = Fixtures.buyer(store, "ben");
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    @Test
    void sellerAcceptsOneOffer_eachBuyersBoardShowsTheirOwnNews_newestFirst() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer beaOffer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        store.addOffer(new PriceOffer(book.getId(), "ben", 90));
        notifications.send("bea", NotificationCategory.ANNOUNCEMENT, "Welcome to the marketplace");
        clock.advance(Duration.ofMinutes(5));

        rules.accept(beaOffer); // the seller's action (Manage Offers & Requests)

        SessionManager.login(bea);
        List<Notification> beaBoard = notifications.getMyNotifications();
        assertEquals(2, beaBoard.size());
        assertEquals(NotificationCategory.OFFER_UPDATE, beaBoard.get(0).getCategory()); // newest first
        assertTrue(beaBoard.get(0).getContent().contains("accepted your price offer"));
        assertEquals(NotificationCategory.ANNOUNCEMENT, beaBoard.get(1).getCategory());
        assertEquals(2, notifications.getUnreadCount());

        SessionManager.login(ben);
        List<Notification> benBoard = notifications.getMyNotifications();
        assertEquals(1, benBoard.size());
        assertTrue(benBoard.get(0).getContent().contains("declined because the seller accepted another offer"));

        SessionManager.login(sam);
        assertTrue(notifications.getMyNotifications().isEmpty()); // the seller's own board is separate
    }

    @Test
    void markingOneAsRead_changesOnlyThatOne_andSurvivesReloadingTheBoard() {
        Notification older = notifications.send("bea", NotificationCategory.ANNOUNCEMENT, "first");
        clock.advance(Duration.ofMinutes(1));
        Notification newer = notifications.send("bea", NotificationCategory.NEW_MESSAGE, "second");
        SessionManager.login(bea);

        notifications.markRead(newer.getId()); // UC4 step 3

        List<Notification> reloaded = notifications.getMyNotifications(); // what refresh() shows
        assertEquals(List.of(newer, older), reloaded);
        assertTrue(reloaded.get(0).isRead());
        assertFalse(reloaded.get(1).isRead());
        assertEquals(1, notifications.getUnreadCount());
    }

    @Test
    void userWithNoNotifications_seesAnEmptyBoard() {
        // UC4 A1: the board then says "There are no notifications yet."
        SessionManager.login(ben);
        assertTrue(notifications.getMyNotifications().isEmpty());
        assertEquals(0, notifications.getUnreadCount());
    }

    @Test
    void nobodyLoggedIn_cannotOpenABoard() {
        assertThrows(AccessDeniedException.class, notifications::getMyNotifications);
    }
}
