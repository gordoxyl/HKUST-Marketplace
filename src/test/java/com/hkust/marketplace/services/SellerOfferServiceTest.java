package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Claim;
import com.hkust.marketplace.models.GiveawayListing;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.LendListing;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2.5 Manage Offers &amp; Requests: the seller's accept, decline and counter actions, and
 * the ownership rules around them. The cross-role half of accepting is covered by
 * {@link DealRulesTest}; here the focus is the seller's side of the screen.
 */
class SellerOfferServiceTest {

    private TestClock clock;
    private DataStore store;
    private SellerOfferService service;
    private Seller sam;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        service = new SellerOfferService(store);
        sam = Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
        Fixtures.buyer(store, "ben");
        SessionManager.login(sam);
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    private SaleListing liveSale() {
        return Fixtures.live(Fixtures.sale(store, "sam", 100));
    }

    private LendListing liveLend() {
        return Fixtures.live(Fixtures.lend(store, "sam", Duration.ofDays(7)));
    }

    private Offer priceOffer(Listing listing, String buyer, double price) {
        return store.addOffer(new PriceOffer(listing.getId(), buyer, price));
    }

    private BorrowRequest borrowRequest(Listing listing, String buyer) {
        return (BorrowRequest) store.addOffer(new BorrowRequest(listing.getId(), buyer,
                clock.now().plusMinutes(1), clock.now().plusDays(2)));
    }

