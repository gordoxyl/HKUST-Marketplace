package com.hkust.marketplace.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the UC1 Register as Buyer validation rules (alternative flows A1-A4). */
class AccountValidatorTest {

    // ---------- Username (UC1 A1) ----------

    @Test
    void validateUsername_validAsciiUsername_returnsNull() {
        assertNull(AccountValidator.validateUsername("gordon_lee99"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void validateUsername_emptyOrBlank_returnsError(String username) {
        assertEquals("Username cannot be empty.", AccountValidator.validateUsername(username));
    }

    @Test
    void validateUsername_nonAscii_returnsError() {
        assertNotNull(AccountValidator.validateUsername("陳大文"));
        assertNotNull(AccountValidator.validateUsername("josé"));
    }

    // ---------- Full name (UC1 A2) ----------

    @Test
    void validateFullName_nonAsciiName_isAllowed() {
        // Special Requirement 3: full names may contain any characters
        assertNull(AccountValidator.validateFullName("陳大文"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void validateFullName_emptyOrBlank_returnsError(String fullName) {
        assertEquals("Full name cannot be empty.", AccountValidator.validateFullName(fullName));
    }

    // ---------- Password strength (UC1 A3) ----------

    @Test
    void validatePassword_meetsAllRules_returnsNull() {
        assertNull(AccountValidator.validatePassword("Secure#123"));
    }

    @Test
    void validatePassword_exactlyMinimumLength_returnsNull() {
        // Boundary: 8 characters is the shortest valid password
        assertNull(AccountValidator.validatePassword("Abcde1!x"));
    }

    @Test
    void validatePassword_oneBelowMinimumLength_returnsLengthError() {
        // Boundary: 7 characters, otherwise valid
        assertEquals("Password must be at least 8 characters.",
                AccountValidator.validatePassword("Abcd1!x"));
    }

    @ParameterizedTest(name = "\"{0}\" is missing a {1}")
    @CsvSource({
            "secure#123, uppercase letter",
            "SECURE#123, lowercase letter",
            "Secure#abc, digit",
            "Secure1234, special character"
    })
    void validatePassword_missingOneCharacterType_returnsMatchingError(String password, String missing) {
        String error = AccountValidator.validatePassword(password);

        assertNotNull(error);
        assertTrue(error.contains(missing), "Expected an error about the " + missing + ", got: " + error);
    }

    @Test
    void validatePassword_spaceDoesNotCountAsSpecialCharacter() {
        assertNotNull(AccountValidator.validatePassword("Secure 1234"));
    }

    @Test
    void validatePassword_nonAscii_returnsAsciiError() {
        assertEquals("Password can only contain English letters, digits and ASCII symbols.",
                AccountValidator.validatePassword("Pässword#1"));
    }

    @Test
    void validatePassword_controlCharacter_returnsAsciiError() {
        // Boundary value: the tab comes just below the space, the first printable ASCII character
        assertEquals("Password can only contain English letters, digits and ASCII symbols.",
                AccountValidator.validatePassword("Secure#1\t23"));
    }

    @Test
    void validatePassword_characterJustAfterLowercaseZ_isASymbolNotALetter() {
        // Boundary value: '{' follows 'z' in ASCII
        assertEquals("Password must contain at least one lowercase letter.",
                AccountValidator.validatePassword("SECURE#1{"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void validatePassword_empty_returnsError(String password) {
        assertEquals("Password cannot be empty.", AccountValidator.validatePassword(password));
    }

    @Test
    void aRefusedPassword_isExplainedWithTheRules_anAcceptedOneIsNot() { // UC1 A3, UC3 A2 (regression log R11)
        assertEquals("Password must contain at least one digit. Password rules: " + AccountValidator.PASSWORD_RULES,
                AccountValidator.validatePasswordShowingRules("Password!"));
        assertNull(AccountValidator.validatePasswordShowingRules("Secure#123"));
    }

    // ---------- Confirmation (UC1 A4) ----------

    @Test
    void validateConfirmation_matching_returnsNull() {
        assertNull(AccountValidator.validateConfirmation("Secure#123", "Secure#123"));
    }

    @Test
    void validateConfirmation_different_returnsError() {
        assertEquals("The two passwords do not match.",
                AccountValidator.validateConfirmation("Secure#123", "Secure#12"));
        assertNotNull(AccountValidator.validateConfirmation("Secure#123", null));
        assertEquals("The two passwords do not match.", AccountValidator.validateConfirmation(null, "Secure#123"));
    }
}
