package com.waste.link.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.remote.FirebaseEmulatorConfig
import com.waste.link.data.remote.RemoteAuthErrorCode
import com.waste.link.data.remote.RemoteAuthException
import com.waste.link.di.AuthRepositoryEntryPoint
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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android instrumentation integration tests exercising the actual Hilt-provided Kotlin
 * [AuthRepositoryImpl] and [com.waste.link.data.remote.FirebaseAuthDataSource] against
 * the local Firebase Authentication Emulator (`10.0.2.2:9099`) and Firestore Emulator (`10.0.2.2:8080`).
 *
 * Safety Guarantee:
 * - Connects `FirebaseAuth` and `FirebaseFirestore` to the local Emulator Suite BEFORE
 *   requesting `AuthRepositoryImpl` from Hilt, and verifies Emulator HTTP reachability first
 *   so zero calls can ever reach production Firebase.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AuthRepositoryAndroidIntegrationTest {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var remoteDataSource: AuthRemoteDataSource
    private lateinit var authRepository: AuthRepository
    private lateinit var authRepositoryImpl: AuthRepositoryImpl
    private lateinit var projectId: String
    private var emulatorHost: String = FirebaseEmulatorConfig.DEFAULT_ANDROID_EMULATOR_HOST

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            AuthRepositoryEntryPoint::class.java
        )

        // 1. Obtain FirebaseAuth and FirebaseFirestore singletons FIRST and connect to local Emulators
        //    before AuthRepositoryImpl or FirebaseAuthDataSource executes any Firebase operation.
        firebaseAuth = entryPoint.firebaseAuth()
        firestore = entryPoint.firebaseFirestore()

        emulatorHost = resolveReachableEmulatorHost()
        FirebaseEmulatorConfig.connectToEmulators(
            firebaseAuth = firebaseAuth,
            firestore = firestore,
            host = emulatorHost,
            authPort = FirebaseEmulatorConfig.DEFAULT_AUTH_PORT,
            firestorePort = FirebaseEmulatorConfig.DEFAULT_FIRESTORE_PORT
        )
        assertTrue(
            "FirebaseEmulatorConfig must be connected before running integration tests",
            FirebaseEmulatorConfig.isEmulatorConnected()
        )

        projectId = FirebaseApp.getInstance().options.projectId
            ?: error("FirebaseApp projectId must not be null")

        // 2. Obtain the actual Hilt-provided AuthRepositoryImpl and verify singleton binding
        remoteDataSource = entryPoint.authRemoteDataSource()
        authRepository = entryPoint.authRepository()
        authRepositoryImpl = entryPoint.authRepositoryImpl()
        assertSame(
            "Hilt AuthRepository binding must resolve to the singleton AuthRepositoryImpl",
            authRepositoryImpl,
            authRepository
        )

        // 3. Clean session and clear local Auth + Firestore Emulators before each test
        authRepositoryImpl.logout()
        clearAuthEmulatorAccounts()
        clearFirestoreEmulatorDocuments()
    }

    @After
    fun tearDown() = runBlocking {
        if (::authRepositoryImpl.isInitialized) {
            authRepositoryImpl.logout()
        }
    }

    // =========================================================================
    // 1. Resident Registration
    // =========================================================================
    @Test
    fun test01_residentRegistration_createsRealAuthUserAndCanonicalFirestoreDocument() = runBlocking {
        val email = "amina.resident@wastelink.co.tz"
        val outcome = authRepositoryImpl.registerResident(
            fullName = "Amina Nyerere",
            email = email,
            phoneNumber = "0712 345 678",
            password = "ResidentPass123",
            confirmPassword = "ResidentPass123"
        )

        assertTrue("Expected RegistrationOutcome.Success but got $outcome", outcome is RegistrationOutcome.Success)
        val success = outcome as RegistrationOutcome.Success

        val uid = success.user.id
        assertEquals(firebaseAuth.currentUser?.uid, uid)
        assertEquals("Amina Nyerere", success.user.fullName)
        assertEquals(email, success.user.email)
        assertEquals("+255712345678", success.user.phoneNumber)
        assertEquals(UserRole.RESIDENT, success.user.role)
        assertEquals(AccountStatus.ACTIVE, success.user.accountStatus)
        assertEquals(OnboardingStatus.NOT_STARTED, success.user.onboardingStatus)
        assertEquals(CollectorVerificationStatus.NOT_APPLICABLE, success.user.collectorVerificationStatus)
        assertTrue(success.verificationEmailSent)
        assertTrue(authRepositoryImpl.isServerVerifiedSession())

        // Verify actual document in the Firestore Emulator
        val snapshot = firestore.collection("users").document(uid).get(Source.SERVER).await()
        assertTrue("Expected /users/$uid to exist in Firestore Emulator", snapshot.exists())
        assertEquals(uid, snapshot.getString("id"))
        assertEquals(uid, snapshot.getString("uid"))
        assertEquals("Amina Nyerere", snapshot.getString("fullName"))
        assertEquals("Amina Nyerere", snapshot.getString("name"))
        assertEquals(email, snapshot.getString("email"))
        assertEquals("+255712345678", snapshot.getString("phoneNumber"))
        assertEquals("+255712345678", snapshot.getString("phone"))
        assertEquals("resident", snapshot.getString("role"))
        assertEquals("ACTIVE", snapshot.getString("accountStatus"))
        assertEquals("ACTIVE", snapshot.getString("status"))
        assertEquals("NOT_STARTED", snapshot.getString("onboardingStatus"))
        assertEquals("NOT_APPLICABLE", snapshot.getString("collectorVerificationStatus"))
        assertEquals(false, snapshot.getBoolean("isVerified"))
    }

    // =========================================================================
    // 2. Collector Registration
    // =========================================================================
    @Test
    fun test02_collectorRegistration_createsRealAuthUserAndNotSubmittedCollectorDocument() = runBlocking {
        val email = "baraka.collector@wastelink.co.tz"
        val outcome = authRepositoryImpl.registerCollector(
            fullName = "Baraka Mwinyi",
            email = email,
            phoneNumber = "+255754112233",
            password = "CollectorPass123",
            confirmPassword = "CollectorPass123"
        )

        assertTrue("Expected RegistrationOutcome.Success but got $outcome", outcome is RegistrationOutcome.Success)
        val success = outcome as RegistrationOutcome.Success
        val uid = success.user.id

        assertEquals(UserRole.COLLECTOR, success.user.role)
        assertEquals(CollectorVerificationStatus.NOT_SUBMITTED, success.user.collectorVerificationStatus)
        assertFalse(success.user.isCollectorOperational)

        val snapshot = firestore.collection("users").document(uid).get(Source.SERVER).await()
        assertTrue(snapshot.exists())
        assertEquals("collector", snapshot.getString("role"))
        assertEquals("NOT_SUBMITTED", snapshot.getString("collectorVerificationStatus"))
        assertEquals(false, snapshot.getBoolean("isVerified"))
    }

    // =========================================================================
    // 3. Controlled Failure Simulation & Profile Initialization Retry (Same-Session)
    // =========================================================================
    @Test
    fun test03_interruptedProfileInitialization_retainsUidAndRecoversWithoutSecondAuthAccount() = runBlocking {
        val email = "halima.recovery@wastelink.co.tz"

        // Arm the controlled one-shot failure between Firebase Auth creation and Firestore profile creation
        remoteDataSource.simulateNextProfileWriteFailureOnce(
            RemoteAuthException(
                code = RemoteAuthErrorCode.NETWORK_UNAVAILABLE,
                message = "Simulated interruption between Auth creation and Firestore profile write"
            )
        )

        val regOutcome = authRepositoryImpl.registerCollector(
            fullName = "Halima Kassim",
            email = email,
            phoneNumber = "0765432109",
            password = "HalimaPass123",
            confirmPassword = "HalimaPass123"
        )

        assertTrue(
            "Expected PartialSuccessProfilePending, got $regOutcome",
            regOutcome is RegistrationOutcome.PartialSuccessProfilePending
        )
        val partial = regOutcome as RegistrationOutcome.PartialSuccessProfilePending

        // 1. Verify the Firebase user remains valid and authenticated
        val currentFirebaseUser = firebaseAuth.currentUser
        assertNotNull("Firebase user must remain signed in after Firestore failure", currentFirebaseUser)
        val originalUid = currentFirebaseUser!!.uid
        assertEquals(originalUid, partial.uid)

        // 2. Verify /users/{originalUid} does NOT exist yet in Firestore
        val missingSnap = firestore.collection("users").document(originalUid).get(Source.SERVER).await()
        assertFalse("Profile should not exist before retry", missingSnap.exists())
        assertTrue(authRepositoryImpl.sessionState.value is SessionState.AuthenticatedProfileIncomplete)

        // 3. Retry profile initialization
        val retryOutcome = authRepositoryImpl.retryProfileInitialization()
        assertTrue(
            "Expected ProfileInitializationOutcome.Success on retry, got $retryOutcome",
            retryOutcome is ProfileInitializationOutcome.Success
        )
        val initialized = retryOutcome as ProfileInitializationOutcome.Success

        // 4. Verify the original UID is retained and NO second authentication account was created
        assertEquals(originalUid, firebaseAuth.currentUser?.uid)
        assertEquals(originalUid, initialized.user.id)
        assertEquals(1, countAuthEmulatorAccounts())

        // 5. Verify the final profile in Firestore is canonical and accurate
        val createdSnap = firestore.collection("users").document(originalUid).get(Source.SERVER).await()
        assertTrue(createdSnap.exists())
        assertEquals("Halima Kassim", createdSnap.getString("fullName"))
        assertEquals("+255765432109", createdSnap.getString("phoneNumber"))
        assertEquals("collector", createdSnap.getString("role"))
        assertEquals("NOT_SUBMITTED", createdSnap.getString("collectorVerificationStatus"))
        assertNull(authRepositoryImpl.getPendingRegistrationDraft())
        assertTrue(authRepositoryImpl.sessionState.value is SessionState.Authenticated)

        // 6. Verify repeating retryProfileInitialization() is idempotent
        val secondRetry = authRepositoryImpl.retryProfileInitialization(partial.draft)
        assertTrue(secondRetry is ProfileInitializationOutcome.Success)
        assertTrue((secondRetry as ProfileInitializationOutcome.Success).wasAlreadyInitialized)
        assertEquals(1, countAuthEmulatorAccounts())
    }

    // =========================================================================
    // 4. Email Verification & Token Refresh
    // =========================================================================
    @Test
    fun test04_emailVerification_appliesOobCodeRefreshesTokenAndUnlocksVerifiedUpdate() = runBlocking {
        val email = "neema.verify@wastelink.co.tz"
        val regOutcome = authRepositoryImpl.registerResident(
            fullName = "Neema Mushi",
            email = email,
            phoneNumber = "+255712000111",
            password = "VerifyPass123",
            confirmPassword = "VerifyPass123"
        )
        assertTrue(regOutcome is RegistrationOutcome.Success)
        assertFalse(authRepositoryImpl.isEmailVerified())

        // Unverified Resident is blocked from completing onboarding
        val blockedOnboarding = authRepositoryImpl.completeResidentOnboarding("zone_kinondoni")
        assertTrue(blockedOnboarding is ProfileUpdateOutcome.Failure)
        assertTrue((blockedOnboarding as ProfileUpdateOutcome.Failure).error is AuthError.EmailNotVerified)

        // Resend verification email through repository and retrieve OOB code from Auth Emulator
        val resendOutcome = authRepositoryImpl.resendEmailVerification()
        assertEquals(EmailVerificationOutcome.Sent(email), resendOutcome)

        val verifyOobCode = fetchLatestOobCode(email = email, requestType = "VERIFY_EMAIL")
        assertNotNull("Expected VERIFY_EMAIL OOB code from Auth Emulator", verifyOobCode)

        // Apply verification code on FirebaseAuth and refresh repository verification state
        firebaseAuth.applyActionCode(verifyOobCode!!).await()

        val refreshOutcome = authRepositoryImpl.refreshEmailVerificationStatus()
        assertEquals(
            EmailVerificationOutcome.StatusRefreshed(isEmailVerified = true, tokenRefreshed = true),
            refreshOutcome
        )
        assertTrue(authRepositoryImpl.isEmailVerified())
        assertTrue((authRepositoryImpl.sessionState.value as SessionState.Authenticated).isEmailVerified)

        // Now that the ID token has email_verified == true, completeResidentOnboarding succeeds against firestore.rules!
        val onboardOutcome = authRepositoryImpl.completeResidentOnboarding("zone_kinondoni")
        assertTrue("Expected onboarding update to succeed after verification, got $onboardOutcome", onboardOutcome is ProfileUpdateOutcome.Success)
        val updatedSnap = firestore.collection("users")
            .document(firebaseAuth.currentUser!!.uid)
            .get(Source.SERVER)
            .await()
        assertEquals("COMPLETED", updatedSnap.getString("onboardingStatus"))
        assertEquals("zone_kinondoni", updatedSnap.getString("defaultZoneId"))
    }

    // =========================================================================
    // 5. Password Reset
    // =========================================================================
    @Test
    fun test05_passwordReset_dispatchesResetOobCodeAndPreservesEnumerationPrivacy() = runBlocking {
        val email = "reset.account@wastelink.co.tz"
        authRepositoryImpl.registerResident(
            fullName = "Juma Bakari",
            email = email,
            phoneNumber = "+255754998877",
            password = "OldPassword123",
            confirmPassword = "OldPassword123"
        )
        authRepositoryImpl.logout()

        // 1. Registered email dispatches password reset OOB code
        val resetExisting = authRepositoryImpl.sendPasswordReset("  Reset.Account@WasteLink.co.tz ")
        assertEquals(PasswordResetOutcome.ResetEmailDispatched(email), resetExisting)

        val resetOobCode = fetchLatestOobCode(email = email, requestType = "PASSWORD_RESET")
        assertNotNull("Expected PASSWORD_RESET OOB code from Auth Emulator", resetOobCode)

        // 2. Non-existent email returns identical ResetEmailDispatched (enumeration-safe)
        val resetUnknown = authRepositoryImpl.sendPasswordReset("nonexistent@wastelink.co.tz")
        assertEquals(
            PasswordResetOutcome.ResetEmailDispatched("nonexistent@wastelink.co.tz"),
            resetUnknown
        )
    }

    // =========================================================================
    // 6. Duplicate Email Collision
    // =========================================================================
    @Test
    fun test06_duplicateEmail_returnsEmailAlreadyInUseWithoutModifyingExistingProfile() = runBlocking {
        val email = "duplicate@wastelink.co.tz"
        val firstOutcome = authRepositoryImpl.registerResident(
            fullName = "Original Resident",
            email = email,
            phoneNumber = "+255712345678",
            password = "OriginalPass123",
            confirmPassword = "OriginalPass123"
        )
        assertTrue(firstOutcome is RegistrationOutcome.Success)
        val originalUid = (firstOutcome as RegistrationOutcome.Success).user.id
        authRepositoryImpl.logout()

        val collisionOutcome = authRepositoryImpl.registerCollector(
            fullName = "Attempted Overwrite",
            email = "DUPLICATE@wastelink.co.tz",
            phoneNumber = "+255788990011",
            password = "SecondPassword999",
            confirmPassword = "SecondPassword999"
        )

        assertTrue(collisionOutcome is RegistrationOutcome.Failure)
        val error = (collisionOutcome as RegistrationOutcome.Failure).error
        assertTrue("Expected EmailAlreadyInUse but got $error", error is AuthError.EmailAlreadyInUse)
        assertEquals(email, (error as AuthError.EmailAlreadyInUse).email)

        // Verify only 1 account exists and original profile in Firestore is untouched
        assertEquals(1, countAuthEmulatorAccounts())
        assertNull(firebaseAuth.currentUser)

        // Sign in as original user to verify Firestore document was not modified
        val loginOutcome = authRepositoryImpl.login(email, "OriginalPass123")
        assertTrue(loginOutcome is LoginOutcome.Authenticated)
        assertEquals("Original Resident", (loginOutcome as LoginOutcome.Authenticated).user.fullName)
        assertEquals(UserRole.RESIDENT, loginOutcome.user.role)
        assertEquals(originalUid, loginOutcome.user.id)
    }

    // =========================================================================
    // 7. Login & Credential Validation
    // =========================================================================
    @Test
    fun test07_login_resolvesCanonicalProfileAndRejectsWrongPassword() = runBlocking {
        val email = "login.resident@wastelink.co.tz"
        authRepositoryImpl.registerResident(
            fullName = "Salim Ahmed",
            email = email,
            phoneNumber = "+255713998877",
            password = "CorrectPassword123",
            confirmPassword = "CorrectPassword123"
        )
        authRepositoryImpl.logout()

        // 1. Wrong password returns InvalidCredentials
        val wrongPassOutcome = authRepositoryImpl.login(email, "WrongPassword999")
        assertTrue(wrongPassOutcome is LoginOutcome.Failure)
        assertTrue((wrongPassOutcome as LoginOutcome.Failure).error is AuthError.InvalidCredentials)
        assertEquals(SessionState.Unauthenticated, authRepositoryImpl.sessionState.value)

        // 2. Correct password resolves canonical User from Firestore
        val validLogin = authRepositoryImpl.login(email, "CorrectPassword123")
        assertTrue("Expected LoginOutcome.Authenticated, got $validLogin", validLogin is LoginOutcome.Authenticated)
        val authenticated = validLogin as LoginOutcome.Authenticated
        assertEquals("Salim Ahmed", authenticated.user.fullName)
        assertEquals(UserRole.RESIDENT, authenticated.user.role)
        assertTrue(authRepositoryImpl.isServerVerifiedSession())
    }

    // =========================================================================
    // 8. Missing-Profile Recovery (Cross-Session Ghost Account)
    // =========================================================================
    @Test
    fun test08_missingProfileRecovery_detectsGhostAccountOnLoginAndCompletesCanonicalProfile() = runBlocking {
        val email = "ghost.crosssession@wastelink.co.tz"

        // Create a real Firebase Auth account directly on the Auth Emulator WITHOUT a /users/{uid} document
        val rawCred = firebaseAuth.createUserWithEmailAndPassword(email, "GhostPass123").await()
        val ghostUid = rawCred.user!!.uid
        firebaseAuth.signOut()

        // 1. Login via AuthRepositoryImpl detects missing /users/{uid} and returns ProfileIncomplete
        val loginOutcome = authRepositoryImpl.login(email, "GhostPass123")
        assertTrue(
            "Expected LoginOutcome.ProfileIncomplete, got $loginOutcome",
            loginOutcome is LoginOutcome.ProfileIncomplete
        )
        val incomplete = loginOutcome as LoginOutcome.ProfileIncomplete
        assertEquals(ghostUid, incomplete.uid)
        assertEquals(email, incomplete.email)
        assertEquals(ProfileIncompleteReason.DOCUMENT_MISSING, incomplete.reason)
        assertTrue(authRepositoryImpl.sessionState.value is SessionState.AuthenticatedProfileIncomplete)
        assertNotNull("Ghost Account must remain authenticated for recovery", firebaseAuth.currentUser)

        // 2. Complete missing profile via completeMissingProfile
        val recoveryOutcome = authRepositoryImpl.completeMissingProfile(
            ProfileRecoveryInput(
                fullName = "Zainab Omar",
                phoneNumber = "0714556677",
                role = UserRole.COLLECTOR
            )
        )
        assertTrue(
            "Expected ProfileInitializationOutcome.Success, got $recoveryOutcome",
            recoveryOutcome is ProfileInitializationOutcome.Success
        )
        val recovered = (recoveryOutcome as ProfileInitializationOutcome.Success).user
        assertEquals(ghostUid, recovered.id)
        assertEquals("Zainab Omar", recovered.fullName)
        assertEquals("+255714556677", recovered.phoneNumber)
        assertEquals(UserRole.COLLECTOR, recovered.role)
        assertEquals(CollectorVerificationStatus.NOT_SUBMITTED, recovered.collectorVerificationStatus)

        // 3. Verify only 1 Auth account exists and Firestore document is persisted
        assertEquals(1, countAuthEmulatorAccounts())
        val snap = firestore.collection("users").document(ghostUid).get(Source.SERVER).await()
        assertTrue(snap.exists())
        assertEquals("Zainab Omar", snap.getString("fullName"))
        assertEquals("collector", snap.getString("role"))
    }

    // =========================================================================
    // 9. Logout & AuthStateListener Synchronization
    // =========================================================================
    @Test
    fun test09_logout_clearsSessionAndSynchronizesListenerWithoutDeletingFirestoreProfile() = runBlocking {
        val email = "logout.test@wastelink.co.tz"
        val regOutcome = authRepositoryImpl.registerResident(
            fullName = "Logout Tester",
            email = email,
            phoneNumber = "+255712345678",
            password = "LogoutPass123",
            confirmPassword = "LogoutPass123"
        )
        val uid = (regOutcome as RegistrationOutcome.Success).user.id
        assertTrue(authRepositoryImpl.sessionState.value is SessionState.Authenticated)

        val logoutOutcome = authRepositoryImpl.logout()
        assertEquals(LogoutOutcome.Success, logoutOutcome)
        assertNull(firebaseAuth.currentUser)
        assertNull(authRepositoryImpl.getCurrentUser())
        assertNull(authRepositoryImpl.getPendingRegistrationDraft())
        assertEquals(SessionState.Unauthenticated, authRepositoryImpl.sessionState.value)

        // Re-authenticate to verify /users/{uid} was NOT deleted in Firestore
        val relogin = authRepositoryImpl.login(email, "LogoutPass123")
        assertTrue(relogin is LoginOutcome.Authenticated)
        assertEquals(uid, (relogin as LoginOutcome.Authenticated).user.id)
    }

    // =========================================================================
    // 10. Session Restoration
    // =========================================================================
    @Test
    fun test10_sessionRestoration_distinguishesUnauthenticatedValidProfileAndMissingProfile() = runBlocking {
        // Case A: Unauthenticated
        authRepositoryImpl.logout()
        assertEquals(SessionState.Unauthenticated, authRepositoryImpl.restoreSession())

        // Case B: Authenticated with valid server-verified profile
        val email = "restore.valid@wastelink.co.tz"
        val reg = authRepositoryImpl.registerResident(
            fullName = "Restore Resident",
            email = email,
            phoneNumber = "+255712345678",
            password = "RestorePass123",
            confirmPassword = "RestorePass123"
        )
        val validUid = (reg as RegistrationOutcome.Success).user.id

        val restoredValid = authRepositoryImpl.restoreSession()
        assertTrue("Expected SessionState.Authenticated, got $restoredValid", restoredValid is SessionState.Authenticated)
        assertTrue(restoredValid.isServerVerified)
        assertTrue(restoredValid.canPerformProtectedOperations)
        assertEquals(validUid, (restoredValid as SessionState.Authenticated).user.id)

        // Case C: Authenticated with missing profile (Ghost Account)
        authRepositoryImpl.logout()
        val ghostCred = firebaseAuth.createUserWithEmailAndPassword(
            "restore.ghost@wastelink.co.tz",
            "GhostPass123"
        ).await()
        val ghostUid = ghostCred.user!!.uid

        val restoredGhost = authRepositoryImpl.restoreSession()
        assertTrue(
            "Expected SessionState.AuthenticatedProfileIncomplete, got $restoredGhost",
            restoredGhost is SessionState.AuthenticatedProfileIncomplete
        )
        assertEquals(ghostUid, (restoredGhost as SessionState.AuthenticatedProfileIncomplete).uid)
        assertNotNull(firebaseAuth.currentUser)
    }

    // =========================================================================
    // Emulator REST Helpers (Strictly Local Emulator Endpoints)
    // =========================================================================

    private fun resolveReachableEmulatorHost(): String {
        val candidates = listOf(
            FirebaseEmulatorConfig.DEFAULT_ANDROID_EMULATOR_HOST,
            FirebaseEmulatorConfig.DEFAULT_LOCALHOST
        )
        for (host in candidates) {
            try {
                val url = URL("http://$host:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..499) {
                    return host
                }
            } catch (_: Exception) {
                // Try next candidate
            }
        }
        error(
            "Local Firebase Auth Emulator is not reachable on 10.0.2.2:9099 or 127.0.0.1:9099. " +
                "Refusing to run integration tests without an active local Emulator."
        )
    }

    private fun clearAuthEmulatorAccounts() {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/emulator/v1/projects/$projectId/accounts"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 5000
            readTimeout = 5000
        }
        val code = conn.responseCode
        conn.disconnect()
        assertTrue("Failed to clear Auth Emulator accounts (HTTP $code)", code in 200..299)
    }

    private fun clearFirestoreEmulatorDocuments() {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_FIRESTORE_PORT}/emulator/v1/projects/$projectId/databases/(default)/documents"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 5000
            readTimeout = 5000
        }
        val code = conn.responseCode
        conn.disconnect()
        assertTrue("Failed to clear Firestore Emulator documents (HTTP $code)", code in 200..299)
    }

    private fun countAuthEmulatorAccounts(): Int {
        // Query IdentityToolkit accounts via Auth Emulator Admin endpoint (requires Bearer owner)
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/identitytoolkit.googleapis.com/v1/projects/$projectId/accounts:query"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer owner")
            connectTimeout = 5000
            readTimeout = 5000
        }
        conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val userInfoArray = json.optJSONArray("userInfo") ?: return 0
        return userInfoArray.length()
    }

    private fun fetchLatestOobCode(email: String, requestType: String): String? {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/emulator/v1/projects/$projectId/oobCodes"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val array = json.optJSONArray("oobCodes") ?: return null
        for (i in array.length() - 1 downTo 0) {
            val item = array.getJSONObject(i)
            if (item.optString("email").equals(email, ignoreCase = true) &&
                item.optString("requestType") == requestType
            ) {
                return item.optString("oobCode").takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
