package com.waste.link.ui.navigation

import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.AuthError
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.ProfileIncompleteReason
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.Destinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Authoritative JVM Unit Test Suite for [SessionNavigationResolver] (Module 1, Stage 6).
 *
 * Verifies all 15 states of the centralized navigation decision matrix, offline caching safety,
 * role-standing security guards, and the mandatory Firebase Auth custom claim token.admin == true invariant.
 */
class SessionNavigationResolverTest {

    private fun buildUser(
        id: String = "test-uid",
        role: UserRole = UserRole.RESIDENT,
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
        onboardingStatus: OnboardingStatus = OnboardingStatus.COMPLETED,
        collectorVerificationStatus: CollectorVerificationStatus = CollectorVerificationStatus.NOT_APPLICABLE,
        collectorRejectionReason: String? = null,
        statusReason: String? = null,
        suspendedUntilEpochMs: Long? = null
    ): User {
        return User(
            id = id,
            fullName = "Amina Nyerere",
            email = "amina@wastelink.co.tz",
            phoneNumber = "+255712345678",
            role = role,
            accountStatus = accountStatus,
            onboardingStatus = onboardingStatus,
            collectorVerificationStatus = collectorVerificationStatus,
            collectorRejectionReason = collectorRejectionReason,
            statusReason = statusReason,
            suspendedUntilEpochMs = suspendedUntilEpochMs,
            defaultZoneId = "zone_kinondoni",
            createdAtEpochMs = 1700000000000L,
            updatedAtEpochMs = 1700000000000L
        )
    }

    // =========================================================================
    // 1. Unauthenticated & Revoked Sessions
    // =========================================================================

    @Test
    fun `1 unauthenticated session resolves to Login`() {
        val decision = SessionNavigationResolver.resolve(SessionState.Unauthenticated)
        assertTrue(decision is NavigationDestinationDecision.Login)
        assertEquals(Destinations.LOGIN, decision.route)
    }

