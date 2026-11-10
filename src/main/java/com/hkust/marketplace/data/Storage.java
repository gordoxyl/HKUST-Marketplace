package com.hkust.marketplace.data;

import java.util.Optional;

/**
 * Where a DataStore's data is kept between runs. DataStore depends only on this interface,
 * not on files or formats, so how data is stored can change without touching the rest of
 * the system (Lecture 16: encapsulate the implementation environment behind a bridge).
 * The app uses {@link EncryptedJsonStorage}; tests use a store without storage.
 */
public interface Storage {

    /**
     * The saved data, or empty if nothing has been saved yet (the first run).
     * @throws StorageException if saved data exists but cannot be read
     */
    Optional<StoreSnapshot> load();

    /** Saves the data, replacing what was saved before. */
    void save(StoreSnapshot snapshot);

    /**
     * Moves saved data that cannot be read out of the way, keeping it, so the app can start
     * fresh without losing the file.
     * @return a description of where the file now is
     */
    String setAsideUnreadable();

    /** Saves a deleted account's data snapshot as its own file (Audit Log, class diagram note 2). */
    void saveAuditSnapshot(String logReference, String snapshot);

    /** The snapshot saved for that audit log entry, or empty if there is none. */
    Optional<String> loadAuditSnapshot(String logReference);

    /** Saves a listing photo as its own file, under the photo's id. A saved photo is never changed. */
    void savePhoto(String photoId, byte[] image);

    /**
     * The photo saved under that id, or empty if there is none.
     * @throws StorageException if the photo's file exists but cannot be read
     */
    Optional<byte[]> loadPhoto(String photoId);
}
