package com.waste.link.domain

import com.google.firebase.Timestamp
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.UserDto
import com.waste.link.data.models.UserMapper
import com.waste.link.data.models.toDomainOrNull
import com.waste.link.data.models.toDto
import com.waste.link.data.models.toFirestoreMap
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class UserDomainAndMapperTest {

    // =========================================================================
    // 1. UserRole Conversions, Legacy Mapping & Privilege Escalation Regression
    // =========================================================================

    @Test
    fun `UserRole fromFirestoreValue maps canonical and legacy values case-insensitively`() {
        assertEquals(UserRole.RESIDENT, UserRole.fromFirestoreValue("resident"))
        assertEquals(UserRole.RESIDENT, UserRole.fromFirestoreValue("RESIDENT"))
        assertEquals(UserRole.RESIDENT, UserRole.fromFirestoreValue("  Resident  "))
        assertEquals(UserRole.RESIDENT, UserRole.fromFirestoreValue("CLIENT"))
        assertEquals(UserRole.RESIDENT, UserRole.fromFirestoreValue("client"))

        assertEquals(UserRole.COLLECTOR, UserRole.fromFirestoreValue("collector"))
        assertEquals(UserRole.COLLECTOR, UserRole.fromFirestoreValue("COLLECTOR"))
        assertEquals(UserRole.COLLECTOR, UserRole.fromFirestoreValue("WASTE_COMPANY"))
        assertEquals(UserRole.COLLECTOR, UserRole.fromFirestoreValue("waste_company"))

        assertEquals(UserRole.ADMIN, UserRole.fromFirestoreValue("admin"))
        assertEquals(UserRole.ADMIN, UserRole.fromFirestoreValue("ADMIN"))
    }

    @Test
    fun `UserRole fromFirestoreValue returns null for unassigned and unknown values and NEVER escalates to ADMIN`() {
        val malformedInputs = listOf(
            null,
            "",
            "   ",
            "unassigned",
            "UNASSIGNED",
            "superuser",
            "root",
            "admin_user",
            "administrator",
            "moderator",
            "system",
            "1",
            "true",
            "{role:admin}"
        )

        for (input in malformedInputs) {
            val parsed = UserRole.fromFirestoreValue(input)
            assertNull("Expected null for malformed role '$input', but got $parsed", parsed)
        }
    }

    @Test
    fun `UserRole fromClientSelection permits RESIDENT and COLLECTOR but strictly blocks ADMIN`() {
        assertEquals(UserRole.RESIDENT, UserRole.fromClientSelection("resident"))
        assertEquals(UserRole.COLLECTOR, UserRole.fromClientSelection("collector"))
        assertNull(UserRole.fromClientSelection("admin"))
        assertNull(UserRole.fromClientSelection("ADMIN"))
        assertNull(UserRole.fromClientSelection("unassigned"))
        assertFalse(UserRole.ADMIN.isSelfRegistrable)
        assertTrue(UserRole.RESIDENT.isSelfRegistrable)
        assertTrue(UserRole.COLLECTOR.isSelfRegistrable)
    }

    // =========================================================================
    // 2. AccountStatus Conversions
    // =========================================================================

    @Test
    fun `AccountStatus fromFirestoreValue maps canonical and legacy status values accurately`() {
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromFirestoreValue("ACTIVE"))
        assertEquals(AccountStatus.RESTRICTED, AccountStatus.fromFirestoreValue("RESTRICTED"))
        assertEquals(AccountStatus.SUSPENDED, AccountStatus.fromFirestoreValue("SUSPENDED"))
        assertEquals(AccountStatus.BANNED, AccountStatus.fromFirestoreValue("BANNED"))

        // Legacy AppUser status values "UNVERIFIED" and "REJECTED" map to ACTIVE account standing
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromFirestoreValue("UNVERIFIED"))
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromFirestoreValue("REJECTED"))
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromFirestoreValue(null))
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromFirestoreValue(""))

        // Legacy deletedAt or isActive=false maps to BANNED
        assertEquals(
            AccountStatus.BANNED,
            AccountStatus.fromFirestoreValue("ACTIVE", legacyDeletedAtEpochMs = 1730000000000L)
        )
        assertEquals(
            AccountStatus.BANNED,
            AccountStatus.fromFirestoreValue("ACTIVE", legacyIsActive = false)
        )
    }

    // =========================================================================
    // 3. OnboardingStatus Conversions
    // =========================================================================

    @Test
    fun `OnboardingStatus fromFirestoreValue handles explicit states and legacy role fallback`() {
        assertEquals(OnboardingStatus.NOT_STARTED, OnboardingStatus.fromFirestoreValue("NOT_STARTED"))
        assertEquals(OnboardingStatus.IN_PROGRESS, OnboardingStatus.fromFirestoreValue("IN_PROGRESS"))
        assertEquals(OnboardingStatus.COMPLETED, OnboardingStatus.fromFirestoreValue("COMPLETED"))

        // When missing on a brand-new record without existing role fallback
        assertEquals(
            OnboardingStatus.NOT_STARTED,
            OnboardingStatus.fromFirestoreValue(null, hasExistingValidRole = false)
        )
        // When missing on a pre-existing legacy user document with a valid role
        assertEquals(
            OnboardingStatus.COMPLETED,
            OnboardingStatus.fromFirestoreValue(null, hasExistingValidRole = true)
        )
    }

    // =========================================================================
    // 4. CollectorVerificationStatus Conversions
    // =========================================================================

    @Test
    fun `CollectorVerificationStatus forces NOT_APPLICABLE for non-collector roles`() {
        assertEquals(
            CollectorVerificationStatus.NOT_APPLICABLE,
            CollectorVerificationStatus.fromFirestoreValue(
                value = "APPROVED",
                role = UserRole.RESIDENT,
                legacyIsVerified = true
            )
        )
        assertEquals(
            CollectorVerificationStatus.NOT_APPLICABLE,
            CollectorVerificationStatus.fromFirestoreValue(
                value = "APPROVED",
                role = UserRole.ADMIN,
                legacyIsVerified = true
            )
        )
    }

    @Test
    fun `CollectorVerificationStatus resolves canonical and legacy collector states`() {
        assertEquals(
            CollectorVerificationStatus.NOT_SUBMITTED,
            CollectorVerificationStatus.fromFirestoreValue("NOT_SUBMITTED", UserRole.COLLECTOR)
        )
        assertEquals(
            CollectorVerificationStatus.PENDING_REVIEW,
            CollectorVerificationStatus.fromFirestoreValue("PENDING_REVIEW", UserRole.COLLECTOR)
        )
        assertEquals(
            CollectorVerificationStatus.APPROVED,
            CollectorVerificationStatus.fromFirestoreValue("APPROVED", UserRole.COLLECTOR)
        )
        assertEquals(
            CollectorVerificationStatus.REJECTED,
            CollectorVerificationStatus.fromFirestoreValue("REJECTED", UserRole.COLLECTOR)
        )

        // Legacy collector fallbacks when collectorVerificationStatus field is absent
        assertEquals(
            CollectorVerificationStatus.APPROVED,
            CollectorVerificationStatus.fromFirestoreValue(
                value = null,
                role = UserRole.COLLECTOR,
                legacyIsVerified = true,
                legacyStatus = "ACTIVE"
            )
        )
        assertEquals(
            CollectorVerificationStatus.REJECTED,
            CollectorVerificationStatus.fromFirestoreValue(
                value = null,
                role = UserRole.COLLECTOR,
                legacyIsVerified = false,
                legacyStatus = "REJECTED"
            )
        )
        assertEquals(
            CollectorVerificationStatus.PENDING_REVIEW,
            CollectorVerificationStatus.fromFirestoreValue(
                value = null,
                role = UserRole.COLLECTOR,
                legacyIsVerified = false,
                legacyStatus = "UNVERIFIED"
            )
        )
    }

    // =========================================================================
    // 5. DTO-to-Domain & Domain-to-DTO Round-Trip & Legacy Compatibility
    // =========================================================================

    @Test
    fun `UserMapper round-trips canonical Collector User through UserDto and FirestoreMap`() {
        val original = User(
            id = "uid_collector_101",
            fullName = "Amina Nyerere",
            email = "amina@wastelink.co.tz",
            phoneNumber = "+255712345678",
            role = UserRole.COLLECTOR,
            accountStatus = AccountStatus.ACTIVE,
            statusReason = null,
            suspendedUntilEpochMs = null,
            onboardingStatus = OnboardingStatus.COMPLETED,
            collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
            collectorRejectionReason = null,
            defaultZoneId = "zone_kinondoni",
            vehicleType = "Compactor Truck",
            vehiclePlateNumber = "T 456 ABC",
            preferredLanguage = "sw",
            createdAtEpochMs = 1730000000000L,
            updatedAtEpochMs = 1730000500000L
        )

        val dto = original.toDto()
        assertEquals("uid_collector_101", dto.id)
        assertEquals("uid_collector_101", dto.uid)
        assertEquals("Amina Nyerere", dto.fullName)
        assertEquals("Amina Nyerere", dto.name)
        assertEquals("collector", dto.role)
        assertEquals("ACTIVE", dto.accountStatus)
        assertEquals("ACTIVE", dto.status)
        assertEquals(false, dto.isVerified)
        assertEquals("PENDING_REVIEW", dto.collectorVerificationStatus)

        val firestoreMap = original.toFirestoreMap()
        // Authoritative email/phone verification booleans must NOT be duplicated in Firestore map
        assertFalse(firestoreMap.containsKey("isEmailVerified"))
        assertFalse(firestoreMap.containsKey("isPhoneVerified"))

        val roundTripped = UserMapper.fromFirestoreMap(firestoreMap)
        assertEquals(original, roundTripped)
        assertFalse(roundTripped!!.isCollectorOperational)
    }

    @Test
    fun `UserMapper maps legacy AppUser Firestore document with missing canonical fields`() {
        val legacyMap = mapOf<String, Any?>(
            "uid" to "legacy_res_99",
            "name" to "Juma Bakari",
            "email" to "Juma.Bakari@Example.com",
            "phone" to "+255754112233",
            "role" to "resident",
            "isVerified" to false,
            "status" to "UNVERIFIED",
            "createdAt" to Timestamp(Date(1725000000000L)),
            "updatedAt" to Date(1725000900000L)
        )

        val domainUser = UserMapper.fromFirestoreMap(legacyMap)
        assertNotNull(domainUser)
        assertEquals("legacy_res_99", domainUser!!.id)
        assertEquals("Juma Bakari", domainUser.fullName)
        assertEquals("juma.bakari@example.com", domainUser.email)
        assertEquals("+255754112233", domainUser.phoneNumber)
        assertEquals(UserRole.RESIDENT, domainUser.role)
        assertEquals(AccountStatus.ACTIVE, domainUser.accountStatus)
        assertEquals(OnboardingStatus.COMPLETED, domainUser.onboardingStatus)
        assertEquals(CollectorVerificationStatus.NOT_APPLICABLE, domainUser.collectorVerificationStatus)
        assertEquals(1725000000000L, domainUser.createdAtEpochMs)
        assertEquals(1725000900000L, domainUser.updatedAtEpochMs)
    }

    @Test
    fun `UserMapper rejects legacy fabricated ghost profile and unassigned roles`() {
        val fabricatedGhostUser = UserDto(
            uid = "ghost_uid_1",
            name = "User",
            email = "ghost@example.com",
            phone = "Not provided",
            role = "resident",
            status = "ACTIVE"
        )
        assertNull(fabricatedGhostUser.toDomainOrNull())

        val unassignedUser = UserDto(
            uid = "unassigned_uid_2",
            name = "Real Name",
            email = "real@example.com",
            phone = "+255712345678",
            role = "unassigned",
            status = "UNVERIFIED"
        )
        assertNull(unassignedUser.toDomainOrNull())
    }

    @Test
    fun `UserMapper normalizes blank optional strings to null and strips collector fields from Resident`() {
        val dto = UserDto(
            id = "res_uid_10",
            fullName = "Neema Mushi",
            email = "neema@example.com",
            phoneNumber = "+255712000111",
            role = "RESIDENT",
            statusReason = "   ",
            collectorRejectionReason = "Should not apply to resident",
            defaultZoneId = "  ",
            vehicleType = "Tricycle",
            vehiclePlateNumber = "T 111 AAA"
        )

        val user = dto.toDomainOrNull()
        assertNotNull(user)
        assertNull(user!!.statusReason)
        assertNull(user.defaultZoneId)
        assertNull(user.collectorRejectionReason)
        assertNull(user.vehicleType)
        assertNull(user.vehiclePlateNumber)
        assertEquals(CollectorVerificationStatus.NOT_APPLICABLE, user.collectorVerificationStatus)
    }

    @Test
    fun `AppUser and legacy data User adapters convert accurately to and from canonical User`() {
        val canonicalCollector = User(
            id = "col_555",
            fullName = "Baraka Mwinyi",
            email = "baraka@fleet.co.tz",
            phoneNumber = "+255788990011",
            role = UserRole.COLLECTOR,
            accountStatus = AccountStatus.ACTIVE,
            onboardingStatus = OnboardingStatus.COMPLETED,
            collectorVerificationStatus = CollectorVerificationStatus.APPROVED,
            vehicleType = "Side Loader",
            vehiclePlateNumber = "T 888 WST"
        )

        val appUser = AppUser.fromDomainUser(canonicalCollector)
        assertEquals("col_555", appUser.uid)
        assertEquals("Baraka Mwinyi", appUser.name)
        assertEquals("collector", appUser.role)
        assertTrue(appUser.isVerified)
        assertEquals("ACTIVE", appUser.status)

        val backToDomain = appUser.toDomainUserOrNull()
        assertEquals(canonicalCollector, backToDomain)
        assertTrue(backToDomain!!.isCollectorOperational)

        val legacyDataUser = com.waste.link.data.models.User(
            id = "legacy_company_1",
            email = "ops@cleaners.co.tz",
            fullName = "Cleaners Ltd",
            phoneNumber = "+255711223344",
            role = "WASTE_COMPANY",
            status = "SUSPENDED",
            bannedUntil = Date(1740000000000L),
            createdAt = Date(1710000000000L)
        )
        val convertedFromLegacyDataUser = legacyDataUser.toDomainUserOrNull()
        assertNotNull(convertedFromLegacyDataUser)
        assertEquals(UserRole.COLLECTOR, convertedFromLegacyDataUser!!.role)
        assertEquals(AccountStatus.SUSPENDED, convertedFromLegacyDataUser.accountStatus)
        assertEquals(1740000000000L, convertedFromLegacyDataUser.suspendedUntilEpochMs)
        assertTrue(convertedFromLegacyDataUser.isAccountLocked)
    }
}
