package com.hkust.marketplace.data;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Encrypts saved files so they are never stored as readable plain text (the data and every
 * listing's information must be "securely stored").
 *
 * <p>Uses AES-GCM from the JDK, which also detects any change to an encrypted file: a file
 * that was edited, damaged or encrypted with another key fails to decrypt instead of giving
 * wrong data. A file is: "HKMP1" (format marker) + 12-byte random nonce + ciphertext and tag.
 *
 * <p><b>Key.</b> The key is built into the code (team decision, see the assumptions register):
 * it is derived from {@link #DEFAULT_PASSPHRASE}, so every copy of the app can read files saved
 * by any other copy, and anyone with the source code could decrypt them. Changing the
 * passphrase makes old files unreadable.
 */
public final class DataCipher {

    /** The passphrase the built-in key is derived from. */
    private static final String DEFAULT_PASSPHRASE = "HKUST Marketplace / COMP 3111 Group 44 / save-file key, version 1";

    private static final byte[] FORMAT_MARKER = "HKMP1".getBytes(StandardCharsets.US_ASCII);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DataCipher DEFAULT = new DataCipher(DEFAULT_PASSPHRASE);

    private final SecretKey key;

    /** A cipher with a key derived from the passphrase. The app uses {@link #defaultCipher()}; tests try other keys. */
    public DataCipher(String passphrase) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES"); // 256-bit AES key
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is built into every Java runtime.", e);
        }
    }

    /** The cipher with the built-in key. */
    public static DataCipher defaultCipher() {
        return DEFAULT;
    }

    /** Encrypts the bytes. Encrypting the same bytes twice gives different results (new random nonce). */
    public byte[] encrypt(byte[] plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(FORMAT_MARKER);
            byte[] sealed = cipher.doFinal(plain);

            byte[] out = new byte[FORMAT_MARKER.length + NONCE_BYTES + sealed.length];
            System.arraycopy(FORMAT_MARKER, 0, out, 0, FORMAT_MARKER.length);
            System.arraycopy(nonce, 0, out, FORMAT_MARKER.length, NONCE_BYTES);
            System.arraycopy(sealed, 0, out, FORMAT_MARKER.length + NONCE_BYTES, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is built into every Java runtime.", e);
        }
    }

    /**
     * Decrypts bytes made by {@link #encrypt}.
     * @throws StorageException if they are not such a file, or were changed, damaged or encrypted with another key
     */
    public byte[] decrypt(byte[] data) {
        int headerLength = FORMAT_MARKER.length + NONCE_BYTES;
        if (data.length <= headerLength || !Arrays.equals(Arrays.copyOf(data, FORMAT_MARKER.length), FORMAT_MARKER)) {
            throw new StorageException("The file is not a HKUST Marketplace save file.");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, data, FORMAT_MARKER.length, NONCE_BYTES));
            cipher.updateAAD(FORMAT_MARKER);
            return cipher.doFinal(data, headerLength, data.length - headerLength);
        } catch (AEADBadTagException e) {
            throw new StorageException("The file was changed or damaged, or it was saved with a different key.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is built into every Java runtime.", e);
        }
    }
}
