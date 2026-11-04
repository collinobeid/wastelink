package com.waste.link.domain

import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import com.waste.link.domain.validation.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    // =========================================================================
    // 1. Required Field Validation
    // =========================================================================

    @Test
    fun `validateRequired rejects null and blank inputs and trims valid input`() {
        assertEquals(ValidationError.REQUIRED_FIELD, AuthValidator.validateRequired(null).errorOrNull())
        assertEquals(ValidationError.REQUIRED_FIELD, AuthValidator.validateRequired("").errorOrNull())
        assertEquals(ValidationError.REQUIRED_FIELD, AuthValidator.validateRequired("   ").errorOrNull())
        assertEquals("Kinondoni", AuthValidator.validateRequired("  Kinondoni  ").getOrNull())
    }

    // =========================================================================
    // 2. Full Name Validation
    // =========================================================================

    @Test
    fun `validateFullName accepts legitimate names and normalizes internal whitespace`() {
        val result1 = AuthValidator.validateFullName("  Amina   Nyerere-Mwinyi  ")
        assertTrue(result1.isValid)
        assertEquals("Amina Nyerere-Mwinyi", result1.getOrNull())

        val result2 = AuthValidator.validateFullName("Jean-Luc O'Connor Jr.")
        assertTrue(result2.isValid)
        assertEquals("Jean-Luc O'Connor Jr.", result2.getOrNull())

        val result3 = AuthValidator.validateFullName("Renée Ndayishimiye")
        assertTrue(result3.isValid)
        assertEquals("Renée Ndayishimiye", result3.getOrNull())
    }

    @Test
    fun `validateFullName rejects blank, short, long, placeholder, and symbol-containing inputs`() {
        assertEquals(ValidationError.REQUIRED_FIELD, AuthValidator.validateFullName(null).errorOrNull())
        assertEquals(ValidationError.REQUIRED_FIELD, AuthValidator.validateFullName("   ").errorOrNull())
        assertEquals(ValidationError.FULL_NAME_TOO_SHORT, AuthValidator.validateFullName("A").errorOrNull())
        assertEquals(
            ValidationError.FULL_NAME_TOO_LONG,
            AuthValidator.validateFullName("A".repeat(81)).errorOrNull()
        )
        assertEquals(
            ValidationError.FULL_NAME_RESERVED_PLACEHOLDER,
            AuthValidator.validateFullName("User").errorOrNull()
        )
        assertEquals(
            ValidationError.FULL_NAME_RESERVED_PLACEHOLDER,
            AuthValidator.validateFullName("Not provided").errorOrNull()
        )
        assertEquals(
            ValidationError.FULL_NAME_RESERVED_PLACEHOLDER,
            AuthValidator.validateFullName("Admin").errorOrNull()
        )
        assertEquals(
            ValidationError.FULL_NAME_INVALID_CHARACTERS,
            AuthValidator.validateFullName("John123").errorOrNull()
        )
        assertEquals(
            ValidationError.FULL_NAME_INVALID_CHARACTERS,
            AuthValidator.validateFullName("<script>alert(1)</script>").errorOrNull()
        )
    }

    // =========================================================================
    // 3. Email Validation
    // =========================================================================

    @Test
    fun `validateEmail accepts valid emails and normalizes to lowercase`() {
        val result = AuthValidator.validateEmail("  Resident.User+Tag@WasteLink.co.tz ")
        assertTrue(result.isValid)
        assertEquals("resident.user+tag@wastelink.co.tz", result.getOrNull())
    }

    @Test
    fun `validateEmail rejects malformed email addresses`() {
        assertEquals(ValidationError.REQUIRED_FIELD, AuthValidator.validateEmail("").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail("plainaddress").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail("@missinglocal.com").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail("user@").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail("user..double@domain.com").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail(".leadingdot@domain.com").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail("user@domain..com").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail("user@singlelabel").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID_FORMAT, AuthValidator.validateEmail("user@domain.c").errorOrNull())
        assertEquals(
            ValidationError.EMAIL_TOO_LONG,
            AuthValidator.validateEmail("${"a".repeat(245)}@example.com").errorOrNull()
        )
    }

    // =========================================================================
    // 4. Phone Number Validation & International E.164 Normalization
    // =========================================================================

    @Test
    fun `validateAndNormalizePhone normalizes Tanzanian and non-Tanzanian international numbers to E164`() {
        // Tanzanian local national number (default +255)
        assertEquals(
            "+255712345678",
            AuthValidator.validateAndNormalizePhone("0712 345 678").getOrNull()
        )
        // Tanzanian explicit E.164
        assertEquals(
            "+255655123456",
            AuthValidator.validateAndNormalizePhone("+255 655-123-456").getOrNull()
        )
        // Ugandan international number (+256)
        assertEquals(
            "+256772123456",
            AuthValidator.validateAndNormalizePhone("+256 772 123456").getOrNull()
        )
        // Kenyan international number (+254)
        assertEquals(
            "+254712345678",
            AuthValidator.validateAndNormalizePhone("+254-712-345678").getOrNull()
        )
        // Rwandan international number (+250)
        assertEquals(
            "+250788123456",
            AuthValidator.validateAndNormalizePhone("+250 (788) 123 456").getOrNull()
        )
        // United States / Canada number (+1)
        assertEquals(
            "+14155552671",
            AuthValidator.validateAndNormalizePhone("+1 (415) 555-2671").getOrNull()
        )
        // United Kingdom number (+44)
        assertEquals(
            "+447911123456",
            AuthValidator.validateAndNormalizePhone("+44 7911 123456").getOrNull()
        )
        // India number (+91)
        assertEquals(
            "+919876543210",
            AuthValidator.validateAndNormalizePhone("+91 98765 43210").getOrNull()
        )
        // International 00 prefix (Germany +49)
        assertEquals(
            "+4915123456789",
            AuthValidator.validateAndNormalizePhone("0049 1512 3456789").getOrNull()
        )
        // Bare country code + subscriber digits
        assertEquals(
            "+254712345678",
            AuthValidator.validateAndNormalizePhone("254712345678").getOrNull()
        )
        // Local number with custom default country code (+256)
        assertEquals(
            "+256772123456",
            AuthValidator.validateAndNormalizePhone("0772123456", defaultCountryCallingCode = "+256").getOrNull()
        )
    }

    @Test
    fun `validateAndNormalizePhone rejects invalid, placeholder, too short, and too long numbers`() {
        assertEquals(
            ValidationError.REQUIRED_FIELD,
            AuthValidator.validateAndNormalizePhone(null).errorOrNull()
        )
        assertEquals(
            ValidationError.REQUIRED_FIELD,
            AuthValidator.validateAndNormalizePhone("Not provided").errorOrNull()
        )
        assertEquals(
            ValidationError.PHONE_INVALID_FORMAT,
            AuthValidator.validateAndNormalizePhone("+0123456789").errorOrNull()
        )
        assertEquals(
            ValidationError.PHONE_INVALID_FORMAT,
            AuthValidator.validateAndNormalizePhone("+255712ABC678").errorOrNull()
        )
        assertEquals(
            ValidationError.PHONE_TOO_SHORT,
            AuthValidator.validateAndNormalizePhone("+255123").errorOrNull()
        )
        assertEquals(
            ValidationError.PHONE_TOO_SHORT,
            AuthValidator.validateAndNormalizePhone("07123").errorOrNull()
        )
        assertEquals(
            ValidationError.PHONE_TOO_LONG,
            AuthValidator.validateAndNormalizePhone("+1234567890123456").errorOrNull()
        )
    }

    // =========================================================================
    // 5. Password Validation & Non-Exposure Security Guarantee
    // =========================================================================

    @Test
    fun `validatePassword enforces complexity rules and never exposes secret in results`() {
        val secretValid = "WasteLink2026!"
        val validResult = AuthValidator.validatePassword(secretValid)
        assertTrue(validResult.isValid)
        assertFalse(validResult.toString().contains(secretValid))

        assertEquals(
            ValidationError.REQUIRED_FIELD,
            AuthValidator.validatePassword("").errorOrNull()
        )
        assertEquals(
            ValidationError.PASSWORD_TOO_SHORT,
            AuthValidator.validatePassword("Ab1").errorOrNull()
        )
        assertEquals(
            ValidationError.PASSWORD_TOO_LONG,
            AuthValidator.validatePassword("Aa1" + "x".repeat(62)).errorOrNull()
        )
        assertEquals(
            ValidationError.PASSWORD_MISSING_UPPERCASE,
            AuthValidator.validatePassword("lowercase123").errorOrNull()
        )
        assertEquals(
            ValidationError.PASSWORD_MISSING_LOWERCASE,
            AuthValidator.validatePassword("UPPERCASE123").errorOrNull()
        )
        val missingDigitSecret = "OnlyLettersHere"
        val invalidDigitResult = AuthValidator.validatePassword(missingDigitSecret)
        assertEquals(ValidationError.PASSWORD_MISSING_DIGIT, invalidDigitResult.errorOrNull())
        assertFalse(invalidDigitResult.toString().contains(missingDigitSecret))
        assertFalse(invalidDigitResult.errorMessageOrNull().orEmpty().contains(missingDigitSecret))
    }

    @Test
    fun `validatePasswordConfirmation verifies exact match without exposing passwords`() {
        val pw = "SecurePass99"
        val mismatch = "SecurePass98"
        val mismatchResult = AuthValidator.validatePasswordConfirmation(pw, mismatch)
        assertEquals(ValidationError.PASSWORD_CONFIRMATION_MISMATCH, mismatchResult.errorOrNull())
        assertFalse(mismatchResult.toString().contains(pw))
        assertFalse(mismatchResult.toString().contains(mismatch))

        assertTrue(AuthValidator.validatePasswordConfirmation(pw, pw).isValid)
    }

    // =========================================================================
    // 6. Self-Registration Role Validation Regression
    // =========================================================================

    @Test
    fun `validateSelfRegistrationRole allows RESIDENT and COLLECTOR and rejects null or ADMIN`() {
        assertEquals(
            UserRole.RESIDENT,
            AuthValidator.validateSelfRegistrationRole(UserRole.RESIDENT).getOrNull()
        )
        assertEquals(
            UserRole.COLLECTOR,
            AuthValidator.validateSelfRegistrationRole(UserRole.COLLECTOR).getOrNull()
        )
        assertEquals(
            ValidationError.ROLE_NOT_SELECTED,
            AuthValidator.validateSelfRegistrationRole(null).errorOrNull()
        )
        assertEquals(
            ValidationError.ROLE_NOT_ALLOWED_FOR_SELF_REGISTRATION,
            AuthValidator.validateSelfRegistrationRole(UserRole.ADMIN).errorOrNull()
        )
    }
}
