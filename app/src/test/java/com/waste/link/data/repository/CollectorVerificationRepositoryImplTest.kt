package com.waste.link.data.repository

import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.remote.RemoteAuthUser
import com.waste.link.data.remote.RemoteDocumentResult
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.AuthError
import com.waste.link.domain.model.CollectorSubmission
import com.waste.link.domain.model.CollectorSubmissionHistoryEntry
import com.waste.link.domain.model.CollectorSubmissionInput
import com.waste.link.domain.model.CollectorSubmissionOutcome
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.EmailVerificationOutcome
import com.waste.link.domain.model.LoginOutcome
import com.waste.link.domain.model.LogoutOutcome
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.PasswordResetOutcome
import com.waste.link.domain.model.ProfileInitializationOutcome
import com.waste.link.domain.model.ProfileRecoveryInput
import com.waste.link.domain.model.ProfileUpdateOutcome
import com.waste.link.domain.model.RegistrationDraft
import com.waste.link.domain.model.RegistrationOutcome
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectorVerificationRepositoryImplTest {

    private lateinit var fakeRemote: TestAuthRemoteDataSource
    private lateinit var fakeAuthRepo: TestAuthRepository
    private lateinit var repository: CollectorVerificationRepositoryImpl
    private var currentEpochMs: Long = 1731500000000L

    @Before
    fun setUp() {
        currentEpochMs = 1731500000000L
        fakeRemote = TestAuthRemoteDataSource()
        fakeAuthRepo = TestAuthRepository()
        repository = CollectorVerificationRepositoryImpl(
            remoteDataSource = fakeRemote,
            authRepository = fakeAuthRepo,
            clock = { currentEpochMs }
        )
    }

    private fun buildCollectorUser(
        uid: String = "collector_test_uid",
        verificationStatus: CollectorVerificationStatus = CollectorVerificationStatus.NOT_SUBMITTED,
        onboardingStatus: OnboardingStatus = OnboardingStatus.NOT_STARTED
    ): User = User(
        id = uid,
        fullName = "Juma Bakari",
        email = "juma@wastelink.co.tz",
        phoneNumber = "+255712345678",
        role = UserRole.COLLECTOR,
        accountStatus = AccountStatus.ACTIVE,
        onboardingStatus = onboardingStatus,
        collectorVerificationStatus = verificationStatus,
        createdAtEpochMs = currentEpochMs,
        updatedAtEpochMs = currentEpochMs
    )

    private fun setupAuthenticatedCollector(
        uid: String = "collector_test_uid",
        isEmailVerified: Boolean = true,
        verificationStatus: CollectorVerificationStatus = CollectorVerificationStatus.NOT_SUBMITTED,
        onboardingStatus: OnboardingStatus = OnboardingStatus.NOT_STARTED
    ) {
        fakeRemote.currentUser = RemoteAuthUser(
            uid = uid,
            email = "juma@wastelink.co.tz",
            displayName = "Juma Bakari",
            phoneNumber = "+255712345678",
            isEmailVerified = isEmailVerified,
            hasAdminClaim = false
        )
        val user = buildCollectorUser(uid, verificationStatus, onboardingStatus)
        fakeAuthRepo.resolvedUser = user
        fakeRemote.serverUserDocuments[uid] = mapOf(
            "id" to uid,
            "fullName" to user.fullName,
            "email" to user.email,
            "phoneNumber" to user.phoneNumber,
            "role" to "collector",
            "accountStatus" to "active",
            "onboardingStatus" to onboardingStatus.name.lowercase(),
            "collectorVerificationStatus" to verificationStatus.name,
            "isVerified" to false,
            "createdAt" to currentEpochMs,
            "updatedAt" to currentEpochMs
        )
    }

    // =========================================================================
    // 1. Initial KYC Submission Validation
    // =========================================================================

    @Test
    fun `submitInitialVerification rejects unauthenticated user`() = runBlocking {
        fakeRemote.currentUser = null
        val input = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "T 123 ABC",
            licenseNumber = "DL-12345678",
            serviceZoneId = "zone_kinondoni"
        )
        val outcome = repository.submitInitialVerification(input)
        assertTrue(outcome is CollectorSubmissionOutcome.Failure)
        assertTrue((outcome as CollectorSubmissionOutcome.Failure).error is AuthError.Unauthenticated)
    }

    @Test
    fun `submitInitialVerification rejects unverified email`() = runBlocking {
        setupAuthenticatedCollector(isEmailVerified = false)
        val input = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "T 123 ABC",
            licenseNumber = "DL-12345678",
            serviceZoneId = "zone_kinondoni"
        )
        val outcome = repository.submitInitialVerification(input)
        assertTrue(outcome is CollectorSubmissionOutcome.Failure)
        assertTrue((outcome as CollectorSubmissionOutcome.Failure).error is AuthError.EmailNotVerified)
    }

    @Test
    fun `submitInitialVerification rejects invalid vehicle fields and unsupported zone`() = runBlocking {
        setupAuthenticatedCollector(isEmailVerified = true)

        // Invalid vehicle type (too short)
        val shortTypeOutcome = repository.submitInitialVerification(
            CollectorSubmissionInput(
                vehicleType = "A",
                vehiclePlateNumber = "T 123 ABC",
                licenseNumber = "DL-12345678",
                serviceZoneId = "zone_kinondoni"
            )
        )
        assertTrue(shortTypeOutcome is CollectorSubmissionOutcome.Failure)
        val error1 = (shortTypeOutcome as CollectorSubmissionOutcome.Failure).error
        assertTrue(error1 is AuthError.ValidationFailed)

        // Explicit unsupported zone
        val unsupportedZoneOutcome = repository.submitInitialVerification(
            CollectorSubmissionInput(
                vehicleType = "Pickup Truck",
                vehiclePlateNumber = "T 123 ABC",
                licenseNumber = "DL-12345678",
                serviceZoneId = AuthValidator.UNSUPPORTED_ZONE_ID
            )
        )
        assertTrue(unsupportedZoneOutcome is CollectorSubmissionOutcome.Failure)
        val error2 = (unsupportedZoneOutcome as CollectorSubmissionOutcome.Failure).error
        assertTrue(error2 is AuthError.ValidationFailed)
        val fieldErrors = (error2 as AuthError.ValidationFailed).errors
        assertTrue(fieldErrors.any { it.field == "serviceZoneId" })
    }

    @Test
    fun `submitInitialVerification writes submission, creates history entry and updates user`() = runBlocking {
        val uid = "collector_test_uid"
        setupAuthenticatedCollector(uid = uid, isEmailVerified = true)

        val input = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "t 123 abc",
            licenseNumber = "DL-12345678",
            serviceZoneId = "zone_kinondoni"
        )

        currentEpochMs += 10_000L
        val outcome = repository.submitInitialVerification(input)
        assertTrue("Expected Success but got $outcome", outcome is CollectorSubmissionOutcome.Success)
        val submission = (outcome as CollectorSubmissionOutcome.Success).submission

        assertEquals(uid, submission.userId)
        assertEquals(CollectorVerificationStatus.PENDING_REVIEW, submission.status)
        assertEquals("Pickup Truck", submission.vehicleType)
        assertEquals("T 123 ABC", submission.vehiclePlateNumber)
        assertEquals("DL-12345678", submission.licenseNumber)
        assertEquals("zone_kinondoni", submission.serviceZoneId)
        assertEquals(1, submission.submissionAttempt)
        assertEquals(currentEpochMs, submission.submittedAtEpochMs)

        // Verify history entry
        val history = repository.getSubmissionHistory(uid).getOrNull()
        assertNotNull(history)
        assertEquals(1, history!!.size)
        assertEquals(1, history[0].submissionAttempt)
        assertEquals("SUBMITTED", history[0].eventType)

        // Verify session was updated
        assertEquals(CollectorVerificationStatus.PENDING_REVIEW, fakeAuthRepo.resolvedUser?.collectorVerificationStatus)
        assertEquals(OnboardingStatus.COMPLETED, fakeAuthRepo.resolvedUser?.onboardingStatus)
    }

    // =========================================================================
    // 2. Idempotent Retry Handling
    // =========================================================================

    @Test
    fun `submitInitialVerification returns Success idempotently on rapid retry of identical data`() = runBlocking {
        val uid = "collector_test_uid"
        setupAuthenticatedCollector(uid = uid, isEmailVerified = true)

        val input = CollectorSubmissionInput(
            vehicleType = "Compactor Truck",
            vehiclePlateNumber = "T 999 ZZZ",
            licenseNumber = "DL-99998888",
            serviceZoneId = "zone_ilala"
        )

        // First submission succeeds
        val firstOutcome = repository.submitInitialVerification(input)
        assertTrue(firstOutcome is CollectorSubmissionOutcome.Success)

        // Second submission with exact same input (e.g. rapid double tap or network retry)
        val secondOutcome = repository.submitInitialVerification(input)
        assertTrue("Subsequent submission with identical data should be idempotent Success", secondOutcome is CollectorSubmissionOutcome.Success)
        assertEquals((firstOutcome as CollectorSubmissionOutcome.Success).submission.userId, (secondOutcome as CollectorSubmissionOutcome.Success).submission.userId)
        // Attempt count must remain 1
        assertEquals(1, secondOutcome.submission.submissionAttempt)
    }

    @Test
    fun `submitInitialVerification blocks submission if already PENDING_REVIEW with different data`() = runBlocking {
        val uid = "collector_test_uid"
        setupAuthenticatedCollector(uid = uid, isEmailVerified = true)

        val input1 = CollectorSubmissionInput(
            vehicleType = "Compactor Truck",
            vehiclePlateNumber = "T 999 ZZZ",
            licenseNumber = "DL-99998888",
            serviceZoneId = "zone_ilala"
        )
        repository.submitInitialVerification(input1)

        // Second submission with DIFFERENT data while PENDING_REVIEW should fail
        val input2 = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "T 111 AAA",
            licenseNumber = "DL-11112222",
            serviceZoneId = "zone_temeke"
        )
        val secondOutcome = repository.submitInitialVerification(input2)
        assertTrue(secondOutcome is CollectorSubmissionOutcome.Failure)
        assertTrue((secondOutcome as CollectorSubmissionOutcome.Failure).error is AuthError.PermissionDenied)
    }

    // =========================================================================
    // 3. Re-submission after Rejection
    // =========================================================================

    @Test
    fun `resubmitVerification increments attempt number and appends history entry`() = runBlocking {
        val uid = "collector_test_uid"
        setupAuthenticatedCollector(uid = uid, isEmailVerified = true)

        // Initial submission
        val input1 = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "T 123 ABC",
            licenseNumber = "DL-12345678",
            serviceZoneId = "zone_kinondoni"
        )
        repository.submitInitialVerification(input1)

        // Admin rejects submission
        fakeRemote.currentUser = RemoteAuthUser(
            uid = "admin_uid_1",
            email = "admin@wastelink.co.tz",
            displayName = "Admin Officer",
            phoneNumber = null,
            isEmailVerified = true,
            hasAdminClaim = true
        )
        repository.adminRejectCollectorSubmission(
            collectorUid = uid,
            adminUid = "admin_uid_1",
            rejectionReason = "Expired driving license document"
        )

        // Collector logs back in
        setupAuthenticatedCollector(
            uid = uid,
            isEmailVerified = true,
            verificationStatus = CollectorVerificationStatus.REJECTED,
            onboardingStatus = OnboardingStatus.COMPLETED
        )

        // Collector resubmits with renewed license
        currentEpochMs += 60_000L
        val input2 = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "T 123 ABC",
            licenseNumber = "DL-87654321", // updated
            serviceZoneId = "zone_kinondoni"
        )
        val resubmitOutcome = repository.resubmitVerification(input2)
        assertTrue("Expected Success on resubmission but got $resubmitOutcome", resubmitOutcome is CollectorSubmissionOutcome.Success)
        val resubmission = (resubmitOutcome as CollectorSubmissionOutcome.Success).submission

        assertEquals(2, resubmission.submissionAttempt)
        assertEquals(CollectorVerificationStatus.PENDING_REVIEW, resubmission.status)
        assertEquals("DL-87654321", resubmission.licenseNumber)
        assertNull(resubmission.rejectionReason) // Cleared for active review

        // History must have 3 entries: initial submission, rejection, resubmission
        val history = repository.getSubmissionHistory(uid).getOrNull()
        assertNotNull(history)
        assertEquals(3, history!!.size)
        assertEquals("RESUBMITTED", history[2].eventType)
        assertEquals(2, history[2].submissionAttempt)
    }

    // =========================================================================
    // 4. Admin Approval & Rejection Lifecycles
    // =========================================================================

    @Test
    fun `adminApproveCollectorSubmission transitions collector to APPROVED and COMPLETED`() = runBlocking {
        val uid = "collector_test_uid"
        setupAuthenticatedCollector(uid = uid, isEmailVerified = true)

        val input = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "T 123 ABC",
            licenseNumber = "DL-12345678",
            serviceZoneId = "zone_kinondoni"
        )
        repository.submitInitialVerification(input)

        // Non-admin cannot approve
        fakeRemote.currentUser = RemoteAuthUser(
            uid = "random_user_uid",
            email = "resident@wastelink.co.tz",
            displayName = "Resident User",
            phoneNumber = null,
            isEmailVerified = true,
            hasAdminClaim = false
        )
        val unauthorizedResult = repository.adminApproveCollectorSubmission(uid, "random_user_uid")
        assertTrue("Non-admin approval must fail", unauthorizedResult.isFailure)

        // Admin approves
        fakeRemote.currentUser = RemoteAuthUser(
            uid = "admin_super_uid",
            email = "admin@wastelink.co.tz",
            displayName = "Super Admin",
            phoneNumber = null,
            isEmailVerified = true,
            hasAdminClaim = true
        )
        val adminResult = repository.adminApproveCollectorSubmission(uid, "admin_super_uid")
        assertTrue("Admin approval must succeed", adminResult.isSuccess)

        // Verify submission record
        val submission = repository.getSubmission(uid).getOrNull()
        assertNotNull(submission)
        assertEquals(CollectorVerificationStatus.APPROVED, submission!!.status)
        assertNotNull(submission.reviewedAtEpochMs)
        assertEquals("admin_super_uid", submission.reviewedByUid)

        // Verify user document
        val userDoc = fakeRemote.serverUserDocuments[uid]
        assertNotNull(userDoc)
        assertEquals("APPROVED", userDoc!!["collectorVerificationStatus"])
        assertEquals("COMPLETED", userDoc["onboardingStatus"])
        assertEquals(true, userDoc["isVerified"])
    }

    @Test
    fun `adminRejectCollectorSubmission requires non-blank rejection reason`() = runBlocking {
        val uid = "collector_test_uid"
        setupAuthenticatedCollector(uid = uid, isEmailVerified = true)

        val input = CollectorSubmissionInput(
            vehicleType = "Pickup Truck",
            vehiclePlateNumber = "T 123 ABC",
            licenseNumber = "DL-12345678",
            serviceZoneId = "zone_kinondoni"
        )
        repository.submitInitialVerification(input)

        fakeRemote.currentUser = RemoteAuthUser(
            uid = "admin_uid_1",
            email = "admin@wastelink.co.tz",
            displayName = "Admin",
            phoneNumber = null,
            isEmailVerified = true,
            hasAdminClaim = true
        )

        // Blank rejection reason fails
        val blankResult = repository.adminRejectCollectorSubmission(uid, "admin_uid_1", "   ")
        assertTrue(blankResult.isFailure)

        // Valid rejection reason succeeds
        val validResult = repository.adminRejectCollectorSubmission(
            uid,
            "admin_uid_1",
            "Vehicle plate photo is unreadable"
        )
        assertTrue(validResult.isSuccess)

        val submission = repository.getSubmission(uid).getOrNull()
        assertNotNull(submission)
        assertEquals(CollectorVerificationStatus.REJECTED, submission!!.status)
        assertEquals("Vehicle plate photo is unreadable", submission.rejectionReason)
    }
}

