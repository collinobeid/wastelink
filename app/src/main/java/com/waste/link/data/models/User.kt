package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * TEMPORARY LEGACY DATA MODEL — Retained solely for binary/source compatibility with the
 * unrouted legacy `AdminUsersViewScreen.kt` until Module 4 decommissions it.
 *
 * Do NOT use this class in any new or refactored code.
 * The single canonical domain entity is [com.waste.link.domain.model.User].
 */
data class User(
    @DocumentId
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val preferredLanguage: String = "en",
    val isActive: Boolean = true, // Legacy, migrate to status later

    // Core Architecture Fields
    val role: String = "CLIENT", // CLIENT, WASTE_COMPANY, ADMIN
    val status: String = "UNVERIFIED", // UNVERIFIED, ACTIVE, RESTRICTED, SUSPENDED, BANNED
    val bannedUntil: Date? = null,
    val contractUntil: Date? = null,
    val deletedAt: Date? = null,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    /**
     * Converts this legacy [com.waste.link.data.models.User] into the canonical
     * [com.waste.link.domain.model.User] domain entity, or returns `null` if invalid.
     */
    fun toDomainUserOrNull(fallbackDocumentId: String? = null): com.waste.link.domain.model.User? {
        val dto = UserDto(
            id = id.takeIf { it.isNotBlank() },
            fullName = fullName.takeIf { it.isNotBlank() },
            email = email.takeIf { it.isNotBlank() },
            phoneNumber = phoneNumber.takeIf { it.isNotBlank() },
            role = role.takeIf { it.isNotBlank() },
            status = status.takeIf { it.isNotBlank() },
            bannedUntil = bannedUntil?.time,
            contractUntil = contractUntil?.time,
            deletedAt = deletedAt?.time,
            isActive = isActive,
            preferredLanguage = preferredLanguage
        )
        return UserMapper.toDomainOrNull(
            dto = dto,
            fallbackDocumentId = fallbackDocumentId,
            legacyCreatedAtRaw = createdAt,
            legacyUpdatedAtRaw = updatedAt
        )
    }
}
