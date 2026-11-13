package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.data.SeedData;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.services.BrowseQuery;
import com.hkust.marketplace.services.BrowseService;
import com.hkust.marketplace.services.Deal;
import com.hkust.marketplace.services.MyDealsService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the practice data (scripts/practice-data) gives a buyer one deal for every
 * button of My Deals and listings in every availability state, so the tool keeps working as
 * the rules change.
 */
class PracticeDataTest {

    private TestClock clock;
    private DataStore store;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        SeedData.populate(store); // the demo data the app starts with
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    private Map<String, Deal> dealsOf(String buyer) {
        SessionManager.login(store.findUser(buyer));
        return new MyDealsService(store).getMyDeals().stream()
                .collect(Collectors.toMap(deal -> deal.listing().getTitle(), deal -> deal));
    }

    private Listing listing(String title) {
        return store.getAllListings().stream().filter(l -> l.getTitle().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void buyerGetsADealForEveryButtonOfMyDeals() {
        PracticeData.addTo(store, "alice", "bob");

        Map<String, Deal> deals = dealsOf("alice");

        assertTrue(deals.get("Desk Fan").canBeMarkedComplete());
        assertTrue(deals.get("Free Notebooks").canBeMarkedComplete());
        assertTrue(deals.get("Camera Tripod").canBeReturned());
        assertTrue(deals.get("HDMI Cable").canBeReturned());
        assertTrue(deals.get("Bluetooth Speaker").hasCounter());
        assertTrue(deals.get("Camping Tent").hasCounter());
        assertTrue(deals.get("Scientific Calculator").isCurrent());
        assertTrue(deals.get("USB-C Hub").canBeReviewed());
        assertTrue(deals.get("Umbrella").offer().isCompleted());
        assertFalse(deals.containsKey("Office Chair")); // declined: only in the chat
        assertEquals(ListingStatus.RESERVED, listing("Desk Fan").getStatus());
        assertEquals(ListingStatus.LENT, listing("Camera Tripod").getStatus());
        assertEquals(ListingStatus.SOLD, listing("USB-C Hub").getStatus());
        assertEquals(ListingStatus.LIVE, listing("Umbrella").getStatus()); // returned, so it can be borrowed again
    }

    @Test
    void shortBorrow_canBeReturnedOnlyUntilItsDueDate() {
        PracticeData.addTo(store, "alice", "bob");
        assertTrue(dealsOf("alice").get("HDMI Cable").canBeReturned());

        clock.advance(Duration.ofMinutes(3)); // the due date

        assertFalse(dealsOf("alice").get("HDMI Cable").canBeReturned());
        assertTrue(dealsOf("alice").get("Camera Tripod").canBeReturned());
    }

    @Test
    void browseShowsAvailableListings_andTheOtherBuyersAsUnavailable() {
        PracticeData.addTo(store, "alice", "bob");
        SessionManager.login(store.findUser("alice"));

        List<Listing> shown = new BrowseService(store).search(BrowseQuery.ALL);

        for (String title : List.of("Mechanical Keyboard", "Yoga Mat", "Rice Cooker", "Lab Coat")) {
            assertTrue(shown.contains(listing(title)), title);
            assertTrue(store.isAvailable(listing(title)), title);
        }
        for (String title : List.of("Study Desk", "Projector", "Winter Jacket")) {
            assertTrue(shown.contains(listing(title)), title);       // shown, greyed out
            assertFalse(store.isAvailable(listing(title)), title);
        }
        assertEquals(ListingStatus.RESERVED, listing("Study Desk").getStatus());
        assertEquals(ListingStatus.LENT, listing("Projector").getStatus());
        assertTrue(store.hasPendingClaim(listing("Winter Jacket").getId()));
    }

    @Test
    void practiceData_isRecognised_andTheClockIsLeftAsItWas() {
        assertFalse(PracticeData.isIn(store));

        PracticeData.addTo(store, "Alice", "bob");

        assertTrue(PracticeData.isIn(store));
        assertEquals(clock.now(), store.now());
    }

    @Test
    void practiceData_isForBuyersOnly_andAddsNothingOtherwise() {
        int before = store.getAllListings().size();

        assertThrows(IllegalArgumentException.class, () -> PracticeData.addTo(store, "charlie", "bob"));
        assertThrows(IllegalArgumentException.class, () -> PracticeData.addTo(store, "alice", "nobody"));

        assertEquals(before, store.getAllListings().size());
        assertFalse(PracticeData.isIn(store));
    }

    @Test
    void practiceData_isSaved_andLoadsAgain(@TempDir Path folder) {
        Path file = folder.resolve("marketplace.dat");
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(file));
        saved.loadOrSeed();
        PracticeData.addTo(saved, "bob", "alice");

        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(file));
        restarted.loadOrSeed();

        assertNull(restarted.getLoadProblem());
        assertTrue(PracticeData.isIn(restarted));
        assertEquals(saved.getAllOffers().size(), restarted.getAllOffers().size());
        assertEquals(saved.getAllListings().size(), restarted.getAllListings().size());
    }
}
