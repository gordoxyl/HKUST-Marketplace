package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for UC6 Chat About Listing: the basic flow, A1 (empty message),
 * A2 (search) and the special requirements, for the buyer and the seller. Each test logs in
 * the participant who acts, as the app does, so the role checks are part of what is tested.
 */
class ChatServiceTest {

    private TestClock clock;
    private DataStore store;
    private ChatService chat;
    private NotificationService notifications;
    private Seller sam;
    private Seller sue;
    private Buyer bea;
    private Buyer ben;
    private Admin ada;
    private SaleListing book;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        chat = new ChatService(store);
        notifications = new NotificationService(store);
        sam = Fixtures.seller(store, "sam");
        sue = Fixtures.seller(store, "sue");
        bea = Fixtures.buyer(store, "bea");
        ben = Fixtures.buyer(store, "ben");
        ada = Fixtures.admin(store, "ada");
        book = Fixtures.live(Fixtures.sale(store, "sam", 100));
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** Logs the user in and returns their notification board. */
    private List<Notification> boardOf(User user) {
        SessionManager.login(user);
        return notifications.getMyNotifications();
    }

    /** Bea starts the chat about the book with one message, and stays logged in. */
    private Conversation beaStartsChat(String firstMessage) {
        SessionManager.login(bea);
        return chat.send(chat.openForListing(book.getId()), firstMessage);
    }

    // ---------- Basic flow ----------

    @Test
    void buyerOpensAListingWithNoChatYet_seesAnEmptyChat_andNothingIsStored() {
        SessionManager.login(bea);

        Conversation opened = chat.openForListing(book.getId()); // UC6 step 2

        assertTrue(opened.entries().isEmpty());
        assertTrue(opened.lastEntry().isEmpty());
        assertSame(book, opened.listing());
        assertTrue(opened.viewerIsBuyer());
        assertEquals("Seller sam", opened.otherPartyName());
        assertNull(store.findThread("bea", book.getId())); // the thread is created by the first message
    }

    @Test
    void buyersFirstMessage_createsTheThread_storesSenderAndTime_andNotifiesTheSeller() {
        Conversation sent = beaStartsChat("Is it still available?");

        ChatThread thread = store.findThread("bea", book.getId());
        assertNotNull(thread); // step 5
        assertEquals(1, thread.getMessages().size());
        ChatEntry entry = sent.entries().get(0);
        assertEquals("Is it still available?", entry.content());
        assertEquals("bea", entry.senderUsername());
        assertEquals("Buyer bea", entry.senderName());
        assertEquals(Fixtures.START, entry.time());
        assertFalse(entry.isOffer());
        assertTrue(sent.isSentByViewer(entry));

        List<Notification> samBoard = boardOf(sam); // step 6, Special Requirement 4
        assertEquals(1, samBoard.size());
        assertEquals(NotificationCategory.NEW_MESSAGE, samBoard.get(0).getCategory());
        assertEquals("Buyer bea sent you a message about 'Book'.", samBoard.get(0).getContent());
        assertTrue(boardOf(bea).isEmpty()); // the sender is not notified
    }

    @Test
    void sellerReplies_buyerIsNotified_andBothSeeTheSameHistoryInOrder() {
        beaStartsChat("Is it still available?");
        clock.advance(Duration.ofMinutes(5));

        SessionManager.login(sam);
        Conversation samView = chat.getMyConversations().get(0); // the seller opens it from the Chats screen
        assertFalse(samView.viewerIsBuyer());
        assertEquals("Buyer bea", samView.otherPartyName());
        samView = chat.send(samView, "Yes, meet at the Library?");

        assertEquals(List.of("Is it still available?", "Yes, meet at the Library?"),
                samView.entries().stream().map(ChatEntry::content).toList());
        assertEquals(List.of("bea", "sam"), samView.entries().stream().map(ChatEntry::senderUsername).toList());
        assertEquals(Fixtures.START.plusMinutes(5), samView.lastEntry().orElseThrow().time());
        assertFalse(samView.isSentByViewer(samView.entries().get(0)));
        assertTrue(samView.isSentByViewer(samView.entries().get(1)));

        List<Notification> beaBoard = boardOf(bea);
        assertEquals(1, beaBoard.size());
        assertEquals("Seller sam sent you a message about 'Book'.", beaBoard.get(0).getContent());

        Conversation beaView = chat.openForListing(book.getId()); // step 6: both participants see it
        assertEquals(samView.entries(), beaView.entries());
    }

