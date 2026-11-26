package com.hkust.marketplace.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.PasswordHasher;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests of DataStore with its encrypted file storage: data is saved after every
 * change, survives a restart unchanged, and an unreadable file never loses data.
 */
class PersistenceTest {

    @TempDir
    Path folder;

    private TestClock clock;
    private Path file;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        file = folder.resolve("marketplace.dat");
    }

    /** A store as the app starts it: load what was saved, or create the demo data. */
    private DataStore start() {
        DataStore store = new DataStore(clock, new EncryptedJsonStorage(file));
        store.loadOrSeed();
        return store;
    }

    private static String json(StoreSnapshot snapshot) throws IOException {
        ObjectMapper mapper = JsonMapping.newMapper();
        return mapper.writeValueAsString(snapshot);
    }

    @Test
    void firstRun_createsTheDemoData_andSavesIt() {
        DataStore store = start();

        assertTrue(Files.exists(file));
        assertNull(store.getLoadProblem());
        assertInstanceOf(Buyer.class, store.findUser("alice"));
    }

    @Test
    void restart_loadsExactlyWhatWasSaved() throws IOException {
        DataStore before = start();
        DataStore after = start(); // a new run reading the same file

        assertEquals(json(before.snapshot()), json(after.snapshot()));

        // Spot checks that do not rely on the JSON mapping itself
        assertInstanceOf(Seller.class, after.findUser("charlie"));
        assertTrue(PasswordHasher.verify(SeedData.DEMO_PASSWORD, after.findUser("alice").getPasswordHash()));
        Listing calculus = after.getAllListings().stream()
                .filter(l -> l.getTitle().startsWith("Calculus")).findFirst().orElseThrow();
        assertInstanceOf(SaleListing.class, calculus);
        assertEquals(ListingStatus.RESERVED, calculus.getStatus());
        assertEquals(180.0, ((SaleListing) calculus).getPrice());
        ChatThread chat = after.findThread("alice", calculus.getId());
        assertEquals(2, chat.getMessages().size());
        OptionalDouble avg = after.getAverageRating("charlie");
        assertTrue(avg.isPresent());
        assertEquals(5.0, avg.getAsDouble());
        LendListing calculator = (LendListing) after.getAllListings().stream()
                .filter(l -> l instanceof LendListing).findFirst().orElseThrow();
        assertEquals(Duration.ofDays(90), calculator.getLendingDuration());
    }

    @Test
    void changes_areSavedAutomatically_andIdsContinueAfterARestart() {
        DataStore store = start();
        Listing lamp = Fixtures.live(Fixtures.giveaway(store, "diana"));
        store.addOffer(new Claim(lamp.getId(), "alice"));

        DataStore restarted = start();

        Listing loaded = restarted.findListing(lamp.getId());
        assertEquals(ListingStatus.LIVE, loaded.getStatus());
        assertEquals(1, restarted.getOffersForListing(lamp.getId()).size());
        Listing next = restarted.addListing(new GiveawayListing("diana", "Chair", Category.FURNITURE,
                ItemCondition.USED, "desc", MeetupLocation.LIBRARY));
        assertNotNull(next.getId());
        assertNotEquals(lamp.getId(), next.getId());
        assertEquals(1, restarted.getAllListings().stream().filter(l -> l.getId().equals(next.getId())).count());
    }

    @Test
    void adminDeactivationAndReviewHiding_surviveARestart() {
        // Regression test for R4 (docs/regression-log.md): the admin screen changed these objects
        // outside a transaction, so they were never saved. These are the calls the screen now makes.
        DataStore store = start();
        User bob = store.findUser("bob");
        Review review = store.getReviewsForSeller("charlie").get(0);

        store.transaction(() -> bob.setActive(false));
        store.transaction(() -> review.setHidden(true));

        DataStore restarted = start();
        assertFalse(restarted.findUser("bob").isActive());
        assertTrue(restarted.getReviewsForSellerIncludingHidden("charlie").get(0).isHidden());
        assertTrue(restarted.getReviewsForSeller("charlie").isEmpty()); // not shown (R5)
        assertTrue(restarted.getAverageRating("charlie").isEmpty()); // and not counted
    }

    @Test
    void stateChangesInsideATransaction_areSaved() {
        DataStore store = start();
        Listing fridge = store.getAllListings().stream()
                .filter(l -> l.getTitle().equals("Mini Fridge")).findFirst().orElseThrow();
        store.transaction(fridge::approve); // the change is on the model object, inside a transaction

        assertEquals(ListingStatus.LIVE, start().findListing(fridge.getId()).getStatus());
    }

    @Test
    void readsDoNotSave_andUnchangedDataIsNotRewritten() throws IOException {
        DataStore store = new DataStore(clock, new EncryptedJsonStorage(file));
        store.getAllListings();
        store.findUser("nobody");
        assertFalse(Files.exists(file), "reads must not save");

        Fixtures.buyer(store, "bea");
        byte[] saved = Files.readAllBytes(file);
        store.transaction(() -> { }); // a transaction that changes nothing
        assertArrayEquals(saved, Files.readAllBytes(file), "unchanged data must not be rewritten");

        Fixtures.buyer(store, "ben");
        assertFalse(java.util.Arrays.equals(saved, Files.readAllBytes(file)));
    }

    @Test
    void nestedTransactions_saveOnceAtTheEnd() {
        CountingStorage storage = new CountingStorage();
        DataStore store = new DataStore(clock, storage);

        store.transaction(() -> {
            Fixtures.buyer(store, "bea");
            Fixtures.buyer(store, "ben");
            Fixtures.seller(store, "sam");
        });

        assertEquals(1, storage.saves);
    }

    @Test
    void unreadableFile_isKept_demoDataIsUsed_andTheProblemIsReported() throws IOException {
        start();
        byte[] damaged = Files.readAllBytes(file);
        damaged[damaged.length / 2] ^= 1;
        Files.write(file, damaged);

        DataStore store = start();

        assertNotNull(store.getLoadProblem());
        assertInstanceOf(Buyer.class, store.findUser("alice")); // demo data instead
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(1, files.filter(f -> f.getFileName().toString().startsWith("marketplace.dat.unreadable-")).count());
        }
        assertNull(start().getLoadProblem(), "the new demo data was saved and loads normally");
    }

    @Test
    void auditEntryWithASnapshot_alsoWritesAnEncryptedTxtFile() {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(file);
        DataStore store = new DataStore(clock, storage);

        AuditLogEntry deletion = store.addAuditLogEntry(
                new AuditLogEntry("com/hkust/marketplace/controllers/admin", "bob", "Deleted account", "Spam", "@bob: 2 listings, 1 review"));
        AuditLogEntry hiding = store.addAuditLogEntry(
                new AuditLogEntry("com/hkust/marketplace/controllers/admin", "carol", "Hid a review", "Offensive", null));

        assertEquals(Optional.of("@bob: 2 listings, 1 review"), store.readAuditSnapshotFile(deletion.getLogReference()));
        assertTrue(Files.exists(storage.getAuditFolder().resolve(deletion.getLogReference() + ".txt")));
        assertTrue(store.readAuditSnapshotFile(hiding.getLogReference()).isEmpty());
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
}
