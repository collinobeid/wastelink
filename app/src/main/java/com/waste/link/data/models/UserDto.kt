package com.waste.link.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.ProfileIncompleteReason
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import java.util.Date

/**
 * Detailed classification of a Firestore `/users/{uid}` document lookup.
 */
sealed interface ProfileMappingResult {
    data class Valid(val user: User) : ProfileMappingResult

    data class Incomplete(
        val reason: ProfileIncompleteReason,
        val partialFullName: String? = null,
        val partialPhoneNumber: String? = null
    ) : ProfileMappingResult

    data class Malformed(val reason: String) : ProfileMappingResult
}

/**
 * Firestore Data Transfer Object for `/users/{userId}` documents.
 *
 * Supports dual-read and dual-write compatibility between the canonical [User] domain model
 * and legacy [AppUser] / [com.waste.link.data.models.User] Firestore schemas.
 *
 * Security & Authority Invariants:
 * - Does NOT store or write authoritative email-verification state (`isEmailVerified`);
 *   email verification truth comes exclusively from Firebase Authentication.
 * - `isVerified` is maintained strictly as a legacy read/admin-write projection of
 *   `collectorVerificationStatus == APPROVED` for backward compatibility with unmigrated Admin queries.
 */
data class UserDto(
    val id: String? = null,
    val uid: String? = null,
    val fullName: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val accountStatus: String? = null,
    val status: String? = null,
    val statusReason: String? = null,
    val suspendedUntilEpochMs: Long? = null,
    val bannedUntil: Long? = null,
    val contractUntil: Long? = null,
    val deletedAt: Long? = null,
    val isActive: Boolean? = null,
    val onboardingStatus: String? = null,
    val isVerified: Boolean? = null,
    val collectorVerificationStatus: String? = null,
    val collectorRejectionReason: String? = null,
    val defaultZoneId: String? = null,
    val vehicleType: String? = null,
    val vehiclePlateNumber: String? = null,
    val preferredLanguage: String? = null,
    val createdAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long? = null
)

/**
 * Centralized mapping functions between Firestore data representations and the canonical [User] domain model.
 */
object UserMapper {

    private const val LEGACY_FABRICATED_NAME = "User"
    private const val LEGACY_FABRICATED_PHONE = "Not provided"

    /**
     * Constructs a brand-new canonical [User] profile for registration or Ghost Account recovery.
     *
     * Guaranteed to satisfy Stage 2.1 `firestore.rules` (`isValidSelfProfileCreate`).
     */
    fun createInitialProfile(
        uid: String,
        fullName: String,
        email: String,
        phoneNumber: String,
        role: UserRole,
        nowEpochMs: Long = System.currentTimeMillis()
    ): User {
        require(role.isSelfRegistrable) {
            "Role $role cannot be used for self-registration or client profile recovery"
        }
        val effectiveNow = if (nowEpochMs > 0L) nowEpochMs else 1L
        return User(
            id = uid.trim(),
            fullName = fullName.trim(),
            email = email.trim().lowercase(),
            phoneNumber = phoneNumber.trim(),
            role = role,
            accountStatus = AccountStatus.ACTIVE,
            statusReason = null,
            suspendedUntilEpochMs = null,
            onboardingStatus = OnboardingStatus.NOT_STARTED,
            isPhoneVerified = false,
            collectorVerificationStatus = if (role == UserRole.COLLECTOR) {
                CollectorVerificationStatus.NOT_SUBMITTED
            } else {
                CollectorVerificationStatus.NOT_APPLICABLE
            },
            collectorRejectionReason = null,
            defaultZoneId = null,
            vehicleType = null,
            vehiclePlateNumber = null,
            preferredLanguage = "en",
            createdAtEpochMs = effectiveNow,
            updatedAtEpochMs = effectiveNow
        )
    }

