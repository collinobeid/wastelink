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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
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
import com.google.firebase.firestore.Source
import org.json.JSONObject
import com.waste.link.data.models.UserMapper
import com.waste.link.data.remote.FirebaseEmulatorConfig
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.AuthRepositoryImpl
import com.waste.link.di.AuthRepositoryEntryPoint
import com.waste.link.domain.model.CollectorSubmissionInput
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.Destinations
import com.waste.link.ui.theme.DarkBackground
import com.waste.link.ui.theme.DarkForeground
import com.waste.link.ui.theme.WasteLinkTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Module 1 Stage 5 Compose UI & Firebase Emulator End-to-End Instrumentation Tests.
 *
 * Exercises:
 * 1. Full Resident Onboarding flow -> Zone selection, address input, permissions, WriteBatch, and persistent route resolution.
 * 2. Collector KYC form -> Input validation, review card, atomic WriteBatch submission (/collector_submissions, /history/attempt_1, /users).
 * 3. Collector Verification Status -> Pending state presentation, refresh mechanism, rejection callout.
 * 4. Resubmission -> Edit & resubmit workflow incrementing attempt to attempt_2 and clearing rejection reason.
 * 5. Administrative Approval -> Unlocks Collector Dashboard.
 *
 * Safety Guarantee:
 * - Fails closed immediately if local Firebase Emulators (8080, 9099) are not reachable. Never touches production Firebase.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AuthStage5OnboardingTest {

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
        const val TAG_RESIDENT_ENTRY = "TEST_DEST_RESIDENT_HOME"
        const val TAG_COLLECTOR_ENTRY = "TEST_DEST_COLLECTOR_DASHBOARD"
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
            "FirebaseEmulatorConfig must be connected to local emulators before Stage 5 tests run",
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
    // 1. Resident Onboarding Workflow
    // =========================================================================

    @Test
    fun test01_residentOnboardingWorkflow_savesZoneAndRoutesToResidentHome() = runBlocking<Unit> {
        // Register a verified resident
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "resident.stage5@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        // Create resident profile with NOT_STARTED onboarding
        val profile = UserMapper.createInitialProfile(
            uid = uid,
            fullName = "Amina Nyerere",
            email = "resident.stage5@wastelink.co.tz",
            phoneNumber = "+255712345678",
            role = UserRole.RESIDENT
        )
        firestore.collection("users").document(uid).set(UserMapper.toFirestoreMap(profile)).await()

        val session = authRepository.restoreSession()
        assertTrue("Session must be Authenticated", session is SessionState.Authenticated)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage5TestNavHost(
                    viewModel = viewModel,
                    initialRoute = Destinations.ONBOARDING
                )
            }
        }

        // Wait for screen to render
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_ROLE_BADGE).assertIsDisplayed()

        // Verify resident role badge text
        composeRule.onNodeWithText("RESIDENT ACCOUNT").assertIsDisplayed()

        // Capture visual artifact: stage5_resident_onboarding.png
        saveDeviceScreenshot("stage5_resident_onboarding.png")

        // Click Finish Button without selecting zone -> should show validation error
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_RES_BTN_FINISH)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Service Zone Required").assertIsDisplayed()

        // Select Unsupported Zone option -> shows warning banner
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_RES_ZONE_DROPDOWN)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Other / Area Not Listed").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Service Area Not Yet Supported").assertIsDisplayed()

        // Select valid zone: Kinondoni Municipality
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_RES_ZONE_DROPDOWN)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Kinondoni Municipality").performClick()
        composeRule.waitForIdle()

        // Input street address
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_RES_INPUT_ADDRESS)
            .performScrollTo()
            .performTextInput("Plot 42, Samora Avenue")

        // Click Finish Button
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_RES_BTN_FINISH)
            .performScrollTo()
            .performClick()

        // Wait for navigation transition to settle
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        // Verify navigated to Resident Home
        composeRule.onNodeWithTag(TAG_RESIDENT_ENTRY).assertIsDisplayed()

        // Verify Firestore profile updated to COMPLETED
        val userDoc = firestore.collection("users").document(uid).get(Source.SERVER).await()
        assertEquals("COMPLETED", userDoc.getString("onboardingStatus"))
        assertEquals("zone_kinondoni", userDoc.getString("defaultZoneId"))
    }

    // =========================================================================
    // 2. Collector Onboarding KYC Submission & Status Presentation
    // =========================================================================

    @Test
    fun test02_collectorKycSubmission_executesAtomicBatchAndDisplaysStatus() = runBlocking<Unit> {
        // Register a verified collector
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "collector.stage5@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        // Create collector profile with NOT_SUBMITTED verification
        val profile = UserMapper.createInitialProfile(
            uid = uid,
            fullName = "Juma Rashid",
            email = "collector.stage5@wastelink.co.tz",
            phoneNumber = "+255755123456",
            role = UserRole.COLLECTOR
        )
        firestore.collection("users").document(uid).set(UserMapper.toFirestoreMap(profile)).await()

        val session = authRepository.restoreSession()
        assertTrue("Session must be Authenticated", session is SessionState.Authenticated)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage5TestNavHost(
                    viewModel = viewModel,
                    initialRoute = Destinations.ONBOARDING
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("WASTE COLLECTOR ACCOUNT").assertIsDisplayed()

        // Select Vehicle Type: Compactor Truck
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_VEHICLE_DROPDOWN)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Compactor Truck").performClick()
        composeRule.waitForIdle()

        // Select Service Zone: Kinondoni Municipality
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_ZONE_DROPDOWN)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Kinondoni Municipality").performClick()
        composeRule.waitForIdle()

        // Enter plate number
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_INPUT_PLATE)
            .performScrollTo()
            .performTextInput("T 123 ABC")

        // Enter license number
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_INPUT_LICENSE)
            .performScrollTo()
            .performTextInput("DL-987654321")

        // Capture visual artifact: stage5_collector_onboarding.png
        saveDeviceScreenshot("stage5_collector_onboarding.png")

        // Click Review Application
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_BTN_REVIEW)
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()

        // Capture visual artifact: stage5_collector_review_step.png
        saveDeviceScreenshot("stage5_collector_review_step.png")

        // Click Submit Application
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_BTN_SUBMIT)
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()
        Thread.sleep(1_500L)
        composeRule.waitForIdle()

        // Verify navigated to Collector Verification Status Screen
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("STATUS: UNDER REVIEW").assertIsDisplayed()

        // Capture visual artifact: stage5_collector_status_pending.png
        saveDeviceScreenshot("stage5_collector_status_pending.png")

        // Verify Firestore documents created atomically:
        // 1. /collector_submissions/{uid}
        val subDoc = firestore.collection("collector_submissions").document(uid).get(Source.SERVER).await()
        assertTrue("collector_submission document must exist", subDoc.exists())
        assertEquals("PENDING_REVIEW", subDoc.getString("status"))
        assertEquals("Compactor Truck", subDoc.getString("vehicleType"))
        assertEquals("T 123 ABC", subDoc.getString("vehiclePlateNumber"))
        assertEquals("DL-987654321", subDoc.getString("licenseNumber"))
        assertEquals(1L, subDoc.getLong("submissionAttempt"))

        // 2. /collector_submissions/{uid}/history/attempt_1
        val histDoc = firestore.collection("collector_submissions")
            .document(uid)
            .collection("history")
            .document("attempt_1")
            .get(Source.SERVER)
            .await()
        assertTrue("attempt_1 history document must exist", histDoc.exists())
        assertEquals("SUBMITTED", histDoc.getString("eventType"))

        // 3. /users/{uid} updated
        val userDoc = firestore.collection("users").document(uid).get(Source.SERVER).await()
        assertEquals("PENDING_REVIEW", userDoc.getString("collectorVerificationStatus"))
        assertEquals("COMPLETED", userDoc.getString("onboardingStatus"))
        assertEquals("T 123 ABC", userDoc.getString("vehiclePlateNumber"))
    }

    // =========================================================================
    // 3. Collector Rejection & Resubmission Workflow
    // =========================================================================

    @Test
    fun test03_collectorRejectionAndResubmissionWorkflow() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "collector.resubmit@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val epochNow = System.currentTimeMillis()

        val rejectionReason = "Commercial insurance certificate expired or license plate mismatch."
        val adminUid = "admin_supervisor_1"

        // Seed user profile with REJECTED verification
        val userProfile = UserMapper.toFirestoreMap(
            UserMapper.createInitialProfile(
                uid = uid,
                fullName = "Bakari Juma",
                email = "collector.resubmit@wastelink.co.tz",
                phoneNumber = "+255755999888",
                role = UserRole.COLLECTOR,
                nowEpochMs = epochNow
            ).copy(
                onboardingStatus = OnboardingStatus.COMPLETED,
                collectorVerificationStatus = CollectorVerificationStatus.REJECTED,
                collectorRejectionReason = rejectionReason,
                defaultZoneId = "zone_kinondoni",
                vehicleType = "Flatbed Truck",
                vehiclePlateNumber = "T 999 OLD"
            )
        )
        seedFirestoreDocWithAdminBypass("users", uid, userProfile)

        // Seed collector submission with REJECTED status and review metadata
        val initialSub = mapOf(
            "userId" to uid,
            "vehicleType" to "Flatbed Truck",
            "vehiclePlateNumber" to "T 999 OLD",
            "licenseNumber" to "DL-11112222",
            "serviceZoneId" to "zone_kinondoni",
            "status" to "REJECTED",
            "submissionAttempt" to 1L,
            "submittedAtEpochMs" to epochNow,
            "reviewedByUid" to adminUid,
            "reviewedAtEpochMs" to epochNow,
            "rejectionReason" to rejectionReason,
            "adminNotes" to null,
            "lastRejectionReason" to null,
            "lastReviewedByUid" to null,
            "lastReviewedAtEpochMs" to null
        )
        seedFirestoreDocWithAdminBypass("collector_submissions", uid, initialSub)

        val session = authRepository.restoreSession()
        assertTrue("Session must be Authenticated", session is SessionState.Authenticated)

        val viewModel = AuthViewModel(authRepository)
        viewModel.refreshCollectorVerificationStatus()

        composeRule.setContent {
            WasteLinkTheme {
                Stage5TestNavHost(
                    viewModel = viewModel,
                    initialRoute = Destinations.COLLECTOR_VERIFICATION_STATUS
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("STATUS: REJECTED").assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_REJECTION_REASON).assertIsDisplayed()

        // Capture visual artifact: stage5_collector_status_rejected.png
        saveDeviceScreenshot("stage5_collector_status_rejected.png")

        // Click Edit & Resubmit Application
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_BTN_RESUBMIT)
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.ONBOARDING_SCREEN).assertIsDisplayed()

        // Update plate number with valid replacement
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_INPUT_PLATE)
            .performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_INPUT_PLATE)
            .performTextInput("T 888 NEW")

        // Click Review
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_BTN_REVIEW)
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()

        // Click Resubmit Application
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_BTN_SUBMIT)
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()
        Thread.sleep(1_500L)
        composeRule.waitForIdle()

        // Verify returned to status screen in PENDING_REVIEW state
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("STATUS: UNDER REVIEW").assertIsDisplayed()

        // Verify Firestore submission updated to attempt 2
        val updatedSub = firestore.collection("collector_submissions").document(uid).get(Source.SERVER).await()
        assertEquals("PENDING_REVIEW", updatedSub.getString("status"))
        assertEquals(2L, updatedSub.getLong("submissionAttempt"))
        assertEquals("T 888 NEW", updatedSub.getString("vehiclePlateNumber"))
        assertEquals(rejectionReason, updatedSub.getString("lastRejectionReason"))
        assertNull(updatedSub.getString("rejectionReason"))

        // Verify history/attempt_2 created
        val hist2 = firestore.collection("collector_submissions")
            .document(uid)
            .collection("history")
            .document("attempt_2")
            .get(Source.SERVER)
            .await()
        assertTrue("attempt_2 history must exist", hist2.exists())
        assertEquals("RESUBMITTED", hist2.getString("eventType"))

        // Verify user profile collectorRejectionReason was cleared
        val updatedUser = firestore.collection("users").document(uid).get(Source.SERVER).await()
        assertEquals("PENDING_REVIEW", updatedUser.getString("collectorVerificationStatus"))
        assertNull(updatedUser.getString("collectorRejectionReason"))
    }

    // =========================================================================
    // 4. Collector Approval & Dashboard Unlock
    // =========================================================================

    @Test
    fun test04_collectorApproval_unlocksDashboard() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "collector.approved@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val epochNow = System.currentTimeMillis()

        val adminUid = "admin_supervisor_1"

        // Seed user profile with APPROVED verification
        val userProfile = UserMapper.toFirestoreMap(
            UserMapper.createInitialProfile(
                uid = uid,
                fullName = "Hamisi Ali",
                email = "collector.approved@wastelink.co.tz",
                phoneNumber = "+255755777777",
                role = UserRole.COLLECTOR,
                nowEpochMs = epochNow
            ).copy(
                onboardingStatus = OnboardingStatus.COMPLETED,
                collectorVerificationStatus = CollectorVerificationStatus.APPROVED,
                defaultZoneId = "zone_kinondoni",
                vehicleType = "Compactor Truck",
                vehiclePlateNumber = "T 777 OK"
            )
        )
        seedFirestoreDocWithAdminBypass("users", uid, userProfile)

        // Seed approved collector submission
        val subData = mapOf(
            "userId" to uid,
            "vehicleType" to "Compactor Truck",
            "vehiclePlateNumber" to "T 777 OK",
            "licenseNumber" to "DL-77777777",
            "serviceZoneId" to "zone_kinondoni",
            "status" to "APPROVED",
            "submissionAttempt" to 1L,
            "submittedAtEpochMs" to epochNow,
            "reviewedByUid" to adminUid,
            "reviewedAtEpochMs" to epochNow,
            "rejectionReason" to null,
            "adminNotes" to null,
            "lastRejectionReason" to null,
            "lastReviewedByUid" to null,
            "lastReviewedAtEpochMs" to null
        )
        seedFirestoreDocWithAdminBypass("collector_submissions", uid, subData)

        val session = authRepository.restoreSession()
        assertTrue("Session must be Authenticated", session is SessionState.Authenticated)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage5TestNavHost(
                    viewModel = viewModel,
                    initialRoute = Destinations.COLLECTOR_VERIFICATION_STATUS
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("STATUS: APPROVED").assertIsDisplayed()

        // Capture visual artifact: stage5_collector_status_approved.png
        saveDeviceScreenshot("stage5_collector_status_approved.png")

        // Click Open Collector Dashboard
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_BTN_DASHBOARD)
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        // Verify navigated to Collector Dashboard
        composeRule.onNodeWithTag(TAG_COLLECTOR_ENTRY).assertIsDisplayed()
    }

    // =========================================================================
    // 5. Unapproved Collector Dashboard Guarding
    // =========================================================================

    @Test
    fun test05_unapprovedCollectorBlockedFromDashboard() = runBlocking<Unit> {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(
            "collector.pending@wastelink.co.tz",
            "WasteLink2026!"
        ).await()
        val user = authResult.user!!
        val uid = user.uid
        verifyCurrentUserEmail(user)

        val epochNow = System.currentTimeMillis()

        // Seed user profile with PENDING_REVIEW verification
        val userProfile = UserMapper.toFirestoreMap(
            UserMapper.createInitialProfile(
                uid = uid,
                fullName = "Pending Collector",
                email = "collector.pending@wastelink.co.tz",
                phoneNumber = "+255755111222",
                role = UserRole.COLLECTOR,
                nowEpochMs = epochNow
            ).copy(
                onboardingStatus = OnboardingStatus.COMPLETED,
                collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
                defaultZoneId = "zone_kinondoni",
                vehicleType = "Pickup Truck",
                vehiclePlateNumber = "T 111 PND"
            )
        )
        seedFirestoreDocWithAdminBypass("users", uid, userProfile)

        val session = authRepository.restoreSession()
        assertTrue("Session must be Authenticated", session is SessionState.Authenticated)

        val viewModel = AuthViewModel(authRepository)

        composeRule.setContent {
            WasteLinkTheme {
                Stage5TestNavHost(
                    viewModel = viewModel,
                    initialRoute = Destinations.COLLECTOR_DASHBOARD
                )
            }
        }

        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()

        // Navigation guard redirects unapproved collector to status screen
        composeRule.onNodeWithTag(AuthTestTags.COL_STATUS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("STATUS: UNDER REVIEW").assertIsDisplayed()
    }

    // =========================================================================
    // Test NavHost
    // =========================================================================

    @Composable
    private fun Stage5TestNavHost(
        viewModel: AuthViewModel,
        initialRoute: String
    ) {
        val navController = rememberNavController()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(navController, viewModel) {
            viewModel.navigationEvents.collect { event ->
                when (event) {
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
                    is AuthNavigationEvent.NavigateToDestination -> {
                        navController.navigate(event.route) {
                            if (event.clearBackStack) {
                                popUpTo(0) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    }
                    else -> {}
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.background(DarkBackground)
        ) {
            composable(Destinations.ONBOARDING) {
                OnboardingScreen(
                    uiState = uiState,
                    onResidentZoneChanged = viewModel::onResidentZoneChanged,
                    onResidentAddressChanged = viewModel::onResidentAddressChanged,
                    onCompleteResidentOnboarding = { viewModel.completeResidentOnboarding() },
                    onCollectorVehicleTypeChanged = viewModel::onCollectorVehicleTypeChanged,
                    onCollectorPlateNumberChanged = viewModel::onCollectorPlateNumberChanged,
                    onCollectorLicenseNumberChanged = viewModel::onCollectorLicenseNumberChanged,
                    onCollectorServiceZoneChanged = viewModel::onCollectorServiceZoneChanged,
                    onCollectorReviewToggled = viewModel::onCollectorReviewToggled,
                    onSubmitCollectorOnboarding = viewModel::submitCollectorOnboarding,
                    onResubmitCollectorOnboarding = viewModel::resubmitCollectorOnboarding,
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground)
                        .testTag(TAG_RESIDENT_ENTRY),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Resident Home Entry", color = DarkForeground)
                }
            }

            composable(Destinations.COLLECTOR_DASHBOARD) {
                val user = uiState.currentUser
                if (user != null && user.role == UserRole.COLLECTOR &&
                    user.collectorVerificationStatus != CollectorVerificationStatus.APPROVED
                ) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Destinations.COLLECTOR_VERIFICATION_STATUS) {
                            popUpTo(Destinations.COLLECTOR_DASHBOARD) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground)
                            .testTag(TAG_COLLECTOR_ENTRY),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Collector Dashboard Entry", color = DarkForeground)
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
            val downloadDir = File("/sdcard/Download/stage5_screenshots")
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
        error("Local Firebase Auth Emulator is not reachable on 10.0.2.2:9099 or 127.0.0.1:9099.")
    }

    private fun clearAuthEmulatorAccounts() {
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_AUTH_PORT}/emulator/v1/projects/$projectId/accounts"
        )
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
}