    @Test
    fun `2 authentication revoked session resolves to Login`() {
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticationRevoked(AuthError.SessionRevoked())
        )
        assertTrue(decision is NavigationDestinationDecision.Login)
        assertEquals(Destinations.LOGIN, decision.route)
    }

    // =========================================================================
    // 2. Profile Incomplete & Load Error Sessions
    // =========================================================================

    @Test
    fun `3 incomplete profile resolves to CompleteProfile with reason`() {
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedProfileIncomplete(
                uid = "ghost-uid",
                email = "ghost@wastelink.co.tz",
                isEmailVerified = true,
                reason = ProfileIncompleteReason.DOCUMENT_MISSING
            )
        )
        assertTrue(decision is NavigationDestinationDecision.CompleteProfile)
        val complete = decision as NavigationDestinationDecision.CompleteProfile
        assertEquals("ghost-uid", complete.uid)
        assertEquals("ghost@wastelink.co.tz", complete.email)
        assertEquals(ProfileIncompleteReason.DOCUMENT_MISSING, complete.reason)
        assertEquals(Destinations.COMPLETE_PROFILE, complete.route)
    }

    @Test
    fun `4 profile load error resolves to Login with prefilled email and error`() {
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedProfileLoadError(
                uid = "uid-1",
                email = "user@wastelink.co.tz",
                error = AuthError.NetworkFailure("Firestore read timeout")
            )
        )
        assertTrue(decision is NavigationDestinationDecision.Login)
        val login = decision as NavigationDestinationDecision.Login
        assertEquals("user@wastelink.co.tz", login.prefillEmail)
        assertEquals("Firestore read timeout", login.loadError)
        assertEquals(Destinations.LOGIN, login.route)
    }

    // =========================================================================
    // 3. Email Verification Gates
    // =========================================================================

    @Test
    fun `5 unverified email for resident resolves to VerifyEmail`() {
        val resident = buildUser(role = UserRole.RESIDENT)
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(
                user = resident,
                isEmailVerified = false,
                hasAdminClaim = false
            )
        )
        assertTrue(decision is NavigationDestinationDecision.VerifyEmail)
        assertEquals("amina@wastelink.co.tz", (decision as NavigationDestinationDecision.VerifyEmail).email)
        assertEquals(Destinations.VERIFY_EMAIL, decision.route)
    }

    @Test
    fun `6 unverified email for collector resolves to VerifyEmail`() {
        val collector = buildUser(role = UserRole.COLLECTOR)
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(
                user = collector,
                isEmailVerified = false,
                hasAdminClaim = false
            )
        )
        assertTrue(decision is NavigationDestinationDecision.VerifyEmail)
        assertEquals(Destinations.VERIFY_EMAIL, decision.route)
    }

    // =========================================================================
    // 4. Resident Lifecycle Routing
    // =========================================================================

    @Test
    fun `7 resident with incomplete onboarding resolves to ResidentOnboarding`() {
        val resident = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(user = resident, isEmailVerified = true)
        )
        assertTrue(decision is NavigationDestinationDecision.ResidentOnboarding)
        assertEquals(Destinations.ONBOARDING, decision.route)
    }

    @Test
    fun `8 resident with completed onboarding resolves to ResidentHome`() {
        val resident = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.COMPLETED
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(user = resident, isEmailVerified = true)
        )
        assertTrue(decision is NavigationDestinationDecision.ResidentHome)
        val home = decision as NavigationDestinationDecision.ResidentHome
        assertFalse(home.isRestricted)
        assertFalse(home.isOffline)
        assertEquals(Destinations.RESIDENT_HOME, home.route)
    }

    // =========================================================================
    // 5. Collector Verification Lifecycle Routing
    // =========================================================================

    @Test
    fun `9 collector not submitted resolves to CollectorOnboarding`() {
        val collector = buildUser(
            role = UserRole.COLLECTOR,
            collectorVerificationStatus = CollectorVerificationStatus.NOT_SUBMITTED
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(user = collector, isEmailVerified = true)
        )
        assertTrue(decision is NavigationDestinationDecision.CollectorOnboarding)
        assertEquals(Destinations.ONBOARDING, decision.route)
    }

    @Test
    fun `10 collector pending review resolves to CollectorStatus pending`() {
        val collector = buildUser(
            role = UserRole.COLLECTOR,
            collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(user = collector, isEmailVerified = true)
        )
        assertTrue(decision is NavigationDestinationDecision.CollectorStatus)
        val status = decision as NavigationDestinationDecision.CollectorStatus
        assertEquals(CollectorVerificationStatus.PENDING_REVIEW, status.status)
        assertEquals(Destinations.COLLECTOR_VERIFICATION_STATUS, status.route)
    }

    @Test
    fun `11 collector rejected resolves to CollectorStatus rejected with reason`() {
        val collector = buildUser(
            role = UserRole.COLLECTOR,
            collectorVerificationStatus = CollectorVerificationStatus.REJECTED,
            collectorRejectionReason = "Expired driving license"
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(user = collector, isEmailVerified = true)
        )
        assertTrue(decision is NavigationDestinationDecision.CollectorStatus)
        val status = decision as NavigationDestinationDecision.CollectorStatus
        assertEquals(CollectorVerificationStatus.REJECTED, status.status)
        assertEquals("Expired driving license", status.rejectionReason)
        assertEquals(Destinations.COLLECTOR_VERIFICATION_STATUS, status.route)
    }

    @Test
    fun `12 collector approved resolves to CollectorDashboard`() {
        val collector = buildUser(
            role = UserRole.COLLECTOR,
            collectorVerificationStatus = CollectorVerificationStatus.APPROVED
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(user = collector, isEmailVerified = true)
        )
        assertTrue(decision is NavigationDestinationDecision.CollectorDashboard)
        val dash = decision as NavigationDestinationDecision.CollectorDashboard
        assertFalse(dash.isRestricted)
        assertFalse(dash.isOffline)
        assertEquals(Destinations.COLLECTOR_DASHBOARD, dash.route)
    }

    // =========================================================================
    // 6. Account Standing Guards (RESTRICTED, SUSPENDED, BANNED)
    // =========================================================================

    @Test
    fun `13 restricted resident routes to ResidentHome with isRestricted true`() {
        val resident = buildUser(
            role = UserRole.RESIDENT,
            accountStatus = AccountStatus.RESTRICTED
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedRestricted(
                user = resident,
                isEmailVerified = true,
                accountStatus = AccountStatus.RESTRICTED,
                statusReason = "Booking frequency restricted",
                suspendedUntilEpochMs = null
            )
        )
        assertTrue(decision is NavigationDestinationDecision.ResidentHome)
        assertTrue((decision as NavigationDestinationDecision.ResidentHome).isRestricted)
        assertEquals(Destinations.RESIDENT_HOME, decision.route)
    }

    @Test
    fun `14 restricted collector routes to CollectorDashboard with isRestricted true`() {
        val collector = buildUser(
            role = UserRole.COLLECTOR,
            accountStatus = AccountStatus.RESTRICTED,
            collectorVerificationStatus = CollectorVerificationStatus.APPROVED
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedRestricted(
                user = collector,
                isEmailVerified = true,
                accountStatus = AccountStatus.RESTRICTED,
                statusReason = "Vehicle capacity restricted",
                suspendedUntilEpochMs = null
            )
        )
        assertTrue(decision is NavigationDestinationDecision.CollectorDashboard)
        assertTrue((decision as NavigationDestinationDecision.CollectorDashboard).isRestricted)
        assertEquals(Destinations.COLLECTOR_DASHBOARD, decision.route)
    }

    @Test
    fun `15 suspended account resolves to AccountStatusLock with status and expiration`() {
        val user = buildUser(
            accountStatus = AccountStatus.SUSPENDED,
            statusReason = "Temporary suspension pending municipal review",
            suspendedUntilEpochMs = 1750000000000L
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedRestricted(
                user = user,
                isEmailVerified = true,
                accountStatus = AccountStatus.SUSPENDED,
                statusReason = "Temporary suspension pending municipal review",
                suspendedUntilEpochMs = 1750000000000L
            )
        )
        assertTrue(decision is NavigationDestinationDecision.AccountStatusLock)
        val lock = decision as NavigationDestinationDecision.AccountStatusLock
        assertEquals(AccountStatus.SUSPENDED, lock.status)
        assertEquals("Temporary suspension pending municipal review", lock.reason)
        assertEquals(1750000000000L, lock.suspendedUntilEpochMs)
        assertEquals(Destinations.ACCOUNT_STATUS_LOCK, lock.route)
    }

    @Test
    fun `16 banned account resolves to AccountStatusLock with BANNED status`() {
        val user = buildUser(
            accountStatus = AccountStatus.BANNED,
            statusReason = "Permanent deactivation for terms violation"
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedRestricted(
                user = user,
                isEmailVerified = true,
                accountStatus = AccountStatus.BANNED,
                statusReason = "Permanent deactivation for terms violation",
                suspendedUntilEpochMs = null
            )
        )
        assertTrue(decision is NavigationDestinationDecision.AccountStatusLock)
        val lock = decision as NavigationDestinationDecision.AccountStatusLock
        assertEquals(AccountStatus.BANNED, lock.status)
        assertEquals("Permanent deactivation for terms violation", lock.reason)
        assertEquals(Destinations.ACCOUNT_STATUS_LOCK, lock.route)
    }

    // =========================================================================
    // 7. Critical Administrator Security Invariant
    // =========================================================================

    @Test
    fun `17 admin with verified server token admin claim routes to AdminConsole`() {
        val admin = buildUser(role = UserRole.ADMIN)
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(
                user = admin,
                isEmailVerified = true,
                hasAdminClaim = true
            )
        )
        assertTrue(decision is NavigationDestinationDecision.AdminConsole)
        assertEquals(Destinations.ADMIN_SHELL, decision.route)
    }

    @Test
    fun `18 admin profile without server token admin claim is blocked and routed to SafeRecovery`() {
        val fakeAdmin = buildUser(role = UserRole.ADMIN)
        // hasAdminClaim is FALSE: user manipulated firestore document or claim missing
        val decision = SessionNavigationResolver.resolve(
            SessionState.Authenticated(
                user = fakeAdmin,
                isEmailVerified = true,
                hasAdminClaim = false
            )
        )
        assertTrue("Admin without server claim must NOT reach AdminConsole", decision !is NavigationDestinationDecision.AdminConsole)
        assertTrue(decision is NavigationDestinationDecision.SafeRecovery)
        assertEquals(Destinations.LOGIN, decision.route)
    }

    // =========================================================================
    // 8. Offline Cached Session Safety
    // =========================================================================

    @Test
    fun `19 offline cached resident resolves to ResidentHome with isOffline true`() {
        val resident = buildUser(role = UserRole.RESIDENT, onboardingStatus = OnboardingStatus.COMPLETED)
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedCachedOffline(
                cachedUser = resident,
                isEmailVerified = true,
                networkError = AuthError.NetworkFailure("Device offline")
            )
        )
        assertTrue(decision is NavigationDestinationDecision.ResidentHome)
        val home = decision as NavigationDestinationDecision.ResidentHome
        assertTrue(home.isOffline)
        assertFalse(home.isRestricted)
        assertEquals(Destinations.RESIDENT_HOME, home.route)
    }

    @Test
    fun `20 offline cached admin NEVER reaches AdminConsole`() {
        val cachedAdmin = buildUser(role = UserRole.ADMIN)
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedCachedOffline(
                cachedUser = cachedAdmin,
                isEmailVerified = true,
                networkError = AuthError.NetworkFailure("Offline")
            )
        )
        assertTrue("Offline cached admin must NEVER reach AdminConsole", decision !is NavigationDestinationDecision.AdminConsole)
        assertTrue(decision is NavigationDestinationDecision.SafeRecovery)
    }

    @Test
    fun `21 offline cached suspended user resolves to AccountStatusLock`() {
        val suspendedUser = buildUser(
            accountStatus = AccountStatus.SUSPENDED,
            statusReason = "Suspended prior to losing connection"
        )
        val decision = SessionNavigationResolver.resolve(
            SessionState.AuthenticatedCachedOffline(
                cachedUser = suspendedUser,
                isEmailVerified = true,
                networkError = AuthError.NetworkFailure("Offline")
            )
        )
        assertTrue(decision is NavigationDestinationDecision.AccountStatusLock)
        val lock = decision as NavigationDestinationDecision.AccountStatusLock
        assertEquals(AccountStatus.SUSPENDED, lock.status)
        assertEquals(Destinations.ACCOUNT_STATUS_LOCK, lock.route)
    }
}