    /**
     * Converts a [UserDto] to the canonical [User] domain model.
     *
     * Returns `null` if:
     * - The user ID (`id`, `uid`, or [fallbackDocumentId]) is missing/blank or mismatches [fallbackDocumentId].
     * - The email is missing/blank or structurally invalid.
     * - The full name (`fullName` or `name`) is missing/blank.
     * - The document matches the legacy fabricated ghost-account placeholder (`name == "User"` and `phone == "Not provided"`).
     * - The role is missing, `"unassigned"`, or an unrecognized value (preventing silent privilege escalation).
     */
    fun toDomainOrNull(
        dto: UserDto,
        fallbackDocumentId: String? = null,
        legacyCreatedAtRaw: Any? = null,
        legacyUpdatedAtRaw: Any? = null
    ): User? {
        val trimmedFallbackId = firstNonBlank(fallbackDocumentId)
        val trimmedId = firstNonBlank(dto.id)
        val trimmedUid = firstNonBlank(dto.uid)

        if (trimmedId != null && trimmedUid != null && trimmedId != trimmedUid) {
            return null
        }
        if (trimmedFallbackId != null) {
            if (trimmedId != null && trimmedId != trimmedFallbackId) return null
            if (trimmedUid != null && trimmedUid != trimmedFallbackId) return null
        }

        val resolvedId = firstNonBlank(trimmedId, trimmedUid, trimmedFallbackId) ?: return null
        val resolvedEmail = firstNonBlank(dto.email)?.lowercase() ?: return null
        if (AuthValidator.validateEmail(resolvedEmail).isInvalid) {
            return null
        }
        val resolvedName = firstNonBlank(dto.fullName, dto.name) ?: return null
        val rawPhone = firstNonBlank(dto.phoneNumber, dto.phone)

        // Reject legacy fabricated ghost-account placeholders so they never pass as real user profiles
        if (resolvedName.equals(LEGACY_FABRICATED_NAME, ignoreCase = true) &&
            rawPhone.equals(LEGACY_FABRICATED_PHONE, ignoreCase = true)
        ) {
            return null
        }

        val resolvedRole = UserRole.fromFirestoreValue(dto.role) ?: return null

        val sanitizedPhone = if (rawPhone.equals(LEGACY_FABRICATED_PHONE, ignoreCase = true)) {
            ""
        } else {
            rawPhone ?: ""
        }

        val resolvedAccountStatus = AccountStatus.fromFirestoreValue(
            value = firstNonBlank(dto.accountStatus, dto.status),
            legacyDeletedAtEpochMs = dto.deletedAt,
            legacyIsActive = dto.isActive
        )

        val resolvedOnboardingStatus = OnboardingStatus.fromFirestoreValue(
            value = dto.onboardingStatus,
            hasExistingValidRole = true
        )

        val resolvedCollectorStatus = CollectorVerificationStatus.fromFirestoreValue(
            value = dto.collectorVerificationStatus,
            role = resolvedRole,
            legacyIsVerified = dto.isVerified,
            legacyStatus = dto.status
        )

        val resolvedSuspendedUntil = dto.suspendedUntilEpochMs ?: dto.bannedUntil
        val resolvedCreatedAt = resolveEpochMillis(dto.createdAtEpochMs, legacyCreatedAtRaw)
        val resolvedUpdatedAt = resolveEpochMillis(dto.updatedAtEpochMs, legacyUpdatedAtRaw)

        return User(
            id = resolvedId,
            fullName = resolvedName,
            email = resolvedEmail,
            phoneNumber = sanitizedPhone,
            role = resolvedRole,
            accountStatus = resolvedAccountStatus,
            statusReason = normalizeOptional(dto.statusReason),
            suspendedUntilEpochMs = resolvedSuspendedUntil,
            onboardingStatus = resolvedOnboardingStatus,
            isPhoneVerified = false,
            collectorVerificationStatus = resolvedCollectorStatus,
            collectorRejectionReason = if (resolvedRole == UserRole.COLLECTOR) {
                normalizeOptional(dto.collectorRejectionReason)
            } else {
                null
            },
            defaultZoneId = normalizeOptional(dto.defaultZoneId),
            vehicleType = if (resolvedRole == UserRole.COLLECTOR) {
                normalizeOptional(dto.vehicleType)
            } else {
                null
            },
            vehiclePlateNumber = if (resolvedRole == UserRole.COLLECTOR) {
                normalizeOptional(dto.vehiclePlateNumber)
            } else {
                null
            },
            preferredLanguage = firstNonBlank(dto.preferredLanguage) ?: "en",
            createdAtEpochMs = resolvedCreatedAt,
            updatedAtEpochMs = resolvedUpdatedAt
        )
    }

