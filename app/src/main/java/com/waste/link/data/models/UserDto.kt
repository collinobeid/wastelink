package com.waste.link.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import java.util.Date

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
     * Converts a [UserDto] to the canonical [User] domain model.
     *
     * Returns `null` if:
     * - The user ID (`id`, `uid`, or [fallbackDocumentId]) is missing/blank.
     * - The email is missing/blank.
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
        val resolvedId = firstNonBlank(dto.id, dto.uid, fallbackDocumentId) ?: return null
        val resolvedEmail = firstNonBlank(dto.email)?.lowercase() ?: return null
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
