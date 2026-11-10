package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.UserRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Adds listing photos (Create Listings step 8 and the admin's Manage Marketplace Listings:
 * up to 6 photos per listing). A photo chosen from the computer is copied into the app's own
 * storage, encrypted like the rest of the data, and the listing keeps only the photo's id:
 * <pre>{@code
 * List<String> photoIds = new ArrayList<>();
 * for (File chosen : chosenFiles) {
 *     photoIds.add(photoService.importPhoto(chosen.toPath()));
 * }
 * listing.setPhotos(photoIds);   // at most Listing.MAX_PHOTOS; the first is the thumbnail
 * }</pre>
 * Import the photos when the listing is submitted, not when the files are chosen, so a
 * cancelled form stores nothing. Screens show a stored photo with {@code ui.PhotoFrame}.
 */
public class PhotoService {

    /** The image formats every screen can show. */
    public static final String SUPPORTED_FORMATS = "PNG, JPEG, GIF or BMP";

    private final DataStore store;

    public PhotoService() {
        this(DataStore.getInstance());
    }

    public PhotoService(DataStore store) {
        this.store = store;
    }

    /**
     * Copies an image file into the app's storage as a listing photo. Only sellers and the
     * admin post or edit listings, so only they can add photos.
     * @return the photo's id, to put into {@code Listing.setPhotos}
     * @throws ServiceException if the file cannot be read or is not an image in one of the
     *         {@link #SUPPORTED_FORMATS}; nothing is stored then
     */
    public String importPhoto(Path file) {
        AccessControl.requireRole(UserRole.SELLER, UserRole.ADMIN);
        if (file == null || !Files.isRegularFile(file)) {
            throw new ServiceException("The photo file could not be found.");
        }
        String name = file.getFileName().toString();
        byte[] image;
        try {
            image = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ServiceException("The photo '" + name + "' could not be read.");
        }
        if (formatOf(image).isEmpty()) {
            throw new ServiceException("'" + name + "' is not a " + SUPPORTED_FORMATS + " image.");
        }
        return store.addPhoto(image);
    }

    /**
     * The image format, told from how the file's content begins, not from its name, so a
     * renamed file of another kind is not taken for a photo. Empty if it is none of the
     * {@link #SUPPORTED_FORMATS}.
     */
    static Optional<String> formatOf(byte[] data) {
        if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("PNG");
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("JPEG");
        }
        if (startsWith(data, 'G', 'I', 'F', '8', '7', 'a') || startsWith(data, 'G', 'I', 'F', '8', '9', 'a')) {
            return Optional.of("GIF");
        }
        if (startsWith(data, 'B', 'M')) {
            return Optional.of("BMP");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