    /**
     * Inspects a Firestore `/users/{uid}` document map and classifies it into:
     * - [ProfileMappingResult.Valid]: Complete canonical or legacy user profile.
     * - [ProfileMappingResult.Incomplete]: Missing document or legacy incomplete Ghost Account placeholder
     *   (`role == "unassigned"` or `name == "User"` + `phone == "Not provided"`).
     * - [ProfileMappingResult.Malformed]: Document exists in Firestore but contains corrupted, missing,
     *   or invalid identity/role fields.
     */
    fun inspectFirestoreMap(
        data: Map<String, Any?>?,
        expectedUid: String
    ): ProfileMappingResult {
        if (data == null) {
            return ProfileMappingResult.Incomplete(
                reason = ProfileIncompleteReason.DOCUMENT_MISSING
            )
        }

        val normalizedExpectedUid = expectedUid.trim()
        val rawId = data["id"]
        val rawUid = data["uid"]

        if (rawId != null && rawId !is String) {
            return ProfileMappingResult.Malformed("Field 'id' must be a string.")
        }
        if (rawUid != null && rawUid !is String) {
            return ProfileMappingResult.Malformed("Field 'uid' must be a string.")
        }

        val docId = firstNonBlank(rawId as? String)
        val docUid = firstNonBlank(rawUid as? String)

        if (docId != null && docId != normalizedExpectedUid) {
            return ProfileMappingResult.Malformed("Document 'id' ($docId) does not match authenticated UID ($normalizedExpectedUid).")
        }
        if (docUid != null && docUid != normalizedExpectedUid) {
            return ProfileMappingResult.Malformed("Document 'uid' ($docUid) does not match authenticated UID ($normalizedExpectedUid).")
        }

        val rawFullName = data["fullName"]
        val rawName = data["name"]
        if (rawFullName != null && rawFullName !is String) {
            return ProfileMappingResult.Malformed("Field 'fullName' must be a string.")
        }
        if (rawName != null && rawName !is String) {
            return ProfileMappingResult.Malformed("Field 'name' must be a string.")
        }

        val resolvedName = firstNonBlank(rawFullName as? String, rawName as? String)
        val rawPhone = firstNonBlank(data["phoneNumber"] as? String, data["phone"] as? String)

        // Check for legacy fabricated placeholder (`name == "User"` and `phone == "Not provided"`)
        if (resolvedName != null &&
            resolvedName.equals(LEGACY_FABRICATED_NAME, ignoreCase = true) &&
            rawPhone.equals(LEGACY_FABRICATED_PHONE, ignoreCase = true)
        ) {
            return ProfileMappingResult.Incomplete(
                reason = ProfileIncompleteReason.LEGACY_FABRICATED_PLACEHOLDER
            )
        }

        val rawRole = data["role"]
        if (rawRole != null && rawRole !is String) {
            return ProfileMappingResult.Malformed("Field 'role' must be a string.")
        }
        val roleStr = firstNonBlank(rawRole as? String)
        if (roleStr != null && roleStr.equals("unassigned", ignoreCase = true)) {
            return ProfileMappingResult.Incomplete(
                reason = ProfileIncompleteReason.LEGACY_UNASSIGNED_ROLE,
                partialFullName = resolvedName?.takeUnless { it.equals(LEGACY_FABRICATED_NAME, ignoreCase = true) },
                partialPhoneNumber = rawPhone?.takeUnless { it.equals(LEGACY_FABRICATED_PHONE, ignoreCase = true) }
            )
        }

        val rawEmail = data["email"]
        if (rawEmail != null && rawEmail !is String) {
            return ProfileMappingResult.Malformed("Field 'email' must be a string.")
        }
        val resolvedEmail = firstNonBlank(rawEmail as? String)?.lowercase()
            ?: return ProfileMappingResult.Malformed("Required field 'email' is missing or blank.")

        if (AuthValidator.validateEmail(resolvedEmail).isInvalid) {
            return ProfileMappingResult.Malformed("Field 'email' ('$resolvedEmail') is not a valid email address.")
        }

        if (resolvedName == null) {
            return ProfileMappingResult.Malformed("Required field 'fullName' / 'name' is missing or blank.")
        }

        if (roleStr == null) {
            return ProfileMappingResult.Malformed("Required field 'role' is missing or blank.")
        }

        if (UserRole.fromFirestoreValue(roleStr) == null) {
            return ProfileMappingResult.Malformed("Unrecognized or corrupted user role '$roleStr'.")
        }

        val mappedUser = fromFirestoreMap(data, fallbackDocumentId = normalizedExpectedUid)
            ?: return ProfileMappingResult.Malformed("Failed to map Firestore profile fields into canonical User.")

        return ProfileMappingResult.Valid(mappedUser)
    }

