package com.waste.link.data.repository

import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.remote.RemoteAuthErrorCode
import com.waste.link.data.remote.RemoteAuthException
import com.waste.link.data.remote.RemoteAuthUser
import com.waste.link.data.remote.RemoteDocumentResult
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
import com.waste.link.domain.model.RegistrationOutcome
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit test suite for [AuthRepositoryImpl] covering all 24 mandatory
 * Module 1 Stage 3 scenarios.
 */
class AuthRepositoryImplTest {

    private lateinit var fakeRemote: FakeAuthRemoteDataSource
    private lateinit var repository: AuthRepositoryImpl
    private var currentEpochMs: Long = 1731000000000L

    @Before
    fun setUp() {
        currentEpochMs = 1731000000000L
        fakeRemote = FakeAuthRemoteDataSource()
        repository = AuthRepositoryImpl(
            remoteDataSource = fakeRemote,
            clock = { currentEpochMs }
        )
    }

    // =========================================================================
    // Scenario 1: Resident registration creates Auth user and canonical /users/{uid}
    // =========================================================================
    @Test
    fun `1 - Resident registration creates Auth user and canonical Firestore profile`() = runBlocking {
        val outcome = repository.registerResident(
            fullName = "Amina Nyerere",
            email = "Amina.Nyerere@WasteLink.co.tz",
            phoneNumber = "0712 345 678",
            password = "SecurePass123",
            confirmPassword = "SecurePass123"
        )

        assertTrue("Expected RegistrationOutcome.Success but got $outcome", outcome is RegistrationOutcome.Success)
        val success = outcome as RegistrationOutcome.Success

        assertEquals(1, fakeRemote.createAuthUserCallCount)
        assertEquals(1, fakeRemote.writeUserDocumentCallCount)
        assertTrue(success.verificationEmailSent)
        assertNull(success.emailSendError)

        val user = success.user
        assertEquals(fakeRemote.currentUser?.uid, user.id)
        assertEquals("Amina Nyerere", user.fullName)
        assertEquals("amina.nyerere@wastelink.co.tz", user.email)
        assertEquals("+255712345678", user.phoneNumber)
        assertEquals(UserRole.RESIDENT, user.role)
        assertEquals(AccountStatus.ACTIVE, user.accountStatus)
        assertEquals(OnboardingStatus.NOT_STARTED, user.onboardingStatus)
        assertEquals(CollectorVerificationStatus.NOT_APPLICABLE, user.collectorVerificationStatus)
        assertEquals(currentEpochMs, user.createdAtEpochMs)
        assertEquals(currentEpochMs, user.updatedAtEpochMs)

        // Verify raw Firestore document satisfies Stage 2.1 firestore.rules schema
        val storedDoc = fakeRemote.serverUserDocuments[user.id]
        assertNotNull(storedDoc)
        assertFirestoreDocumentSatisfiesSelfCreateRules(user.id, user.email, storedDoc!!)
        assertEquals("resident", storedDoc["role"])
        assertEquals("NOT_APPLICABLE", storedDoc["collectorVerificationStatus"])
        assertEquals(false, storedDoc["isVerified"])
    }

    // =========================================================================
    // Scenario 2: Collector registration creates Auth user and canonical /users/{uid}
    // =========================================================================
    @Test
    fun `2 - Collector registration creates Auth user and canonical profile with NOT_SUBMITTED`() = runBlocking {
        val outcome = repository.registerCollector(
            fullName = "Baraka Mwinyi",
            email = "baraka@fleet.co.tz",
            phoneNumber = "+255754112233",
            password = "CollectorPass99",
            confirmPassword = "CollectorPass99"
        )

        assertTrue(outcome is RegistrationOutcome.Success)
        val success = outcome as RegistrationOutcome.Success

        assertEquals(UserRole.COLLECTOR, success.user.role)
        assertEquals(CollectorVerificationStatus.NOT_SUBMITTED, success.user.collectorVerificationStatus)
        assertEquals(OnboardingStatus.NOT_STARTED, success.user.onboardingStatus)
        assertEquals(AccountStatus.ACTIVE, success.user.accountStatus)
        assertFalse(success.user.isCollectorOperational)

        val storedDoc = fakeRemote.serverUserDocuments[success.user.id]
        assertNotNull(storedDoc)
        assertFirestoreDocumentSatisfiesSelfCreateRules(success.user.id, success.user.email, storedDoc!!)
        assertEquals("collector", storedDoc["role"])
        assertEquals("NOT_SUBMITTED", storedDoc["collectorVerificationStatus"])
        assertEquals(false, storedDoc["isVerified"])
    }

    // =========================================================================
    // Scenario 3: Attempted self-registration as ADMIN is rejected before any Firebase write
    // =========================================================================
    @Test
    fun `3 - Attempted self-registration as ADMIN is rejected before any Firebase call`() = runBlocking {
        val outcome = repository.registerUser(
            fullName = "Escalation Attempter",
            email = "attacker@wastelink.co.tz",
            phoneNumber = "+255712345678",
            password = "StrongPass123",
            confirmPassword = "StrongPass123",
            role = UserRole.ADMIN
        )

        assertTrue(outcome is RegistrationOutcome.Failure)
        val failure = outcome as RegistrationOutcome.Failure
        assertTrue(failure.error is AuthError.UnauthorizedRoleSelection)

        assertEquals(0, fakeRemote.createAuthUserCallCount)
        assertEquals(0, fakeRemote.writeUserDocumentCallCount)
        assertTrue(fakeRemote.serverUserDocuments.isEmpty())
    }

    // =========================================================================
    // Scenario 4: Invalid registration inputs are rejected before any Firebase call
    // =========================================================================
    @Test
    fun `4 - Invalid registration inputs are rejected before any Firebase call`() = runBlocking {
        val outcome = repository.registerResident(
            fullName = "User", // Reserved placeholder name
            email = "not-an-email",
            phoneNumber = "123",
            password = "weak",
            confirmPassword = "mismatch"
        )

        assertTrue(outcome is RegistrationOutcome.Failure)
        val failure = outcome as RegistrationOutcome.Failure
        assertTrue(failure.error is AuthError.ValidationFailed)
        val validationFailed = failure.error as AuthError.ValidationFailed
        assertEquals(5, validationFailed.errors.size)

        assertEquals(0, fakeRemote.createAuthUserCallCount)
        assertEquals(0, fakeRemote.writeUserDocumentCallCount)
    }

