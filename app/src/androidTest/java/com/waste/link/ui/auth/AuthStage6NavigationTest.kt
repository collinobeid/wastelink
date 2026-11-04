package com.waste.link.ui.auth

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.waste.link.data.remote.FirebaseEmulatorConfig
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.AuthRepositoryImpl
import com.waste.link.di.AuthRepositoryEntryPoint
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.Destinations
import com.waste.link.ui.theme.DarkBackground
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.WasteLinkTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Module 1 Stage 6 Compose UI & Firebase Emulator End-to-End Instrumentation Tests.
 *
 * Exercises:
 * 1. Cold launch without session -> routes to Login.
 * 2. Cold launch with completed resident session -> routes to ResidentHome.
 * 3. Cold launch with pending collector session -> routes to CollectorVerificationStatus.
 * 4. Cold launch with approved collector session -> routes to CollectorDashboard.
 * 5. Suspended account -> routes to AccountStatusLockScreen, displays reason & expiry; lifting suspension restores dashboard on refresh.
 * 6. Banned account -> routes to AccountStatusLockScreen with permanent deactivation notice.
 * 7. Admin claim guard -> user with role == ADMIN in Firestore but missing server token admin claim is gated away from AdminShell.
 *
 * Safety Guarantee:
 * - Fails closed immediately if local Firebase Emulators (8080, 9099) are not reachable. Never touches production Firebase.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AuthStage6NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var authRepository: AuthRepository
    private lateinit var authRepositoryImpl: AuthRepositoryImpl
    private lateinit var projectId: String
    private var emulatorHost: String = FirebaseEmulatorConfig.DEFAULT_ANDROID_EMULATOR_HOST

    companion object {
        const val TAG_LOGIN_ENTRY = "TEST_DEST_LOGIN"
        const val TAG_RESIDENT_ENTRY = "TEST_DEST_RESIDENT_HOME"
        const val TAG_COLLECTOR_ENTRY = "TEST_DEST_COLLECTOR_DASHBOARD"
        const val TAG_ADMIN_ENTRY = "TEST_DEST_ADMIN_SHELL"
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            AuthRepositoryEntryPoint::class.java
        )

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
            "FirebaseEmulatorConfig must be connected to local emulators before Stage 6 tests run",
            FirebaseEmulatorConfig.isEmulatorConnected()
        )

        projectId = FirebaseApp.getInstance().options.projectId
            ?: error("FirebaseApp projectId must not be null")
        authRepository = entryPoint.authRepository()
        authRepositoryImpl = entryPoint.authRepositoryImpl()

        clearAuthEmulatorAccounts()
        clearFirestoreEmulatorDocuments()
        firebaseAuth.signOut()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        try {
            firebaseAuth.signOut()
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 1. Cold Launch Without Session
    // =========================================================================

    @Test
    fun test01_coldLaunchWithoutSession_routesToLogin() = runBlocking<Unit> {
        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_LOGIN_ENTRY).assertIsDisplayed()
    }

    // =========================================================================
    // 2. Cold Launch With Completed Resident
    // =========================================================================

    @Test
    fun test02_coldLaunchWithCompletedResident_routesToResidentHome() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "resident.active@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val profileFields: Map<String, Any?> = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Amina Nyerere",
            "name" to "Amina Nyerere",
            "email" to "resident.active@wastelink.co.tz",
            "phoneNumber" to "+255712345678",
            "phone" to "+255712345678",
            "role" to "RESIDENT",
            "accountStatus" to "ACTIVE",
            "status" to "ACTIVE",
            "onboardingStatus" to "COMPLETED",
            "defaultZoneId" to "zone_kinondoni",
            "isVerified" to false,
            "collectorVerificationStatus" to "NOT_APPLICABLE",
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", uid, profileFields)

        val preSession = authRepository.restoreSession()
        assertTrue("Pre-session must be Authenticated, was $preSession", preSession is SessionState.Authenticated)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_RESIDENT_ENTRY).assertIsDisplayed()
    }

    // =========================================================================
    // 3. Cold Launch With Pending Collector
    // =========================================================================

    @Test
    fun test03_coldLaunchWithPendingCollector_routesToCollectorVerificationStatus() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "collector.pending@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val profileFields: Map<String, Any?> = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Juma Rashid",
            "name" to "Juma Rashid",
            "email" to "collector.pending@wastelink.co.tz",
            "phoneNumber" to "+255755123456",
            "phone" to "+255755123456",
            "role" to "COLLECTOR",
            "accountStatus" to "ACTIVE",
            "status" to "ACTIVE",
            "onboardingStatus" to "COMPLETED",
            "collectorVerificationStatus" to "PENDING_REVIEW",
            "vehicleType" to "Compactor Truck",
            "vehiclePlateNumber" to "T 123 ABC",
            "isVerified" to false,
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", uid, profileFields)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuthTestTags.COLLECTOR_STATUS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("STATUS: UNDER REVIEW").assertIsDisplayed()
    }

    // =========================================================================
    // 4. Cold Launch With Approved Collector
    // =========================================================================

    @Test
    fun test04_coldLaunchWithApprovedCollector_routesToCollectorDashboard() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "collector.approved@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val profileFields = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Juma Rashid",
            "name" to "Juma Rashid",
            "email" to "collector.approved@wastelink.co.tz",
            "phoneNumber" to "+255755123456",
            "phone" to "+255755123456",
            "role" to "COLLECTOR",
            "accountStatus" to "ACTIVE",
            "status" to "ACTIVE",
            "onboardingStatus" to "COMPLETED",
            "collectorVerificationStatus" to "APPROVED",
            "vehicleType" to "Compactor Truck",
            "vehiclePlateNumber" to "T 123 ABC",
            "isVerified" to true,
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", uid, profileFields)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_COLLECTOR_ENTRY).assertIsDisplayed()
    }

    // =========================================================================
    // 5. Suspended Account & Reinstatement Flow
    // =========================================================================

    @Test
    fun test05_suspendedAccount_routesToLockScreen_andReinstatementRestoresAccess() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "suspended.user@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val profileFields: Map<String, Any?> = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Hamisi Bakari",
            "name" to "Hamisi Bakari",
            "email" to "suspended.user@wastelink.co.tz",
            "phoneNumber" to "+255788112233",
            "phone" to "+255788112233",
            "role" to "RESIDENT",
            "accountStatus" to "SUSPENDED",
            "status" to "SUSPENDED",
            "statusReason" to "Suspended pending municipal moderation review",
            "suspendedUntilEpochMs" to 1750000000000L,
            "onboardingStatus" to "COMPLETED",
            "defaultZoneId" to "zone_kinondoni",
            "isVerified" to false,
            "collectorVerificationStatus" to "NOT_APPLICABLE",
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", uid, profileFields)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        // 1. Verify AccountStatusLockScreen is rendered
        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_STATUS_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("SUSPENDED").assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_REASON).assertIsDisplayed()
        composeRule.onNodeWithText("Suspended pending municipal moderation review").assertIsDisplayed()

        // Capture artifact: stage6_account_suspended_lock.png
        saveDeviceScreenshot("stage6_account_suspended_lock.png")

        // 2. Admin reinstates the account via bypass update
        val reinstatedFields: MutableMap<String, Any?> = profileFields.toMutableMap().apply {
            put("accountStatus", "ACTIVE")
            put("status", "ACTIVE")
            put("statusReason", null)
            put("suspendedUntilEpochMs", null)
        }
        seedFirestoreDocWithAdminBypass("users", uid, reinstatedFields)

        // 3. Live snapshot automatically restores access or user taps "Check Status" CTA
        try {
            composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_BTN_REFRESH)
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()
            Thread.sleep(1_500L)
            composeRule.waitForIdle()
        } catch (_: Throwable) {
            // Live snapshot listener already reacted and restored RESIDENT_HOME automatically
        }

        // 4. Verify user was automatically navigated to Resident Home upon reinstatement
        composeRule.onNodeWithTag(TAG_RESIDENT_ENTRY).assertIsDisplayed()

        // Capture artifact: stage6_account_reinstated_home.png
        saveDeviceScreenshot("stage6_account_reinstated_home.png")
    }

    // =========================================================================
    // 6. Banned Account Permanent Lock Presentation
    // =========================================================================

    @Test
    fun test06_bannedAccount_routesToLockScreenWithBannedPresentation() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "banned.user@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val profileFields: Map<String, Any?> = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Said Mwamba",
            "name" to "Said Mwamba",
            "email" to "banned.user@wastelink.co.tz",
            "phoneNumber" to "+255766334455",
            "phone" to "+255766334455",
            "role" to "RESIDENT",
            "accountStatus" to "BANNED",
            "status" to "BANNED",
            "statusReason" to "Permanent deactivation due to terms of service violation",
            "suspendedUntilEpochMs" to null,
            "onboardingStatus" to "COMPLETED",
            "defaultZoneId" to "zone_kinondoni",
            "isVerified" to false,
            "collectorVerificationStatus" to "NOT_APPLICABLE",
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", uid, profileFields)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_STATUS_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("BANNED").assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_REASON).assertIsDisplayed()
        composeRule.onNodeWithText("Permanent deactivation due to terms of service violation").assertIsDisplayed()

        // Capture artifact: stage6_account_banned_lock.png
        saveDeviceScreenshot("stage6_account_banned_lock.png")
    }

    // =========================================================================
    // 7. Administrator Custom Claim Gating
    // =========================================================================

    @Test
    fun test07_adminWithoutAdminCustomClaim_isGatedAwayFromAdminShell() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "fake.admin@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        // Firestore document says role == ADMIN, but Firebase Auth custom claim token.admin == true is NOT provisioned
        val profileFields = mapOf(
            "id" to uid,
            "uid" to uid,
            "fullName" to "Fake Admin",
            "name" to "Fake Admin",
            "email" to "fake.admin@wastelink.co.tz",
            "phoneNumber" to "+255799887766",
            "phone" to "+255799887766",
            "role" to "ADMIN",
            "accountStatus" to "ACTIVE",
            "status" to "ACTIVE",
            "onboardingStatus" to "COMPLETED",
            "defaultZoneId" to "zone_kinondoni",
            "isVerified" to false,
            "collectorVerificationStatus" to "NOT_APPLICABLE",
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", uid, profileFields)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        // Security Invariant: The user must NEVER reach Admin Shell
        composeRule.onNodeWithTag(TAG_LOGIN_ENTRY).assertIsDisplayed()
    }

    // =========================================================================
    // 8. Active Session Suspension Live Enforcement & Reinstatement (Two-Account)
    // =========================================================================

    @Test
    fun test08_activeSession_administratorSuspendsResident_liveEnforcementAndReinstatement() = runBlocking<Unit> {
        // Step 1: Device A (Resident) signs up and logs in as ACTIVE resident
        val residentEmail = "resident.session.active@wastelink.co.tz"
        val residentPass = "WasteLink2026!"
        val residentAuthResult = firebaseAuth.createUserWithEmailAndPassword(residentEmail, residentPass).await()
        val residentUser = residentAuthResult.user!!
        val residentUid = residentUser.uid
        verifyCurrentUserEmail(residentUser)

        val residentProfile: Map<String, Any?> = mapOf(
            "id" to residentUid,
            "uid" to residentUid,
            "fullName" to "Amina Resident",
            "name" to "Amina Resident",
            "email" to residentEmail,
            "phoneNumber" to "+255711223344",
            "phone" to "+255711223344",
            "role" to "RESIDENT",
            "accountStatus" to "ACTIVE",
            "status" to "ACTIVE",
            "onboardingStatus" to "COMPLETED",
            "defaultZoneId" to "zone_kinondoni",
            "isVerified" to false,
            "collectorVerificationStatus" to "NOT_APPLICABLE",
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", residentUid, residentProfile)

        val preSession = authRepository.restoreSession()
        assertTrue("Pre-session must be Authenticated, was $preSession", preSession is SessionState.Authenticated)

        // Device A ViewModel & UI
        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage6TestNavHost(viewModel = viewModel)
            }
        }

        viewModel.restoreSessionOnLaunch()
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        // Verify Device A is on RESIDENT_HOME
        composeRule.onNodeWithTag(TAG_RESIDENT_ENTRY).assertIsDisplayed()

        // Verify Device A can perform a valid protected operation (updating profile) while active
        val initialUpdate = authRepository.updateProfile(
            fullName = "Amina Resident Initial",
            phoneNumber = "+255711223344",
            defaultZoneId = "zone_kinondoni",
            preferredLanguage = "en"
        )
        assertTrue(
            "Initial update should succeed while active: $initialUpdate",
            initialUpdate is com.waste.link.domain.model.ProfileUpdateOutcome.Success
        )

        // Step 2: Device B (Administrator) on a separate FirebaseApp instance signs in with authorized admin claim
        val adminAppName = "DeviceB_Admin"
        val adminApp = try {
            FirebaseApp.getInstance(adminAppName)
        } catch (_: Exception) {
            FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, adminAppName)
        }
        val adminAuth = FirebaseAuth.getInstance(adminApp)
        val adminFirestore = FirebaseFirestore.getInstance(adminApp)
        adminAuth.useEmulator(emulatorHost, FirebaseEmulatorConfig.DEFAULT_AUTH_PORT)
        adminFirestore.useEmulator(emulatorHost, FirebaseEmulatorConfig.DEFAULT_FIRESTORE_PORT)

        val adminEmail = "admin.deviceb@wastelink.co.tz"
        val adminPass = "WasteLink2026!"
        val adminAuthResult = adminAuth.createUserWithEmailAndPassword(adminEmail, adminPass).await()
        val adminUser = adminAuthResult.user!!
        val adminUid = adminUser.uid

        // Verify admin email via OOB code
        adminUser.sendEmailVerification().await()
        val adminOob = fetchLatestOobCode(adminEmail, "VERIFY_EMAIL")
            ?: error("Expected admin VERIFY_EMAIL OOB code")
        adminAuth.applyActionCode(adminOob).await()
        adminUser.reload().await()

        // Provision admin custom claim on Auth Emulator & refresh token
        setAdminCustomClaim(adminUid)
        adminUser.getIdToken(true).await()

        val adminProfile: Map<String, Any?> = mapOf(
            "id" to adminUid,
            "uid" to adminUid,
            "fullName" to "Government Moderator",
            "name" to "Government Moderator",
            "email" to adminEmail,
            "phoneNumber" to "+255700000009",
            "phone" to "+255700000009",
            "role" to "ADMIN",
            "accountStatus" to "ACTIVE",
            "status" to "ACTIVE",
            "onboardingStatus" to "COMPLETED",
            "isVerified" to true,
            "collectorVerificationStatus" to "NOT_APPLICABLE",
            "createdAtEpochMs" to 1700000000000L,
            "updatedAtEpochMs" to 1700000000000L
        )
        seedFirestoreDocWithAdminBypass("users", adminUid, adminProfile)

        // Device B executes administrative suspension batch via Firestore client SDK
        val suspendBatch = adminFirestore.batch()
        val residentUserRef = adminFirestore.collection("users").document(residentUid)
        suspendBatch.update(
            residentUserRef,
            mapOf(
                "accountStatus" to "SUSPENDED",
                "status" to "SUSPENDED",
                "statusReason" to "Suspended by municipal moderator for investigation",
                "suspendedUntilEpochMs" to 1750000000000L,
                "updatedAtEpochMs" to System.currentTimeMillis()
            )
        )
        val modRef = adminFirestore.collection("user_moderation").document(residentUid)
        suspendBatch.set(
            modRef,
            mapOf(
                "userId" to residentUid,
                "action" to "SUSPENDED",
                "accountStatus" to "SUSPENDED",
                "reason" to "Suspended by municipal moderator for investigation",
                "suspendedUntilEpochMs" to 1750000000000L,
                "moderatedByUid" to adminUid,
                "moderatedAtEpochMs" to System.currentTimeMillis()
            )
        )
        suspendBatch.commit().await()

        // Step 3: Device A (Resident) — Isolation Verification:
        // Do NOT call restoreSession(), refreshAccountStatus(), or any protected operation.
        // Wait exclusively for the live Firestore snapshot listener to detect suspension and
        // automatically transition the active UI to AccountStatusLockScreen.
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.ACCOUNT_LOCK_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_STATUS_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("SUSPENDED").assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ACCOUNT_LOCK_REASON).assertIsDisplayed()
        composeRule.onNodeWithText("Suspended by municipal moderator for investigation").assertIsDisplayed()

        // Step 4: After lockout is displayed, verify Firestore security rules enforce restriction
        // by attempting a protected write directly using Device A's Firestore client
        val directWriteException = try {
            firestore.collection("users").document(residentUid).update(
                mapOf(
                    "fullName" to "Direct Write While Suspended",
                    "name" to "Direct Write While Suspended",
                    "updatedAtEpochMs" to System.currentTimeMillis()
                )
            ).await()
            null
        } catch (e: Throwable) {
            e
        }
        assertNotNull("Direct Firestore write while suspended must fail", directWriteException)
        val isPermissionDenied = (directWriteException as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            directWriteException?.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
        assertTrue(
            "Expected PERMISSION_DENIED on direct write while suspended, got: $directWriteException",
            isPermissionDenied
        )

        // Subsequent protected operations via repository also fail
        val updateAttempt = authRepository.updateProfile(
            fullName = "Amina Resident Blocked",
            phoneNumber = "+255711223344",
            defaultZoneId = "zone_kinondoni",
            preferredLanguage = "en"
        )
        assertTrue(
            "Subsequent protected operation via repository must also be rejected: $updateAttempt",
            updateAttempt is com.waste.link.domain.model.ProfileUpdateOutcome.Failure
        )

        // Step 5: Device B (Administrator) reinstates the resident account
        val reinstateBatch = adminFirestore.batch()
        reinstateBatch.update(
            residentUserRef,
            mapOf(
                "accountStatus" to "ACTIVE",
                "status" to "ACTIVE",
                "statusReason" to null,
                "suspendedUntilEpochMs" to null,
                "updatedAtEpochMs" to System.currentTimeMillis()
            )
        )
        val modReinstateRef = adminFirestore.collection("user_moderation").document(residentUid)
        reinstateBatch.set(
            modReinstateRef,
            mapOf(
                "userId" to residentUid,
                "action" to "REINSTATED",
                "accountStatus" to "ACTIVE",
                "reason" to "Reinstated following successful review",
                "moderatedByUid" to adminUid,
                "moderatedAtEpochMs" to System.currentTimeMillis()
            )
        )
        reinstateBatch.commit().await()

        // Step 6: Under Reinstatement:
        // Do NOT tap Check Status, do NOT restart, do NOT sign out.
        // Wait via composeRule.waitUntil for live snapshot to restore RESIDENT_HOME automatically.
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(TAG_RESIDENT_ENTRY).fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm the resident has recovered operational access to RESIDENT_HOME without signing out or manual refresh
        composeRule.onNodeWithTag(TAG_RESIDENT_ENTRY).assertIsDisplayed()

        // Confirm protected operations succeed again
        val finalUpdate = authRepository.updateProfile(
            fullName = "Amina Reinstated",
            phoneNumber = "+255711223344",
            defaultZoneId = "zone_kinondoni",
            preferredLanguage = "en"
        )
        assertTrue(
            "Protected operation should succeed after reinstatement: $finalUpdate",
            finalUpdate is com.waste.link.domain.model.ProfileUpdateOutcome.Success
        )

        // Confirm direct write to Firestore succeeds now that account is ACTIVE
        val directWriteSuccess = try {
            firestore.collection("users").document(residentUid).update(
                mapOf(
                    "fullName" to "Amina Direct Reinstated",
                    "name" to "Amina Direct Reinstated",
                    "updatedAtEpochMs" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (_: Throwable) {
            false
        }
        assertTrue("Direct Firestore write must succeed after reinstatement", directWriteSuccess)
    }

    // =========================================================================
    // Test NavHost
    // =========================================================================

    @Composable
    private fun Stage6TestNavHost(
        viewModel: AuthViewModel,
        initialRoute: String = Destinations.CHECK_AUTH
    ) {
        val navController = rememberNavController()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(navController, viewModel) {
            viewModel.navigationEvents.collect { event ->
                when (event) {
                    is AuthNavigationEvent.NavigateToLogin -> {
                        navController.navigate(Destinations.LOGIN) {
                            if (event.clearBackStack) {
                                popUpTo(0) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    }
                    is AuthNavigationEvent.NavigateToRegister -> {
                        navController.navigate(Destinations.REGISTER) {
                            launchSingleTop = true
                        }
                    }
                    is AuthNavigationEvent.NavigateToOnboarding -> {
                        navController.navigate(Destinations.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    is AuthNavigationEvent.NavigateToCollectorVerificationStatus -> {
                        navController.navigate(Destinations.COLLECTOR_VERIFICATION_STATUS) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    is AuthNavigationEvent.NavigateToVerifyEmail -> {
                        navController.navigate(Destinations.VERIFY_EMAIL) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    is AuthNavigationEvent.NavigateToCompleteProfile -> {
                        navController.navigate(Destinations.COMPLETE_PROFILE) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    is AuthNavigationEvent.NavigateToDestination -> {
                        navController.navigate(event.route) {
                            if (event.clearBackStack) {
                                popUpTo(0) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.background(DarkBackground)
        ) {
            composable(Destinations.CHECK_AUTH) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Checking Auth...", color = DarkForeground)
                }
            }

            composable(Destinations.LOGIN) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBackground).testTag(TAG_LOGIN_ENTRY),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Login Entry Screen", color = DarkForeground)
                }
            }

            composable(Destinations.VERIFY_EMAIL) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBackground).testTag("TEST_DEST_VERIFY_EMAIL"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Verify Email Screen", color = DarkForeground)
                }
            }

            composable(Destinations.COMPLETE_PROFILE) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBackground).testTag("TEST_DEST_COMPLETE_PROFILE"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Complete Profile Screen", color = DarkForeground)
                }
            }

            composable(Destinations.ONBOARDING) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBackground).testTag("TEST_DEST_ONBOARDING"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Onboarding Screen", color = DarkForeground)
                }
            }

            composable(Destinations.ACCOUNT_STATUS_LOCK) {
                AccountStatusLockScreen(
                    uiState = uiState,
                    onRefreshStatus = viewModel::refreshAccountStatus,
                    onContactSupport = {},
                    onLogoutClick = viewModel::logout
                )
            }

            composable(Destinations.COLLECTOR_VERIFICATION_STATUS) {
                CollectorVerificationStatusScreen(
                    uiState = uiState,
                    onRefreshStatus = viewModel::refreshCollectorVerificationStatus,
                    onEditAndResubmit = viewModel::startCollectorEditAndResubmit,
                    onNavigateToDashboard = {
                        navController.navigate(Destinations.COLLECTOR_DASHBOARD) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(Destinations.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onLogoutClick = viewModel::logout
                )
            }

            composable(Destinations.RESIDENT_HOME) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBackground).testTag(TAG_RESIDENT_ENTRY),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Resident Home Dashboard", color = DarkForeground)
                }
            }

            composable(Destinations.COLLECTOR_DASHBOARD) {
                Box(
                    modifier = Modifier.fillMaxSize().background(DarkBackground).testTag(TAG_COLLECTOR_ENTRY),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Collector Operations Dashboard", color = DarkForeground)
                }
            }

            composable(Destinations.ADMIN_SHELL) {
                if (!uiState.hasAdminClaim || uiState.currentUser?.role != UserRole.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Destinations.LOGIN) {
                            popUpTo(Destinations.ADMIN_SHELL) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(DarkBackground).testTag(TAG_ADMIN_ENTRY),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Admin Console Shell", color = DarkForeground)
                    }
                }
            }
        }
    }

    // =========================================================================
    // Emulator & Screenshot Helpers
    // =========================================================================

    private fun saveDeviceScreenshot(fileName: String) {
        try {
            composeRule.waitForIdle()
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            Thread.sleep(1_000L)
            composeRule.waitForIdle()
            val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: return
            val downloadDir = File("/sdcard/Download/stage6_screenshots")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val outFile = File(downloadDir, fileName)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {}
    }

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
            } catch (_: Exception) {}
        }
        return FirebaseEmulatorConfig.DEFAULT_ANDROID_EMULATOR_HOST
    }

    private fun clearAuthEmulatorAccounts() {
        val url = URL("http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/emulator/v1/projects/$projectId/accounts")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 15000
            readTimeout = 15000
        }
        val code = conn.responseCode
        conn.disconnect()
        assertTrue("Failed to clear Auth Emulator accounts", code in 200..299)
    }

    private fun clearFirestoreEmulatorDocuments() {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_FIRESTORE_PORT}/emulator/v1/projects/$projectId/databases/(default)/documents"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 15000
            readTimeout = 15000
        }
        val code = conn.responseCode
        conn.disconnect()
        assertTrue("Failed to clear Firestore Emulator documents", code in 200..299)
    }

    private fun seedFirestoreDocWithAdminBypass(collectionPath: String, documentId: String, fields: Map<String, Any?>) {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_FIRESTORE_PORT}/v1/projects/$projectId/databases/(default)/documents/$collectionPath/$documentId"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Authorization", "Bearer owner")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }
        val jsonFields = JSONObject()
        for ((key, value) in fields) {
            val fieldVal = JSONObject()
            when (value) {
                null -> fieldVal.put("nullValue", JSONObject.NULL)
                is String -> fieldVal.put("stringValue", value)
                is Boolean -> fieldVal.put("booleanValue", value)
                is Long -> fieldVal.put("integerValue", value.toString())
                is Int -> fieldVal.put("integerValue", value.toString())
                is Double -> fieldVal.put("doubleValue", value)
                else -> fieldVal.put("stringValue", value.toString())
            }
            jsonFields.put(key, fieldVal)
        }
        val payload = JSONObject().put("fields", jsonFields).toString()
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        assertTrue("Admin bypass write to $collectionPath/$documentId failed with HTTP $code", code in 200..299)
    }

    private fun setAdminCustomClaim(uid: String) {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/identitytoolkit.googleapis.com/v1/projects/$projectId/accounts:update?key=fake-api-key"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer owner")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }
        val payload = JSONObject().apply {
            put("localId", uid)
            put("customAttributes", "{\"admin\":true}")
        }.toString()
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        assertTrue("Setting admin custom claim failed with HTTP $code", code in 200..299)
    }

    private fun fetchLatestOobCode(email: String, requestType: String): String? {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/emulator/v1/projects/$projectId/oobCodes"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
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

    private suspend fun verifyCurrentUserEmail(user: FirebaseUser) {
        user.sendEmailVerification().await()
        val oobCode = fetchLatestOobCode(user.email!!, "VERIFY_EMAIL")
            ?: error("Expected VERIFY_EMAIL OOB code for ${user.email}")
        firebaseAuth.applyActionCode(oobCode).await()
        user.reload().await()
        user.getIdToken(true).await()
    }
}