    /**
     * Converts a raw Firestore field map (`Map<String, Any?>`) into a canonical [User] domain entity.
     * Safely handles missing legacy fields, polymorphic timestamp types ([Timestamp], [Date], [Long]),
     * and type mismatches without throwing ClassCastException.
     */
    fun fromFirestoreMap(
        data: Map<String, Any?>?,
        fallbackDocumentId: String? = null
    ): User? {
        if (data == null) return null

        val dto = UserDto(
            id = data["id"] as? String,
            uid = data["uid"] as? String,
            fullName = data["fullName"] as? String,
            name = data["name"] as? String,
            email = data["email"] as? String,
            phoneNumber = data["phoneNumber"] as? String,
            phone = data["phone"] as? String,
            role = data["role"] as? String,
            accountStatus = data["accountStatus"] as? String,
            status = data["status"] as? String,
            statusReason = data["statusReason"] as? String,
            suspendedUntilEpochMs = extractNullableEpochMillis(data["suspendedUntilEpochMs"]),
            bannedUntil = extractNullableEpochMillis(data["bannedUntil"]),
            contractUntil = extractNullableEpochMillis(data["contractUntil"]),
            deletedAt = extractNullableEpochMillis(data["deletedAt"]),
            isActive = data["isActive"] as? Boolean,
            onboardingStatus = data["onboardingStatus"] as? String,
            isVerified = data["isVerified"] as? Boolean,
            collectorVerificationStatus = data["collectorVerificationStatus"] as? String,
            collectorRejectionReason = data["collectorRejectionReason"] as? String,
            defaultZoneId = data["defaultZoneId"] as? String,
            vehicleType = data["vehicleType"] as? String,
            vehiclePlateNumber = data["vehiclePlateNumber"] as? String,
            preferredLanguage = data["preferredLanguage"] as? String,
            createdAtEpochMs = extractNullableEpochMillis(data["createdAtEpochMs"]),
            updatedAtEpochMs = extractNullableEpochMillis(data["updatedAtEpochMs"])
        )

        return toDomainOrNull(
            dto = dto,
            fallbackDocumentId = fallbackDocumentId,
            legacyCreatedAtRaw = data["createdAt"],
            legacyUpdatedAtRaw = data["updatedAt"]
        )
    }

    /**
     * Converts a Firestore [DocumentSnapshot] into a canonical [User] domain entity.
     */
    fun fromDocument(document: DocumentSnapshot): User? {
        if (!document.exists()) return null
        return fromFirestoreMap(document.data, fallbackDocumentId = document.id)
    }

    /**
     * Converts a canonical [User] domain entity into a [UserDto] populated with both
     * canonical and legacy compatibility fields.
     */
    fun toDto(user: User): UserDto {
        val normalizedId = user.id.trim()
        val normalizedName = user.fullName.trim()
        val normalizedEmail = user.email.trim().lowercase()
        val normalizedPhone = user.phoneNumber.trim()

        val effectiveCollectorStatus = if (user.role == UserRole.COLLECTOR) {
            if (user.collectorVerificationStatus == CollectorVerificationStatus.NOT_APPLICABLE) {
                CollectorVerificationStatus.NOT_SUBMITTED
            } else {
                user.collectorVerificationStatus
            }
        } else {
            CollectorVerificationStatus.NOT_APPLICABLE
        }

        // Legacy `isVerified` projection for unmigrated AdminFleetViewModel queries:
        // Only true when a Collector has been explicitly APPROVED (or for server-provisioned ADMIN).
        val legacyIsVerifiedProjection = when (user.role) {
            UserRole.COLLECTOR -> effectiveCollectorStatus == CollectorVerificationStatus.APPROVED
            UserRole.ADMIN -> true
            UserRole.RESIDENT -> false
        }

        // Legacy `status` projection for backward compatibility with AdminFleetViewModel / AdminModerationViewModel
        val legacyStatusProjection = when {
            user.accountStatus != AccountStatus.ACTIVE -> user.accountStatus.name
            user.role == UserRole.COLLECTOR && effectiveCollectorStatus == CollectorVerificationStatus.REJECTED -> "REJECTED"
            else -> AccountStatus.ACTIVE.name
        }

        return UserDto(
            id = normalizedId,
            uid = normalizedId,
            fullName = normalizedName,
            name = normalizedName,
            email = normalizedEmail,
            phoneNumber = normalizedPhone,
            phone = normalizedPhone,
            role = user.role.firestoreValue,
            accountStatus = user.accountStatus.name,
            status = legacyStatusProjection,
            statusReason = normalizeOptional(user.statusReason),
            suspendedUntilEpochMs = user.suspendedUntilEpochMs,
            bannedUntil = user.suspendedUntilEpochMs,
            onboardingStatus = user.onboardingStatus.name,
            isVerified = legacyIsVerifiedProjection,
            collectorVerificationStatus = effectiveCollectorStatus.name,
            collectorRejectionReason = if (user.role == UserRole.COLLECTOR) {
                normalizeOptional(user.collectorRejectionReason)
            } else {
                null
            },
            defaultZoneId = normalizeOptional(user.defaultZoneId),
            vehicleType = if (user.role == UserRole.COLLECTOR) {
                normalizeOptional(user.vehicleType)
            } else {
                null
            },
            vehiclePlateNumber = if (user.role == UserRole.COLLECTOR) {
                normalizeOptional(user.vehiclePlateNumber)
            } else {
                null
            },
            preferredLanguage = firstNonBlank(user.preferredLanguage) ?: "en",
            createdAtEpochMs = user.createdAtEpochMs,
            updatedAtEpochMs = user.updatedAtEpochMs
        )
    }