    // =========================================================================
    // Scenario 5: Registration with existing email returns EmailAlreadyInUse
    // =========================================================================
    @Test
    fun `5 - Registration with existing email returns EmailAlreadyInUse without overwriting profile`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "existing_uid_1",
            email = "existing@wastelink.co.tz",
            password = "ExistingPass123"
        )
        fakeRemote.seedCanonicalResidentProfile("existing_uid_1", "existing@wastelink.co.tz")

        val outcome = repository.registerResident(
            fullName = "Someone Else",
            email = "EXISTING@wastelink.co.tz",
            phoneNumber = "+255788001122",
            password = "NewPassword123",
            confirmPassword = "NewPassword123"
        )

        assertTrue(outcome is RegistrationOutcome.Failure)
        val failure = outcome as RegistrationOutcome.Failure
        assertTrue(failure.error is AuthError.EmailAlreadyInUse)
        assertEquals("existing@wastelink.co.tz", (failure.error as AuthError.EmailAlreadyInUse).email)

        // Existing profile must remain untouched and current session must remain unauthenticated
        assertEquals(0, fakeRemote.writeUserDocumentCallCount)
        assertEquals("Existing Resident", fakeRemote.serverUserDocuments["existing_uid_1"]?.get("fullName"))
        assertNull(fakeRemote.currentUser)
    }

    // =========================================================================
    // Scenario 6: Same-session partial registration failure preserves auth session
    //             and allows profile initialization retry without calling createUserWithEmailAndPassword again
    // =========================================================================
    @Test
    fun `6 - Same-session partial registration failure preserves session and draft and retries without creating second Auth user`() = runBlocking {
        fakeRemote.nextWriteUserError = RemoteAuthException(
            code = RemoteAuthErrorCode.NETWORK_UNAVAILABLE,
            message = "Firestore unavailable during registration"
        )

        val regOutcome = repository.registerCollector(
            fullName = "Halima Kassim",
            email = "halima@wastelink.co.tz",
            phoneNumber = "0765432109",
            password = "HalimaPass123",
            confirmPassword = "HalimaPass123"
        )

        assertTrue(
            "Expected PartialSuccessProfilePending, got $regOutcome",
            regOutcome is RegistrationOutcome.PartialSuccessProfilePending
        )
        val partial = regOutcome as RegistrationOutcome.PartialSuccessProfilePending
        assertTrue(partial.profileError is AuthError.NetworkFailure)
        assertEquals("Halima Kassim", partial.draft.fullName)
        assertEquals("+255765432109", partial.draft.phoneNumber)
        assertEquals(UserRole.COLLECTOR, partial.draft.role)

        // Auth user was created once, session is preserved, and draft is cached
        assertEquals(1, fakeRemote.createAuthUserCallCount)
        assertNotNull(fakeRemote.currentUser)
        assertNotNull(repository.getPendingRegistrationDraft())
        assertTrue(repository.sessionState.value is SessionState.AuthenticatedProfileIncomplete)

        // Now retry profile initialization when network recovers
        val retryOutcome = repository.retryProfileInitialization()
        assertTrue(
            "Expected ProfileInitializationOutcome.Success, got $retryOutcome",
            retryOutcome is ProfileInitializationOutcome.Success
        )
        val initialized = retryOutcome as ProfileInitializationOutcome.Success

        // CRITICAL: createUserWithEmailAndPassword was NEVER called a second time
        assertEquals(1, fakeRemote.createAuthUserCallCount)
        assertEquals(partial.uid, initialized.user.id)
        assertEquals("Halima Kassim", initialized.user.fullName)
        assertEquals(UserRole.COLLECTOR, initialized.user.role)
        assertEquals(CollectorVerificationStatus.NOT_SUBMITTED, initialized.user.collectorVerificationStatus)
        assertNull(repository.getPendingRegistrationDraft())
        assertTrue(repository.sessionState.value is SessionState.Authenticated)
    }

    // =========================================================================
    // Scenario 7: Cross-session Ghost Account login detects missing /users/{uid}
    //             and returns recoverable ProfileIncomplete state
    // =========================================================================
    @Test
    fun `7 - Cross-session Ghost Account login detects missing Firestore profile and returns ProfileIncomplete without signing out`() = runBlocking {
        // Seed Firebase Auth account with NO corresponding /users/{uid} document
        fakeRemote.seedAuthAccount(
            uid = "ghost_uid_77",
            email = "ghost@wastelink.co.tz",
            password = "GhostPass123"
        )

        val loginOutcome = repository.login(
            email = "ghost@wastelink.co.tz",
            password = "GhostPass123"
        )

        assertTrue(
            "Expected LoginOutcome.ProfileIncomplete, got $loginOutcome",
            loginOutcome is LoginOutcome.ProfileIncomplete
        )
        val incomplete = loginOutcome as LoginOutcome.ProfileIncomplete
        assertEquals("ghost_uid_77", incomplete.uid)
        assertEquals("ghost@wastelink.co.tz", incomplete.email)
        assertEquals(ProfileIncompleteReason.DOCUMENT_MISSING, incomplete.reason)

        // Must NOT sign the user out or fabricate a fake /users/{uid} document
        assertNotNull(fakeRemote.currentUser)
        assertFalse(fakeRemote.serverUserDocuments.containsKey("ghost_uid_77"))
        assertTrue(repository.sessionState.value is SessionState.AuthenticatedProfileIncomplete)
    }

    // =========================================================================
    // Scenario 8: Completing profile recovery creates a valid canonical /users/{uid}
    //             document that passes firestore.rules
    // =========================================================================
    @Test
    fun `8 - Completing profile recovery creates valid canonical Firestore profile satisfying firestore rules`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "ghost_uid_88",
            email = "recover@wastelink.co.tz",
            password = "RecoverPass123"
        )
        val loginOutcome = repository.login("recover@wastelink.co.tz", "RecoverPass123")
        assertTrue(loginOutcome is LoginOutcome.ProfileIncomplete)

        val recoveryOutcome = repository.completeMissingProfile(
            ProfileRecoveryInput(
                fullName = "Salim Ahmed",
                phoneNumber = "0713998877",
                role = UserRole.RESIDENT
            )
        )

        assertTrue(
            "Expected ProfileInitializationOutcome.Success, got $recoveryOutcome",
            recoveryOutcome is ProfileInitializationOutcome.Success
        )
        val success = recoveryOutcome as ProfileInitializationOutcome.Success
        assertEquals("ghost_uid_88", success.user.id)
        assertEquals("Salim Ahmed", success.user.fullName)
        assertEquals("recover@wastelink.co.tz", success.user.email)
        assertEquals("+255713998877", success.user.phoneNumber)
        assertEquals(UserRole.RESIDENT, success.user.role)
        assertEquals(CollectorVerificationStatus.NOT_APPLICABLE, success.user.collectorVerificationStatus)

        val storedDoc = fakeRemote.serverUserDocuments["ghost_uid_88"]
        assertNotNull(storedDoc)
        assertFirestoreDocumentSatisfiesSelfCreateRules("ghost_uid_88", "recover@wastelink.co.tz", storedDoc!!)
        assertTrue(repository.sessionState.value is SessionState.Authenticated)
    }

    // =========================================================================
    // Scenario 9: Repeating profile initialization is idempotent
    // =========================================================================
    @Test
    fun `9 - Repeating profile initialization is idempotent and does not overwrite or fail`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "idem_uid_99",
            email = "idem@wastelink.co.tz",
            password = "IdemPass123"
        )
        repository.login("idem@wastelink.co.tz", "IdemPass123")

        val input = ProfileRecoveryInput(
            fullName = "Zainab Omar",
            phoneNumber = "+255714556677",
            role = UserRole.COLLECTOR
        )

        val firstAttempt = repository.completeMissingProfile(input)
        assertTrue(firstAttempt is ProfileInitializationOutcome.Success)
        assertFalse((firstAttempt as ProfileInitializationOutcome.Success).wasAlreadyInitialized)
        assertEquals(1, fakeRemote.writeUserDocumentCallCount)

        // Advance clock and repeat completeMissingProfile + retryProfileInitialization
        currentEpochMs += 60_000L
        val secondAttempt = repository.completeMissingProfile(input)
        assertTrue(secondAttempt is ProfileInitializationOutcome.Success)
        val secondSuccess = secondAttempt as ProfileInitializationOutcome.Success
        assertTrue(secondSuccess.wasAlreadyInitialized)
        assertEquals(firstAttempt.user, secondSuccess.user)

        // writeUserDocument was NOT called a second time because the valid profile already existed
        assertEquals(1, fakeRemote.writeUserDocumentCallCount)
        assertEquals(0, fakeRemote.createAuthUserCallCount)
    }

    // =========================================================================
    // Scenario 10: Valid Resident login returns canonical User
    // =========================================================================
    @Test
    fun `10 - Valid Resident login returns canonical User`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "res_10",
            email = "resident10@wastelink.co.tz",
            password = "ResidentPass10",
            isEmailVerified = true
        )
        fakeRemote.seedCanonicalResidentProfile("res_10", "resident10@wastelink.co.tz")

        val outcome = repository.login("resident10@wastelink.co.tz", "ResidentPass10")
        assertTrue(outcome is LoginOutcome.Authenticated)
        val authenticated = outcome as LoginOutcome.Authenticated

        assertEquals("res_10", authenticated.user.id)
        assertEquals(UserRole.RESIDENT, authenticated.user.role)
        assertEquals(AccountStatus.ACTIVE, authenticated.user.accountStatus)
        assertEquals(CollectorVerificationStatus.NOT_APPLICABLE, authenticated.user.collectorVerificationStatus)
        assertTrue(authenticated.isEmailVerified)
        assertTrue(repository.sessionState.value is SessionState.Authenticated)
    }

    // =========================================================================
    // Scenario 11: Valid Collector login returns canonical User
    // =========================================================================
    @Test
    fun `11 - Valid Collector login returns canonical User`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "col_11",
            email = "collector11@wastelink.co.tz",
            password = "CollectorPass11",
            isEmailVerified = true
        )
        fakeRemote.seedCanonicalCollectorProfile(
            uid = "col_11",
            email = "collector11@wastelink.co.tz",
            verificationStatus = CollectorVerificationStatus.APPROVED
        )

        val outcome = repository.login("collector11@wastelink.co.tz", "CollectorPass11")
        assertTrue(outcome is LoginOutcome.Authenticated)
        val authenticated = outcome as LoginOutcome.Authenticated

        assertEquals("col_11", authenticated.user.id)
        assertEquals(UserRole.COLLECTOR, authenticated.user.role)
        assertEquals(CollectorVerificationStatus.APPROVED, authenticated.user.collectorVerificationStatus)
        assertTrue(authenticated.user.isCollectorOperational)
    }

    // =========================================================================
    // Scenario 12: Login with wrong password returns InvalidCredentials
    // =========================================================================
    @Test
    fun `12 - Login with wrong password returns InvalidCredentials`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "res_12",
            email = "user12@wastelink.co.tz",
            password = "CorrectPassword123"
        )
        fakeRemote.seedCanonicalResidentProfile("res_12", "user12@wastelink.co.tz")

        val outcome = repository.login("user12@wastelink.co.tz", "WrongPassword999")
        assertTrue(outcome is LoginOutcome.Failure)
        val failure = outcome as LoginOutcome.Failure
        assertTrue(failure.error is AuthError.InvalidCredentials)
        assertNull(repository.getCurrentUser())
    }

    // =========================================================================
    // Scenario 13: Login with unknown account returns appropriate error without fabricating user
    // =========================================================================
    @Test
    fun `13 - Login with unknown account returns AccountNotFound without fabricating a user`() = runBlocking {
        val outcome = repository.login("unknown@wastelink.co.tz", "SomePassword123")
        assertTrue(outcome is LoginOutcome.Failure)
        val failure = outcome as LoginOutcome.Failure
        assertTrue(
            failure.error is AuthError.AccountNotFound || failure.error is AuthError.InvalidCredentials
        )
        assertNull(repository.getCurrentUser())
        assertTrue(fakeRemote.serverUserDocuments.isEmpty())
    }

    // =========================================================================
    // Scenario 14: Login with disabled Firebase Auth user returns UserDisabled
    // =========================================================================
    @Test
    fun `14 - Login with disabled Firebase Auth user returns UserDisabled`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "disabled_uid_14",
            email = "disabled@wastelink.co.tz",
            password = "DisabledPass123",
            isDisabled = true
        )

        val outcome = repository.login("disabled@wastelink.co.tz", "DisabledPass123")
        assertTrue(outcome is LoginOutcome.Failure)
        val failure = outcome as LoginOutcome.Failure
        assertTrue(failure.error is AuthError.UserDisabled)
        assertNull(repository.getCurrentUser())
    }

    // =========================================================================
    // Scenario 15: Login when /users/{uid} has SUSPENDED, RESTRICTED, or BANNED
    //              returns the corresponding account status cleanly
    // =========================================================================
    @Test
    fun `15 - Login with RESTRICTED, SUSPENDED, or BANNED profile returns AccountLocked with exact standing`() = runBlocking {
        val statuses = listOf(
            Triple(AccountStatus.RESTRICTED, "Repeated missed pickups", null),
            Triple(AccountStatus.SUSPENDED, "Temporary investigation", 1740000000000L),
            Triple(AccountStatus.BANNED, "Fraudulent activity", null)
        )

        for ((index, triple) in statuses.withIndex()) {
            val (accountStatus, reason, suspendedUntil) = triple
            val uid = "mod_uid_$index"
            val email = "mod$index@wastelink.co.tz"

            fakeRemote.seedAuthAccount(
                uid = uid,
                email = email,
                password = "ModPass123",
                isEmailVerified = true
            )
            fakeRemote.seedCanonicalResidentProfile(
                uid = uid,
                email = email,
                accountStatus = accountStatus,
                statusReason = reason,
                suspendedUntilEpochMs = suspendedUntil
            )

            val outcome = repository.login(email, "ModPass123")
            assertTrue("Expected LoginOutcome.AccountLocked for $accountStatus", outcome is LoginOutcome.AccountLocked)
            val locked = outcome as LoginOutcome.AccountLocked
            assertEquals(accountStatus, locked.accountStatus)
            assertEquals(reason, locked.statusReason)
            assertEquals(suspendedUntil, locked.suspendedUntilEpochMs)

            val session = repository.sessionState.value
            assertTrue(session is SessionState.AuthenticatedRestricted)
            assertEquals(accountStatus, (session as SessionState.AuthenticatedRestricted).accountStatus)
        }
    }

    // =========================================================================
    // Scenario 16: Login when Firestore is temporarily unavailable returns retryable error
    //              and does not fabricate a default user
    // =========================================================================
    @Test
    fun `16 - Login when Firestore is unavailable returns retryable NetworkFailure and never fabricates user`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "net_uid_16",
            email = "net16@wastelink.co.tz",
            password = "NetPass123"
        )
        fakeRemote.alwaysFailFetchServerWith = RemoteAuthException(
            code = RemoteAuthErrorCode.NETWORK_UNAVAILABLE,
            message = "UNAVAILABLE: Firestore offline"
        )

        val outcome = repository.login("net16@wastelink.co.tz", "NetPass123")
        assertTrue("Expected ProfileLoadFailed, got $outcome", outcome is LoginOutcome.ProfileLoadFailed)
        val failed = outcome as LoginOutcome.ProfileLoadFailed
        assertTrue(failed.error is AuthError.NetworkFailure)
        assertTrue((failed.error as AuthError.NetworkFailure).isRetryable)

        // Verify bounded retry (2 attempts) occurred and NO default user was fabricated
        assertEquals(2, fakeRemote.fetchServerCallCount)
        assertNull(repository.getCurrentUser())
        assertTrue(fakeRemote.serverUserDocuments.isEmpty())
        assertTrue(repository.sessionState.value is SessionState.AuthenticatedProfileLoadError)
    }

    // =========================================================================
    // Scenario 17: Legacy /users/{uid} document is mapped into canonical User
    // =========================================================================
    @Test
    fun `17 - Legacy AppUser document in Firestore is mapped transparently into canonical User on login`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "legacy_uid_17",
            email = "legacy17@wastelink.co.tz",
            password = "LegacyPass123",
            isEmailVerified = true
        )
        fakeRemote.serverUserDocuments["legacy_uid_17"] = mapOf(
            "uid" to "legacy_uid_17",
            "name" to "Bakari Mwapachu",
            "email" to "legacy17@wastelink.co.tz",
            "phone" to "+255754998877",
            "role" to "WASTE_COMPANY",
            "isVerified" to true,
            "status" to "ACTIVE"
        )

        val outcome = repository.login("legacy17@wastelink.co.tz", "LegacyPass123")
        assertTrue(outcome is LoginOutcome.Authenticated)
        val authenticated = outcome as LoginOutcome.Authenticated

        assertEquals("legacy_uid_17", authenticated.user.id)
        assertEquals("Bakari Mwapachu", authenticated.user.fullName)
        assertEquals("+255754998877", authenticated.user.phoneNumber)
        assertEquals(UserRole.COLLECTOR, authenticated.user.role)
        assertEquals(AccountStatus.ACTIVE, authenticated.user.accountStatus)
        assertEquals(OnboardingStatus.COMPLETED, authenticated.user.onboardingStatus)
        assertEquals(CollectorVerificationStatus.APPROVED, authenticated.user.collectorVerificationStatus)
        assertTrue(authenticated.user.isCollectorOperational)
    }

    // =========================================================================
    // Scenario 18: Malformed /users/{uid} document returns typed profile corruption error
    // =========================================================================
    @Test
    fun `18 - Malformed Firestore user document returns MalformedProfile error without fabricating user`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "corrupt_uid_18",
            email = "corrupt18@wastelink.co.tz",
            password = "CorruptPass123"
        )
        // Document with corrupted/unrecognized role and missing fullName
        fakeRemote.serverUserDocuments["corrupt_uid_18"] = mapOf(
            "uid" to "corrupt_uid_18",
            "email" to "corrupt18@wastelink.co.tz",
            "fullName" to "Corrupt Role User",
            "role" to "SUPER_ROOT_HACKER"
        )

        val outcome = repository.login("corrupt18@wastelink.co.tz", "CorruptPass123")
        assertTrue("Expected ProfileLoadFailed, got $outcome", outcome is LoginOutcome.ProfileLoadFailed)
        val failed = outcome as LoginOutcome.ProfileLoadFailed
        assertTrue("Expected MalformedProfile, got ${failed.error}", failed.error is AuthError.MalformedProfile)
        assertNull(repository.getCurrentUser())
    }

    // =========================================================================
    // Scenario 19: Verification email send/resend works through the repository contract
    // =========================================================================
    @Test
    fun `19 - Verification email send and resend work and handle AlreadyVerified and RateLimited states`() = runBlocking {
        // Unauthenticated call fails cleanly
        val unauthSend = repository.sendEmailVerification()
        assertTrue(unauthSend is EmailVerificationOutcome.Failure)
        assertTrue((unauthSend as EmailVerificationOutcome.Failure).error is AuthError.Unauthenticated)

        // Authenticated unverified user
        fakeRemote.seedAuthAccount(
            uid = "verify_uid_19",
            email = "verify19@wastelink.co.tz",
            password = "VerifyPass123",
            isEmailVerified = false
        )
        fakeRemote.seedCanonicalResidentProfile("verify_uid_19", "verify19@wastelink.co.tz")
        repository.login("verify19@wastelink.co.tz", "VerifyPass123")

        val sendResult = repository.sendEmailVerification()
        assertEquals(EmailVerificationOutcome.Sent("verify19@wastelink.co.tz"), sendResult)
        assertEquals(1, fakeRemote.sendVerificationEmailCallCount)

        val resendResult = repository.resendEmailVerification()
        assertEquals(EmailVerificationOutcome.Sent("verify19@wastelink.co.tz"), resendResult)
        assertEquals(2, fakeRemote.sendVerificationEmailCallCount)

        // Rate limit handling
        fakeRemote.nextSendVerificationError = RemoteAuthException(
            code = RemoteAuthErrorCode.TOO_MANY_REQUESTS,
            message = "Too many verification email requests"
        )
        val rateLimitedResult = repository.resendEmailVerification()
        assertTrue(rateLimitedResult is EmailVerificationOutcome.Failure)
        assertTrue((rateLimitedResult as EmailVerificationOutcome.Failure).error is AuthError.RateLimited)

        // Once verified, returns AlreadyVerified without redundant network call
        fakeRemote.setServerEmailVerified("verify_uid_19", true)
        repository.refreshEmailVerificationStatus()
        val alreadyVerifiedResult = repository.sendEmailVerification()
        assertEquals(EmailVerificationOutcome.AlreadyVerified("verify19@wastelink.co.tz"), alreadyVerifiedResult)
    }

    // =========================================================================
    // Scenario 20: Verification state refresh reloads FirebaseUser and refreshes token when verified
    // =========================================================================
    @Test
    fun `20 - Verification state refresh reloads FirebaseUser and forces token refresh when verified`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "verify_uid_20",
            email = "verify20@wastelink.co.tz",
            password = "VerifyPass123",
            isEmailVerified = false
        )
        fakeRemote.seedCanonicalResidentProfile("verify_uid_20", "verify20@wastelink.co.tz")
        repository.login("verify20@wastelink.co.tz", "VerifyPass123")
        assertFalse(repository.isEmailVerified())

        // Still unverified on server -> reloads user, does not force token refresh
        val refresh1 = repository.refreshEmailVerificationStatus()
        assertEquals(
            EmailVerificationOutcome.StatusRefreshed(isEmailVerified = false, tokenRefreshed = false),
            refresh1
        )
        assertEquals(0, fakeRemote.refreshIdTokenCallCount)

        // User clicks verification link on server
        fakeRemote.setServerEmailVerified("verify_uid_20", true)
        val refresh2 = repository.refreshEmailVerificationStatus()
        assertEquals(
            EmailVerificationOutcome.StatusRefreshed(isEmailVerified = true, tokenRefreshed = true),
            refresh2
        )
        assertEquals(1, fakeRemote.refreshIdTokenCallCount)
        assertTrue(repository.isEmailVerified())
        assertTrue((repository.sessionState.value as SessionState.Authenticated).isEmailVerified)

        // Crucial security check: client never writes isVerified=true to /users/{uid}
        assertEquals(false, fakeRemote.serverUserDocuments["verify_uid_20"]?.get("isVerified"))
    }

    // =========================================================================
    // Scenario 21: Password reset validates email and returns privacy-preserving success/error states
    // =========================================================================
    @Test
    fun `21 - Password reset validates email and returns enumeration-safe ResetEmailDispatched`() = runBlocking {
        // 1. Invalid email rejected before any Firebase call
        val invalidOutcome = repository.sendPasswordReset("invalid-email-format")
        assertTrue(invalidOutcome is PasswordResetOutcome.Failure)
        assertTrue((invalidOutcome as PasswordResetOutcome.Failure).error is AuthError.ValidationFailed)
        assertEquals(0, fakeRemote.sendPasswordResetCallCount)

        // 2. Registered email dispatches reset
        fakeRemote.seedAuthAccount("reset_uid_1", "registered@wastelink.co.tz", "Pass1234")
        val existingOutcome = repository.sendPasswordReset("  Registered@WasteLink.co.tz ")
        assertEquals(
            PasswordResetOutcome.ResetEmailDispatched("registered@wastelink.co.tz"),
            existingOutcome
        )
        assertEquals(1, fakeRemote.sendPasswordResetCallCount)

        // 3. Unregistered email ALSO returns ResetEmailDispatched (enumeration-safe)
        val unknownOutcome = repository.sendPasswordReset("nonexistent@wastelink.co.tz")
        assertEquals(
            PasswordResetOutcome.ResetEmailDispatched("nonexistent@wastelink.co.tz"),
            unknownOutcome
        )
        assertEquals(2, fakeRemote.sendPasswordResetCallCount)

        // 4. Rate limit and network errors are reported accurately
        fakeRemote.nextPasswordResetError = RemoteAuthException(
            code = RemoteAuthErrorCode.TOO_MANY_REQUESTS,
            message = "Rate limit exceeded"
        )
        val rateLimited = repository.sendPasswordReset("registered@wastelink.co.tz")
        assertTrue(rateLimited is PasswordResetOutcome.Failure)
        assertTrue((rateLimited as PasswordResetOutcome.Failure).error is AuthError.RateLimited)
    }

    // =========================================================================
    // Scenario 22: Session restoration distinguishes unauthenticated, authenticated with valid profile,
    //              authenticated with missing profile, and temporary network failure
    // =========================================================================
    @Test
    fun `22 - Session restoration distinguishes Unauthenticated, Authenticated, ProfileIncomplete, NetworkFailure, and Cache fallback`() = runBlocking {
        // Case A: Unauthenticated
        assertEquals(SessionState.Unauthenticated, repository.restoreSession())

        // Case B: Authenticated with valid profile
        fakeRemote.seedAuthAccount("sess_uid_1", "sess1@wastelink.co.tz", "Pass1234", isEmailVerified = true)
        fakeRemote.seedCanonicalResidentProfile("sess_uid_1", "sess1@wastelink.co.tz")
        fakeRemote.setActiveSession("sess_uid_1")

        val validSession = repository.restoreSession()
        assertTrue(validSession is SessionState.Authenticated)
        assertEquals("sess_uid_1", (validSession as SessionState.Authenticated).user.id)

        // Case C: Authenticated with missing profile (Ghost Account)
        fakeRemote.seedAuthAccount("sess_ghost_2", "ghost2@wastelink.co.tz", "Pass1234")
        fakeRemote.setActiveSession("sess_ghost_2")

        val ghostSession = repository.restoreSession()
        assertTrue(ghostSession is SessionState.AuthenticatedProfileIncomplete)
        assertEquals("sess_ghost_2", (ghostSession as SessionState.AuthenticatedProfileIncomplete).uid)
        assertNotNull(fakeRemote.currentUser) // Must not sign out!

        // Case D: Temporary network failure with NO local cache -> AuthenticatedProfileLoadError (session kept!)
        fakeRemote.alwaysFailFetchServerWith = RemoteAuthException(
            code = RemoteAuthErrorCode.NETWORK_UNAVAILABLE,
            message = "Offline"
        )
        val offlineNoCacheSession = repository.restoreSession()
        assertTrue(offlineNoCacheSession is SessionState.AuthenticatedProfileLoadError)
        assertNotNull(fakeRemote.currentUser) // Must NOT destroy valid Firebase session!

        // Case E: Temporary network failure WITH local Firestore cache -> AuthenticatedCachedOffline (NEVER Authenticated)
        fakeRemote.setActiveSession("sess_uid_1")
        fakeRemote.cachedUserDocuments["sess_uid_1"] = fakeRemote.serverUserDocuments["sess_uid_1"]!!
        val offlineCachedSession = repository.restoreSession()
        assertTrue(
            "Expected AuthenticatedCachedOffline, got $offlineCachedSession",
            offlineCachedSession is SessionState.AuthenticatedCachedOffline
        )
        assertFalse(offlineCachedSession.isServerVerified)
        assertFalse(offlineCachedSession.canPerformProtectedOperations)
        assertFalse(repository.isServerVerifiedSession())
        // Server-verified getCurrentUser() must be null while offline; read-only getCachedOrVerifiedUser() returns cached snapshot
        assertNull(repository.getCurrentUser())
        assertEquals("sess_uid_1", repository.getCachedOrVerifiedUser()?.id)
    }

    // =========================================================================
    // Scenario 23: Logout clears auth session and in-memory draft state without deleting /users/{uid}
    // =========================================================================
    @Test
    fun `23 - Logout clears auth session and pending registration draft without deleting Firestore user document`() = runBlocking {
        // Trigger a partial registration so a draft is cached in memory
        fakeRemote.nextWriteUserError = RemoteAuthException(
            code = RemoteAuthErrorCode.NETWORK_UNAVAILABLE,
            message = "Temporary error"
        )
        repository.registerResident(
            fullName = "Draft User",
            email = "draft@wastelink.co.tz",
            phoneNumber = "+255712345678",
            password = "DraftPassword123",
            confirmPassword = "DraftPassword123"
        )
        val createdUid = fakeRemote.currentUser!!.uid
        // Also seed a document to ensure logout does not delete /users/{uid}
        fakeRemote.seedCanonicalResidentProfile(createdUid, "draft@wastelink.co.tz")
        assertNotNull(repository.getPendingRegistrationDraft())

        val logoutOutcome = repository.logout()
        assertEquals(LogoutOutcome.Success, logoutOutcome)
        assertNull(fakeRemote.currentUser)
        assertNull(repository.getPendingRegistrationDraft())
        assertNull(repository.getCurrentUser())
        assertEquals(SessionState.Unauthenticated, repository.sessionState.value)

        // Firestore /users/{uid} document MUST remain intact
        assertTrue(fakeRemote.serverUserDocuments.containsKey(createdUid))
    }

    // =========================================================================
    // Scenario 24: Self-service Resident onboarding & profile update rules contract
    // =========================================================================
    @Test
    fun `24 - Resident onboarding completion and profile update enforce email verification and preserve immutable fields`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "onboard_uid_24",
            email = "onboard24@wastelink.co.tz",
            password = "OnboardPass123",
            isEmailVerified = false
        )
        fakeRemote.seedCanonicalResidentProfile("onboard_uid_24", "onboard24@wastelink.co.tz")
        repository.login("onboard24@wastelink.co.tz", "OnboardPass123")

        // Unverified user cannot complete onboarding
        val blockedResult = repository.completeResidentOnboarding("zone_ilala")
        assertTrue(blockedResult is ProfileUpdateOutcome.Failure)
        assertTrue((blockedResult as ProfileUpdateOutcome.Failure).error is AuthError.EmailNotVerified)

        // Verify email and refresh status
        fakeRemote.setServerEmailVerified("onboard_uid_24", true)
        repository.refreshEmailVerificationStatus()

        // Null zone rejected
        val nullZoneResult = repository.completeResidentOnboarding(null)
        assertTrue(nullZoneResult is ProfileUpdateOutcome.Failure)
        assertTrue((nullZoneResult as ProfileUpdateOutcome.Failure).error is AuthError.ValidationFailed)

        // Blank zone rejected
        val blankZoneResult = repository.completeResidentOnboarding("   ")
        assertTrue(blankZoneResult is ProfileUpdateOutcome.Failure)
        assertTrue((blankZoneResult as ProfileUpdateOutcome.Failure).error is AuthError.ValidationFailed)

        // Unsupported zone rejected
        val unsupportedZoneResult = repository.completeResidentOnboarding(AuthValidator.UNSUPPORTED_ZONE_ID)
        assertTrue(unsupportedZoneResult is ProfileUpdateOutcome.Failure)
        assertTrue((unsupportedZoneResult as ProfileUpdateOutcome.Failure).error is AuthError.ValidationFailed)

        currentEpochMs += 5_000L
        val onboardResult = repository.completeResidentOnboarding("zone_ilala")
        assertTrue(onboardResult is ProfileUpdateOutcome.Success)
        val updatedUser = (onboardResult as ProfileUpdateOutcome.Success).user
        assertEquals(OnboardingStatus.COMPLETED, updatedUser.onboardingStatus)
        assertEquals("zone_ilala", updatedUser.defaultZoneId)
    }

    // =========================================================================
    // Scenario 25: Offline cached ACTIVE session is not treated as server-verified
    //              and blocks protected mutations or login bypass when suspended on server
    // =========================================================================
    @Test
    fun `25 - Cached ACTIVE profile is not treated as proof of server standing and blocks protected writes while offline`() = runBlocking {
        fakeRemote.seedAuthAccount(
            uid = "cache_safety_25",
            email = "safety25@wastelink.co.tz",
            password = "SafetyPass123",
            isEmailVerified = true
        )
        fakeRemote.seedCanonicalResidentProfile("cache_safety_25", "safety25@wastelink.co.tz")

        // Populate local cache with ACTIVE profile, then suspend user on server and go offline
        fakeRemote.cachedUserDocuments["cache_safety_25"] = fakeRemote.serverUserDocuments["cache_safety_25"]!!
        fakeRemote.seedCanonicalResidentProfile(
            uid = "cache_safety_25",
            email = "safety25@wastelink.co.tz",
            accountStatus = AccountStatus.SUSPENDED,
            statusReason = "Suspended on server while client offline",
            suspendedUntilEpochMs = 1750000000000L
        )
        fakeRemote.alwaysFailFetchServerWith = RemoteAuthException(
            code = RemoteAuthErrorCode.NETWORK_UNAVAILABLE,
            message = "Server unreachable"
        )

        // 1. Fresh login while offline MUST NOT fall back to stale ACTIVE cache
        val offlineLogin = repository.login("safety25@wastelink.co.tz", "SafetyPass123")
        assertTrue(offlineLogin is LoginOutcome.ProfileLoadFailed)

        // 2. Session restoration while offline produces AuthenticatedCachedOffline (isServerVerified = false)
        val restored = repository.restoreSession()
        assertTrue(restored is SessionState.AuthenticatedCachedOffline)
        assertFalse(restored.isServerVerified)
        assertFalse(restored.canPerformProtectedOperations)

        // 3. Protected profile update is rejected while in AuthenticatedCachedOffline
        val offlineUpdate = repository.updateProfile(
            fullName = "Updated While Offline",
            phoneNumber = "+255712345678",
            defaultZoneId = "zone_temeke"
        )
        assertTrue(offlineUpdate is ProfileUpdateOutcome.Failure)
        assertTrue((offlineUpdate as ProfileUpdateOutcome.Failure).error is AuthError.NetworkFailure)

        // 4. When server connectivity returns, attempting a protected update or restoreSession detects server SUSPENDED status
        fakeRemote.alwaysFailFetchServerWith = null
        val revalidatedSession = repository.restoreSession()
        assertTrue(revalidatedSession is SessionState.AuthenticatedRestricted)
        assertEquals(AccountStatus.SUSPENDED, (revalidatedSession as SessionState.AuthenticatedRestricted).accountStatus)
    }

    // =========================================================================
    // Scenario 26: FirebaseAuth state listener synchronizes sign-in, external sign-out,
    //              disabled account, revoked session, and unregisters cleanly without leaks
    // =========================================================================
    @Test
    fun `26 - FirebaseAuth listener synchronizes external sign-out, account switch, disabled account, and revoked token`() = runBlocking {
        assertEquals(1, fakeRemote.activeAuthListenerCount)

        fakeRemote.seedAuthAccount(
            uid = "listen_uid_1",
            email = "listen1@wastelink.co.tz",
            password = "ListenPass123",
            isEmailVerified = false
        )
        fakeRemote.seedCanonicalResidentProfile("listen_uid_1", "listen1@wastelink.co.tz")

        val loginOutcome = repository.login("listen1@wastelink.co.tz", "ListenPass123")
        assertTrue(loginOutcome is LoginOutcome.Authenticated)
        assertTrue(repository.sessionState.value is SessionState.Authenticated)

        // 1. External token update (email verified via listener notification) updates sessionState
        fakeRemote.setServerEmailVerified("listen_uid_1", true)
        fakeRemote.notifyListenersOfCurrentAccountUpdate()
        assertTrue((repository.sessionState.value as SessionState.Authenticated).isEmailVerified)

        // 2. External sign-out on FirebaseAuth immediately transitions sessionState to Unauthenticated
        fakeRemote.signOut()
        assertEquals(SessionState.Unauthenticated, repository.sessionState.value)
        assertNull(repository.getCurrentUser())

        // 3. Re-sign-in, then disable account on server -> restoreSession transitions to AuthenticationRevoked(UserDisabled)
        repository.login("listen1@wastelink.co.tz", "ListenPass123")
        fakeRemote.setAccountDisabled("listen_uid_1", true)
        val disabledSession = repository.restoreSession()
        assertTrue(
            "Expected AuthenticationRevoked, got $disabledSession",
            disabledSession is SessionState.AuthenticationRevoked
        )
        assertTrue((disabledSession as SessionState.AuthenticationRevoked).error is AuthError.UserDisabled)
        assertNull(fakeRemote.currentUser)

        // 4. Re-enable, sign in, then expire/revoke token -> restoreSession transitions to AuthenticationRevoked(SessionRevoked)
        fakeRemote.setAccountDisabled("listen_uid_1", false)
        repository.login("listen1@wastelink.co.tz", "ListenPass123")
        fakeRemote.setTokenRevoked("listen_uid_1", true)
        val revokedSession = repository.restoreSession()
        assertTrue(revokedSession is SessionState.AuthenticationRevoked)
        assertTrue((revokedSession as SessionState.AuthenticationRevoked).error is AuthError.SessionRevoked)
        assertNull(fakeRemote.currentUser)

        // 5. Closing repository unregisters the listener cleanly without leaking
        repository.close()
        assertEquals(0, fakeRemote.activeAuthListenerCount)
    }

    // =========================================================================
    // Helper: Asserts that a written /users/{uid} map satisfies Stage 2.1 firestore.rules
    // =========================================================================
    private fun assertFirestoreDocumentSatisfiesSelfCreateRules(
        expectedUid: String,
        expectedEmail: String,
        data: Map<String, Any?>
    ) {
        val allowedKeys = setOf(
            "id", "uid", "fullName", "name", "email", "phoneNumber", "phone",
            "role", "accountStatus", "status", "statusReason", "suspendedUntilEpochMs",
            "onboardingStatus", "isVerified", "collectorVerificationStatus",
            "collectorRejectionReason", "defaultZoneId", "vehicleType",
            "vehiclePlateNumber", "preferredLanguage", "createdAtEpochMs",
            "updatedAtEpochMs", "fcmToken"
        )
        assertTrue(
            "Unexpected keys in Firestore document: ${data.keys - allowedKeys}",
            allowedKeys.containsAll(data.keys)
        )
        assertEquals(expectedUid, data["id"])
        assertEquals(expectedUid, data["uid"])
        assertEquals(expectedEmail, data["email"])
        assertEquals(data["fullName"], data["name"])
        assertEquals(data["phoneNumber"], data["phone"])
        assertTrue(data["role"] in setOf("resident", "collector"))
        assertEquals("ACTIVE", data["accountStatus"])
        assertEquals("ACTIVE", data["status"])
        assertNull(data["statusReason"])
        assertNull(data["suspendedUntilEpochMs"])
        assertEquals("NOT_STARTED", data["onboardingStatus"])
        assertEquals(false, data["isVerified"])
        assertNull(data["collectorRejectionReason"])
        assertNull(data["vehicleType"])
        assertNull(data["vehiclePlateNumber"])
        val createdAt = data["createdAtEpochMs"] as Long
        val updatedAt = data["updatedAtEpochMs"] as Long
        assertTrue(createdAt > 0L)
        assertTrue(updatedAt >= createdAt)
    }
}

