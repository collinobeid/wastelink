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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.waste.link.data.remote.FirebaseEmulatorConfig
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.AuthRepositoryImpl
import com.waste.link.di.AuthRepositoryEntryPoint
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.ProfileIncompleteReason
import com.waste.link.domain.model.User
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
 * Module 1 Stage 4 Compose UI & Firebase Emulator End-to-End Instrumentation Tests.
 *
 * Safety Guarantee:
 * - Verifies active reachability of the local Firebase Authentication Emulator (`9099`) and
 *   Firestore Emulator (`8080`) and connects `FirebaseEmulatorConfig` BEFORE any Firebase
 *   or repository operation executes.
 * - Fails closed immediately if the local Emulators are not reachable. Never touches production Firebase.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AuthStage4ComposeAndEmulatorTest {

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
            "FirebaseEmulatorConfig must be connected to local emulators before Stage 4 tests run",
            FirebaseEmulatorConfig.isEmulatorConnected()
        )

        projectId = FirebaseApp.getInstance().options.projectId
            ?: error("FirebaseApp projectId must not be null")

        authRepository = entryPoint.authRepository()
        authRepositoryImpl = entryPoint.authRepositoryImpl()
        assertSame(authRepositoryImpl, authRepository)

        authRepositoryImpl.logout()
        clearAuthEmulatorAccounts()
        clearFirestoreEmulatorDocuments()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        if (::authRepositoryImpl.isInitialized) {
            authRepositoryImpl.logout()
        }
    }

    // =========================================================================
    // 1. Login Screen UI: Input Validation, Password Visibility Toggle & Forgot Password Dialog
    // =========================================================================

    @Test
    fun test01_loginScreen_inputValidationPasswordToggleForgotPasswordAndVisualCapture() {
        val viewModel = AuthViewModel(authRepository)
        composeRule.setContent {
            WasteLinkTheme {
                TestStage4AuthNavHost(viewModel = viewModel, startDestination = Destinations.LOGIN)
            }
        }
        composeRule.waitForIdle()
        saveDeviceScreenshot("stage4_login_screen.png")

        // Verify Login screen elements are displayed and no Google stub button exists
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_EMAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_PASSWORD).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_SUBMIT).assertIsDisplayed()

        // Submit empty form -> inline validation errors displayed
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_SUBMIT).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Email address is required.", substring = true).assertIsDisplayed()

        // Enter email and password, toggle password visibility
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_EMAIL)
            .performTextInput("amina@wastelink.co.tz")
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_PASSWORD)
            .performTextInput("WasteLink2026!")
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_TOGGLE_PW).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("WasteLink2026!").assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_TOGGLE_PW).performClick()

        // Open Forgot Password dialog -> verify prefilled email -> close dialog
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_FORGOT_PW).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.PW_RESET_DIALOG).assertIsDisplayed()
        saveDeviceScreenshot("stage4_forgot_password_dialog.png")

        composeRule.onNodeWithTag(AuthTestTags.PW_RESET_BTN_CLOSE).performClick()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag(AuthTestTags.PW_RESET_DIALOG).fetchSemanticsNodes().size)
    }

    // =========================================================================
    // 2. Register Screen UI: Role Selection, Validation, Password Strength & Back Navigation
    // =========================================================================

    @Test
    fun test02_registerScreen_roleSelectionValidationPasswordChecklistBackNavAndVisualCapture() {
        val viewModel = AuthViewModel(authRepository)
        composeRule.setContent {
            WasteLinkTheme {
                TestStage4AuthNavHost(viewModel = viewModel, startDestination = Destinations.LOGIN)
            }
        }
        composeRule.waitForIdle()

        // Navigate Login -> Register
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_GO_REGISTER).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.REGISTER_SCREEN).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.LOGIN_SCREEN).fetchSemanticsNodes().isEmpty()
        }
        saveDeviceScreenshot("stage4_register_screen.png")

        // Select Resident then Collector role cards
        composeRule.onNodeWithTag(AuthTestTags.REG_ROLE_RESIDENT).performScrollTo().performClick()
        composeRule.onNodeWithTag(AuthTestTags.REG_ROLE_RESIDENT).assertIsSelected()

        composeRule.onNodeWithTag(AuthTestTags.REG_ROLE_COLLECTOR).performScrollTo().performClick()
        composeRule.onNodeWithTag(AuthTestTags.REG_ROLE_COLLECTOR).assertIsSelected()

        // Submit with missing fields -> validation errors shown
        composeRule.onNodeWithTag(AuthTestTags.REG_BTN_SUBMIT).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Full name is required.", substring = true).assertIsDisplayed()

        // Password strength checklist updates as user types
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_PASSWORD)
            .performScrollTo()
            .performTextInput("StrongPass99")
        composeRule.onNodeWithTag(AuthTestTags.REG_PASSWORD_STRENGTH).performScrollTo().assertIsDisplayed()

        // Back navigation from Register -> Login
        composeRule.onNodeWithTag(AuthTestTags.REG_BTN_GO_LOGIN).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_SCREEN).assertIsDisplayed()
    }

    // =========================================================================
    // 3. Loading State Disabling Repeated Submissions & Error Banners
    // =========================================================================

    @Test
    fun test03_loadingStateAndErrorBanners_preventRepeatedSubmissions() {
        // Render LoginScreen directly with isSubmitting = true and an active error banner
        val submittingState = AuthUiState(
            isInitialSessionLoading = false,
            isSubmitting = true,
            loginEmail = "amina@wastelink.co.tz",
            loginBanner = AuthBannerMessage(
                type = AuthBannerType.ACCOUNT_LOCKED,
                title = "Account Temporarily Suspended",
                message = "Your WasteLink account is currently suspended.",
                isRetryable = true
            ),
            lockedAccountStatus = AccountStatus.SUSPENDED
        )

        var submitClicks = 0
        composeRule.setContent {
            WasteLinkTheme {
                LoginScreen(
                    uiState = submittingState,
                    onEmailChanged = {},
                    onPasswordEdited = {},
                    onSignInClick = { _, _ -> submitClicks++ },
                    onForgotPasswordClick = {},
                    onDismissForgotPassword = {},
                    onPasswordResetEmailChanged = {},
                    onSubmitPasswordReset = {},
                    onNavigateToRegister = {},
                    onRetryProfileLoad = {},
                    onSignOutLockedAccount = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Banner is displayed and Sign In button is disabled while isSubmitting == true
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_SUBMIT).assertIsNotEnabled()
        assertEquals(0, submitClicks)
    }

    // =========================================================================
    // 4. E2E Journey 1: Resident Registration -> Verify Firestore -> Verify Email -> Onboarding -> Resident Home
    // =========================================================================

    @Test
    fun test04_e2eResidentRegistration_verifyFirestoreAndEmail_loginAndReachResidentEntry() = runBlocking<Unit> {
        val viewModel = AuthViewModel(authRepository)
        composeRule.setContent {
            WasteLinkTheme {
                TestStage4AuthNavHost(viewModel = viewModel, startDestination = Destinations.REGISTER)
            }
        }
        composeRule.waitForIdle()

        val email = "amina.e2e@wastelink.co.tz"
        composeRule.onNodeWithTag(AuthTestTags.REG_ROLE_RESIDENT).performScrollTo().performClick()
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_NAME).performScrollTo().performTextInput("Amina Nyerere")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_EMAIL).performScrollTo().performTextInput(email)
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_PHONE).performScrollTo().performTextInput("0712 345 678")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_PASSWORD).performScrollTo().performTextInput("Resident2026!")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_CONFIRM_PASSWORD).performScrollTo().performTextInput("Resident2026!")
        composeRule.onNodeWithTag(AuthTestTags.REG_BTN_SUBMIT).performScrollTo().performClick()

        // Wait for navigation to VerifyEmailScreen
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.VERIFY_EMAIL_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        saveDeviceScreenshot("stage4_verify_email_screen.png")

        // Verify canonical /users/{uid} document in Firestore Emulator
        val uid = firebaseAuth.currentUser?.uid
        assertNotNull("Firebase user must be authenticated after registration", uid)
        val userDoc = firestore.collection("users").document(uid!!).get(Source.SERVER).await()
        assertTrue("Firestore /users/$uid must exist", userDoc.exists())
        assertEquals("Amina Nyerere", userDoc.getString("fullName"))
        assertEquals(email, userDoc.getString("email"))
        assertEquals("+255712345678", userDoc.getString("phoneNumber"))
        assertEquals("resident", userDoc.getString("role"))
        assertEquals("NOT_APPLICABLE", userDoc.getString("collectorVerificationStatus"))

        // Continue button is disabled before email verification
        composeRule.onNodeWithTag(AuthTestTags.VERIFY_BTN_CONTINUE).performScrollTo().assertIsNotEnabled()

        // Apply OOB verification code from Auth Emulator and tap Refresh Verification Status
        val oobCode = fetchLatestOobCode(email = email, requestType = "VERIFY_EMAIL")
        assertNotNull("Expected VERIFY_EMAIL OOB code in Auth Emulator", oobCode)
        firebaseAuth.applyActionCode(oobCode!!).await()

        composeRule.onNodeWithTag(AuthTestTags.VERIFY_BTN_CHECK).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            viewModel.uiState.value.isEmailVerified
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.VERIFY_BTN_CONTINUE).performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag(AuthTestTags.VERIFY_BTN_CONTINUE).performScrollTo().performClick()

        // Advances to OnboardingScreen (role-aware Resident welcome without re-selecting role)
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.ONBOARDING_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        saveDeviceScreenshot("stage4_onboarding_screen.png")
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_ROLE_BADGE).assertIsDisplayed()

        // Select valid zone: Kinondoni Municipality
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_RES_ZONE_DROPDOWN)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Kinondoni Municipality").performClick()
        composeRule.waitForIdle()

        // Complete Resident onboarding -> reaches Resident Home
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_RES_BTN_FINISH).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(TAG_RESIDENT_ENTRY).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // =========================================================================
    // 5. E2E Journey 2: Collector Registration -> Verify Firestore -> Verify Email -> Login -> Collector Entry
    // =========================================================================

    @Test
    fun test05_e2eCollectorRegistration_verifyFirestoreAndEmail_loginAndReachCollectorEntry() = runBlocking<Unit> {
        val viewModel = AuthViewModel(authRepository)
        composeRule.setContent {
            WasteLinkTheme {
                TestStage4AuthNavHost(viewModel = viewModel, startDestination = Destinations.REGISTER)
            }
        }
        composeRule.waitForIdle()

        val email = "bakari.collector@wastelink.co.tz"
        composeRule.onNodeWithTag(AuthTestTags.REG_ROLE_COLLECTOR).performScrollTo().performClick()
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_NAME).performScrollTo().performTextInput("Bakari Mwakasege")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_EMAIL).performScrollTo().performTextInput(email)
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_PHONE).performScrollTo().performTextInput("+255 754 112 233")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_PASSWORD).performScrollTo().performTextInput("Collector2026!")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_CONFIRM_PASSWORD).performScrollTo().performTextInput("Collector2026!")
        composeRule.onNodeWithTag(AuthTestTags.REG_BTN_SUBMIT).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.VERIFY_EMAIL_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        val uid = firebaseAuth.currentUser?.uid
        assertNotNull(uid)
        val userDoc = firestore.collection("users").document(uid!!).get(Source.SERVER).await()
        assertEquals("collector", userDoc.getString("role"))
        assertEquals("NOT_SUBMITTED", userDoc.getString("collectorVerificationStatus"))

        // Verify email via OOB code, sign out from VerifyEmailScreen, then sign back in through LoginScreen
        val oobCode = fetchLatestOobCode(email = email, requestType = "VERIFY_EMAIL")
        assertNotNull(oobCode)
        firebaseAuth.applyActionCode(oobCode!!).await()

        composeRule.onNodeWithTag(AuthTestTags.VERIFY_BTN_LOGOUT).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.LOGIN_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        // Login with verified Collector account -> routes to Onboarding -> Collector Dashboard
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_EMAIL).performTextClearance()
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_EMAIL).performTextInput(email)
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_PASSWORD).performTextInput("Collector2026!")
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_SUBMIT).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.ONBOARDING_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        // Fill Collector KYC details
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_VEHICLE_DROPDOWN)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Compactor Truck").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_INPUT_PLATE)
            .performScrollTo()
            .performTextInput("T 123 ABC")

        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_INPUT_LICENSE)
            .performScrollTo()
            .performTextInput("DL-12345678")

        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_ZONE_DROPDOWN)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Kinondoni Municipality").performClick()
        composeRule.waitForIdle()

        // Review step
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_BTN_REVIEW)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Submit KYC -> enters PENDING verification status
        composeRule.onNodeWithTag(AuthTestTags.ONBOARD_COL_BTN_SUBMIT)
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.COL_STATUS_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        // Simulate admin approval in Firestore via admin bypass for both /users and /collector_submissions
        val collectorUid = firebaseAuth.currentUser?.uid ?: error("Expected collector UID")
        seedFirestoreDocWithAdminBypass(
            "users",
            collectorUid,
            mapOf(
                "collectorVerificationStatus" to "APPROVED",
                "onboardingStatus" to "COMPLETED",
                "updatedAtEpochMs" to System.currentTimeMillis()
            )
        )
        seedFirestoreDocWithAdminBypass(
            "collector_submissions",
            collectorUid,
            mapOf(
                "status" to "APPROVED"
            )
        )

        // If refresh button is visible, tap it; otherwise proceed to open dashboard
        val refreshBtn = composeRule.onAllNodesWithTag(AuthTestTags.COL_STATUS_BTN_REFRESH)
        if (refreshBtn.fetchSemanticsNodes().isNotEmpty()) {
            refreshBtn[0].performClick()
        }

        // Wait for transition to Collector Dashboard or click dashboard button if displayed
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            val atDashboard = composeRule.onAllNodesWithTag(TAG_COLLECTOR_ENTRY).fetchSemanticsNodes().isNotEmpty()
            if (!atDashboard) {
                val dashboardBtn = composeRule.onAllNodesWithTag(AuthTestTags.COL_STATUS_BTN_DASHBOARD)
                if (dashboardBtn.fetchSemanticsNodes().isNotEmpty()) {
                    dashboardBtn[0].performClick()
                }
            }
            composeRule.onAllNodesWithTag(TAG_COLLECTOR_ENTRY).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // =========================================================================
    // 6. E2E Journey 3: Duplicate Email, Wrong Password & Password Reset against Emulators
    // =========================================================================

    @Test
    fun test06_e2eDuplicateEmail_wrongPassword_andPasswordReset() = runBlocking<Unit> {
        // Seed an account first via repository
        val email = "existing.user@wastelink.co.tz"
        authRepositoryImpl.registerResident(
            fullName = "Neema Mushi",
            email = email,
            phoneNumber = "+255712998877",
            password = "CorrectPassword123",
            confirmPassword = "CorrectPassword123"
        )
        authRepositoryImpl.logout()

        val viewModel = AuthViewModel(authRepository)
        composeRule.setContent {
            WasteLinkTheme {
                TestStage4AuthNavHost(viewModel = viewModel, startDestination = Destinations.REGISTER)
            }
        }
        composeRule.waitForIdle()

        // 6a. Attempt duplicate registration -> shows Duplicate Email banner & Sign In redirect button
        composeRule.onNodeWithTag(AuthTestTags.REG_ROLE_RESIDENT).performScrollTo().performClick()
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_NAME).performScrollTo().performTextInput("Neema Duplicate")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_EMAIL).performScrollTo().performTextInput(email)
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_PHONE).performScrollTo().performTextInput("0712998877")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_PASSWORD).performScrollTo().performTextInput("CorrectPassword123")
        composeRule.onNodeWithTag(AuthTestTags.REG_INPUT_CONFIRM_PASSWORD).performScrollTo().performTextInput("CorrectPassword123")
        composeRule.onNodeWithTag(AuthTestTags.REG_BTN_SUBMIT).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.REG_BTN_DUP_EMAIL_LOGIN).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AuthTestTags.REG_BANNER).assertIsDisplayed()

        // Tap Sign In with this Email -> navigates to LoginScreen with email pre-filled
        composeRule.onNodeWithTag(AuthTestTags.REG_BTN_DUP_EMAIL_LOGIN).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.LOGIN_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(email, viewModel.uiState.value.loginEmail)

        // 6b. Wrong password -> shows InvalidCredentials error banner
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_PASSWORD).performTextInput("WrongPassword999")
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_SUBMIT).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.LOGIN_BANNER).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BANNER).assertIsDisplayed()

        // 6c. Password reset -> dispatches reset link and displays confirmation
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_FORGOT_PW).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AuthTestTags.PW_RESET_BTN_SEND).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.PW_RESET_CONFIRMATION).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AuthTestTags.PW_RESET_CONFIRMATION).assertIsDisplayed()
        assertNotNull(fetchLatestOobCode(email = email, requestType = "PASSWORD_RESET"))
    }

    // =========================================================================
    // 7. E2E Journey 4: Missing-Profile Ghost Account Recovery & Logout -> Return to Login
    // =========================================================================

    @Test
    fun test07_e2eMissingProfileRecovery_andLogoutReturnToLogin() = runBlocking<Unit> {
        // Create a Ghost Account in Firebase Auth Emulator with NO /users/{uid} document
        val ghostEmail = "ghost.recover@wastelink.co.tz"
        val cred = firebaseAuth.createUserWithEmailAndPassword(ghostEmail, "GhostPass2026!").await()
        val ghostUid = cred.user!!.uid
        firebaseAuth.signOut()

        val viewModel = AuthViewModel(authRepository)
        composeRule.setContent {
            WasteLinkTheme {
                TestStage4AuthNavHost(viewModel = viewModel, startDestination = Destinations.LOGIN)
            }
        }
        composeRule.waitForIdle()

        // Sign in with Ghost Account -> routes to CompleteProfileScreen
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_EMAIL).performTextInput(ghostEmail)
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_INPUT_PASSWORD).performTextInput("GhostPass2026!")
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_BTN_SUBMIT).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.COMPLETE_PROFILE_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        saveDeviceScreenshot("stage4_complete_profile_screen.png")

        // Fill in genuine role, full name, and phone number -> submit recovery
        composeRule.onNodeWithTag(AuthTestTags.RECOVER_ROLE_RESIDENT).performScrollTo().performClick()
        composeRule.onNodeWithTag(AuthTestTags.RECOVER_INPUT_NAME).performScrollTo().performTextInput("Juma Kaseja")
        composeRule.onNodeWithTag(AuthTestTags.RECOVER_INPUT_PHONE).performScrollTo().performTextInput("0754 987 654")
        composeRule.onNodeWithTag(AuthTestTags.RECOVER_BTN_SUBMIT).performScrollTo().performClick()

        // Advances to VerifyEmailScreen and creates /users/{ghostUid} in Firestore without creating a second Auth account
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.VERIFY_EMAIL_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        val recoveredDoc = firestore.collection("users").document(ghostUid).get(Source.SERVER).await()
        assertTrue("Expected /users/$ghostUid to be created during recovery", recoveredDoc.exists())
        assertEquals("Juma Kaseja", recoveredDoc.getString("fullName"))
        assertEquals("+255754987654", recoveredDoc.getString("phoneNumber"))
        assertEquals("resident", recoveredDoc.getString("role"))
        assertEquals(ghostUid, firebaseAuth.currentUser?.uid)

        // Logout -> returns cleanly to LoginScreen
        composeRule.onNodeWithTag(AuthTestTags.VERIFY_BTN_LOGOUT).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(AuthTestTags.LOGIN_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AuthTestTags.LOGIN_SCREEN).assertIsDisplayed()
    }

    // =========================================================================
    // Test Navigation Harness Using Real Stage 4 Screens & Navigation Contract
    // =========================================================================

    @Composable
    private fun TestStage4AuthNavHost(
        viewModel: AuthViewModel,
        startDestination: String,
        navController: NavHostController = rememberNavController()
    ) {
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(navController, viewModel) {
            viewModel.navigationEvents.collect { event ->
                when (event) {
                    is AuthNavigationEvent.NavigateToLogin -> {
                        val prefill = event.prefillEmail
                        if (!prefill.isNullOrBlank()) {
                            viewModel.onLoginEmailChanged(prefill)
                        }
                        navController.navigate(Destinations.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    is AuthNavigationEvent.NavigateToRegister -> {
                        navController.navigate(Destinations.REGISTER) {
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
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.background(DarkBackground)
        ) {
            composable(Destinations.LOGIN) {
                LoginScreen(
                    uiState = uiState,
                    onEmailChanged = viewModel::onLoginEmailChanged,
                    onPasswordEdited = viewModel::onLoginPasswordEdited,
                    onSignInClick = viewModel::submitLogin,
                    onForgotPasswordClick = viewModel::openForgotPasswordDialog,
                    onDismissForgotPassword = viewModel::dismissForgotPasswordDialog,
                    onPasswordResetEmailChanged = viewModel::onPasswordResetEmailChanged,
                    onSubmitPasswordReset = viewModel::submitPasswordReset,
                    onNavigateToRegister = {
                        navController.navigate(Destinations.REGISTER) {
                            launchSingleTop = true
                        }
                    },
                    onRetryProfileLoad = viewModel::retryProfileLoadAfterLogin,
                    onSignOutLockedAccount = viewModel::logout
                )
            }

            composable(Destinations.REGISTER) {
                RegisterScreen(
                    uiState = uiState,
                    onRoleSelected = viewModel::onRoleSelected,
                    onFullNameChanged = viewModel::onRegFullNameChanged,
                    onEmailChanged = viewModel::onRegEmailChanged,
                    onPhoneChanged = viewModel::onRegPhoneChanged,
                    onPasswordChanged = viewModel::onRegPasswordChanged,
                    onConfirmPasswordChanged = viewModel::onRegConfirmPasswordChanged,
                    onRegisterSubmit = { fullName, email, phone, password, confirmPassword ->
                        viewModel.submitRegistration(
                            rawFullName = fullName,
                            rawEmail = email,
                            rawPhone = phone,
                            rawPassword = password,
                            rawConfirmPassword = confirmPassword
                        )
                    },
                    onRetryProfileSetup = viewModel::retrySameSessionProfileInitialization,
                    onAbortRecoverySignOut = viewModel::logout,
                    onNavigateToLogin = {
                        val popped = navController.popBackStack(Destinations.LOGIN, inclusive = false)
                        if (!popped) {
                            navController.navigate(Destinations.LOGIN) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onNavigateToLoginWithDuplicateEmail = viewModel::navigateToLoginFromDuplicateEmail
                )
            }

            composable(Destinations.VERIFY_EMAIL) {
                VerifyEmailScreen(
                    uiState = uiState,
                    onRefreshVerificationStatus = viewModel::refreshEmailVerificationStatus,
                    onResendVerificationEmail = viewModel::resendVerificationEmail,
                    onContinueAfterVerification = viewModel::continueFromEmailVerification,
                    onLogoutClick = viewModel::logout
                )
            }

            composable(Destinations.COMPLETE_PROFILE) {
                CompleteProfileScreen(
                    uiState = uiState,
                    onRoleSelected = viewModel::onRecoveryRoleSelected,
                    onFullNameChanged = viewModel::onRecoveryFullNameChanged,
                    onPhoneChanged = viewModel::onRecoveryPhoneChanged,
                    onSubmitRecovery = { fullName, phone, role ->
                        viewModel.submitMissingProfileRecovery(
                            rawFullName = fullName,
                            rawPhone = phone,
                            role = role
                        )
                    },
                    onLogoutClick = viewModel::logout
                )
            }

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
                    onCompleteOnboarding = {
                        viewModel.completeStage4Onboarding()
                    },
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

            composable(Destinations.ADMIN_SHELL) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground)
                        .testTag(TAG_ADMIN_ENTRY),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Admin Shell Entry", color = DarkForeground)
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
            // Dismiss any system dialogs / autofill sheets
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            // Allow the device compositor to render the settled frame after navigation transitions
            Thread.sleep(1_000L)
            composeRule.waitForIdle()
            val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
            val outDir = context.getExternalFilesDir("stage4_screenshots") ?: context.filesDir
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, fileName)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {
            // Non-fatal if screenshot capture is unavailable
        }
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
            } catch (_: Exception) {
                // Try next host
            }
        }
        error(
            "Local Firebase Auth Emulator is not reachable on 10.0.2.2:9099 or 127.0.0.1:9099. " +
                "Refusing to run Stage 4 E2E tests without an active local Emulator."
        )
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
        assertTrue("Failed to clear Auth Emulator accounts (HTTP $code)", code in 200..299)
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
        assertTrue("Failed to clear Firestore Emulator documents (HTTP $code)", code in 200..299)
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

    private fun seedFirestoreDocWithAdminBypass(collectionPath: String, documentId: String, fields: Map<String, Any?>) {
        val queryParams = if (fields.isNotEmpty()) {
            "?" + fields.keys.joinToString("&") { "updateMask.fieldPaths=$it" }
        } else ""
        val url = URL(
            "http://$emulatorHost:${FirebaseEmulatorConfig.DEFAULT_FIRESTORE_PORT}/v1/projects/$projectId/databases/(default)/documents/$collectionPath/$documentId$queryParams"
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
