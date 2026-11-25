package com.hkust.marketplace.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void verify_correctPassword_returnsTrue() {
        String stored = PasswordHasher.hash("Secure#123");

        assertTrue(PasswordHasher.verify("Secure#123", stored));
    }

    @Test
    void verify_wrongPassword_returnsFalse() {
        String stored = PasswordHasher.hash("Secure#123");

        assertFalse(PasswordHasher.verify("Secure#124", stored));
        assertFalse(PasswordHasher.verify("secure#123", stored)); // case matters
    }

    @Test
    void hash_doesNotContainPlaintextPassword() {
        String stored = PasswordHasher.hash("Secure#123");

        assertNotEquals("Secure#123", stored);
        assertFalse(stored.contains("Secure#123"));
    }

    @Test
    void hash_samePasswordTwice_givesDifferentHashesBecauseOfSalt() {
        String first = PasswordHasher.hash("Secure#123");
        String second = PasswordHasher.hash("Secure#123");

        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify("Secure#123", first));
        assertTrue(PasswordHasher.verify("Secure#123", second));
    }

    @Test
    void verify_storedValueIsPlaintextOrMalformed_returnsFalse() {
        // A password stored in plaintext must never be accepted as a hash
        assertFalse(PasswordHasher.verify("password1", "password1"));
        assertFalse(PasswordHasher.verify("x", "pbkdf2$notANumber$abc$def"));
        assertFalse(PasswordHasher.verify("x", "pbkdf2$1000$***$***"));
        assertFalse(PasswordHasher.verify("x", "md5$1000$abc$def")); // four parts, but another method
    }

    @Test
    void verify_nullInputs_returnsFalse() {
        String stored = PasswordHasher.hash("Secure#123");

        assertFalse(PasswordHasher.verify(null, stored));
        assertFalse(PasswordHasher.verify("Secure#123", null));
    }

    @Test
    void hash_nullPassword_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }
}