/**
 * Deterministic in-memory implementation of [AuthRemoteDataSource] for JVM unit testing.
 */
private class FakeAuthRemoteDataSource : AuthRemoteDataSource {

    data class StoredAuthAccount(
        val uid: String,
        val email: String,
        val password: String,
        var displayName: String? = null,
        var phoneNumber: String? = null,
        var isEmailVerified: Boolean = false,
        var hasAdminClaim: Boolean = false,
        var isDisabled: Boolean = false,
        var isTokenRevoked: Boolean = false
    )

    private val authAccountsByEmail = mutableMapOf<String, StoredAuthAccount>()
    private val authAccountsByUid = mutableMapOf<String, StoredAuthAccount>()
    private val authListeners = mutableListOf<(RemoteAuthUser?) -> Unit>()
    val serverUserDocuments = mutableMapOf<String, Map<String, Any?>>()
    val cachedUserDocuments = mutableMapOf<String, Map<String, Any?>>()

    var currentUser: RemoteAuthUser? = null
    private var nextGeneratedUidIndex = 1

    val activeAuthListenerCount: Int
        get() = authListeners.size

    var createAuthUserCallCount = 0
    var writeUserDocumentCallCount = 0
    var fetchServerCallCount = 0
    var sendVerificationEmailCallCount = 0
    var refreshIdTokenCallCount = 0
    var sendPasswordResetCallCount = 0

