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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: after the admin deletes an account ({@code DataStore.deleteAccount}),
 * the shared services keep working for everyone else. Deals and listings kept as records
 * still name the deleted account, so each flow that would notify or involve it is run here.
 */
class AccountDeletionFlowTest {

    private TestClock clock;
    private DataStore store;
    private DealRules rules;
    private NotificationService notifications;
    private ChatService chat;
    private Seller sam;
    private Buyer bea;
    private Buyer ben;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        rules = new DealRules(store);
        notifications = new NotificationService(store);
        chat = new ChatService(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        ben = Fixtures.buyer(store, "ben");
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    @Test
    void sessionOfADeletedAccount_isRefused() {
        SessionManager.login(bea);

        store.deleteAccount("bea");

        assertThrows(AccessDeniedException.class, AccessControl::requireLoggedIn);
        assertThrows(AccessDeniedException.class, AccessControl::requireBuyer);
    }

    @Test
    void deletedAccount_cannotLogInOrBeRegisteredAgain() {
        AuthService auth = new AuthService(store);
        assertNull(auth.registerBuyer("carl", "Carl Buyer", "Str0ng#Pass", "Str0ng#Pass", null));
        store.deleteAccount("carl");

        assertEquals(LoginResult.INVALID_CREDENTIALS, auth.login("carl", "Str0ng#Pass", UserRole.BUYER));
        assertEquals("This username is already in use.",
                auth.registerBuyer("Carl", "Another Carl", "Str0ng#Pass", "Str0ng#Pass", null));
        assertEquals("This username is already in use.",
                auth.registerSeller("carl", "Seller Carl", "Str0ng#Pass", "Str0ng#Pass", null, null));
    }

    @Test
    void notifyingADeletedAccount_isSkipped_butAnAccountThatNeverExistedIsStillABug() {
        store.deleteAccount("bea");

        assertNull(notifications.send("bea", NotificationCategory.ANNOUNCEMENT, "Hello"));
        assertTrue(store.getNotificationsFor("bea").isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> notifications.send("nobody", NotificationCategory.ANNOUNCEMENT, "Hello"));
    }

    @Test
    void borrowedItemOfADeletedBuyer_isStillReturnedAutomatically() {
        LendListing calculator = Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
        BorrowRequest request = (BorrowRequest) store.addOffer(
                new BorrowRequest(calculator.getId(), "bea", store.now(), store.now().plusDays(7)));
        rules.accept(request);
        store.deleteAccount("bea");
        clock.advance(Duration.ofDays(7));

        rules.returnItem(request, true); // UC12: the timer returns it when the period ends

        assertTrue(request.isCompleted());
        assertEquals(ListingStatus.LIVE, calculator.getStatus());
        SessionManager.login(sam);
        assertEquals(1, notifications.getMyNotifications().size()); // the seller is told; the buyer is gone
    }

    @Test
    void dealAcceptedByADeletedSeller_canStillBeCompletedByTheBuyer() {
        SaleListing desk = Fixtures.live(Fixtures.sale(store, "sam", 300));
        Offer deal = store.addOffer(new PriceOffer(desk.getId(), "bea", 300));
        rules.accept(deal);
        store.deleteAccount("sam");

        rules.complete(deal); // UC7: nobody is left to notify, and that is not an error

        assertTrue(deal.isCompleted());
        assertEquals(ListingStatus.REMOVED, desk.getStatus()); // removed with the account, and it stays removed
    }

    @Test
    void sellerCanStillAcceptAnotherOffer_afterOneBuyerWasDeleted() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer beaOffer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        Offer benOffer = store.addOffer(new PriceOffer(book.getId(), "ben", 90));
        store.deleteAccount("bea");

        rules.accept(benOffer);

        assertEquals(OfferStatus.DECLINED, beaOffer.getStatus());
        assertEquals(OfferStatus.ACCEPTED, benOffer.getStatus());
        assertEquals(ListingStatus.RESERVED, book.getStatus());
    }

    @Test
    void acceptingADeletedBuyersOffer_isRefused_becauseItWasDeclined() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer beaOffer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        store.deleteAccount("bea");

        assertThrows(ServiceException.class, () -> rules.accept(beaOffer));
        assertEquals(ListingStatus.LIVE, book.getStatus());
    }

    @Test
    void chatWithADeletedBuyer_isGoneForTheSeller() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer beaOffer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        SessionManager.login(bea);
        chat.send(chat.openForListing(book.getId()), "Hello");
        store.deleteAccount("bea"); // UC6 Special Requirement 2

        SessionManager.login(sam);
        assertTrue(chat.getMyConversations().isEmpty());
        ServiceException refused = assertThrows(ServiceException.class, () -> chat.openForOffer(beaOffer.getId()));
        assertEquals("There is no chat with this buyer about this listing.", refused.getMessage());
    }

    @Test
    void chatWithADeletedSeller_isGoneForTheBuyer_andCannotBeStartedAgain() {
        SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));
        Offer beaOffer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        SessionManager.login(bea);
        chat.send(chat.openForListing(book.getId()), "Hello");
        store.deleteAccount("sam");

        assertTrue(chat.getMyConversations().isEmpty());
        assertThrows(ServiceException.class, () -> chat.openForOffer(beaOffer.getId()));
        assertThrows(ServiceException.class, () -> chat.openForListing(book.getId()));
        assertNull(store.findThread("bea", book.getId()));
    }
}
