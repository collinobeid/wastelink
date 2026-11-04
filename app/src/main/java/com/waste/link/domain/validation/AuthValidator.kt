package com.waste.link.domain.validation

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
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
    ROLE_NOT_ALLOWED_FOR_SELF_REGISTRATION,
    VEHICLE_TYPE_INVALID,
    VEHICLE_PLATE_INVALID,
    LICENSE_NUMBER_INVALID,
    SERVICE_ZONE_INVALID
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
 * Non-sensitive password strength evaluation report used by registration UI.
 * Never holds or exposes the underlying password string.
 */
data class PasswordStrengthReport(
    val hasMinLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean,
    val score: Int,
    val label: String
) {
    val meetsMinimumPolicy: Boolean
        get() = hasMinLength && hasUppercase && hasLowercase && hasDigit
}

/**
 * Centralized, pure-Kotlin authentication and profile input validator.
 *
 * Security & Design Guarantees:
 * - Zero dependency on Android framework classes (`android.util.Patterns`) so validation behaves
 *   identically in JVM unit tests and on Android devices.
 * - Uses Google's `libphonenumber` ([PhoneNumberUtil]) to validate and normalize domestic and
 *   international phone numbers into ITU-T E.164 format (`+<country><subscriber>`).
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
    const val DEFAULT_REGION_CODE = "TZ"

    private val phoneNumberUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

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
     * Validates and normalizes a phone number into ITU-T E.164 format (`+<country><subscriber>`)
     * backed by Google's `libphonenumber` ([PhoneNumberUtil]).
     *
     * Supported inputs:
     * 1. Explicit E.164 international format from ANY country (`+255712345678`, `+254712345678`,
     *    `+256772123456`, `+1 (415) 555-2671`, `+44 7911 123456`, `+91 98765 43210`, etc.).
     * 2. International `00` prefix (`00255712345678`, `00447911123456`, `004915123456789`), converted to `+`.
     * 3. Local national format starting with a single `0` (`0712 345 678`), normalized using
     *    [defaultCountryCallingCode] (defaults to `"+255"` / region `"TZ"`).
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

        // Validate against Google's libphonenumber ITU-T numbering plans
        val callingCodeInt = normalizedCountryCode.removePrefix("+").toIntOrNull()
        val defaultRegion = callingCodeInt
            ?.let { phoneNumberUtil.getRegionCodeForCountryCode(it) }
            ?.takeIf { it != "ZZ" && it.isNotBlank() }
            ?: DEFAULT_REGION_CODE

        val parsedNumber = try {
            phoneNumberUtil.parse(candidateE164, defaultRegion)
        } catch (e: NumberParseException) {
            return when (e.errorType) {
                NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                NumberParseException.ErrorType.TOO_SHORT_NSN -> ValidationResult.Invalid(
                    error = ValidationError.PHONE_TOO_SHORT,
                    message = "Phone number is too short for the selected country."
                )
                NumberParseException.ErrorType.TOO_LONG -> ValidationResult.Invalid(
                    error = ValidationError.PHONE_TOO_LONG,
                    message = "Phone number exceeds the maximum length."
                )
                else -> ValidationResult.Invalid(
                    error = ValidationError.PHONE_INVALID_FORMAT,
                    message = "Please enter a valid international phone number (e.g., +255 712 345 678)."
                )
            }
        }

        when (phoneNumberUtil.isPossibleNumberWithReason(parsedNumber)) {
            PhoneNumberUtil.ValidationResult.TOO_SHORT -> {
                return ValidationResult.Invalid(
                    error = ValidationError.PHONE_TOO_SHORT,
                    message = "Phone number is too short for its country code."
                )
            }
            PhoneNumberUtil.ValidationResult.TOO_LONG -> {
                return ValidationResult.Invalid(
                    error = ValidationError.PHONE_TOO_LONG,
                    message = "Phone number is too long for its country code."
                )
            }
            PhoneNumberUtil.ValidationResult.INVALID_COUNTRY_CODE,
            PhoneNumberUtil.ValidationResult.INVALID_LENGTH -> {
                return ValidationResult.Invalid(
                    error = ValidationError.PHONE_INVALID_FORMAT,
                    message = "Please enter a valid phone number for its country code."
                )
            }
            PhoneNumberUtil.ValidationResult.IS_POSSIBLE,
            PhoneNumberUtil.ValidationResult.IS_POSSIBLE_LOCAL_ONLY -> {
                // Proceed to full validity check
            }
        }

        if (!phoneNumberUtil.isValidNumber(parsedNumber)) {
            return ValidationResult.Invalid(
                error = ValidationError.PHONE_INVALID_FORMAT,
                message = "Please enter a valid phone number with country code (e.g., +255 712 345 678)."
            )
        }

        val formattedE164 = phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
        return ValidationResult.Valid(formattedE164)
    }

    /**
     * Evaluates password strength criteria without storing or exposing the raw password string.
     */
    fun evaluatePasswordStrength(rawPassword: String?): PasswordStrengthReport {
        val pwd = rawPassword.orEmpty()
        val hasMinLength = pwd.length >= MIN_PASSWORD_LENGTH
        val hasUppercase = pwd.any { it.isUpperCase() }
        val hasLowercase = pwd.any { it.isLowerCase() }
        val hasDigit = pwd.any { it.isDigit() }
        val hasSpecialChar = pwd.any { !it.isLetterOrDigit() && !it.isWhitespace() }

        val criteriaMet = listOf(hasMinLength, hasUppercase, hasLowercase, hasDigit).count { it }
        val score = when {
            pwd.isEmpty() -> 0
            criteriaMet <= 1 -> 1
            criteriaMet == 2 -> 2
            criteriaMet == 3 -> 3
            hasSpecialChar && pwd.length >= 10 -> 4
            else -> 4
        }
        val label = when (score) {
            0 -> "Enter a password"
            1 -> "Weak"
            2 -> "Fair"
            3 -> "Good"
            else -> "Strong"
        }
        return PasswordStrengthReport(
            hasMinLength = hasMinLength,
            hasUppercase = hasUppercase,
            hasLowercase = hasLowercase,
            hasDigit = hasDigit,
            hasSpecialChar = hasSpecialChar,
            score = score,
            label = label
        )
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

    // =========================================================================
    // Stage 5: Role-Specific Onboarding & Collector KYC Validation
    // =========================================================================

    const val MIN_VEHICLE_TYPE_LENGTH = 2
    const val MAX_VEHICLE_TYPE_LENGTH = 40
    const val MIN_VEHICLE_PLATE_LENGTH = 3
    const val MAX_VEHICLE_PLATE_LENGTH = 20
    const val MIN_LICENSE_NUMBER_LENGTH = 4
    const val MAX_LICENSE_NUMBER_LENGTH = 40
    const val MIN_SERVICE_ZONE_LENGTH = 2
    const val MAX_SERVICE_ZONE_LENGTH = 60
    const val UNSUPPORTED_ZONE_ID = "zone_unsupported"

    val STANDARD_VEHICLE_TYPES = listOf(
        "Compactor Truck",
        "Flatbed Truck",
        "Waste Tricycle",
        "Skip Loader"
    )

    data class MunicipalServiceZone(
        val id: String,
        val displayName: String,
        val regionDescription: String
    )

    val MUNICIPAL_SERVICE_ZONES = listOf(
        MunicipalServiceZone("zone_kinondoni", "Kinondoni Municipality", "Dar es Salaam - North & Coastal"),
        MunicipalServiceZone("zone_ilala", "Ilala Municipality", "Dar es Salaam - Central Business District"),
        MunicipalServiceZone("zone_temeke", "Temeke Municipality", "Dar es Salaam - South & Industrial"),
        MunicipalServiceZone("zone_ubungo", "Ubungo Municipality", "Dar es Salaam - West & Transit Hub"),
        MunicipalServiceZone("zone_kigamboni", "Kigamboni Municipality", "Dar es Salaam - South Coast"),
        MunicipalServiceZone("zone_arusha", "Arusha City", "Northern Tourism & Commercial Hub"),
        MunicipalServiceZone("zone_dodoma", "Dodoma Capital", "National Capital & Government Zone")
    )

    /**
     * Validates vehicle type selection/input for Collector onboarding.
     * Enforces length bounds ([MIN_VEHICLE_TYPE_LENGTH]..[MAX_VEHICLE_TYPE_LENGTH]).
     */
    fun validateVehicleType(rawType: String?): ValidationResult<String> {
        val required = validateRequired(rawType, "Vehicle type")
        if (required is ValidationResult.Invalid) return required

        val trimmed = required.getOrNull()!!
        if (trimmed.length < MIN_VEHICLE_TYPE_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.VEHICLE_TYPE_INVALID,
                message = "Vehicle type must be at least $MIN_VEHICLE_TYPE_LENGTH characters."
            )
        }
        if (trimmed.length > MAX_VEHICLE_TYPE_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.VEHICLE_TYPE_INVALID,
                message = "Vehicle type must not exceed $MAX_VEHICLE_TYPE_LENGTH characters."
            )
        }
        return ValidationResult.Valid(trimmed)
    }

    /**
     * Validates vehicle registration / license plate number.
     * Enforces length bounds ([MIN_VEHICLE_PLATE_LENGTH]..[MAX_VEHICLE_PLATE_LENGTH])
     * and normalizes to uppercase.
     */
    fun validateVehiclePlateNumber(rawPlate: String?): ValidationResult<String> {
        val required = validateRequired(rawPlate, "Vehicle plate number")
        if (required is ValidationResult.Invalid) return required

        val normalized = required.getOrNull()!!.uppercase()
        if (normalized.length < MIN_VEHICLE_PLATE_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.VEHICLE_PLATE_INVALID,
                message = "Plate number must be at least $MIN_VEHICLE_PLATE_LENGTH characters."
            )
        }
        if (normalized.length > MAX_VEHICLE_PLATE_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.VEHICLE_PLATE_INVALID,
                message = "Plate number must not exceed $MAX_VEHICLE_PLATE_LENGTH characters."
            )
        }
        val plateRegex = Regex("^[A-Z0-9 -]+$")
        if (!plateRegex.matches(normalized)) {
            return ValidationResult.Invalid(
                error = ValidationError.VEHICLE_PLATE_INVALID,
                message = "Plate number may only contain letters, digits, spaces, and hyphens."
            )
        }
        return ValidationResult.Valid(normalized)
    }

    /**
     * Validates driver license number for Collector onboarding.
     * Enforces length bounds ([MIN_LICENSE_NUMBER_LENGTH]..[MAX_LICENSE_NUMBER_LENGTH]).
     */
    fun validateLicenseNumber(rawLicense: String?): ValidationResult<String> {
        val required = validateRequired(rawLicense, "Driver license number")
        if (required is ValidationResult.Invalid) return required

        val normalized = required.getOrNull()!!.uppercase()
        if (normalized.length < MIN_LICENSE_NUMBER_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.LICENSE_NUMBER_INVALID,
                message = "License number must be at least $MIN_LICENSE_NUMBER_LENGTH characters."
            )
        }
        if (normalized.length > MAX_LICENSE_NUMBER_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.LICENSE_NUMBER_INVALID,
                message = "License number must not exceed $MAX_LICENSE_NUMBER_LENGTH characters."
            )
        }
        val licenseRegex = Regex("^[A-Z0-9 -]+$")
        if (!licenseRegex.matches(normalized)) {
            return ValidationResult.Invalid(
                error = ValidationError.LICENSE_NUMBER_INVALID,
                message = "License number may only contain letters, digits, spaces, and hyphens."
            )
        }
        return ValidationResult.Valid(normalized)
    }

    /**
     * Validates service zone selection for Resident or Collector onboarding.
     * Enforces length bounds ([MIN_SERVICE_ZONE_LENGTH]..[MAX_SERVICE_ZONE_LENGTH])
     * and rejects unlisted or explicitly unsupported service areas.
     */
    fun validateServiceZoneId(rawZoneId: String?): ValidationResult<String> {
        val required = validateRequired(rawZoneId, "Service zone")
        if (required is ValidationResult.Invalid) {
            return ValidationResult.Invalid(
                error = ValidationError.REQUIRED_FIELD,
                message = "Please select your municipal service zone."
            )
        }

        val trimmed = required.getOrNull()!!
        if (trimmed.equals(UNSUPPORTED_ZONE_ID, ignoreCase = true)) {
            return ValidationResult.Invalid(
                error = ValidationError.SERVICE_ZONE_INVALID,
                message = "WasteLink collection is not yet supported in this area. Service is currently limited to active municipal zones."
            )
        }
        if (trimmed.length < MIN_SERVICE_ZONE_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.SERVICE_ZONE_INVALID,
                message = "Service zone identifier is too short."
            )
        }
        if (trimmed.length > MAX_SERVICE_ZONE_LENGTH) {
            return ValidationResult.Invalid(
                error = ValidationError.SERVICE_ZONE_INVALID,
                message = "Service zone identifier must not exceed $MAX_SERVICE_ZONE_LENGTH characters."
            )
        }
        val matchedZone = MUNICIPAL_SERVICE_ZONES.firstOrNull {
            it.id.equals(trimmed, ignoreCase = true) ||
            it.displayName.equals(trimmed, ignoreCase = true) ||
            it.id.removePrefix("zone_").equals(trimmed, ignoreCase = true) ||
            it.displayName.startsWith(trimmed, ignoreCase = true)
        }
        if (matchedZone == null) {
            return ValidationResult.Invalid(
                error = ValidationError.SERVICE_ZONE_INVALID,
                message = "Selected service zone is not recognized."
            )
        }
        return ValidationResult.Valid(matchedZone.id)
    }
}