    var nextWriteUserError: Throwable? = null
    var alwaysFailFetchServerWith: Throwable? = null
    var nextSendVerificationError: Throwable? = null
    var nextPasswordResetError: Throwable? = null

    fun seedAuthAccount(
        uid: String,
        email: String,
        password: String,
        isEmailVerified: Boolean = false,
        hasAdminClaim: Boolean = false,
        isDisabled: Boolean = false
    ) {
        val normalizedEmail = email.trim().lowercase()
        val account = StoredAuthAccount(
            uid = uid,
            email = normalizedEmail,
            password = password,
            isEmailVerified = isEmailVerified,
            hasAdminClaim = hasAdminClaim,
            isDisabled = isDisabled
        )
        authAccountsByEmail[normalizedEmail] = account
        authAccountsByUid[uid] = account
    }

    fun setActiveSession(uid: String) {
        val account = authAccountsByUid[uid] ?: error("Unknown uid $uid")
        currentUser = account.toRemoteUser()
        notifyAuthListeners()
    }

    fun setServerEmailVerified(uid: String, verified: Boolean) {
        authAccountsByUid[uid]?.isEmailVerified = verified
    }

    fun setAccountDisabled(uid: String, disabled: Boolean) {
        authAccountsByUid[uid]?.isDisabled = disabled
    }

