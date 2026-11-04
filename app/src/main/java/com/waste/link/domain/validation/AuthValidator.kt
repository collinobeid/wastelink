package com.waste.link.domain.validation

import com.waste.link.domain.model.UserRole

/**
 * Standardized validation error codes for authentication, registration, and profile recovery workflows.
 */
enum class ValidationError {
    REQUIRED_FIELD,
    FULL_NAME_TOO_SHORT,
    FULL_NAME_TOO_LONG,
    FULL_NAME_INVALID_CHARACTERS,
    FULL_NAME_RESERVED_PLACEHOLDER,
    EMAIL_INVALID_FORMAT,
    EMAIL_TOO_LONG,
    PHONE_INVALID_FORMAT,
    PHONE_TOO_SHORT,
    PHONE_TOO_LONG,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_MISSING_UPPERCASE,
    PASSWORD_MISSING_LOWERCASE,
    PASSWORD_MISSING_DIGIT,
    PASSWORD_CONFIRMATION_MISMATCH,
    ROLE_NOT_SELECTED,
    ROLE_NOT_ALLOWED_FOR_SELF_REGISTRATION
}

/**
 * Strongly typed result returned by [AuthValidator] methods.
 */
sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>

    data class Invalid(
        val error: ValidationError,
        val message: String
    ) : ValidationResult<Nothing>

    val isValid: Boolean
        get() = this is Valid

    val isInvalid: Boolean
        get() = this is Invalid

    fun getOrNull(): T? = (this as? Valid)?.value

    fun errorOrNull(): ValidationError? = (this as? Invalid)?.error

    fun errorMessageOrNull(): String? = (this as? Invalid)?.message
}

/**
 * Centralized, pure-Kotlin authentication and profile input validator.
 *
 * Security & Design Guarantees:
 * - Zero dependency on Android framework classes (`android.util.Patterns`) so validation behaves
 *   identically in JVM unit tests and on Android devices.
 * - Normalizes domestic and international phone numbers into ITU-T E.164 format (`+<7..15 digits>`)
 *   without rejecting legitimate non-Tanzanian international numbers.
 * - Password validation returns `ValidationResult<Unit>` and never embeds or logs password strings
 *   in error messages or result objects.
 */
object AuthValidator {

    const val MIN_FULL_NAME_LENGTH = 2
    const val MAX_FULL_NAME_LENGTH = 80
    const val MAX_EMAIL_LENGTH = 254
    const val MIN_E164_DIGITS = 7
    const val MAX_E164_DIGITS = 15
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 64
    const val DEFAULT_COUNTRY_CALLING_CODE = "+255"

    private val WHITESPACE_REGEX = Regex("\\s+")

    // Allows Unicode letters, marks, spaces, hyphens, apostrophes, and periods.
    private val FULL_NAME_ALLOWED_REGEX = Regex("^[\\p{L}\\p{M} .'-]+$")

    private val RESERVED_PLACEHOLDER_NAMES = setOf(
        "user",
        "unknown",
        "not provided",
        "null",
        "undefined",
        "admin",
        "administrator",
        "anonymous",
        "test user"
    )

    // RFC 5322 compatible structural email check (no spaces, valid local & multi-label domain)
    private val EMAIL_REGEX = Regex(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
    )

    // Formatting characters stripped prior to E.164 evaluation
    private val PHONE_FORMATTING_CHARS_REGEX = Regex("[\\s\\-().]")