    /**
     * Converts a canonical [User] into a Firestore field map matching the exact allowlist
     * in `firestore.rules` (Section 5.2 of `MODULE_1_AUTH_ARCHITECTURE.md`).
     *
     * Does NOT write `isEmailVerified` or `isPhoneVerified`.
     */
    fun toFirestoreMap(user: User): Map<String, Any?> {
        val dto = toDto(user)
        return mapOf(
            "id" to dto.id,
            "uid" to dto.uid,
            "fullName" to dto.fullName,
            "name" to dto.name,
            "email" to dto.email,
            "phoneNumber" to dto.phoneNumber,
            "phone" to dto.phone,
            "role" to dto.role,
            "accountStatus" to dto.accountStatus,
            "status" to dto.status,
            "statusReason" to dto.statusReason,
            "suspendedUntilEpochMs" to dto.suspendedUntilEpochMs,
            "onboardingStatus" to dto.onboardingStatus,
            "isVerified" to dto.isVerified,
            "collectorVerificationStatus" to dto.collectorVerificationStatus,
            "collectorRejectionReason" to dto.collectorRejectionReason,
            "defaultZoneId" to dto.defaultZoneId,
            "vehicleType" to dto.vehicleType,
            "vehiclePlateNumber" to dto.vehiclePlateNumber,
            "preferredLanguage" to dto.preferredLanguage,
            "createdAtEpochMs" to dto.createdAtEpochMs,
            "updatedAtEpochMs" to dto.updatedAtEpochMs
        )
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            val trimmed = value?.trim()
            if (!trimmed.isNullOrEmpty()) {
                return trimmed
            }
        }
        return null
    }

    private fun normalizeOptional(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun resolveEpochMillis(primaryEpochMs: Long?, fallbackRaw: Any?): Long {
        if (primaryEpochMs != null && primaryEpochMs > 0L) {
            return primaryEpochMs
        }
        return extractNullableEpochMillis(fallbackRaw) ?: 0L
    }

    private fun extractNullableEpochMillis(raw: Any?): Long? {
        return when (raw) {
            is Number -> raw.toLong().takeIf { it > 0L }
            is Timestamp -> raw.toDate().time.takeIf { it > 0L }
            is Date -> raw.time.takeIf { it > 0L }
            else -> null
        }
    }
}

/**
 * Extension to convert [UserDto] to the canonical [User] domain entity.
 */
fun UserDto.toDomainOrNull(fallbackDocumentId: String? = null): User? =
    UserMapper.toDomainOrNull(this, fallbackDocumentId = fallbackDocumentId)

/**
 * Extension to convert the canonical [User] domain entity to [UserDto].
 */
fun User.toDto(): UserDto = UserMapper.toDto(this)

/**
 * Extension to convert the canonical [User] domain entity to a Firestore field map.
 */
fun User.toFirestoreMap(): Map<String, Any?> = UserMapper.toFirestoreMap(this)
