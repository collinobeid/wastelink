package com.waste.link.domain.model

/**
 * Immutable business identity role for a WasteLink user.
 *
 * - Selected once during registration ([RESIDENT] or [COLLECTOR]).
 * - [ADMIN] is provisioned strictly server-side and can never be self-assigned by a client.
 * - There is no unassigned role in the canonical domain model.
 */
enum class UserRole(val firestoreValue: String) {
    RESIDENT("resident"),
    COLLECTOR("collector"),
    ADMIN("admin");

    /**
     * True only for roles that a user is allowed to select during client registration or profile recovery.
     */
    val isSelfRegistrable: Boolean
        get() = this == RESIDENT || this == COLLECTOR

    companion object {
        /**
         * Centralized, case-insensitive role parser supporting both canonical and legacy Firestore values.
         *
         * Security guarantee:
         * - Unknown, blank, null, or legacy `"unassigned"` values ALWAYS return `null`.
         * - Unknown or malformed values NEVER default or escalate to [ADMIN] or any other role.
         */
        fun fromFirestoreValue(value: String?): UserRole? {
            return when (value?.trim()?.uppercase()) {
                "RESIDENT", "CLIENT" -> RESIDENT
                "COLLECTOR", "WASTE_COMPANY" -> COLLECTOR
                "ADMIN" -> ADMIN
                else -> null
            }
        }

        /**
         * Parses a role selected during client-side registration or profile recovery.
         * Explicitly rejects [ADMIN] even if `"admin"` or `"ADMIN"` is passed.
         */
        fun fromClientSelection(value: String?): UserRole? {
            val parsed = fromFirestoreValue(value) ?: return null
            return parsed.takeIf { it.isSelfRegistrable }
        }
    }
}

/**
 * Administrative account standing controlled exclusively by authorized administrators or backend rules.
 */
enum class AccountStatus {
    ACTIVE,
    RESTRICTED,
    SUSPENDED,
    BANNED;

    companion object {
        /**
         * Parses [AccountStatus] from Firestore strings.
         *
         * Legacy `"UNVERIFIED"` and `"REJECTED"` status strings on `AppUser` map to [ACTIVE]
         * account standing because email verification (FirebaseAuth) and collector fleet approval
         * ([CollectorVerificationStatus]) are tracked on dedicated orthogonal axes.
         */
        fun fromFirestoreValue(
            value: String?,
            legacyDeletedAtEpochMs: Long? = null,
            legacyIsActive: Boolean? = null
        ): AccountStatus {
            if (legacyDeletedAtEpochMs != null && legacyDeletedAtEpochMs > 0L) {
                return BANNED
            }
            val normalized = value?.trim()?.uppercase()
            val mapped = when (normalized) {
                "RESTRICTED" -> RESTRICTED
                "SUSPENDED" -> SUSPENDED
                "BANNED" -> BANNED
                "ACTIVE", "UNVERIFIED", "REJECTED", null, "" -> ACTIVE
                else -> ACTIVE
            }
            if (legacyIsActive == false && mapped == ACTIVE) {
                return BANNED
            }
            return mapped
        }
    }
}

/**
 * Tracks completion of role-specific post-registration onboarding.
 * Device runtime permissions (notifications, location) are never required to reach [COMPLETED].
 */
enum class OnboardingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED;

    companion object {
        /**
         * Parses [OnboardingStatus] from Firestore strings.
         *
         * @param value Raw Firestore string value (`"NOT_STARTED"`, `"IN_PROGRESS"`, `"COMPLETED"`).
         * @param hasExistingValidRole When `true` and [value] is null/blank (pre-Module 1 legacy document
         *   that already has a valid role), defaults to [COMPLETED] so existing users are not forced to re-onboard.
         */
        fun fromFirestoreValue(
            value: String?,
            hasExistingValidRole: Boolean = false
        ): OnboardingStatus {
            return when (value?.trim()?.uppercase()) {
                "NOT_STARTED" -> NOT_STARTED
                "IN_PROGRESS" -> IN_PROGRESS
                "COMPLETED" -> COMPLETED
                else -> if (hasExistingValidRole) COMPLETED else NOT_STARTED
            }
        }
    }
}