    /**
     * Validates that a required text field is non-null and non-blank after trimming.
     */
    fun validateRequired(value: String?, fieldName: String = "This field"): ValidationResult<String> {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(
                error = ValidationError.REQUIRED_FIELD,
                message = "$fieldName is required."
            )
        }
        return ValidationResult.Valid(trimmed)
    }

    /**
     * Validates and normalizes a user's full name.
     * - Trims leading/trailing whitespace and collapses internal runs of whitespace to a single space.
     * - Enforces length bounds ([MIN_FULL_NAME_LENGTH]..[MAX_FULL_NAME_LENGTH]).
     * - Rejects legacy fabricated placeholders (`"User"`, `"Not provided"`, `"Admin"`, etc.).
     * - Requires at least 2 Unicode letter characters and rejects digits or special symbols.
     */
    fun validateFullName(rawFullName: String?): ValidationResult<String> {
        val required = validateRequired(rawFullName, "Full name")
        if (required is ValidationResult.Invalid) return required

        val normalized = required.getOrNull()!!.replace(WHITESPACE_REGEX, " ")
        if (normalized.length < MIN_FULL_NAME_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.FULL_NAME_TOO_SHORT,
                message = "Full name must be at least $MIN_FULL_NAME_LENGTH characters."
            )
        }
        if (normalized.length > MAX_FULL_NAME_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.FULL_NAME_TOO_LONG,
                message = "Full name must not exceed $MAX_FULL_NAME_LENGTH characters."
            )
        }
        if (normalized.lowercase() in RESERVED_PLACEHOLDER_NAMES) {
            return ValidationResult.Invalid(
                error = ValidationError.FULL_NAME_RESERVED_PLACEHOLDER,
                message = "Please enter your real full name."
            )
        }
        val letterCount = normalized.count { it.isLetter() }
        if (letterCount < MIN_FULL_NAME_LENGTH || !FULL_NAME_ALLOWED_REGEX.matches(normalized)) {
            return ValidationResult.Invalid(
                error = ValidationError.FULL_NAME_INVALID_CHARACTERS,
                message = "Full name may only contain letters, spaces, hyphens, and apostrophes."
            )
        }
        return ValidationResult.Valid(normalized)
    }

    /**
     * Validates and normalizes an email address (trimmed and lowercased).
     */
    fun validateEmail(rawEmail: String?): ValidationResult<String> {
        val required = validateRequired(rawEmail, "Email address")
        if (required is ValidationResult.Invalid) return required

        val normalized = required.getOrNull()!!.lowercase()
        if (normalized.length > MAX_EMAIL_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.EMAIL_TOO_LONG,
                message = "Email address must not exceed $MAX_EMAIL_LENGTH characters."
            )
        }

        val parts = normalized.split("@")
        if (parts.size != 2) {
            return ValidationResult.Invalid(
                error = ValidationError.EMAIL_INVALID_FORMAT,
                message = "Please enter a valid email address."
            )
        }

        val localPart = parts[0]
        val domainPart = parts[1]

        if (localPart.isEmpty() ||
            localPart.length > 64 ||
            localPart.startsWith(".") ||
            localPart.endsWith(".") ||
            localPart.contains("..") ||
            domainPart.startsWith(".") ||
            domainPart.endsWith(".") ||
            domainPart.contains("..") ||
            !EMAIL_REGEX.matches(normalized)
        ) {
            return ValidationResult.Invalid(
                error = ValidationError.EMAIL_INVALID_FORMAT,
                message = "Please enter a valid email address."
            )
        }

        val tld = domainPart.substringAfterLast('.', missingDelimiterValue = "")
        if (tld.length < 2) {
            return ValidationResult.Invalid(
                error = ValidationError.EMAIL_INVALID_FORMAT,
                message = "Please enter a valid email address."
            )
        }

        return ValidationResult.Valid(normalized)
    }

    /**
     * Validates and normalizes a phone number into ITU-T E.164 format (`+<country><subscriber>`).
     *
     * Supported inputs:
     * 1. Explicit E.164 international format from ANY country (`+255712345678`, `+254712345678`,
     *    `+256772123456`, `+1 (415) 555-2671`, `+44 7911 123456`, `+91 98765 43210`, etc.).
     * 2. International `00` prefix (`00255712345678`, `00447911123456`, `0014155552671`), converted to `+`.
     * 3. Local national format starting with a single `0` (`0712 345 678`), normalized using
     *    [defaultCountryCallingCode] (defaults to `"+255"`).
     * 4. Bare international digits starting with non-zero country code (`255712345678`, `254712345678`),
     *    normalized by prepending `+` when length is 10..15 digits.
     */
    fun validateAndNormalizePhone(
        rawPhone: String?,
        defaultCountryCallingCode: String = DEFAULT_COUNTRY_CALLING_CODE
    ): ValidationResult<String> {
        val required = validateRequired(rawPhone, "Phone number")
        if (required is ValidationResult.Invalid) return required

        val trimmed = required.getOrNull()!!
        if (trimmed.equals("Not provided", ignoreCase = true)) {
            return ValidationResult.Invalid(
                error = ValidationError.REQUIRED_FIELD,
                message = "Phone number is required."
            )
        }

        val cleaned = trimmed.replace(PHONE_FORMATTING_CHARS_REGEX, "")
        if (cleaned.isEmpty()) {
            return ValidationResult.Invalid(
                error = ValidationError.REQUIRED_FIELD,
                message = "Phone number is required."
            )
        }

        val normalizedCountryCode = if (defaultCountryCallingCode.startsWith("+")) {
            defaultCountryCallingCode
        } else {
            "+${defaultCountryCallingCode.trimStart('+')}"
        }

        val candidateE164 = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.removePrefix("00")
            cleaned.startsWith("0") -> {
                val nationalDigits = cleaned.drop(1)
                if (nationalDigits.isEmpty() || !nationalDigits.all { it.isDigit() } || nationalDigits.startsWith("0")) {
                    return ValidationResult.Invalid(
                        error = ValidationError.PHONE_INVALID_FORMAT,
                        message = "Please enter a valid phone number."
                    )
                }
                // Local national subscriber numbers must be 7 to 12 digits after the trunk '0'
                if (nationalDigits.length < 7) {
                    return ValidationResult.Invalid(
                        error = ValidationError.PHONE_TOO_SHORT,
                        message = "Phone number is too short."
                    )
                }
                if (nationalDigits.length > 12) {
                    return ValidationResult.Invalid(
                        error = ValidationError.PHONE_TOO_LONG,
                        message = "Phone number is too long."
                    )
                }
                "$normalizedCountryCode$nationalDigits"
            }
            cleaned.all { it.isDigit() } -> {
                // Bare digits starting with [1-9]
                if (cleaned.length < 10) {
                    return ValidationResult.Invalid(
                        error = ValidationError.PHONE_TOO_SHORT,
                        message = "Phone number is too short. Include your country code (e.g., +255)."
                    )
                }
                "+$cleaned"
            }
            else -> {
                return ValidationResult.Invalid(
                    error = ValidationError.PHONE_INVALID_FORMAT,
                    message = "Phone number may only contain digits and an optional leading '+'."
                )
            }
        }

        val digitsOnly = candidateE164.removePrefix("+")
        if (digitsOnly.isEmpty() || !digitsOnly.all { it.isDigit() }) {
            return ValidationResult.Invalid(
                error = ValidationError.PHONE_INVALID_FORMAT,
                message = "Phone number may only contain digits and an optional leading '+'."
            )
        }

        // E.164 country codes never begin with '0'
        if (digitsOnly.startsWith("0")) {
            return ValidationResult.Invalid(
                error = ValidationError.PHONE_INVALID_FORMAT,
                message = "Country code after '+' cannot start with 0."
            )
        }

        if (digitsOnly.length < MIN_E164_DIGITS) {
            return ValidationResult.Invalid(
                error = ValidationError.PHONE_TOO_SHORT,
                message = "Phone number must contain at least $MIN_E164_DIGITS digits."
            )
        }

        if (digitsOnly.length > MAX_E164_DIGITS) {
            return ValidationResult.Invalid(
                error = ValidationError.PHONE_TOO_LONG,
                message = "Phone number must not exceed $MAX_E164_DIGITS digits."
            )
        }

        return ValidationResult.Valid("+$digitsOnly")
    }

    /**
     * Validates password strength without logging, echoing, or storing the password value.
     *
     * Requirements:
     * - 8 to 64 characters
     * - At least 1 uppercase letter (`A-Z`)
     * - At least 1 lowercase letter (`a-z`)
     * - At least 1 digit (`0-9`)
     */
    fun validatePassword(rawPassword: String?): ValidationResult<Unit> {
        if (rawPassword.isNullOrEmpty() || rawPassword.isBlank()) {
            return ValidationResult.Invalid(
                error = ValidationError.REQUIRED_FIELD,
                message = "Password is required."
            )
        }
        if (rawPassword.length < MIN_PASSWORD_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.PASSWORD_TOO_SHORT,
                message = "Password must be at least $MIN_PASSWORD_LENGTH characters."
            )
        }
        if (rawPassword.length > MAX_PASSWORD_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.PASSWORD_TOO_LONG,
                message = "Password must not exceed $MAX_PASSWORD_LENGTH characters."
            )
        }
        if (rawPassword.none { it.isUpperCase() }) {
            return ValidationResult.Invalid(
                error = ValidationError.PASSWORD_MISSING_UPPERCASE,
                message = "Password must contain at least one uppercase letter."
            )
        }
        if (rawPassword.none { it.isLowerCase() }) {
            return ValidationResult.Invalid(
                error = ValidationError.PASSWORD_MISSING_LOWERCASE,
                message = "Password must contain at least one lowercase letter."
            )
        }
        if (rawPassword.none { it.isDigit() }) {
            return ValidationResult.Invalid(
                error = ValidationError.PASSWORD_MISSING_DIGIT,
                message = "Password must contain at least one number."
            )
        }
        return ValidationResult.Valid(Unit)
    }

    /**
     * Validates that a password confirmation field is present and matches [password]
     * without exposing either password in the result or error message.
     */
    fun validatePasswordConfirmation(
        password: String?,
        confirmPassword: String?
    ): ValidationResult<Unit> {
        if (confirmPassword.isNullOrEmpty()) {
            return ValidationResult.Invalid(
                error = ValidationError.REQUIRED_FIELD,
                message = "Please confirm your password."
            )
        }
        if (password != confirmPassword) {
            return ValidationResult.Invalid(
                error = ValidationError.PASSWORD_CONFIRMATION_MISMATCH,
                message = "Passwords do not match."
            )
        }
        return ValidationResult.Valid(Unit)
    }

    /**
     * Validates that a role selected during client-side registration or profile recovery is
     * present and is strictly [UserRole.RESIDENT] or [UserRole.COLLECTOR] (never [UserRole.ADMIN]).
     */
    fun validateSelfRegistrationRole(role: UserRole?): ValidationResult<UserRole> {
        if (role == null) {
            return ValidationResult.Invalid(
                error = ValidationError.ROLE_NOT_SELECTED,
                message = "Please select an account type (Resident or Collector)."
            )
        }
        if (!role.isSelfRegistrable) {
            return ValidationResult.Invalid(
                error = ValidationError.ROLE_NOT_ALLOWED_FOR_SELF_REGISTRATION,
                message = "Selected role cannot be self-registered."
            )
        }
        return ValidationResult.Valid(role)
    }
}
