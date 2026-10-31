package com.hkust.marketplace.services;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes and verifies passwords so they are never stored in plaintext
 * (UC1 Special Requirement 1, UC2 Special Requirement 1).
 *
 * Uses salted PBKDF2 from the JDK, so no extra library is needed.
 * A stored hash looks like: pbkdf2$&lt;iterations&gt;$&lt;salt&gt;$&lt;hash&gt; (salt and hash in Base64).
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
        // Utility class
    }

    /**
     * Returns a salted hash of the password. Hashing the same password twice
     * gives different results, because each hash gets a new random salt.
     */
    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /**
     * Checks a password against a hash made by {@link #hash(String)}.
     * Returns false (never throws) for a null input or a value that is not
     * such a hash, e.g. a password that was stored in plaintext by mistake.
     */
    public static boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            // Constant-time comparison, so timing does not leak how much matched
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            // Bad number or bad Base64 in the stored value
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            // PBKDF2WithHmacSHA256 is built into every Java 17+ runtime
            throw new IllegalStateException("Password hashing is not available.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