// =========================================================================
// Test Fakes
// =========================================================================

private class TestAuthRepository : AuthRepository {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    var resolvedUser: User? = null

    override fun getCurrentUser(): User? = resolvedUser
    override fun getCachedOrVerifiedUser(): User? = resolvedUser
    override fun isServerVerifiedSession(): Boolean = resolvedUser != null
    override fun getPendingRegistrationDraft(): RegistrationDraft? = null
    override fun isEmailVerified(): Boolean = true

    override fun applyServerVerifiedUserToSession(user: User) {
        resolvedUser = user
        _sessionState.value = SessionState.Authenticated(user = user, isEmailVerified = true)
    }

    override suspend fun registerResident(fullName: String, email: String, phoneNumber: String, password: String, confirmPassword: String): RegistrationOutcome =
        RegistrationOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun registerCollector(fullName: String, email: String, phoneNumber: String, password: String, confirmPassword: String): RegistrationOutcome =
        RegistrationOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun registerUser(fullName: String, email: String, phoneNumber: String, password: String, confirmPassword: String, role: UserRole): RegistrationOutcome =
        RegistrationOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun retryProfileInitialization(overrideDraft: RegistrationDraft?): ProfileInitializationOutcome =
        ProfileInitializationOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun completeMissingProfile(input: ProfileRecoveryInput): ProfileInitializationOutcome =
        ProfileInitializationOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun login(email: String, password: String): LoginOutcome =
        LoginOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun sendEmailVerification(): EmailVerificationOutcome =
        EmailVerificationOutcome.Sent("test@wastelink.co.tz")
    override suspend fun resendEmailVerification(): EmailVerificationOutcome =
        EmailVerificationOutcome.Sent("test@wastelink.co.tz")
    override suspend fun refreshEmailVerificationStatus(): EmailVerificationOutcome =
        EmailVerificationOutcome.StatusRefreshed(isEmailVerified = true, tokenRefreshed = false)
    override suspend fun sendPasswordReset(email: String): PasswordResetOutcome =
        PasswordResetOutcome.ResetEmailDispatched("test@wastelink.co.tz")
    override suspend fun restoreSession(): SessionState = _sessionState.value
    override suspend fun logout(): LogoutOutcome = LogoutOutcome.Success
    override suspend fun completeResidentOnboarding(defaultZoneId: String?): ProfileUpdateOutcome =
        ProfileUpdateOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun submitCollectorOnboarding(input: CollectorSubmissionInput): CollectorSubmissionOutcome =
        CollectorSubmissionOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun resubmitCollectorOnboarding(input: CollectorSubmissionInput): CollectorSubmissionOutcome =
        CollectorSubmissionOutcome.Failure(AuthError.NetworkFailure())
    override suspend fun getCollectorSubmission(uid: String): Result<CollectorSubmission?> =
        Result.success(null)
    override suspend fun adminApproveCollectorSubmission(collectorUid: String, adminUid: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun adminRejectCollectorSubmission(collectorUid: String, adminUid: String, rejectionReason: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun updateProfile(fullName: String, phoneNumber: String, defaultZoneId: String?, preferredLanguage: String?): ProfileUpdateOutcome =
        ProfileUpdateOutcome.Failure(AuthError.NetworkFailure())

    @Deprecated("Legacy")
    override suspend fun signUp(email: String, password: String, name: String, phone: String): Result<com.waste.link.data.models.AppUser> =
        Result.failure(UnsupportedOperationException())
    @Deprecated("Legacy")
    override suspend fun setRole(uid: String, role: String): Result<com.waste.link.data.models.AppUser> =
        Result.failure(UnsupportedOperationException())
    @Deprecated("Legacy")
    override suspend fun signIn(email: String, password: String): Result<com.waste.link.data.models.AppUser> =
        Result.failure(UnsupportedOperationException())
    @Deprecated("Legacy")
    override suspend fun getUserRole(uid: String): Result<com.waste.link.data.models.AppUser> =
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

private class TestAuthRemoteDataSource : AuthRemoteDataSource {
    var currentUser: RemoteAuthUser? = null
    val serverUserDocuments = mutableMapOf<String, Map<String, Any?>>()
    val serverCollectorSubmissions = mutableMapOf<String, Map<String, Any?>>()
    val serverCollectorHistory = mutableMapOf<String, MutableList<Map<String, Any?>>>()

    override fun getCurrentAuthUser(): RemoteAuthUser? = currentUser

    override fun registerAuthStateListener(listener: (RemoteAuthUser?) -> Unit): AutoCloseable =
        AutoCloseable {}

    override suspend fun createAuthUser(email: String, password: String): RemoteAuthUser =
        throw UnsupportedOperationException()
    override suspend fun signInWithEmailAndPassword(email: String, password: String): RemoteAuthUser =
        throw UnsupportedOperationException()
    override suspend fun reloadCurrentAuthUser(forceTokenRefresh: Boolean): RemoteAuthUser? = currentUser
    override suspend fun refreshIdToken(): RemoteAuthUser? = currentUser
    override suspend fun updateAuthDisplayName(fullName: String) {}
    override suspend fun sendEmailVerification() {}
    override suspend fun sendPasswordResetEmail(email: String) {}
    override fun signOut() { currentUser = null }
    override suspend fun deleteCurrentAuthUser() { currentUser = null }

    override suspend fun fetchUserDocumentServerFirst(uid: String): RemoteDocumentResult {
        val doc = serverUserDocuments[uid]
        return if (doc != null) RemoteDocumentResult.Found(doc, isFromCache = false)
        else RemoteDocumentResult.NotFound(isFromCache = false)
    }

    override suspend fun fetchUserDocumentFromCache(uid: String): RemoteDocumentResult {
        val doc = serverUserDocuments[uid]
        return if (doc != null) RemoteDocumentResult.Found(doc, isFromCache = true)
        else RemoteDocumentResult.NotFound(isFromCache = true)
    }

    override suspend fun writeUserDocument(uid: String, data: Map<String, Any?>) {
        serverUserDocuments[uid] = data.toMap()
    }

    override suspend fun updateUserDocument(uid: String, fields: Map<String, Any?>) {
        val existing = serverUserDocuments[uid]?.toMutableMap() ?: mutableMapOf()
        existing.putAll(fields)
        serverUserDocuments[uid] = existing
    }

    override suspend fun fetchCollectorSubmission(uid: String): RemoteDocumentResult {
        val doc = serverCollectorSubmissions[uid]
        return if (doc != null) RemoteDocumentResult.Found(doc, isFromCache = false)
        else RemoteDocumentResult.NotFound(isFromCache = false)
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
        val list = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        list.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override suspend fun executeCollectorResubmissionBatch(
        uid: String,
        submissionUpdateData: Map<String, Any?>,
        historyData: Map<String, Any?>,
        attemptId: String,
        userUpdateFields: Map<String, Any?>
    ) {
        val existing = serverCollectorSubmissions[uid]?.toMutableMap() ?: mutableMapOf()
        existing.putAll(submissionUpdateData)
        serverCollectorSubmissions[uid] = existing
        val list = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        list.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override suspend fun executeAdminCollectorApprovalBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    ) {
        val existing = serverCollectorSubmissions[uid]?.toMutableMap() ?: mutableMapOf()
        existing.putAll(submissionUpdateData)
        serverCollectorSubmissions[uid] = existing
        val list = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        list.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override suspend fun executeAdminCollectorRejectionBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    ) {
        val existing = serverCollectorSubmissions[uid]?.toMutableMap() ?: mutableMapOf()
        existing.putAll(submissionUpdateData)
        serverCollectorSubmissions[uid] = existing
        val list = serverCollectorHistory.getOrPut(uid) { mutableListOf() }
        list.add(historyData)
        updateUserDocument(uid, userUpdateFields)
    }

    override fun simulateNextProfileWriteFailureOnce(failure: Throwable) {}
}