    private String buyerNews(String username) {
        return store.getNotificationsFor(username).stream()
                .map(notification -> notification.getContent())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    // ---------- Step 2: the inbox ----------

    @Test
    void openOffers_listsOnlyThisSellersOpenOnes() {
        SaleListing mine = liveSale();
        Fixtures.seller(store, "sue");
        SaleListing hers = Fixtures.live(Fixtures.sale(store, "sue", 50));

        Offer pending = priceOffer(mine, "bea", 80);
        PriceOffer countered = (PriceOffer) priceOffer(mine, "ben", 90);
        countered.counter(95);
        Offer declined = priceOffer(mine, "bea", 70);
        declined.decline();
        Offer onSomebodyElses = priceOffer(hers, "bea", 40);

        assertEquals(List.of(pending, countered), service.openOffers());
        assertFalse(service.openOffers().contains(declined));
        assertFalse(service.openOffers().contains(onSomebodyElses));
    }

    @Test
    void aSellerWithoutOffers_getsAnEmptyInbox() {
        assertTrue(service.openOffers().isEmpty());
    }

    // ---------- 3.3: accept ----------

    @Test
    void acceptingAnOffer_reservesTheListing_declinesTheOthers_andNotifiesTheWinner() {
        SaleListing listing = liveSale();
        Offer winner = priceOffer(listing, "bea", 80);
        Offer other = priceOffer(listing, "ben", 90);

        service.accept(winner.getId());

        assertEquals(OfferStatus.ACCEPTED, winner.getStatus());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertEquals(OfferStatus.DECLINED, other.getStatus());
        assertTrue(buyerNews("bea").contains(sam.getFullName() + " accepted your price offer for 'Book'"));
        assertTrue(buyerNews("ben").contains("declined because the seller accepted another offer"));
    }

    @Test
    void acceptingACounteredOffer_isRefused() {
        SaleListing listing = liveSale();
        PriceOffer offer = (PriceOffer) priceOffer(listing, "bea", 80);
        offer.counter(90);

        assertEquals("Only a pending price offer can be accepted.",
                assertThrows(ServiceException.class, () -> service.accept(offer.getId())).getMessage());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void acceptingAnOfferOnARemovedListing_isRefused() {
        SaleListing listing = liveSale();
        Offer offer = priceOffer(listing, "bea", 80);
        listing.remove();

        assertEquals("This listing is no longer available.",
                assertThrows(ServiceException.class, () -> service.accept(offer.getId())).getMessage());
        assertEquals(OfferStatus.PENDING, offer.getStatus());
    }

    @Test
    void acceptingAClaim_worksEvenThoughThePendingClaimMakesTheGiveawayUnavailable() {
        // Regression R2: the listing status is Live even though isAvailable() is false
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        Offer claim = store.addOffer(new Claim(lamp.getId(), "bea"));

        service.accept(claim.getId());

        assertEquals(OfferStatus.ACCEPTED, claim.getStatus());
        assertEquals(ListingStatus.RESERVED, lamp.getStatus());
    }

    // ---------- 3.4: decline ----------

    @Test
    void decliningAnOffer_tellsTheBuyer() {
        SaleListing listing = liveSale();
        Offer offer = priceOffer(listing, "bea", 80);

        service.decline(offer.getId());

        assertEquals(OfferStatus.DECLINED, offer.getStatus());
        assertTrue(buyerNews("bea").contains(sam.getFullName() + " declined your price offer for 'Book'"));
    }

    @Test
    void aCounteredOfferCanStillBeDeclined() {
        SaleListing listing = liveSale();
        PriceOffer offer = (PriceOffer) priceOffer(listing, "bea", 80);
        offer.counter(95);

        service.decline(offer.getId());

        assertEquals(OfferStatus.DECLINED, offer.getStatus());
    }

    @Test
    void anAcceptedOfferCanNoLongerBeDeclined() {
        SaleListing listing = liveSale();
        Offer offer = priceOffer(listing, "bea", 80);
        service.accept(offer.getId());

        assertEquals("This price offer is no longer open.",
                assertThrows(ServiceException.class, () -> service.decline(offer.getId())).getMessage());
        assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
    }

    // ---------- 3.5: counter ----------

    @Test
    void counteringAPriceOffer_recordsTheCounter_andNotifiesTheBuyer() {
        SaleListing listing = liveSale();
        PriceOffer offer = (PriceOffer) priceOffer(listing, "bea", 80);

        service.counterPrice(offer.getId(), 95);

        assertEquals(OfferStatus.COUNTERED, offer.getStatus());
        assertEquals(95, offer.getCounterPrice());
        assertTrue(buyerNews("bea").contains("countered your price offer for 'Book' with HK$ 95"));
    }

    @Test
    void counteringABorrowRequest_recordsTheNewDates_andNotifiesTheBuyer() {
        LendListing calculator = liveLend();
        BorrowRequest request = borrowRequest(calculator, "bea");
        LocalDateTime start = clock.now().plusHours(1);
        LocalDateTime end = clock.now().plusHours(2);

        service.counterBorrow(request.getId(), start, end);

        assertEquals(OfferStatus.COUNTERED, request.getStatus());
        assertEquals(start, request.getCounterStartDate());
        assertEquals(end, request.getCounterEndDate());
        assertTrue(buyerNews("bea").contains("countered your borrow request for 'Calculator'"));
        // Regression R9: the buyer reads the times to the second, not to the minute
        assertTrue(buyerNews("bea").contains(BorrowRequest.formatBorrowTime(start)
                + " – " + BorrowRequest.formatBorrowTime(end)));
    }

    @Test
    void aClaimCannotBeCountered() {
        GiveawayListing lamp = Fixtures.live(Fixtures.giveaway(store, "sam"));
        Offer claim = store.addOffer(new Claim(lamp.getId(), "bea"));

        assertEquals("Claims cannot be countered.",
                assertThrows(ServiceException.class, () -> service.counterPrice(claim.getId(), 10)).getMessage());
        assertEquals("Claims cannot be countered.",
                assertThrows(ServiceException.class, () -> service.counterBorrow(claim.getId(),
                        clock.now().plusMinutes(1), clock.now().plusMinutes(2))).getMessage());
        assertEquals(OfferStatus.PENDING, claim.getStatus());
    }

    @Test
    void onlyAPendingOfferCanBeCountered() {
        SaleListing listing = liveSale();
        Offer offer = priceOffer(listing, "bea", 80);
        offer.decline();

        assertEquals("Only a pending price offer can be countered.",
                assertThrows(ServiceException.class, () -> service.counterPrice(offer.getId(), 90)).getMessage());
        assertEquals(OfferStatus.DECLINED, offer.getStatus());
    }

    @Test
    void aCounterPriceMustBePositive() {
        SaleListing listing = liveSale();
        PriceOffer offer = (PriceOffer) priceOffer(listing, "bea", 80);

        for (double bad : new double[]{0, -5, Double.NaN}) {
            assertEquals("The counter price must be a positive amount, such as 150 or 99.50.",
                    assertThrows(ServiceException.class,
                            () -> service.counterPrice(offer.getId(), bad)).getMessage());
        }
        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertNull(offer.getCounterPrice());
    }

    @Test
    void aTypedCounterPrice_mustBeAPlainPositiveAmount() {
        SaleListing listing = liveSale();
        PriceOffer offer = (PriceOffer) priceOffer(listing, "bea", 80);

        // Equivalence partitions: nothing, not a number, and zero or below
        for (String bad : new String[]{null, "", "   ", "abc", "1e3", "-5", "1,000", "99.999"}) {
            assertEquals("The counter price must be a positive amount, such as 150 or 99.50.",
                    assertThrows(ServiceException.class,
                            () -> service.counterPrice(offer.getId(), bad)).getMessage());
        }
        assertEquals("The counter price must be a positive amount, such as 150 or 99.50.",
                assertThrows(ServiceException.class,
                        () -> service.counterPrice(offer.getId(), "0")).getMessage());
        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertNull(offer.getCounterPrice());

        // Boundary and a plain amount with cents
        service.counterPrice(offer.getId(), "175");
        assertEquals(175, offer.getCounterPrice());

        PriceOffer other = (PriceOffer) priceOffer(listing, "ben", 60);
        service.counterPrice(other.getId(), "99.50");
        assertEquals(99.50, other.getCounterPrice());
    }

    @Test
    void aCounterPeriodIsCheckedInTheUseCaseOrder() {
        LendListing calculator = liveLend();
        BorrowRequest request = borrowRequest(calculator, "bea");
        LocalDateTime start = clock.now().plusMinutes(1);

        assertEquals("Please enter both the start and the end of the counter period, "
                        + "each with a date and a time.",
                assertThrows(ServiceException.class,
                        () -> service.counterBorrow(request.getId(), start, null)).getMessage());
        assertEquals("The counter period cannot start in the past.",
                assertThrows(ServiceException.class, () -> service.counterBorrow(request.getId(),
                        clock.now().minusMinutes(1), start.plusHours(1))).getMessage());
        assertEquals("The counter period must end at least 5 seconds after it starts.",
                assertThrows(ServiceException.class, () -> service.counterBorrow(request.getId(),
                        start, start.plusSeconds(4))).getMessage());
        assertEquals(OfferStatus.PENDING, request.getStatus());
    }

    @Test
    void aCounterMustUseTheRightKindOfTerms() {
        SaleListing listing = liveSale();
        Offer price = priceOffer(listing, "bea", 80);
        LendListing calculator = liveLend();
        Offer borrow = borrowRequest(calculator, "ben");

        assertEquals("Only a borrow request can be countered with dates.",
                assertThrows(ServiceException.class, () -> service.counterBorrow(price.getId(),
                        clock.now().plusMinutes(1), clock.now().plusMinutes(2))).getMessage());
        assertEquals("Only a price offer can be countered with a price.",
                assertThrows(ServiceException.class,
                        () -> service.counterPrice(borrow.getId(), 50)).getMessage());
    }

    // ---------- Ownership and access ----------

    @Test
    void anotherSellersOffer_isRefused() {
        Fixtures.seller(store, "sue");
        SaleListing hers = Fixtures.live(Fixtures.sale(store, "sue", 50));
        Offer offer = priceOffer(hers, "bea", 40);

        String expected = "This offer is not on one of your listings.";
        assertEquals(expected,
                assertThrows(ServiceException.class, () -> service.accept(offer.getId())).getMessage());
        assertEquals(expected,
                assertThrows(ServiceException.class, () -> service.decline(offer.getId())).getMessage());
        assertEquals(expected,
                assertThrows(ServiceException.class, () -> service.counterPrice(offer.getId(), 45)).getMessage());
        assertEquals(OfferStatus.PENDING, offer.getStatus());
    }

    @Test
    void onlyALoggedInSellerCanManageOffers() {
        SaleListing listing = liveSale();
        Offer offer = priceOffer(listing, "bea", 80);

        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> service.openOffers());
        assertThrows(AccessDeniedException.class, () -> service.accept(offer.getId()));

        Buyer bea = (Buyer) store.findUser("bea");
        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class, () -> service.openOffers());
        assertThrows(AccessDeniedException.class, () -> service.decline(offer.getId()));

        assertEquals(OfferStatus.PENDING, offer.getStatus());
    }

    @Test
    void aMissingOffer_isReported() {
        assertEquals("This offer is no longer available.",
                assertThrows(ServiceException.class, () -> service.accept("no-such-id")).getMessage());
        assertEquals("This offer is no longer available.",
                assertThrows(ServiceException.class, () -> service.decline("no-such-id")).getMessage());
    }

    @Test
    void theBuyerNotices_everySellerReplyInTheNotificationCategoryOfferUpdate() {
        SaleListing listing = liveSale();
        Offer offer = priceOffer(listing, "bea", 80);

        service.decline(offer.getId());

        assertTrue(store.getNotificationsFor("bea").stream()
                .anyMatch(n -> n.getCategory() == NotificationCategory.OFFER_UPDATE));
    }
}
