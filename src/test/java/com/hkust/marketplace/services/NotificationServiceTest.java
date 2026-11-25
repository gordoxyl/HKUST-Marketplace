package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
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
 * Tests for UC4 View Notifications and for sending notifications. Equivalence partitions of a
 * board's notifications (Lecture 13): mine and another user's, read and unread, in the chosen
 * category and not; boundary values: an empty board, a board with nothing unread.
 */
class NotificationServiceTest {

    private TestClock clock;
    private DataStore store;
    private NotificationService service;
    private Buyer bea;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        service = new NotificationService(store);
        bea = Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    @Test
    void send_timestampsTheNotificationWithTheStoreClock() {
        Notification n = service.send("bea", NotificationCategory.ANNOUNCEMENT, "Hello");

        assertEquals(clock.now(), n.getTimestamp());
        assertFalse(n.isRead()); // unread until marked (UC4 Special Requirement 3)
    }

    @Test
    void send_toAnUnknownAccount_isRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> service.send("nobody", NotificationCategory.ANNOUNCEMENT, "Hello"));
    }

    @Test
    void sendToAdmins_reachesEveryAdminAccount() {
        // Assumption 9: "the admin" means every admin account
        Fixtures.admin(store, "ada");
        Fixtures.admin(store, "alan");

        service.sendToAdmins(NotificationCategory.LISTING_SUBMITTED, "New listing");

        assertEquals(1, store.getNotificationsFor("ada").size());
        assertEquals(1, store.getNotificationsFor("alan").size());
        assertTrue(store.getNotificationsFor("bea").isEmpty());
    }

    @Test
    void myNotifications_showOnlyMine_newestFirst() {
        Notification first = service.send("bea", NotificationCategory.ANNOUNCEMENT, "first");
        clock.advance(Duration.ofMinutes(1));
        Notification second = service.send("bea", NotificationCategory.OFFER_UPDATE, "second");
        Notification sameTime = service.send("bea", NotificationCategory.NEW_MESSAGE, "same time, added later");
        service.send("ben", NotificationCategory.ANNOUNCEMENT, "not mine");
        SessionManager.login(bea);

        assertEquals(List.of(sameTime, second, first), service.getMyNotifications());
    }

    @Test
    void board_needsALoggedInUser() {
        assertThrows(AccessDeniedException.class, service::getMyNotifications);
        assertThrows(AccessDeniedException.class, service::getUnreadCount);
        assertThrows(AccessDeniedException.class, service::markAllRead);
        assertThrows(AccessDeniedException.class, service::getMyCategories);
        assertThrows(AccessDeniedException.class, () -> service.getMyNotifications(NotificationCategory.ANNOUNCEMENT));
    }

    @Test
    void markRead_marksOnlyMyOwnNotifications() {
        Notification mine = service.send("bea", NotificationCategory.ANNOUNCEMENT, "mine");
        Notification bens = service.send("ben", NotificationCategory.ANNOUNCEMENT, "ben's");
        SessionManager.login(bea);
        assertEquals(1, service.getUnreadCount());

        service.markRead(mine.getId());

        assertTrue(mine.isRead());
        assertEquals(0, service.getUnreadCount());
        assertThrows(AccessDeniedException.class, () -> service.markRead(bens.getId()));
        assertFalse(bens.isRead());
        assertThrows(AccessDeniedException.class, () -> service.markRead("N999"));
    }

    // ---------- Mark all as read (assumptions register 91) ----------

    @Test
    void markAllRead_marksEveryUnreadNotificationOfMine_andNoOneElses() {
        Notification alreadyRead = service.send("bea", NotificationCategory.ANNOUNCEMENT, "read before");
        Notification offer = service.send("bea", NotificationCategory.OFFER_UPDATE, "unread offer");
        Notification message = service.send("bea", NotificationCategory.NEW_MESSAGE, "unread message");
        Notification bens = service.send("ben", NotificationCategory.ANNOUNCEMENT, "ben's");
        SessionManager.login(bea);
        service.markRead(alreadyRead.getId());

        service.markAllRead();

        assertTrue(offer.isRead());
        assertTrue(message.isRead());
        assertTrue(alreadyRead.isRead());
        assertEquals(0, service.getUnreadCount());
        assertFalse(bens.isRead()); // another user's board is left alone
    }

    @Test
    void anEmptyBoard_hasNothingToMark_andNoCategories() {
        SessionManager.login(bea);

        service.markAllRead();

        assertEquals(0, service.getUnreadCount());
        assertEquals(List.of(), service.getMyCategories());
    }

    // ---------- The category filter (assumptions register 93) ----------

    @Test
    void myCategories_areTheOnesMyNotificationsHave_onceEach_inTheEnumsOrder() {
        service.send("bea", NotificationCategory.NEW_MESSAGE, "a message");
        service.send("bea", NotificationCategory.OFFER_UPDATE, "an offer");
        service.send("bea", NotificationCategory.NEW_MESSAGE, "another message");
        service.send("ben", NotificationCategory.ANNOUNCEMENT, "not mine");
        SessionManager.login(bea);

        assertEquals(List.of(NotificationCategory.OFFER_UPDATE, NotificationCategory.NEW_MESSAGE),
                service.getMyCategories());
    }

    @Test
    void myNotificationsInOneCategory_areOnlyThose_newestFirst() {
        Notification older = service.send("bea", NotificationCategory.NEW_MESSAGE, "older");
        service.send("bea", NotificationCategory.OFFER_UPDATE, "an offer");
        clock.advance(Duration.ofMinutes(1));
        Notification newer = service.send("bea", NotificationCategory.NEW_MESSAGE, "newer");
        service.send("ben", NotificationCategory.NEW_MESSAGE, "not mine");
        SessionManager.login(bea);

        assertEquals(List.of(newer, older), service.getMyNotifications(NotificationCategory.NEW_MESSAGE));
        assertEquals(List.of(), service.getMyNotifications(NotificationCategory.ACCOUNT_CHANGE)); // none of mine
    }
}