    fun setTokenRevoked(uid: String, revoked: Boolean) {
        authAccountsByUid[uid]?.isTokenRevoked = revoked
    }

    fun notifyListenersOfCurrentAccountUpdate() {
        val current = currentUser ?: return
        val updated = authAccountsByUid[current.uid]?.toRemoteUser()
        currentUser = updated
        notifyAuthListeners()
    }

    private fun notifyAuthListeners() {
        val snapshot = currentUser
        for (listener in authListeners.toList()) {
            listener(snapshot)
        }
    }

    fun seedCanonicalResidentProfile(
        uid: String,
        email: String,
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
        statusReason: String? = null,
        suspendedUntilEpochMs: Long? = null
    ) {
        serverUserDocuments[uid] = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Existing Resident",
            "name" to "Existing Resident",
            "email" to email.lowercase(),
            "phoneNumber" to "+255712345678",
            "phone" to "+255712345678",
            "role" to "resident",
            "accountStatus" to accountStatus.name,
            "status" to accountStatus.name,
            "statusReason" to statusReason,
            "suspendedUntilEpochMs" to suspendedUntilEpochMs,
            "onboardingStatus" to "COMPLETED",
            "isVerified" to false,
            "collectorVerificationStatus" to "NOT_APPLICABLE",
            "collectorRejectionReason" to null,
            "defaultZoneId" to "zone_kinondoni",
            "vehicleType" to null,
            "vehiclePlateNumber" to null,
            "preferredLanguage" to "en",
            "createdAtEpochMs" to 1730000000000L,
            "updatedAtEpochMs" to 1730000000000L
        )
    }

    fun seedCanonicalCollectorProfile(
        uid: String,
        email: String,
        verificationStatus: CollectorVerificationStatus
    ) {
        serverUserDocuments[uid] = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Existing Collector",
            "name" to "Existing Collector",
            "email" to email.lowercase(),
            "phoneNumber" to "+255754112233",
            "phone" to "+255754112233",
            "role" to "collector",
            "accountStatus" to "ACTIVE",
            "status" to "ACTIVE",
            "statusReason" to null,
            "suspendedUntilEpochMs" to null,
            "onboardingStatus" to "COMPLETED",
            "isVerified" to (verificationStatus == CollectorVerificationStatus.APPROVED),
            "collectorVerificationStatus" to verificationStatus.name,
            "collectorRejectionReason" to null,
            "defaultZoneId" to "zone_ilala",
            "vehicleType" to "Compactor Truck",
            "vehiclePlateNumber" to "T 123 WST",
            "preferredLanguage" to "en",
            "createdAtEpochMs" to 1730000000000L,
            "updatedAtEpochMs" to 1730000000000L
        )
    }

    override fun getCurrentAuthUser(): RemoteAuthUser? = currentUser

    override fun registerAuthStateListener(listener: (RemoteAuthUser?) -> Unit): AutoCloseable {
        authListeners.add(listener)
        return AutoCloseable {
            authListeners.remove(listener)
        }
    }

    override suspend fun createAuthUser(email: String, password: String): RemoteAuthUser {
        createAuthUserCallCount++
        val normalized = email.trim().lowercase()
        if (authAccountsByEmail.containsKey(normalized)) {
            throw RemoteAuthException(
                code = RemoteAuthErrorCode.EMAIL_ALREADY_IN_USE,
                message = "The email address is already in use by another account."
            )
        }
        val uid = "generated_uid_${nextGeneratedUidIndex++}"
        val account = StoredAuthAccount(
            uid = uid,
            email = normalized,
            password = password,
            isEmailVerified = false,
            hasAdminClaim = false,
            isDisabled = false
        )
        authAccountsByEmail[normalized] = account
        authAccountsByUid[uid] = account
        val remoteUser = account.toRemoteUser()
        currentUser = remoteUser
        notifyAuthListeners()
        return remoteUser
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String): RemoteAuthUser {
        val normalized = email.trim().lowercase()
        val account = authAccountsByEmail[normalized]
            ?: throw RemoteAuthException(
                code = RemoteAuthErrorCode.USER_NOT_FOUND,
                message = "There is no user record corresponding to this identifier."
            )

        if (account.isDisabled) {
            throw RemoteAuthException(
                code = RemoteAuthErrorCode.USER_DISABLED,
                message = "The user account has been disabled by an administrator."
            )
        }

        if (account.password != password) {
            throw RemoteAuthException(
                code = RemoteAuthErrorCode.INVALID_CREDENTIALS,
                message = "The password is invalid or the user does not have a password."
            )
        }

        account.isTokenRevoked = false
        val remoteUser = account.toRemoteUser()
        currentUser = remoteUser
        notifyAuthListeners()
        return remoteUser
    }

    override suspend fun reloadCurrentAuthUser(forceTokenRefresh: Boolean): RemoteAuthUser? {
        val current = currentUser ?: return null
        val stored = authAccountsByUid[current.uid]
            ?: throw RemoteAuthException(
                code = RemoteAuthErrorCode.USER_NOT_FOUND,
                message = "User account no longer exists."
            )
        if (stored.isDisabled) {
            throw RemoteAuthException(
                code = RemoteAuthErrorCode.USER_DISABLED,
                message = "User account has been disabled."
            )
        }
        if (stored.isTokenRevoked) {
            throw RemoteAuthException(
                code = RemoteAuthErrorCode.TOKEN_EXPIRED,
                message = "The user's credential is no longer valid. The user must sign in again."
            )
        }
        val reloaded = stored.toRemoteUser()
        currentUser = reloaded
        notifyAuthListeners()
        return reloaded
    }

    override suspend fun refreshIdToken(): RemoteAuthUser? {
        refreshIdTokenCallCount++
        return reloadCurrentAuthUser(forceTokenRefresh = true)
    }

    override suspend fun updateAuthDisplayName(fullName: String) {
        val current = currentUser ?: return
        authAccountsByUid[current.uid]?.displayName = fullName
        currentUser = current.copy(displayName = fullName)
    }

    override suspend fun sendEmailVerification() {
        sendVerificationEmailCallCount++
        nextSendVerificationError?.let {
            nextSendVerificationError = null
            throw it
        }
        if (currentUser == null) {
            throw RemoteAuthException(
                code = RemoteAuthErrorCode.UNAUTHENTICATED,
                message = "Unauthenticated"
            )
        }
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        sendPasswordResetCallCount++
        nextPasswordResetError?.let {
            nextPasswordResetError = null
            throw it
        }
        val normalized = email.trim().lowercase()
        if (!authAccountsByEmail.containsKey(normalized)) {
            throw RemoteAuthException(
                code = RemoteAuthErrorCode.USER_NOT_FOUND,
                message = "User not found"
            )
        }
    }

    override fun signOut() {
        currentUser = null
        notifyAuthListeners()
    }

    override suspend fun deleteCurrentAuthUser() {
        val current = currentUser ?: throw RemoteAuthException(
            code = RemoteAuthErrorCode.UNAUTHENTICATED,
            message = "Unauthenticated"
        )
        authAccountsByUid.remove(current.uid)?.let {
            authAccountsByEmail.remove(it.email)
        }
        currentUser = null
        notifyAuthListeners()
    }

    override suspend fun fetchUserDocumentServerFirst(uid: String): RemoteDocumentResult {
        fetchServerCallCount++
        alwaysFailFetchServerWith?.let { throw it }
        val doc = serverUserDocuments[uid]
        return if (doc != null) {
            RemoteDocumentResult.Found(data = doc, isFromCache = false)
        } else {
            RemoteDocumentResult.NotFound(isFromCache = false)
        }
    }

    override suspend fun fetchUserDocumentFromCache(uid: String): RemoteDocumentResult {
        val doc = cachedUserDocuments[uid]
        return if (doc != null) {
            RemoteDocumentResult.Found(data = doc, isFromCache = true)
        } else {
            RemoteDocumentResult.NotFound(isFromCache = true)
        }
    }

    override suspend fun writeUserDocument(uid: String, data: Map<String, Any?>) {
        nextWriteUserError?.let {
            nextWriteUserError = null
            throw it
        }
        writeUserDocumentCallCount++
        serverUserDocuments[uid] = data.toMap()
    }

    override suspend fun updateUserDocument(uid: String, fields: Map<String, Any?>) {
        val existing = serverUserDocuments[uid]?.toMutableMap()
            ?: throw RemoteAuthException(
                code = RemoteAuthErrorCode.DOCUMENT_NOT_FOUND,
                message = "Document /users/$uid not found"
            )
        existing.putAll(fields)
        serverUserDocuments[uid] = existing
    }

    val serverCollectorSubmissions = mutableMapOf<String, Map<String, Any?>>()
    val serverCollectorHistory = mutableMapOf<String, MutableList<Map<String, Any?>>>()

    override suspend fun fetchCollectorSubmission(uid: String): RemoteDocumentResult {
        val doc = serverCollectorSubmissions[uid]
        return if (doc != null) {
            RemoteDocumentResult.Found(data = doc, isFromCache = false)
        } else {
            RemoteDocumentResult.NotFound(isFromCache = false)
        }
    }

    override suspend fun fetchCollectorSubmissionHistory(uid: String): List<Map<String, Any?>> {
        return serverCollectorHistory[uid]?.toList() ?: emptyList()
    }

    override suspend fun executeCollectorInitialSubmissionBatch(
        uid: String,
        submissionData: Map<String, Any?>,
        historyData: Map<String, Any?>,
        attemptId: String,
        userUpdateFields: Map<String, Any?>
    ) {
        serverCollectorSubmissions[uid] = submissionData
        val historyList = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        historyList.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override suspend fun executeCollectorResubmissionBatch(
        uid: String,
        submissionUpdateData: Map<String, Any?>,
        historyData: Map<String, Any?>,
        attemptId: String,
        userUpdateFields: Map<String, Any?>
    ) {
        serverCollectorSubmissions[uid] = submissionUpdateData
        val historyList = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        historyList.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override suspend fun executeAdminCollectorApprovalBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    ) {
        serverCollectorSubmissions[uid] = submissionUpdateData
        val historyList = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        historyList.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override suspend fun executeAdminCollectorRejectionBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    ) {
        serverCollectorSubmissions[uid] = submissionUpdateData
        val historyList = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        historyList.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override fun simulateNextProfileWriteFailureOnce(failure: Throwable) {
        nextWriteUserError = failure
    }

    private fun StoredAuthAccount.toRemoteUser(): RemoteAuthUser {
        return RemoteAuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            phoneNumber = phoneNumber,
            isEmailVerified = isEmailVerified,
            hasAdminClaim = hasAdminClaim
        )
    }
}
