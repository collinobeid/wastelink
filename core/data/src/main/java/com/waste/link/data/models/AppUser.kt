package com.waste.link.data.models

import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.User

/**
 * TEMPORARY LEGACY ADAPTER — Scheduled for removal once Admin (Module 4) and Profile (Module 5)
 * consumers are migrated to the canonical domain entity [com.waste.link.domain.model.User].
 *
 * Architectural Rule:
 * - [com.waste.link.domain.model.User] is the single business-level source of truth.
 * - Do NOT introduce new usages of [AppUser] in newly written or refactored domain/UI code.
 * - Use [toDomainUserOrNull] and [fromDomainUser] to bridge legacy consumers safely.
 */
data class AppUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "resident", // "resident", "collector", "admin"
    val isVerified: Boolean = false,

    // Core Architecture Fields (Legacy)
    val status: String = "UNVERIFIED", // UNVERIFIED, ACTIVE, RESTRICTED, SUSPENDED, BANNED
    val bannedUntil: Long? = null, // Using Long for timestamp for Firebase compatibility
    val contractUntil: Long? = null,
    val deletedAt: Long? = null,

    // Canonical Compatibility Bridge Fields (populated on v2.1+ documents)
    val id: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val accountStatus: String? = null,
    val statusReason: String? = null,
    val suspendedUntilEpochMs: Long? = null,
    val onboardingStatus: String? = null,
    val collectorVerificationStatus: String? = null,
    val collectorRejectionReason: String? = null,
    val defaultZoneId: String? = null,
    val vehicleType: String? = null,
    val vehiclePlateNumber: String? = null,
    val preferredLanguage: String = "en",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    /**
     * Converts this legacy [AppUser] adapter instance into the canonical [User] domain model.
     * Returns `null` if the identity fields or role are missing, `"unassigned"`, or invalid.
     */
    fun toDomainUserOrNull(fallbackDocumentId: String? = null): User? {
        val dto = UserDto(
            id = id.takeIf { it.isNotBlank() },
            uid = uid.takeIf { it.isNotBlank() },
            fullName = fullName.takeIf { it.isNotBlank() },
            name = name.takeIf { it.isNotBlank() },
            email = email.takeIf { it.isNotBlank() },
            phoneNumber = phoneNumber.takeIf { it.isNotBlank() },
            phone = phone.takeIf { it.isNotBlank() },
            role = role.takeIf { it.isNotBlank() },
            accountStatus = accountStatus,
            status = status.takeIf { it.isNotBlank() },
            statusReason = statusReason,
            suspendedUntilEpochMs = suspendedUntilEpochMs,
            bannedUntil = bannedUntil,
            contractUntil = contractUntil,
            deletedAt = deletedAt,
            onboardingStatus = onboardingStatus,
            isVerified = isVerified,
            collectorVerificationStatus = collectorVerificationStatus,
            collectorRejectionReason = collectorRejectionReason,
            defaultZoneId = defaultZoneId,
            vehicleType = vehicleType,
            vehiclePlateNumber = vehiclePlateNumber,
            preferredLanguage = preferredLanguage,
            createdAtEpochMs = createdAtEpochMs.takeIf { it > 0L },
            updatedAtEpochMs = updatedAtEpochMs.takeIf { it > 0L }
        )
        return UserMapper.toDomainOrNull(dto, fallbackDocumentId = fallbackDocumentId)
    }

    companion object {
        /**
         * Creates a legacy [AppUser] adapter projection from the canonical [User] domain model.
         */
        fun fromDomainUser(user: User): AppUser {
            val dto = UserMapper.toDto(user)
            return AppUser(
                uid = user.id,
                name = user.fullName,
                email = user.email,
                phone = user.phoneNumber,
                role = user.role.firestoreValue,
                isVerified = dto.isVerified ?: false,
                status = dto.status ?: AccountStatus.ACTIVE.name,
                bannedUntil = user.suspendedUntilEpochMs,
                contractUntil = null,
                deletedAt = null,
                id = user.id,
                fullName = user.fullName,
                phoneNumber = user.phoneNumber,
                accountStatus = user.accountStatus.name,
                statusReason = user.statusReason,
                suspendedUntilEpochMs = user.suspendedUntilEpochMs,
                onboardingStatus = user.onboardingStatus.name,
                collectorVerificationStatus = if (user.collectorVerificationStatus == CollectorVerificationStatus.NOT_APPLICABLE) {
                    CollectorVerificationStatus.NOT_APPLICABLE.name
                } else {
                    user.collectorVerificationStatus.name
                },
                collectorRejectionReason = user.collectorRejectionReason,
                defaultZoneId = user.defaultZoneId,
                vehicleType = user.vehicleType,
                vehiclePlateNumber = user.vehiclePlateNumber,
                preferredLanguage = user.preferredLanguage,
                createdAtEpochMs = user.createdAtEpochMs,
                updatedAtEpochMs = user.updatedAtEpochMs
            )
        }
    }
}
