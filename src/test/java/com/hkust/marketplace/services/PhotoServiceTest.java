package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.data.EncryptedJsonStorage;
import com.hkust.marketplace.data.StorageException;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.testsupport.Fixtures;
import com.hkust.marketplace.testsupport.TestClock;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for listing photos: importing an image file (who may, which files), and keeping it
 * in the store's photo storage, in memory and in encrypted files. The image files are real
 * ones, written by the JDK's own image library.
 */
class PhotoServiceTest {

    @TempDir
    Path folder;

    private TestClock clock;
    private DataStore store;
    private PhotoService photos;
    private Seller sam;
    private Buyer bea;
    private Admin ada;

    @BeforeEach
    void setUp() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        photos = new PhotoService(store);
        sam = Fixtures.seller(store, "sam");
        bea = Fixtures.buyer(store, "bea");
        ada = Fixtures.admin(store, "ada");
    }

    @AfterEach
    void logOut() {
        SessionManager.logout();
    }

    /** A real 4x3 image file in the given format ("png", "jpg", "gif" or "bmp"). */
    private Path imageFile(String name, String format) throws IOException {
        Path file = folder.resolve(name);
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xA68137);
        assertTrue(ImageIO.write(image, format, file.toFile()), "the JDK can write " + format);
        return file;
    }

    // ---------- Importing (Create Listings step 8) ----------

    @ParameterizedTest(name = "{0} is recognised as {1}")
    @CsvSource({"png, PNG", "jpg, JPEG", "gif, GIF", "bmp, BMP"})
    void sellerImportsAPhoto_inEachSupportedFormat(String format, String expected) throws IOException {
        Path file = imageFile("lamp." + format, format);
        SessionManager.login(sam);

        String photoId = photos.importPhoto(file);

        byte[] original = Files.readAllBytes(file);
        assertArrayEquals(original, store.readPhoto(photoId).orElseThrow()); // stored exactly as chosen
        assertEquals(Optional.of(expected), PhotoService.formatOf(original));
    }

    @Test
    void importedPhotos_becomeTheListingsPhotos_theFirstIsTheThumbnail() throws IOException {
        SessionManager.login(sam);
        String front = photos.importPhoto(imageFile("front.png", "png"));
        String back = photos.importPhoto(imageFile("back.jpg", "jpg"));
        Listing lamp = Fixtures.sale(store, "sam", 50);

        store.transaction(() -> lamp.setPhotos(List.of(front, back)));

        assertNotEquals(front, back);
        assertEquals(front, lamp.getThumbnail());
        assertTrue(store.readPhoto(lamp.getPhotos().get(1)).isPresent());
    }

    @Test
    void adminCanAddPhotos_aBuyerOrAVisitorCannot() throws IOException {
        Path file = imageFile("lamp.png", "png");

        SessionManager.login(ada); // the admin adds and edits listings too (Manage Marketplace Listings)
        assertNotNull(photos.importPhoto(file));

        SessionManager.login(bea);
        assertThrows(AccessDeniedException.class, () -> photos.importPhoto(file));
        SessionManager.logout();
        assertThrows(AccessDeniedException.class, () -> photos.importPhoto(file));
    }

    @Test
    void fileOfAnotherKind_isRefused_evenWhenNamedLikeAPhoto() throws IOException {
        Path notes = Files.writeString(folder.resolve("notes.png"), "These are my lecture notes, not a picture.");
        Path empty = Files.write(folder.resolve("empty.jpg"), new byte[0]);
        SessionManager.login(sam);

        ServiceException refused = assertThrows(ServiceException.class, () -> photos.importPhoto(notes));

        assertEquals("'notes.png' is not a PNG, JPEG, GIF or BMP image.", refused.getMessage());
        assertThrows(ServiceException.class, () -> photos.importPhoto(empty));
    }

    @Test
    void missingFile_orAFolder_isRefused() {
        SessionManager.login(sam);

        ServiceException missing = assertThrows(ServiceException.class,
                () -> photos.importPhoto(folder.resolve("no-such-photo.png")));

        assertEquals("The photo file could not be found.", missing.getMessage());
        assertThrows(ServiceException.class, () -> photos.importPhoto(folder));
        assertThrows(ServiceException.class, () -> photos.importPhoto(null));
    }

    @Test
    void formatOf_looksAtTheContent_notTheName() {
        assertEquals(Optional.of("GIF"), PhotoService.formatOf("GIF87a....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.of("GIF"), PhotoService.formatOf("GIF89a....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), PhotoService.formatOf("GIF90a....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), PhotoService.formatOf(new byte[] {(byte) 0x89, 'P', 'N'})); // cut short
        assertEquals(Optional.empty(), PhotoService.formatOf(new byte[] {(byte) 0xFF, (byte) 0xD8, 0}));
        assertEquals(Optional.empty(), PhotoService.formatOf(new byte[0]));
    }

    // ---------- Keeping the photos (DataStore.addPhoto / readPhoto) ----------

    @Test
    void storedPhoto_cannotBeChangedThroughTheBytesGivenOrReturned() {
        byte[] given = {1, 2, 3};
        String photoId = store.addPhoto(given);

        given[0] = 9;                                  // the caller's array
        store.readPhoto(photoId).orElseThrow()[1] = 9; // a returned copy

        assertArrayEquals(new byte[] {1, 2, 3}, store.readPhoto(photoId).orElseThrow());
    }

    @Test
    void unknownPhoto_isSimplyMissing() {
        assertTrue(store.readPhoto("I999").isEmpty());
        assertTrue(store.readPhoto(null).isEmpty());
        assertThrows(NullPointerException.class, () -> store.addPhoto(null));
    }

    @Test
    void photos_areKeptInEncryptedFiles_andSurviveARestart() throws IOException {
        Path dataFile = folder.resolve("data").resolve("marketplace.dat");
        EncryptedJsonStorage storage = new EncryptedJsonStorage(dataFile);
        DataStore saved = new DataStore(clock, storage);
        saved.loadOrSeed();
        SessionManager.login(saved.findUser("charlie"));
        Path file = imageFile("charger.png", "png");
        String photoId = new PhotoService(saved).importPhoto(file);
        Listing charger = saved.getListingsBySeller("charlie").get(0);
        saved.transaction(() -> charger.setPhotos(List.of(photoId)));

        DataStore restarted = new DataStore(clock, new EncryptedJsonStorage(dataFile));
        restarted.loadOrSeed();

        Listing reloaded = restarted.findListing(charger.getId());
        assertEquals(List.of(photoId), reloaded.getPhotos());
        assertArrayEquals(Files.readAllBytes(file), restarted.readPhoto(reloaded.getThumbnail()).orElseThrow());
        assertTrue(Files.exists(storage.getPhotoFolder().resolve(photoId + ".img")));
        assertNotEquals(photoId, restarted.addPhoto(new byte[] {1})); // ids continue, so no photo is overwritten
    }

    @Test
    void photoFromAnOlderListing_thatIsNotAStoredPhoto_isSimplyMissing() {
        DataStore saved = new DataStore(clock, new EncryptedJsonStorage(folder.resolve("marketplace.dat")));
        Listing book = new SaleListing("sam", "Book", Category.TEXTBOOKS, ItemCondition.USED, "desc",
                MeetupLocation.LIBRARY, 100);
        book.setPhotos(List.of("C:\\photos\\book.jpg")); // a file path, as a demo listing might have had

        assertTrue(saved.readPhoto(book.getThumbnail()).isEmpty()); // shown as "No photo", not an error
    }

    @Test
    void damagedPhotoFile_isReportedWhenRead() throws IOException {
        EncryptedJsonStorage storage = new EncryptedJsonStorage(folder.resolve("marketplace.dat"));
        DataStore saved = new DataStore(clock, storage);
        String photoId = saved.addPhoto(new byte[] {1, 2, 3});
        Files.writeString(storage.getPhotoFolder().resolve(photoId + ".img"), "not an encrypted photo");

        assertThrows(StorageException.class, () -> saved.readPhoto(photoId));
    }
}
