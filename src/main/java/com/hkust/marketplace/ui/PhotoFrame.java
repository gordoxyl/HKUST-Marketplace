package com.hkust.marketplace.ui;

import com.hkust.marketplace.data.DataStore;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A frame of a fixed size that shows one listing photo, the same everywhere a photo appears:
 * the thumbnail on a listing card (Task 1.3), the photos on the listing detail screen (Task
 * 1.4) and in the admin's review (Task 3.2).
 * <pre>{@code
 * for (String photoId : listing.getPhotos()) {
 *     gallery.getChildren().add(new PhotoFrame(photoId, 240, 180));
 * }
 * }</pre>
 * The photo is read from the app's encrypted storage and decoded in the background, so a
 * screen with many photos appears at once. Until it is ready, and when the listing has no
 * photo or its file is missing or unreadable, the frame says "No photo".
 */
public class PhotoFrame extends StackPane {

    /** A frame for a photo in the app's store. */
    public PhotoFrame(String photoId, double width, double height) {
        this(DataStore.getInstance(), photoId, width, height);
    }

    /**
     * @param store   the store that holds the photo
     * @param photoId a photo id from {@code PhotoService.importPhoto}, or null for no photo
     */
    public PhotoFrame(DataStore store, String photoId, double width, double height) {
        getStyleClass().add("listing-card-thumbnail");
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
        Label none = new Label("No photo");
        none.getStyleClass().add("listing-card-date");
        getChildren().add(none);

        if (photoId != null) {
            CompletableFuture
                    .supplyAsync(() -> load(store, photoId, width, height))
                    .thenAccept(loaded -> loaded.ifPresent(image -> Platform.runLater(() -> {
                        none.setVisible(false);
                        getChildren().add(new ImageView(image));
                    })));
        }
    }

    /** Reads and decodes the photo to fit the frame, keeping its proportions. Empty if there is nothing to show. */
    private static Optional<Image> load(DataStore store, String photoId, double width, double height) {
        try {
            return store.readPhoto(photoId)
                    .map(bytes -> new Image(new ByteArrayInputStream(bytes), width, height, true, true))
                    .filter(image -> !image.isError());
        } catch (RuntimeException e) {
            // A damaged photo file: the listing is still shown, without the photo
            System.err.println("[PhotoFrame] Photo " + photoId + " could not be shown: " + e.getMessage());
            return Optional.empty();
        }
    }
}
