package com.hkust.marketplace.data;

import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the encryption and the encrypted JSON save file (unit level: no DataStore logic). */
class EncryptedJsonStorageTest {

    @TempDir
    Path folder;

    private StoreSnapshot demoSnapshot() {
        DataStore store = new DataStore(Fixtures.clock());
        SeedData.populate(store);
        return store.snapshot();
    }

    private static String asText(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    }

    // ---------- DataCipher ----------

    @Test
    void cipher_roundTrips_andEncryptsTheSameTextDifferentlyEachTime() {
        DataCipher cipher = DataCipher.defaultCipher();
        byte[] plain = "hello, marketplace".getBytes(StandardCharsets.UTF_8);

        byte[] first = cipher.encrypt(plain);
        byte[] second = cipher.encrypt(plain);

        assertFalse(java.util.Arrays.equals(first, second)); // a new random nonce each time
        assertArrayEquals(plain, cipher.decrypt(first));
        assertArrayEquals(plain, cipher.decrypt(second));
    }

    @Test
    void cipher_refusesChangedData_otherKeys_andOtherFiles() {
        byte[] sealed = DataCipher.defaultCipher().encrypt("secret".getBytes(StandardCharsets.UTF_8));

        byte[] changed = sealed.clone();
        changed[changed.length - 1] ^= 1; // flip one bit
        assertThrows(StorageException.class, () -> DataCipher.defaultCipher().decrypt(changed));
        assertThrows(StorageException.class, () -> new DataCipher("another key").decrypt(sealed));
        assertThrows(StorageException.class,
                () -> DataCipher.defaultCipher().decrypt("{\"plain\": \"json\"}".getBytes(StandardCharsets.UTF_8)));
    }

    // ---------- Save file ----------

    @Test
    void firstRun_hasNothingToLoad() {
        assertTrue(new EncryptedJsonStorage(folder.resolve("marketplace.dat")).load().isEmpty());
    }

    @Test
    void saveFile_isNotReadableText() throws IOException {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));
        storage.save(demoSnapshot());

        String raw = asText(storage.getDataFile());
        for (String visible : new String[]{"alice", "Calculus", "passwordHash", "Demo@1234", "kind", "listings"}) {
            assertFalse(raw.contains(visible), "the file must not show '" + visible + "'");
        }
        assertTrue(raw.startsWith("HKMP1"));
    }

    @Test
    void save_leavesNoTemporaryFile() throws IOException {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));
        storage.save(demoSnapshot());

        try (Stream<Path> files = Files.list(folder)) {
            assertTrue(files.noneMatch(f -> f.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void saveFileFromAnotherKey_isRefused() {
        Path file = folder.resolve("marketplace.dat");
        new EncryptedJsonStorage(file).save(demoSnapshot());

        assertThrows(StorageException.class, () -> new EncryptedJsonStorage(file, new DataCipher("another key")).load());
    }

    @Test
    void setAsideUnreadable_keepsTheFileUnderANewName() throws IOException {
        Path file = folder.resolve("marketplace.dat");
        Files.writeString(file, "not a save file");
        EncryptedJsonStorage storage = new EncryptedJsonStorage(file);

        String movedTo = storage.setAsideUnreadable();

        assertFalse(Files.exists(file));
        assertTrue(Files.exists(Path.of(movedTo)));
        assertEquals("not a save file", Files.readString(Path.of(movedTo)));
    }

    // ---------- Audit snapshots ----------

    @Test
    void auditSnapshot_isAnEncryptedTxtFile() throws IOException {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));

        storage.saveAuditSnapshot("G12", "Deleted account @bob with 3 listings");

        Path file = storage.getAuditFolder().resolve("G12.txt");
        assertTrue(Files.exists(file));
        assertFalse(asText(file).contains("bob"));
        assertEquals("Deleted account @bob with 3 listings", storage.loadAuditSnapshot("G12").orElseThrow());
        assertTrue(storage.loadAuditSnapshot("G13").isEmpty());
    }

    @Test
    void auditSnapshot_refusesReferencesThatCouldLeaveTheFolder() {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));
        assertThrows(IllegalArgumentException.class, () -> storage.saveAuditSnapshot("../outside", "x"));
        assertThrows(IllegalArgumentException.class, () -> storage.loadAuditSnapshot("..\\outside"));
    }

    // ---------- Listing photos ----------

    /** The first bytes of every PNG file, followed by some picture data. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8};

    @Test
    void photo_isAnEncryptedFile_thatCannotBeOpenedAsAPicture() throws IOException {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));

        storage.savePhoto("I31", PNG);

        Path file = storage.getPhotoFolder().resolve("I31.img");
        assertEquals(folder.resolve("marketplace-photos"), storage.getPhotoFolder());
        assertTrue(Files.exists(file));
        assertFalse(asText(file).contains("PNG")); // not the picture itself
        assertArrayEquals(PNG, storage.loadPhoto("I31").orElseThrow());
        assertTrue(storage.loadPhoto("I32").isEmpty());
        try (Stream<Path> files = Files.list(storage.getPhotoFolder())) {
            assertEquals(1, files.count()); // no temporary file is left behind
        }
    }

    @Test
    void photoThatWasChangedOrSavedWithAnotherKey_isRefused() throws IOException {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));
        storage.savePhoto("I31", PNG);
        Path file = storage.getPhotoFolder().resolve("I31.img");

        EncryptedJsonStorage otherKey = new EncryptedJsonStorage(folder.resolve("marketplace.dat"), new DataCipher("another key"));
        assertThrows(StorageException.class, () -> otherKey.loadPhoto("I31"));

        byte[] changed = Files.readAllBytes(file);
        changed[changed.length - 1] ^= 1;
        Files.write(file, changed);
        assertThrows(StorageException.class, () -> storage.loadPhoto("I31"));
    }

    @Test
    void photo_refusesIdsThatCouldLeaveTheFolder() {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));
        assertThrows(IllegalArgumentException.class, () -> storage.savePhoto("../outside", PNG));
        assertThrows(IllegalArgumentException.class, () -> storage.loadPhoto("..\\outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.loadPhoto(null));
    }

    @Test
    void devData_keepsItsPhotosInItsOwnFolder() {
        EncryptedJsonStorage dev = new EncryptedJsonStorage(folder.resolve("marketplace-dev.dat"));
        assertEquals(folder.resolve("marketplace-dev-photos"), dev.getPhotoFolder());
    }
}