    @Test
    void everyMessage_notifiesTheOtherParty() {
        Conversation c = beaStartsChat("Hello");
        chat.send(c, "Are you there?");

        assertEquals(2, boardOf(sam).size()); // postcondition: notified of each new message
    }

    @Test
    void offersAppearInTheHistoryAlongsideTheMessages_inTheOrderTheyWereSent() {
        beaStartsChat("Would you take less?");
        clock.advance(Duration.ofMinutes(1));
        Offer first = store.addOffer(new PriceOffer(book.getId(), "bea", 80)); // UC5, from the thread
        clock.advance(Duration.ofMinutes(1));
        SessionManager.login(sam);
        chat.send(chat.openForOffer(first.getId()), "A bit more?");
        clock.advance(Duration.ofMinutes(1));
        Offer second = store.addOffer(new PriceOffer(book.getId(), "bea", 90));

        Conversation history = chat.openForOffer(second.getId());

        assertEquals(4, history.entries().size()); // Special Requirement 3; UC5 SR5: earlier offers stay
        assertEquals("Would you take less?", history.entries().get(0).content());
        assertSame(first, history.entries().get(1).offer());
        assertEquals("A bit more?", history.entries().get(2).content());
        assertSame(second, history.entries().get(3).offer());

        ChatEntry offerEntry = history.entries().get(1);
        assertTrue(offerEntry.isOffer());
        assertNull(offerEntry.content());
        assertEquals("bea", offerEntry.senderUsername()); // an offer is always the buyer's
        assertEquals(Fixtures.START.plusMinutes(1), offerEntry.time());
        assertEquals(List.of(0, 1, 2, 3), history.entries().stream().map(ChatEntry::position).toList());
    }

