package com.hkust.marketplace.data;

/** Saved data could not be written, read or decrypted. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
