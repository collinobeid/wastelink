package com.waste.link.ui.auth

import com.waste.link.data.models.AppUser
import com.waste.link.data.repository.AuthRepository
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.AuthError
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.EmailVerificationOutcome
import com.waste.link.domain.model.LoginOutcome
import com.waste.link.domain.model.LogoutOutcome
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.PasswordResetOutcome
import com.waste.link.domain.model.ProfileIncompleteReason
import com.waste.link.domain.model.ProfileInitializationOutcome
import com.waste.link.domain.model.ProfileRecoveryInput
import com.waste.link.domain.model.ProfileUpdateOutcome
import com.waste.link.domain.model.RegistrationDraft
import com.waste.link.domain.model.RegistrationOutcome
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.Destinations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildUser(
        id: String = "uid-resident-1",
        fullName: String = "Amina Nyerere",
        email: String = "amina@wastelink.co.tz",
        phoneNumber: String = "+255712345678",
        role: UserRole = UserRole.RESIDENT,
        onboardingStatus: OnboardingStatus = OnboardingStatus.COMPLETED,
        collectorVerificationStatus: CollectorVerificationStatus = CollectorVerificationStatus.NOT_APPLICABLE,
        accountStatus: AccountStatus = AccountStatus.ACTIVE
    ): User = User(
        id = id,
        fullName = fullName,
        email = email,
        phoneNumber = phoneNumber,
        role = role,
        accountStatus = accountStatus,
        onboardingStatus = onboardingStatus,
        collectorVerificationStatus = collectorVerificationStatus,
        defaultZoneId = "Kinondoni",
        createdAtEpochMs = 1_700_000_000_000L,
        updatedAtEpochMs = 1_700_000_000_000L
    )

    private fun TestScope.createViewModel(): AuthViewModel {
        val vm = AuthViewModel(fakeRepo)
        runCurrent()
        return vm
    }

    // =========================================================================
    // 1. Valid Login & Routing Matrix
    // =========================================================================

    @Test
    fun `1a valid resident login with verified email and completed onboarding navigates to ResidentHome`() = runTest {
        val vm = createViewModel()
        val resident = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.COMPLETED
        )
        fakeRepo.nextLoginOutcome = LoginOutcome.Authenticated(
            user = resident,
            isEmailVerified = true,
            hasAdminClaim = false
        )

        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026!")
        runCurrent()

        assertEquals(1, fakeRepo.loginCallCount)
        assertEquals(
            AuthNavigationEvent.NavigateToDestination(
                route = Destinations.RESIDENT_HOME,
                clearBackStack = true
            ),
            vm.navigationEvents.first()
        )
    }

    @Test
    fun `1b valid login with unverified email navigates to VerifyEmailScreen`() = runTest {
        val vm = createViewModel()
        val unverifiedResident = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
        fakeRepo.nextLoginOutcome = LoginOutcome.Authenticated(
            user = unverifiedResident,
            isEmailVerified = false,
            hasAdminClaim = false
        )

        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026!")
        runCurrent()

        assertEquals("amina@wastelink.co.tz", vm.uiState.value.verificationEmailAddress)
        assertEquals(
            AuthNavigationEvent.NavigateToVerifyEmail(
                email = "amina@wastelink.co.tz",
                initialEmailSent = true
            ),
            vm.navigationEvents.first()
        )
    }

    @Test
    fun `1c valid login with verified email but incomplete onboarding navigates to OnboardingScreen`() = runTest {
        val vm = createViewModel()
        val residentPendingSetup = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
        fakeRepo.nextLoginOutcome = LoginOutcome.Authenticated(
            user = residentPendingSetup,
            isEmailVerified = true,
            hasAdminClaim = false
        )

        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026!")
        runCurrent()

        assertEquals(
            AuthNavigationEvent.NavigateToOnboarding(UserRole.RESIDENT),
            vm.navigationEvents.first()
        )
    }

    @Test
    fun `1d valid collector and admin logins route to CollectorDashboard and AdminShell`() = runTest {
        val vm = createViewModel()
        val collector = buildUser(
            id = "uid-collector-1",
            role = UserRole.COLLECTOR,
            onboardingStatus = OnboardingStatus.COMPLETED,
            collectorVerificationStatus = CollectorVerificationStatus.NOT_SUBMITTED
        )
        fakeRepo.nextLoginOutcome = LoginOutcome.Authenticated(
            user = collector,
            isEmailVerified = true,
            hasAdminClaim = false
        )

        vm.submitLogin("collector@wastelink.co.tz", "WasteLink2026!")
        runCurrent()
        assertEquals(
            AuthNavigationEvent.NavigateToDestination(
                route = Destinations.COLLECTOR_DASHBOARD,
                clearBackStack = true
            ),
            vm.navigationEvents.first()
        )

        val admin = buildUser(
            id = "uid-admin-1",
            role = UserRole.ADMIN,
            onboardingStatus = OnboardingStatus.COMPLETED
        )
        fakeRepo.nextLoginOutcome = LoginOutcome.Authenticated(
            user = admin,
            isEmailVerified = true,
            hasAdminClaim = true
        )

        vm.submitLogin("admin@wastelink.co.tz", "WasteLink2026!")
        runCurrent()
        assertEquals(
            AuthNavigationEvent.NavigateToDestination(
                route = Destinations.ADMIN_SHELL,
                clearBackStack = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 2. Invalid Login Outcomes
    // =========================================================================

    @Test
    fun `2 invalid login handles InvalidCredentials, NetworkFailure, UserDisabled, ProfileLoadFailed, and RateLimited`() = runTest {
        val vm = createViewModel()

        // 2a. InvalidCredentials
        fakeRepo.nextLoginOutcome = LoginOutcome.Failure(AuthError.InvalidCredentials())
        vm.submitLogin("amina@wastelink.co.tz", "WrongPass123")
        runCurrent()
        assertEquals(AuthBannerType.ERROR, vm.uiState.value.loginBanner?.type)
        assertEquals("Incorrect Credentials", vm.uiState.value.loginBanner?.title)

        // 2b. NetworkFailure
        fakeRepo.nextLoginOutcome = LoginOutcome.Failure(AuthError.NetworkFailure("Offline during sign-in."))
        vm.submitLogin("amina@wastelink.co.tz", "WrongPass123")
        runCurrent()
        assertEquals("Connection Unavailable", vm.uiState.value.loginBanner?.title)
        assertTrue(vm.uiState.value.loginBanner?.isRetryable == true)

        // 2c. UserDisabled
        fakeRepo.nextLoginOutcome = LoginOutcome.Failure(AuthError.UserDisabled())
        vm.submitLogin("amina@wastelink.co.tz", "WrongPass123")
        runCurrent()
        assertEquals(AuthBannerType.ACCOUNT_LOCKED, vm.uiState.value.loginBanner?.type)

        // 2d. ProfileLoadFailed
        fakeRepo.nextLoginOutcome = LoginOutcome.ProfileLoadFailed(
            uid = "uid-1",
            email = "amina@wastelink.co.tz",
            error = AuthError.NetworkFailure("Firestore read timeout.")
        )
        vm.submitLogin("amina@wastelink.co.tz", "WrongPass123")
        runCurrent()
        assertEquals(AuthBannerType.PROFILE_LOAD_FAILED, vm.uiState.value.loginBanner?.type)

        // 2e. RateLimited countdown
        fakeRepo.nextLoginOutcome = LoginOutcome.Failure(AuthError.RateLimited(retryAfterSeconds = 3))
        vm.submitLogin("amina@wastelink.co.tz", "WrongPass123")
        runCurrent()
        assertEquals(3, vm.uiState.value.loginRateLimitSeconds)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(0, vm.uiState.value.loginRateLimitSeconds)
    }

    // =========================================================================
    // 3. Resident Registration
    // =========================================================================

    @Test
    fun `3 resident registration validates inputs, calls registerResident, and navigates to VerifyEmail`() = runTest {
        val vm = createViewModel()
        val createdResident = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
        fakeRepo.nextRegisterResidentOutcome = RegistrationOutcome.Success(
            user = createdResident,
            verificationEmailSent = true
        )

        vm.onRoleSelected(UserRole.RESIDENT)
        vm.submitRegistration(
            rawFullName = "Amina Nyerere",
            rawEmail = "amina@wastelink.co.tz",
            rawPhone = "0712 345 678",
            rawPassword = "WasteLink2026!",
            rawConfirmPassword = "WasteLink2026!",
            role = UserRole.RESIDENT
        )
        runCurrent()

        assertEquals(1, fakeRepo.registerResidentCallCount)
        assertEquals(0, fakeRepo.registerCollectorCallCount)
        assertEquals("+255712345678", vm.uiState.value.regPhone)
        assertEquals(
            AuthNavigationEvent.NavigateToVerifyEmail(
                email = "amina@wastelink.co.tz",
                initialEmailSent = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 4. Collector Registration
    // =========================================================================

    @Test
    fun `4 collector registration validates inputs, calls registerCollector, and navigates to VerifyEmail`() = runTest {
        val vm = createViewModel()
        val createdCollector = buildUser(
            id = "uid-collector-99",
            fullName = "Bakari Mwakasege",
            email = "bakari@wastelink.co.tz",
            phoneNumber = "+255754112233",
            role = UserRole.COLLECTOR,
            onboardingStatus = OnboardingStatus.NOT_STARTED,
            collectorVerificationStatus = CollectorVerificationStatus.NOT_SUBMITTED
        )
        fakeRepo.nextRegisterCollectorOutcome = RegistrationOutcome.Success(
            user = createdCollector,
            verificationEmailSent = true
        )

        vm.onRoleSelected(UserRole.COLLECTOR)
        vm.submitRegistration(
            rawFullName = "Bakari Mwakasege",
            rawEmail = "bakari@wastelink.co.tz",
            rawPhone = "+255 754 112 233",
            rawPassword = "Collector99Pass",
            rawConfirmPassword = "Collector99Pass",
            role = UserRole.COLLECTOR
        )
        runCurrent()

        assertEquals(0, fakeRepo.registerResidentCallCount)
        assertEquals(1, fakeRepo.registerCollectorCallCount)
        assertEquals(createdCollector, vm.uiState.value.currentUser)
        assertEquals(
            AuthNavigationEvent.NavigateToVerifyEmail(
                email = "bakari@wastelink.co.tz",
                initialEmailSent = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 5. Field Validation
    // =========================================================================

    @Test
    fun `5 field validation blocks submission when role, name, email, phone, password, or confirmation are invalid`() = runTest {
        val vm = createViewModel()

        // Submit empty registration form
        vm.submitRegistration(
            rawFullName = "",
            rawEmail = "",
            rawPhone = "",
            rawPassword = "",
            rawConfirmPassword = "",
            role = null
        )
        runCurrent()

        val state = vm.uiState.value
        assertNotNull(state.regRoleError)
        assertNotNull(state.regFullNameError)
        assertNotNull(state.regEmailError)
        assertNotNull(state.regPhoneError)
        assertNotNull(state.regPasswordError)
        assertEquals(0, fakeRepo.registerResidentCallCount)
        assertEquals(0, fakeRepo.registerCollectorCallCount)

        // Password mismatch check
        vm.submitRegistration(
            rawFullName = "Amina Nyerere",
            rawEmail = "amina@wastelink.co.tz",
            rawPhone = "+255712345678",
            rawPassword = "WasteLink2026",
            rawConfirmPassword = "Different2026",
            role = UserRole.RESIDENT
        )
        runCurrent()
        assertNotNull(vm.uiState.value.regConfirmPasswordError)
        assertEquals(0, fakeRepo.registerResidentCallCount)

        // Login field validation
        vm.submitLogin("", "")
        runCurrent()
        assertNotNull(vm.uiState.value.loginEmailError)
        assertNotNull(vm.uiState.value.loginPasswordError)
        assertEquals(0, fakeRepo.loginCallCount)
    }

    // =========================================================================
    // 6. Duplicate Email Handling
    // =========================================================================

    @Test
    fun `6 duplicate email during registration displays actionable banner and redirects to Login with email pre-filled`() = runTest {
        val vm = createViewModel()
        fakeRepo.nextRegisterResidentOutcome = RegistrationOutcome.Failure(
            AuthError.EmailAlreadyInUse(email = "amina@wastelink.co.tz")
        )

        vm.submitRegistration(
            rawFullName = "Amina Nyerere",
            rawEmail = "amina@wastelink.co.tz",
            rawPhone = "+255712345678",
            rawPassword = "WasteLink2026",
            rawConfirmPassword = "WasteLink2026",
            role = UserRole.RESIDENT
        )
        runCurrent()

        assertEquals("amina@wastelink.co.tz", vm.uiState.value.duplicateEmailDetected)
        assertEquals(AuthBannerType.DUPLICATE_EMAIL, vm.uiState.value.regBanner?.type)

        vm.navigateToLoginFromDuplicateEmail()
        runCurrent()

        assertEquals("amina@wastelink.co.tz", vm.uiState.value.loginEmail)
        assertEquals(
            AuthNavigationEvent.NavigateToLogin(
                prefillEmail = "amina@wastelink.co.tz",
                clearBackStack = false
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 7. Partial Registration Recovery (Same-Session)
    // =========================================================================

    @Test
    fun `7 partial registration outcome preserves pending session and recovers via retrySameSessionProfileInitialization`() = runTest {
        val vm = createViewModel()
        val draft = RegistrationDraft(
            fullName = "Amina Nyerere",
            email = "amina@wastelink.co.tz",
            phoneNumber = "+255712345678",
            role = UserRole.RESIDENT
        )
        fakeRepo.nextRegisterResidentOutcome = RegistrationOutcome.PartialSuccessProfilePending(
            uid = "uid-partial-1",
            email = "amina@wastelink.co.tz",
            draft = draft,
            profileError = AuthError.NetworkFailure("Temporary Firestore timeout.")
        )

        vm.submitRegistration(
            rawFullName = "Amina Nyerere",
            rawEmail = "amina@wastelink.co.tz",
            rawPhone = "0712345678",
            rawPassword = "WasteLink2026",
            rawConfirmPassword = "WasteLink2026",
            role = UserRole.RESIDENT
        )
        runCurrent()

        assertTrue(vm.uiState.value.isPartialRegistrationPending)
        assertEquals("uid-partial-1", vm.uiState.value.pendingRegistrationUid)
        assertEquals(draft, vm.uiState.value.pendingRegistrationDraft)

        val initializedUser = buildUser(
            id = "uid-partial-1",
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
        fakeRepo.nextRetryProfileInitOutcome = ProfileInitializationOutcome.Success(
            user = initializedUser,
            verificationEmailSent = true
        )

        vm.retrySameSessionProfileInitialization()
        runCurrent()

        assertEquals(1, fakeRepo.retryProfileInitCallCount)
        assertFalse(vm.uiState.value.isPartialRegistrationPending)
        assertEquals(initializedUser, vm.uiState.value.currentUser)
        assertEquals(
            AuthNavigationEvent.NavigateToVerifyEmail(
                email = "amina@wastelink.co.tz",
                initialEmailSent = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 8. Password Reset Dialog & Enumeration-Safe Flow
    // =========================================================================

    @Test
    fun `8 password reset validates email, handles network failure retry, and confirms dispatch`() = runTest {
        val vm = createViewModel()

        vm.onLoginEmailChanged("amina@wastelink.co.tz")
        vm.openForgotPasswordDialog()
        assertTrue(vm.uiState.value.passwordReset.isDialogVisible)
        assertEquals("amina@wastelink.co.tz", vm.uiState.value.passwordReset.email)

        // Invalid email check
        vm.onPasswordResetEmailChanged("not-an-email")
        vm.submitPasswordReset()
        runCurrent()
        assertNotNull(vm.uiState.value.passwordReset.emailError)
        assertEquals(0, fakeRepo.sendPasswordResetCallCount)

        // Network failure -> retry
        vm.onPasswordResetEmailChanged("amina@wastelink.co.tz")
        fakeRepo.nextPasswordResetOutcome = PasswordResetOutcome.Failure(
            AuthError.NetworkFailure("Offline during password reset.")
        )
        vm.submitPasswordReset()
        runCurrent()
        assertNull(vm.uiState.value.passwordReset.dispatchedToEmail)
        assertEquals("Offline during password reset.", vm.uiState.value.passwordReset.errorBanner)

        // Retry succeeds
        fakeRepo.nextPasswordResetOutcome = PasswordResetOutcome.ResetEmailDispatched(
            normalizedEmail = "amina@wastelink.co.tz"
        )
        vm.submitPasswordReset()
        runCurrent()
        assertEquals("amina@wastelink.co.tz", vm.uiState.value.passwordReset.dispatchedToEmail)
        assertEquals(2, fakeRepo.sendPasswordResetCallCount)
    }

    // =========================================================================
    // 9. Verification Email Resend & Cooldown
    // =========================================================================

    @Test
    fun `9 resendVerificationEmail dispatches link and enforces 60-second cooldown`() = runTest {
        val vm = createViewModel()
        fakeRepo.nextResendVerificationOutcome = EmailVerificationOutcome.Sent(
            email = "amina@wastelink.co.tz"
        )

        vm.resendVerificationEmail()
        runCurrent()

        assertEquals(1, fakeRepo.resendVerificationCallCount)
        assertEquals(60, vm.uiState.value.resendCooldownSeconds)
        assertEquals(AuthBannerType.SUCCESS, vm.uiState.value.verificationBanner?.type)

        // Attempt resend during active cooldown -> blocked
        vm.resendVerificationEmail()
        runCurrent()
        assertEquals(1, fakeRepo.resendVerificationCallCount)
    }

    // =========================================================================
    // 10. Verification Refresh & Continue
    // =========================================================================

    @Test
    fun `10 refreshEmailVerificationStatus updates verification state and continueFromEmailVerification routes to Onboarding`() = runTest {
        val vm = createViewModel()
        val unverified = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
        fakeRepo.resolvedUser = unverified
        fakeRepo.nextLoginOutcome = LoginOutcome.Authenticated(
            user = unverified,
            isEmailVerified = false,
            hasAdminClaim = false
        )
        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026")
        runCurrent()
        assertEquals(
            AuthNavigationEvent.NavigateToVerifyEmail(
                email = "amina@wastelink.co.tz",
                initialEmailSent = true
            ),
            vm.navigationEvents.first()
        )

        // Still unverified
        fakeRepo.nextRefreshVerificationOutcome = EmailVerificationOutcome.StatusRefreshed(
            isEmailVerified = false,
            tokenRefreshed = false
        )
        vm.refreshEmailVerificationStatus()
        runCurrent()
        assertFalse(vm.uiState.value.isEmailVerified)
        assertEquals(AuthBannerType.WARNING, vm.uiState.value.verificationBanner?.type)

        // Now verified
        fakeRepo.emailVerified = true
        fakeRepo.nextRefreshVerificationOutcome = EmailVerificationOutcome.StatusRefreshed(
            isEmailVerified = true,
            tokenRefreshed = true
        )
        vm.refreshEmailVerificationStatus()
        runCurrent()
        assertTrue(vm.uiState.value.isEmailVerified)
        assertEquals(AuthBannerType.SUCCESS, vm.uiState.value.verificationBanner?.type)

        // Continue advances to Onboarding
        vm.continueFromEmailVerification()
        runCurrent()
        assertEquals(
            AuthNavigationEvent.NavigateToOnboarding(UserRole.RESIDENT),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 11. Missing-Profile Recovery (Ghost Account)
    // =========================================================================

    @Test
    fun `11 missing profile login routes to CompleteProfileScreen and submitMissingProfileRecovery creates canonical profile`() = runTest {
        val vm = createViewModel()
        fakeRepo.nextLoginOutcome = LoginOutcome.ProfileIncomplete(
            uid = "uid-ghost-7",
            email = "ghost@wastelink.co.tz",
            isEmailVerified = false,
            reason = ProfileIncompleteReason.DOCUMENT_MISSING
        )

        vm.submitLogin("ghost@wastelink.co.tz", "WasteLink2026")
        runCurrent()

        assertEquals(
            AuthNavigationEvent.NavigateToCompleteProfile(
                uid = "uid-ghost-7",
                email = "ghost@wastelink.co.tz",
                reason = ProfileIncompleteReason.DOCUMENT_MISSING
            ),
            vm.navigationEvents.first()
        )
        assertEquals("uid-ghost-7", vm.uiState.value.recoveryUid)
        assertEquals("ghost@wastelink.co.tz", vm.uiState.value.recoveryEmail)

        // Validation check when submitting blank recovery form
        vm.submitMissingProfileRecovery(rawFullName = "", rawPhone = "", role = null)
        runCurrent()
        assertNotNull(vm.uiState.value.recoveryRoleError)
        assertNotNull(vm.uiState.value.recoveryFullNameError)
        assertNotNull(vm.uiState.value.recoveryPhoneError)
        assertEquals(0, fakeRepo.completeMissingProfileCallCount)

        // Network failure -> retry
        fakeRepo.nextCompleteMissingProfileOutcome = ProfileInitializationOutcome.Failure(
            error = AuthError.NetworkFailure("Temporary Firestore outage.")
        )
        vm.submitMissingProfileRecovery(
            rawFullName = "Juma Kaseja",
            rawPhone = "0754987654",
            role = UserRole.COLLECTOR
        )
        runCurrent()
        assertEquals(1, fakeRepo.completeMissingProfileCallCount)
        assertEquals("Temporary Firestore outage.", vm.uiState.value.recoveryBanner?.message)

        // Retry succeeds -> unverified collector routes to VerifyEmailScreen
        val recoveredUser = buildUser(
            id = "uid-ghost-7",
            fullName = "Juma Kaseja",
            email = "ghost@wastelink.co.tz",
            phoneNumber = "+255754987654",
            role = UserRole.COLLECTOR,
            onboardingStatus = OnboardingStatus.NOT_STARTED,
            collectorVerificationStatus = CollectorVerificationStatus.NOT_SUBMITTED
        )
        fakeRepo.emailVerified = false
        fakeRepo.nextCompleteMissingProfileOutcome = ProfileInitializationOutcome.Success(
            user = recoveredUser,
            verificationEmailSent = true
        )
        vm.submitMissingProfileRecovery(
            rawFullName = "Juma Kaseja",
            rawPhone = "0754987654",
            role = UserRole.COLLECTOR
        )
        runCurrent()

        assertEquals(2, fakeRepo.completeMissingProfileCallCount)
        assertEquals(recoveredUser, vm.uiState.value.currentUser)
        assertEquals(
            AuthNavigationEvent.NavigateToVerifyEmail(
                email = "ghost@wastelink.co.tz",
                initialEmailSent = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 12. Account Restrictions (SUSPENDED, BANNED, RESTRICTED)
    // =========================================================================

    @Test
    fun `12 suspended and banned accounts are blocked with lock banner while restricted accounts route to dashboard`() = runTest {
        val vm = createViewModel()
        val suspendedUser = buildUser(accountStatus = AccountStatus.SUSPENDED)
        fakeRepo.nextLoginOutcome = LoginOutcome.AccountLocked(
            user = suspendedUser,
            isEmailVerified = true,
            accountStatus = AccountStatus.SUSPENDED,
            statusReason = "Account suspended pending review.",
            suspendedUntilEpochMs = null
        )

        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026")
        runCurrent()

        assertEquals(AccountStatus.SUSPENDED, vm.uiState.value.lockedAccountStatus)
        assertEquals("Account suspended pending review.", vm.uiState.value.lockedStatusReason)
        assertEquals(AuthBannerType.ACCOUNT_LOCKED, vm.uiState.value.loginBanner?.type)

        // RESTRICTED account is allowed to sign in and navigate to dashboard
        val restrictedUser = buildUser(accountStatus = AccountStatus.RESTRICTED)
        fakeRepo.nextLoginOutcome = LoginOutcome.AccountLocked(
            user = restrictedUser,
            isEmailVerified = true,
            accountStatus = AccountStatus.RESTRICTED,
            statusReason = "Restricted from creating new requests.",
            suspendedUntilEpochMs = null
        )
        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026")
        runCurrent()

        assertEquals(
            AuthNavigationEvent.NavigateToDestination(
                route = Destinations.RESIDENT_HOME,
                clearBackStack = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 13. Repeated Submission Prevention
    // =========================================================================

    @Test
    fun `13 repeated submission is ignored while an authentication request is already in flight`() = runTest {
        val vm = createViewModel()
        val deferredLogin = CompletableDeferred<LoginOutcome>()
        fakeRepo.deferredLoginOutcome = deferredLogin

        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026")
        runCurrent()
        assertTrue(vm.uiState.value.isSubmitting)
        assertEquals(1, fakeRepo.loginCallCount)

        // Trigger duplicate clicks while first login is still running
        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026")
        vm.submitLogin("amina@wastelink.co.tz", "WasteLink2026")
        runCurrent()
        assertEquals(1, fakeRepo.loginCallCount)

        deferredLogin.complete(
            LoginOutcome.Authenticated(
                user = buildUser(),
                isEmailVerified = true,
                hasAdminClaim = false
            )
        )
        runCurrent()
        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals(
            AuthNavigationEvent.NavigateToDestination(
                route = Destinations.RESIDENT_HOME,
                clearBackStack = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // 14. One-Time Navigation Events (Session Restoration, Onboarding & Logout)
    // =========================================================================

    @Test
    fun `14 restoreSessionOnLaunch, completeStage4Onboarding, and logout emit one-time navigation events`() = runTest {
        val residentPendingSetup = buildUser(
            role = UserRole.RESIDENT,
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
        fakeRepo.resolvedUser = residentPendingSetup
        fakeRepo.nextSessionState = SessionState.Authenticated(
            user = residentPendingSetup,
            isEmailVerified = true,
            hasAdminClaim = false
        )

        val vm = createViewModel()
        vm.restoreSessionOnLaunch()
        runCurrent()
        assertEquals(
            AuthNavigationEvent.NavigateToOnboarding(UserRole.RESIDENT),
            vm.navigationEvents.first()
        )

        val completedResident = residentPendingSetup.copy(onboardingStatus = OnboardingStatus.COMPLETED)
        fakeRepo.completeResidentOnboardingOutcome = ProfileUpdateOutcome.Success(completedResident)

        vm.completeStage4Onboarding("Kinondoni")
        runCurrent()
        assertEquals(
            AuthNavigationEvent.NavigateToDestination(
                route = Destinations.RESIDENT_HOME,
                clearBackStack = true
            ),
            vm.navigationEvents.first()
        )

        vm.logout()
        runCurrent()
        assertEquals(1, fakeRepo.logoutCallCount)
        assertNull(vm.uiState.value.currentUser)
        assertEquals(
            AuthNavigationEvent.NavigateToLogin(
                prefillEmail = null,
                clearBackStack = true
            ),
            vm.navigationEvents.first()
        )
    }

    // =========================================================================
    // Fake Canonical AuthRepository
    // =========================================================================

    private class FakeAuthRepository : AuthRepository {
        private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        var resolvedUser: User? = null
        var emailVerified: Boolean = false
        var pendingDraft: RegistrationDraft? = null

        var nextSessionState: SessionState = SessionState.Unauthenticated
        var nextLoginOutcome: LoginOutcome = LoginOutcome.Failure(AuthError.InvalidCredentials())
        var deferredLoginOutcome: CompletableDeferred<LoginOutcome>? = null
        var nextRegisterResidentOutcome: RegistrationOutcome =
            RegistrationOutcome.Failure(AuthError.NetworkFailure())
        var nextRegisterCollectorOutcome: RegistrationOutcome =
            RegistrationOutcome.Failure(AuthError.NetworkFailure())
        var nextRetryProfileInitOutcome: ProfileInitializationOutcome =
            ProfileInitializationOutcome.Failure(AuthError.NetworkFailure())
        var nextCompleteMissingProfileOutcome: ProfileInitializationOutcome =
            ProfileInitializationOutcome.Failure(AuthError.NetworkFailure())
        var nextResendVerificationOutcome: EmailVerificationOutcome =
            EmailVerificationOutcome.Sent("amina@wastelink.co.tz")
        var nextRefreshVerificationOutcome: EmailVerificationOutcome =
            EmailVerificationOutcome.StatusRefreshed(isEmailVerified = false, tokenRefreshed = false)
        var nextPasswordResetOutcome: PasswordResetOutcome =
            PasswordResetOutcome.ResetEmailDispatched("amina@wastelink.co.tz")
        var completeResidentOnboardingOutcome: ProfileUpdateOutcome =
            ProfileUpdateOutcome.Failure(AuthError.NetworkFailure())

        var loginCallCount = 0
        var registerResidentCallCount = 0
        var registerCollectorCallCount = 0
        var retryProfileInitCallCount = 0
        var completeMissingProfileCallCount = 0
        var resendVerificationCallCount = 0
        var refreshVerificationCallCount = 0
        var sendPasswordResetCallCount = 0
        var logoutCallCount = 0

        override fun getCurrentUser(): User? = resolvedUser
        override fun getCachedOrVerifiedUser(): User? = resolvedUser
        override fun isServerVerifiedSession(): Boolean = resolvedUser != null
        override fun getPendingRegistrationDraft(): RegistrationDraft? = pendingDraft
        override fun isEmailVerified(): Boolean = emailVerified

        override suspend fun registerResident(
            fullName: String,
            email: String,
            phoneNumber: String,
            password: String,
            confirmPassword: String
        ): RegistrationOutcome {
            registerResidentCallCount++
            return nextRegisterResidentOutcome
        }

        override suspend fun registerCollector(
            fullName: String,
            email: String,
            phoneNumber: String,
            password: String,
            confirmPassword: String
        ): RegistrationOutcome {
            registerCollectorCallCount++
            return nextRegisterCollectorOutcome
        }

        override suspend fun registerUser(
            fullName: String,
            email: String,
            phoneNumber: String,
            password: String,
            confirmPassword: String,
            role: UserRole
        ): RegistrationOutcome = when (role) {
            UserRole.RESIDENT -> registerResident(fullName, email, phoneNumber, password, confirmPassword)
            UserRole.COLLECTOR -> registerCollector(fullName, email, phoneNumber, password, confirmPassword)
            UserRole.ADMIN -> RegistrationOutcome.Failure(AuthError.UnauthorizedRoleSelection())
        }

        override suspend fun retryProfileInitialization(
            overrideDraft: RegistrationDraft?
        ): ProfileInitializationOutcome {
            retryProfileInitCallCount++
            return nextRetryProfileInitOutcome
        }

        override suspend fun completeMissingProfile(
            input: ProfileRecoveryInput
        ): ProfileInitializationOutcome {
            completeMissingProfileCallCount++
            return nextCompleteMissingProfileOutcome
        }

        override suspend fun login(email: String, password: String): LoginOutcome {
            loginCallCount++
            deferredLoginOutcome?.let { return it.await() }
            return nextLoginOutcome
        }

        override suspend fun sendEmailVerification(): EmailVerificationOutcome = nextResendVerificationOutcome

        override suspend fun resendEmailVerification(): EmailVerificationOutcome {
            resendVerificationCallCount++
            return nextResendVerificationOutcome
        }

        override suspend fun refreshEmailVerificationStatus(): EmailVerificationOutcome {
            refreshVerificationCallCount++
            return nextRefreshVerificationOutcome
        }

        override suspend fun sendPasswordReset(email: String): PasswordResetOutcome {
            sendPasswordResetCallCount++
            return nextPasswordResetOutcome
        }

        override suspend fun restoreSession(): SessionState {
            _sessionState.value = nextSessionState
            return nextSessionState
        }

        override suspend fun logout(): LogoutOutcome {
            logoutCallCount++
            resolvedUser = null
            _sessionState.value = SessionState.Unauthenticated
            return LogoutOutcome.Success
        }

        override suspend fun completeResidentOnboarding(defaultZoneId: String?): ProfileUpdateOutcome =
            completeResidentOnboardingOutcome

        override suspend fun updateProfile(
            fullName: String,
            phoneNumber: String,
            defaultZoneId: String?,
            preferredLanguage: String?
        ): ProfileUpdateOutcome = completeResidentOnboardingOutcome

        @Deprecated("Legacy")
        override suspend fun signUp(email: String, password: String, name: String, phone: String): Result<AppUser> =
            Result.failure(UnsupportedOperationException())

        @Deprecated("Legacy")
        override suspend fun setRole(uid: String, role: String): Result<AppUser> =
            Result.failure(UnsupportedOperationException())

        @Deprecated("Legacy")
        override suspend fun signIn(email: String, password: String): Result<AppUser> =
            Result.failure(UnsupportedOperationException())

        @Deprecated("Legacy")
        override suspend fun getUserRole(uid: String): Result<AppUser> =
            Result.failure(UnsupportedOperationException())

        @Deprecated("Legacy")
        override fun signOut() {}

        override fun isUserSignedIn(): Boolean = resolvedUser != null
        override fun getCurrentUserId(): String? = resolvedUser?.id

        @Deprecated("Legacy")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun deleteAccount(): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }
}