    @Test
    void anOfferMadeAtTheSameMomentAsAMessage_isListedBeforeIt() {
        beaStartsChat("I just sent you an offer");
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 80)); // the clock has not moved

        Conversation history = chat.openForListing(book.getId());

        assertSame(offer, history.entries().get(0).offer());
        assertEquals("I just sent you an offer", history.entries().get(1).content());
    }

    @Test
    void message_isStoredWithoutTheSpacesAroundIt() {
        Conversation sent = beaStartsChat("  \n Hello there \t ");

        assertEquals("Hello there", sent.entries().get(0).content());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "LENT"})
    void buyerCanStartAChatAboutAnUnavailableListing(ListingStatus status) {
        // UC8 Special Requirement 1: Reserved and Lent listings are shown, and the buyer can still chat
        Listing listing = Fixtures.moveTo(status == ListingStatus.LENT
                ? Fixtures.lend(store, "sam", Duration.ofDays(7)) : Fixtures.sale(store, "sam", 50), status);
        SessionManager.login(bea);

        Conversation sent = chat.send(chat.openForListing(listing.getId()), "When will it be free again?");

        assertEquals(1, sent.entries().size());
    }

    // ---------- A1: Empty Message ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t "})
    void emptyFirstMessage_isRefused_andNoThreadIsCreated(String nothing) {
        SessionManager.login(bea);
        Conversation opened = chat.openForListing(book.getId());

        ServiceException refused = assertThrows(ServiceException.class, () -> chat.send(opened, nothing));

        assertEquals("Please write a message before sending.", refused.getMessage());
        assertNull(store.findThread("bea", book.getId()));
        assertTrue(boardOf(sam).isEmpty());
    }

    @Test
    void emptyMessageInAnExistingChat_isRefused_andNothingIsStoredOrNotified() {
        Conversation c = beaStartsChat("Hello");

        assertThrows(ServiceException.class, () -> chat.send(c, "  "));

        assertEquals(1, store.findThread("bea", book.getId()).getMessages().size());
        assertEquals(1, boardOf(sam).size()); // only the first message's notification
    }

    // ---------- A2: Search Chat History ----------

    @Test
    void search_listsTheMessagesContainingTheKeyword_ignoringCase_withSenderTimeAndPosition() {
        Conversation c = beaStartsChat("Is the BOOK still available?");
        clock.advance(Duration.ofMinutes(1));
        chat.send(c, "I can meet today");
        clock.advance(Duration.ofMinutes(1));
        SessionManager.login(sam);
        chat.send(chat.getMyConversations().get(0), "The book is yours, see you at the library");

        List<ChatEntry> matches = chat.search(chat.getMyConversations().get(0), " Book "); // either participant can search

        assertEquals(2, matches.size());
        assertEquals("Is the BOOK still available?", matches.get(0).content());
        assertEquals("Buyer bea", matches.get(0).senderName());
        assertEquals(Fixtures.START, matches.get(0).time());
        assertEquals(0, matches.get(0).position()); // where to jump to in the history (A2 step 4)
        assertEquals("Seller sam", matches.get(1).senderName());
        assertEquals(2, matches.get(1).position());
    }

    @Test
    void search_looksAtMessagesOnly_andFindsNothingWhenNoMessageMatches() {
        Conversation c = beaStartsChat("Hello");
        store.addOffer(new PriceOffer(book.getId(), "bea", 80));

        assertTrue(chat.search(c, "offer").isEmpty()); // the offer is in the history, but it has no text
        assertTrue(chat.search(c, "goodbye").isEmpty()); // A2 step 2: the screen then says so
        assertEquals(1, chat.search(c, "hello").size());
    }

    @Test
    void search_seesMessagesSentAfterTheChatWasOpened() {
        Conversation opened = beaStartsChat("Hello");
        chat.send(opened, "Is the lamp included?");

        assertEquals(1, chat.search(opened, "lamp").size()); // "opened" is the older copy
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void search_withoutAKeyword_isRefused(String nothing) {
        Conversation c = beaStartsChat("Hello");

        ServiceException refused = assertThrows(ServiceException.class, () -> chat.search(c, nothing));

        assertEquals("Please enter a keyword to search for.", refused.getMessage());
    }

    // ---------- Special Requirement 1: one buyer, one listing, only its two participants ----------

    @Test
    void eachBuyerHasTheirOwnChatAboutTheSameListing() {
        beaStartsChat("Bea here");
        SessionManager.login(ben);
        Conversation benChat = chat.send(chat.openForListing(book.getId()), "Ben here");

        assertEquals(List.of("Ben here"), benChat.entries().stream().map(ChatEntry::content).toList());
        SessionManager.login(sam);
        assertEquals(2, chat.getMyConversations().size());
    }

    @Test
    void anotherBuyer_anotherSeller_andTheAdmin_cannotReadWriteOrSearchTheChat() {
        Conversation beaChat = beaStartsChat("Private message");

        for (User outsider : List.of(ben, sue, ada)) {
            SessionManager.login(outsider);
            // Even holding the chat itself, as if the screens were bypassed
            assertThrows(AccessDeniedException.class, () -> chat.reload(beaChat), outsider.getUsername());
            assertThrows(AccessDeniedException.class, () -> chat.send(beaChat, "Let me in"), outsider.getUsername());
            assertThrows(AccessDeniedException.class, () -> chat.search(beaChat, "private"), outsider.getUsername());
        }
        assertEquals(1, store.findThread("bea", book.getId()).getMessages().size());
    }

    @Test
    void adminHasNoChats_andCannotOpenOne() {
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        SessionManager.login(ada);

        assertThrows(AccessDeniedException.class, chat::getMyConversations);
        assertThrows(AccessDeniedException.class, () -> chat.openForListing(book.getId()));
        assertThrows(AccessDeniedException.class, () -> chat.openForOffer(offer.getId()));
    }

    @Test
    void nobodyLoggedIn_orADeactivatedAccount_cannotChat() {
        Conversation beaChat = beaStartsChat("Hello");

        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> chat.send(beaChat, "Hi"));
        assertThrows(AccessDeniedException.class, chat::getMyConversations);

        store.transaction(() -> bea.setActive(false));
        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class, () -> chat.send(beaChat, "Hi"));
        assertThrows(AccessDeniedException.class, () -> chat.openForListing(book.getId()));
    }

    @Test
    void someoneElsesOffer_doesNotOpenTheirChat() {
        Offer beaOffer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));

        SessionManager.login(ben);
        assertThrows(AccessDeniedException.class, () -> chat.openForOffer(beaOffer.getId()));
        SessionManager.login(sue);
        assertThrows(AccessDeniedException.class, () -> chat.openForOffer(beaOffer.getId()));
    }

    @Test
    void onlyTheBuyerStartsAChat_theSellerCannot() {
        SessionManager.login(sam);
        Conversation notStarted = new Conversation(book, "bea", "Buyer bea", "Seller sam", "sam", List.of());

        ServiceException refused = assertThrows(ServiceException.class, () -> chat.send(notStarted, "Buy my book!"));

        assertEquals("There is no chat with this buyer about this listing.", refused.getMessage());
        assertThrows(ServiceException.class, () -> chat.reload(notStarted));
        assertNull(store.findThread("bea", book.getId()));
        assertTrue(boardOf(bea).isEmpty());
    }

    @Test
    void aSellerCannotPoseAsTheBuyerOfTheirOwnChat() {
        SessionManager.login(sue); // not the book's seller
        Conversation forged = new Conversation(book, "sue", "Seller sue", "Seller sam", "sue", List.of());

        assertThrows(AccessDeniedException.class, () -> chat.send(forged, "Hello"));
    }

    @Test
    void sellerCanWrite_onceTheBuyerMadeAnOffer() {
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 80)); // UC5 step 7 creates the thread
        SessionManager.login(sam);

        Conversation opened = chat.openForOffer(offer.getId()); // Manage Offers & Requests 3.6
        assertEquals(1, opened.entries().size());
        assertSame(offer, opened.entries().get(0).offer());
        Conversation sent = chat.send(opened, "Could you do 90?");

        assertEquals(2, sent.entries().size());
        assertEquals(1, boardOf(bea).size());
    }

    @Test
    void buyerReopensTheChatOfADeal_fromMyDeals() {
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));
        SessionManager.login(bea);

        Conversation reopened = chat.openForOffer(offer.getId()); // UC7 A4

        assertTrue(reopened.viewerIsBuyer());
        assertSame(offer, reopened.lastEntry().orElseThrow().offer());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "REJECTED", "REMOVED", "SOLD", "GIVEN"})
    void buyerCannotStartAChatAboutAListingThatIsNotOnTheMarketplace(ListingStatus status) {
        Listing hidden = Fixtures.moveTo(status == ListingStatus.GIVEN
                ? Fixtures.giveaway(store, "sam") : Fixtures.sale(store, "sam", 50), status);
        SessionManager.login(bea);

        ServiceException refused = assertThrows(ServiceException.class, () -> chat.openForListing(hidden.getId()));

        assertEquals("This listing is not on the marketplace, so a new chat cannot be started.", refused.getMessage());
        Conversation forged = new Conversation(hidden, "bea", "Buyer bea", "Seller sam", "bea", List.of());
        assertThrows(ServiceException.class, () -> chat.send(forged, "Hello"));
        assertNull(store.findThread("bea", hidden.getId()));
    }

    @Test
    void buyerCannotStartAChatWithADeactivatedSeller() {
        store.transaction(() -> sam.setActive(false)); // the seller's listings are hidden from the marketplace
        SessionManager.login(bea);

        assertThrows(ServiceException.class, () -> chat.openForListing(book.getId()));
    }

    @Test
    void anExistingChat_staysOpenToBothAfterTheListingLeavesTheMarketplace() {
        Conversation c = beaStartsChat("Hello");
        store.transaction(book::remove); // the admin removed the listing

        assertEquals(2, chat.send(c, "What happened to the listing?").entries().size());
        assertEquals(2, chat.openForListing(book.getId()).entries().size());
        SessionManager.login(sam);
        assertEquals(3, chat.send(chat.getMyConversations().get(0), "The admin removed it.").entries().size());
    }

    @Test
    void unknownListingOrOffer_isReportedToTheUser() {
        SessionManager.login(bea);

        assertEquals("This listing no longer exists.",
                assertThrows(ServiceException.class, () -> chat.openForListing("L999")).getMessage());
        assertEquals("This offer no longer exists.",
                assertThrows(ServiceException.class, () -> chat.openForOffer("O999")).getMessage());
    }

    // ---------- Chats screen ----------

    @Test
    void chatsScreen_listsOnlyMyChats_theNewestActivityFirst() {
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sue"));
        beaStartsChat("About the book");                                   // 12:00
        clock.advance(Duration.ofMinutes(1));
        chat.send(chat.openForListing(lamp.getId()), "About the lamp");    // 12:01
        clock.advance(Duration.ofMinutes(1));
        SessionManager.login(ben);
        chat.send(chat.openForListing(book.getId()), "Ben about the book"); // 12:02

        SessionManager.login(bea);
        assertEquals(List.of("Lamp", "Book"), titles(chat.getMyConversations()));
        SessionManager.login(sam);
        assertEquals(List.of("Buyer ben", "Buyer bea"),
                chat.getMyConversations().stream().map(Conversation::otherPartyName).toList());
        SessionManager.login(sue);
        assertEquals(List.of("Lamp"), titles(chat.getMyConversations()));

        clock.advance(Duration.ofMinutes(1));
        store.addOffer(new PriceOffer(book.getId(), "bea", 80));            // 12:03: an offer is activity too
        SessionManager.login(bea);
        assertEquals(List.of("Book", "Lamp"), titles(chat.getMyConversations()));
    }

    @Test
    void chatsScreen_isEmptyForAUserWithNoChats_andAnUnusedThreadComesLast() {
        SessionManager.login(ben);
        assertTrue(chat.getMyConversations().isEmpty());

        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sue"));
        store.getOrCreateThread("ben", lamp.getId()); // a thread with no message or offer yet
        clock.advance(Duration.ofMinutes(1));
        chat.send(chat.openForListing(book.getId()), "Hello");

        assertEquals(List.of("Book", "Lamp"), titles(chat.getMyConversations()));
    }

    private static List<String> titles(List<Conversation> conversations) {
        return conversations.stream().map(c -> c.listing().getTitle()).toList();
    }

    // ---------- Special Requirement 2: the history is retained ----------

    @Test
    void history_cannotBeChangedThroughTheChat() {
        Conversation c = beaStartsChat("Hello");

        assertThrows(UnsupportedOperationException.class, () -> c.entries().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> store.findThread("bea", book.getId()).getMessages().clear());
    }

    @Test
    void chatHistory_survivesARestart(@TempDir Path folder) {
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed();
        Listing charger = saved.getLiveListings().stream()
                .filter(l -> l.getTitle().startsWith("MacBook")).findFirst().orElseThrow();
        SessionManager.login(saved.findUser("alice"));
        ChatService savedChat = new ChatService(saved);
        savedChat.send(savedChat.openForListing(charger.getId()), "Does it fit the 2019 model?");
        LocalDateTime sentAt = saved.now();

        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(file));
        restarted.loadOrSeed();
        SessionManager.login(restarted.findUser("charlie"));
        Conversation afterRestart = new ChatService(restarted).getMyConversations().get(0);

        ChatEntry entry = afterRestart.lastEntry().orElseThrow();
        assertEquals("Does it fit the 2019 model?", entry.content());
        assertEquals("Alice Chan", entry.senderName());
        assertEquals(sentAt, entry.time());
        assertEquals(charger.getId(), afterRestart.listing().getId());
    }

    // ---------- The values the screens get ----------

    @Test
    void chatEntry_isEitherAMessageOrAnOffer() {
        Offer offer = store.addOffer(new PriceOffer(book.getId(), "bea", 80));

        assertThrows(IllegalArgumentException.class,
                () -> new ChatEntry(0, "bea", "Buyer bea", Fixtures.START, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatEntry(0, "bea", "Buyer bea", Fixtures.START, "Hello", offer));
    }

    /**
     * The chat screen marks the open chat in its list (assumptions register 90). Partitions of two
     * copies: of one chat, for the same or the other participant, older or newer; or of another
     * chat, by another buyer or about another listing.
     */
    @Test
    void sameChat_meansTheSameListingAndBuyer_whoeverTheCopyIsFor_andHoweverOld() {
        Conversation beasFirst = beaStartsChat("Hi");
        Conversation beasNewer = chat.send(beasFirst, "Still there?");
        SaleListing lamp = Fixtures.live(Fixtures.sale(store, "sam", 30));
        Conversation beasOtherChat = chat.openForListing(lamp.getId());
        SessionManager.login(sam);
        Conversation samsCopy = chat.getMyConversations().get(0);
        SessionManager.login(ben);
        Conversation bensChat = chat.openForListing(book.getId());

        assertTrue(beasFirst.isSameChat(beasNewer));
        assertTrue(beasFirst.isSameChat(samsCopy));
        assertTrue(samsCopy.isSameChat(beasNewer));
        assertFalse(beasFirst.isSameChat(bensChat));      // another buyer, the same listing
        assertFalse(beasFirst.isSameChat(beasOtherChat)); // the same buyer, another listing
    }
}