/**
 * Tracks operational fleet vetting for [UserRole.COLLECTOR] accounts.
 * Always [NOT_APPLICABLE] for [UserRole.RESIDENT] and [UserRole.ADMIN].
 */
enum class CollectorVerificationStatus {
    NOT_APPLICABLE,
    NOT_SUBMITTED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED;

    companion object {
        /**
         * Resolves [CollectorVerificationStatus] from canonical or legacy Firestore fields.
         *
         * Security & domain invariant:
         * - Non-collector roles ([UserRole.RESIDENT], [UserRole.ADMIN], `null`) ALWAYS resolve to [NOT_APPLICABLE].
         * - For [UserRole.COLLECTOR], explicit canonical status values take precedence; if absent on a
         *   legacy document, falls back to legacy `isVerified` and `status` fields.
         */
        fun fromFirestoreValue(
            value: String?,
            role: UserRole?,
            legacyIsVerified: Boolean? = null,
            legacyStatus: String? = null
        ): CollectorVerificationStatus {
            if (role != UserRole.COLLECTOR) {
                return NOT_APPLICABLE
            }
            return when (value?.trim()?.uppercase()) {
                "NOT_SUBMITTED" -> NOT_SUBMITTED
                "PENDING_REVIEW" -> PENDING_REVIEW
                "APPROVED" -> APPROVED
                "REJECTED" -> REJECTED
                else -> when {
                    legacyIsVerified == true -> APPROVED
                    legacyStatus?.trim()?.uppercase() == "REJECTED" -> REJECTED
                    legacyIsVerified == false && !legacyStatus.isNullOrBlank() -> PENDING_REVIEW
                    else -> NOT_SUBMITTED
                }
            }
        }
    }
}

/**
 * Single canonical domain entity representing a WasteLink user profile.
 *
 * Architectural invariants:
 * 1. Completely independent of Firebase annotations (`@DocumentId`, `@ServerTimestamp`, `Timestamp`).
 * 2. Does NOT persist duplicate email-verification booleans in Firestore; authoritative email
 *    verification state comes exclusively from Firebase Authentication (`FirebaseUser.isEmailVerified`
 *    and `request.auth.token.email_verified`).
 * 3. `isPhoneVerified` is always `false` in Module 1 because `phoneNumber` is stored strictly as
 *    E.164 contact metadata until a trusted OTP verification workflow is introduced.
 */
data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val role: UserRole,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val statusReason: String? = null,
    val suspendedUntilEpochMs: Long? = null,
    val onboardingStatus: OnboardingStatus = OnboardingStatus.NOT_STARTED,
    val isPhoneVerified: Boolean = false,
    val collectorVerificationStatus: CollectorVerificationStatus =
        if (role == UserRole.COLLECTOR) CollectorVerificationStatus.NOT_SUBMITTED
        else CollectorVerificationStatus.NOT_APPLICABLE,
    val collectorRejectionReason: String? = null,
    val defaultZoneId: String? = null,
    val vehicleType: String? = null,
    val vehiclePlateNumber: String? = null,
    val preferredLanguage: String = "en",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    init {
        require(id.isNotBlank()) { "User.id must not be blank" }
        require(fullName.isNotBlank()) { "User.fullName must not be blank" }
        require(email.isNotBlank()) { "User.email must not be blank" }
    }

    /**
     * True if the user is a Collector who has been vetted and approved by an Administrator
     * and has an active account standing.
     */
    val isCollectorOperational: Boolean
        get() = role == UserRole.COLLECTOR &&
            accountStatus == AccountStatus.ACTIVE &&
            collectorVerificationStatus == CollectorVerificationStatus.APPROVED

    /**
     * True if the user's account is locked due to temporary suspension or permanent ban.
     */
    val isAccountLocked: Boolean
        get() = accountStatus == AccountStatus.SUSPENDED || accountStatus == AccountStatus.BANNED
}
