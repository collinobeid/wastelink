package com.waste.link.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.CollectorVerificationRepository
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.AuthError
import com.waste.link.domain.model.CollectorSubmission
import com.waste.link.domain.model.CollectorSubmissionInput
import com.waste.link.domain.model.CollectorSubmissionOutcome
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.EmailVerificationOutcome
import com.waste.link.domain.model.FieldValidationFailure
import com.waste.link.domain.model.LoginOutcome
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
import com.waste.link.domain.validation.AuthValidator
import com.waste.link.domain.validation.ValidationResult
import com.waste.link.ui.Destinations
import com.waste.link.ui.navigation.NavigationDestinationDecision
import com.waste.link.ui.navigation.SessionNavigationResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Distinct banner classifications rendered across authentication screens.
 * Never collapses unrelated errors into a single generic failure string.
 */
enum class AuthBannerType {
    ERROR,
    WARNING,
    INFO,
    SUCCESS,
    ACCOUNT_LOCKED,
    DUPLICATE_EMAIL,
    PROFILE_LOAD_FAILED
}

/**
 * Structured, immutable banner model for inline screen feedback.
 */
data class AuthBannerMessage(
    val type: AuthBannerType,
    val title: String,
    val message: String,
    val isRetryable: Boolean = false
)

/**
 * Immutable UI state for the Forgot Password dialog/sheet on `LoginScreen`.
 */
data class PasswordResetUiState(
    val isDialogVisible: Boolean = false,
    val email: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val dispatchedToEmail: String? = null,
    val errorBanner: String? = null,
    val isRetryable: Boolean = false,
    val rateLimitSecondsRemaining: Int = 0
)

/**
 * One-time navigation events emitted by [AuthViewModel].
 * Consumed via a `Channel`-backed `Flow` so recomposition never re-triggers navigation.
 */
sealed interface AuthNavigationEvent {
    data class NavigateToLogin(
        val prefillEmail: String? = null,
        val clearBackStack: Boolean = false
    ) : AuthNavigationEvent

    data object NavigateToRegister : AuthNavigationEvent

    data class NavigateToVerifyEmail(
        val email: String,
        val initialEmailSent: Boolean
    ) : AuthNavigationEvent

    data class NavigateToCompleteProfile(
        val uid: String,
        val email: String,
        val reason: ProfileIncompleteReason
    ) : AuthNavigationEvent

    data class NavigateToOnboarding(
        val role: UserRole
    ) : AuthNavigationEvent

    data object NavigateToCollectorVerificationStatus : AuthNavigationEvent

    data class NavigateToDestination(
        val route: String,
        val clearBackStack: Boolean = true
    ) : AuthNavigationEvent
}

/**
 * Canonical, immutable UI state for all Module 1 authentication and onboarding workflows.
 *
 * Security Invariant:
 * - Raw passwords are NEVER stored in [AuthUiState], [RegistrationDraft], logs, or navigation events.
 */
data class AuthUiState(
    val sessionState: SessionState = SessionState.Unauthenticated,
    val isInitialSessionLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val currentUser: User? = null,
    val isEmailVerified: Boolean = false,
    val hasAdminClaim: Boolean = false,

    // Login Screen State
    val loginEmail: String = "",
    val loginEmailError: String? = null,
    val loginPasswordError: String? = null,
    val loginBanner: AuthBannerMessage? = null,
    val loginRateLimitSeconds: Int = 0,
    val lockedAccountStatus: AccountStatus? = null,
    val lockedStatusReason: String? = null,
    val lockedSuspendedUntilEpochMs: Long? = null,

    // Registration Screen State
    val selectedRole: UserRole? = null,
    val regFullName: String = "",
    val regEmail: String = "",
    val regPhone: String = "",
    val regRoleError: String? = null,
    val regFullNameError: String? = null,
    val regEmailError: String? = null,
    val regPhoneError: String? = null,
    val regPasswordError: String? = null,
    val regConfirmPasswordError: String? = null,
    val regBanner: AuthBannerMessage? = null,
    val regRateLimitSeconds: Int = 0,
    val duplicateEmailDetected: String? = null,
    val isPartialRegistrationPending: Boolean = false,
    val pendingRegistrationUid: String? = null,
    val pendingRegistrationDraft: RegistrationDraft? = null,

    // Email Verification Screen State
    val verificationEmailAddress: String = "",
    val initialVerificationEmailSent: Boolean = true,
    val isRefreshingVerification: Boolean = false,
    val isResendingVerification: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val verificationBanner: AuthBannerMessage? = null,

    // Incomplete Profile Recovery (Ghost Account) State
    val recoveryUid: String = "",
    val recoveryEmail: String = "",
    val recoveryReason: ProfileIncompleteReason = ProfileIncompleteReason.DOCUMENT_MISSING,
    val recoveryRole: UserRole? = null,
    val recoveryFullName: String = "",
    val recoveryPhone: String = "",
    val recoveryRoleError: String? = null,
    val recoveryFullNameError: String? = null,
    val recoveryPhoneError: String? = null,
    val recoveryBanner: AuthBannerMessage? = null,

    // Forgot Password Dialog State
    val passwordReset: PasswordResetUiState = PasswordResetUiState(),

    // Resident Onboarding State
    val residentSelectedZone: String = "",
    val residentZoneError: String? = null,
    val residentAddress: String = "",
    val isCompletingOnboarding: Boolean = false,
    val onboardingError: String? = null,
    val residentOnboardingBanner: AuthBannerMessage? = null,

    // Collector Onboarding & KYC State
    val collectorVehicleType: String = "",
    val collectorVehicleTypeError: String? = null,
    val collectorPlateNumber: String = "",
    val collectorPlateNumberError: String? = null,
    val collectorLicenseNumber: String = "",
    val collectorLicenseNumberError: String? = null,
    val collectorServiceZoneId: String = "",
    val collectorServiceZoneError: String? = null,
    val isCollectorReviewStep: Boolean = false,
    val isSubmittingCollectorOnboarding: Boolean = false,
    val collectorOnboardingBanner: AuthBannerMessage? = null,

    // Collector Verification Status State
    val collectorSubmission: CollectorSubmission? = null,
    val isLoadingCollectorSubmission: Boolean = false,
    val collectorStatusBanner: AuthBannerMessage? = null
) {
    val isAdmin: Boolean
        get() = currentUser?.role == UserRole.ADMIN && hasAdminClaim
}

/**
 * Canonical Authentication ViewModel (Module 1, Stage 4 & 5.1).
 *
 * Connects the presentation layer to the canonical [AuthRepository] and [CollectorVerificationRepository] contracts.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val collectorVerificationRepository: CollectorVerificationRepository
) : ViewModel() {

    constructor(authRepository: AuthRepository) : this(
        authRepository = authRepository,
        collectorVerificationRepository = object : CollectorVerificationRepository {
            override suspend fun submitInitialVerification(input: com.waste.link.domain.model.CollectorSubmissionInput) =
                authRepository.submitCollectorOnboarding(input)
            override suspend fun resubmitVerification(input: com.waste.link.domain.model.CollectorSubmissionInput) =
                authRepository.resubmitCollectorOnboarding(input)
            override suspend fun getSubmission(uid: String) =
                authRepository.getCollectorSubmission(uid)
            override suspend fun getSubmissionHistory(uid: String) =
                Result.success(emptyList<com.waste.link.domain.model.CollectorSubmissionHistoryEntry>())
            override suspend fun adminApproveCollectorSubmission(collectorUid: String, adminUid: String) =
                authRepository.adminApproveCollectorSubmission(collectorUid, adminUid)
            override suspend fun adminRejectCollectorSubmission(collectorUid: String, adminUid: String, rejectionReason: String, adminNotes: String?) =
                authRepository.adminRejectCollectorSubmission(collectorUid, adminUid, rejectionReason)
        }
    )

    companion object {
        const val DEFAULT_COOLDOWN_SECONDS = 60
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<AuthNavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<AuthNavigationEvent> = _navigationEvents.receiveAsFlow()

    private var loginRateLimitJob: Job? = null
    private var regRateLimitJob: Job? = null
    private var resendCooldownJob: Job? = null
    private var resetRateLimitJob: Job? = null

    init {
        observeRepositorySessionState()
    }

    private fun observeRepositorySessionState() {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                _uiState.update { state ->
                    when (session) {
                        is SessionState.Unauthenticated -> state.copy(
                            sessionState = session,
                            currentUser = null,
                            isEmailVerified = false,
                            hasAdminClaim = false,
                            isPartialRegistrationPending = false,
                            pendingRegistrationUid = null,
                            pendingRegistrationDraft = null
                        )
                        is SessionState.Authenticated -> state.copy(
                            sessionState = session,
                            currentUser = session.user,
                            isEmailVerified = session.isEmailVerified,
                            hasAdminClaim = session.hasAdminClaim,
                            verificationEmailAddress = session.user.email,
                            lockedAccountStatus = null,
                            lockedStatusReason = null,
                            lockedSuspendedUntilEpochMs = null,
                            isPartialRegistrationPending = false,
                            pendingRegistrationUid = null,
                            pendingRegistrationDraft = null
                        )
                        is SessionState.AuthenticatedCachedOffline -> state.copy(
                            sessionState = session,
                            currentUser = session.cachedUser,
                            isEmailVerified = session.isEmailVerified,
                            hasAdminClaim = false,
                            verificationEmailAddress = session.cachedUser.email
                        )
                        is SessionState.AuthenticatedRestricted -> state.copy(
                            sessionState = session,
                            currentUser = session.user,
                            isEmailVerified = session.isEmailVerified,
                            hasAdminClaim = false,
                            verificationEmailAddress = session.user.email,
                            lockedAccountStatus = session.accountStatus,
                            lockedStatusReason = session.statusReason,
                            lockedSuspendedUntilEpochMs = session.suspendedUntilEpochMs
                        )
                        is SessionState.AuthenticatedProfileIncomplete -> state.copy(
                            sessionState = session,
                            currentUser = null,
                            isEmailVerified = session.isEmailVerified,
                            hasAdminClaim = false,
                            recoveryUid = session.uid,
                            recoveryEmail = session.email,
                            recoveryReason = session.reason,
                            recoveryRole = session.pendingDraft?.role ?: state.recoveryRole,
                            recoveryFullName = session.pendingDraft?.fullName?.takeIf { it.isNotBlank() }
                                ?: state.recoveryFullName,
                            recoveryPhone = session.pendingDraft?.phoneNumber?.takeIf { it.isNotBlank() }
                                ?: state.recoveryPhone,
                            pendingRegistrationDraft = session.pendingDraft
                        )
                        is SessionState.AuthenticatedProfileLoadError -> state.copy(
                            sessionState = session,
                            currentUser = null,
                            verificationEmailAddress = session.email
                        )
                        is SessionState.AuthenticationRevoked -> state.copy(
                            sessionState = session,
                            currentUser = null,
                            isEmailVerified = false,
                            hasAdminClaim = false,
                            loginBanner = AuthBannerMessage(
                                type = AuthBannerType.WARNING,
                                title = "Session Expired",
                                message = session.error.message,
                                isRetryable = false
                            )
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // 1. Session Restoration & Initial Route Resolution
    // =========================================================================

    private var currentNavigationTarget: NavigationDestinationDecision? = null

    fun restoreSessionOnLaunch() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isInitialSessionLoading = true) }

        viewModelScope.launch {
            val restored = authRepository.restoreSession()
            _uiState.update { it.copy(isInitialSessionLoading = false) }
            val decision = SessionNavigationResolver.resolve(restored)
            navigateFromDecision(decision)
        }
    }

    fun refreshAccountStatus() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val restored = authRepository.restoreSession()
            _uiState.update { it.copy(isSubmitting = false) }
            val decision = SessionNavigationResolver.resolve(restored)
            navigateFromDecision(decision, force = true)
        }
    }

    fun navigateFromDecision(decision: NavigationDestinationDecision, force: Boolean = false) {
        if (!force && decision == currentNavigationTarget) {
            return
        }
        currentNavigationTarget = decision

        when (decision) {
            is NavigationDestinationDecision.Login -> {
                if (decision.loadError != null && decision.prefillEmail != null) {
                    _uiState.update {
                        it.copy(
                            loginEmail = decision.prefillEmail,
                            loginBanner = buildProfileLoadFailedBanner(AuthError.NetworkFailure(decision.loadError))
                        )
                    }
                }
                emitNavigation(
                    AuthNavigationEvent.NavigateToLogin(
                        prefillEmail = decision.prefillEmail,
                        clearBackStack = decision.clearBackStack
                    )
                )
            }
            is NavigationDestinationDecision.VerifyEmail -> {
                _uiState.update {
                    it.copy(
                        verificationEmailAddress = decision.email,
                        initialVerificationEmailSent = decision.initialEmailSent
                    )
                }
                emitNavigation(
                    AuthNavigationEvent.NavigateToVerifyEmail(
                        email = decision.email,
                        initialEmailSent = decision.initialEmailSent
                    )
                )
            }
            is NavigationDestinationDecision.CompleteProfile -> {
                emitNavigation(
                    AuthNavigationEvent.NavigateToCompleteProfile(
                        uid = decision.uid,
                        email = decision.email,
                        reason = decision.reason
                    )
                )
            }
            is NavigationDestinationDecision.ResidentOnboarding -> {
                emitNavigation(AuthNavigationEvent.NavigateToOnboarding(decision.role))
            }
            is NavigationDestinationDecision.CollectorOnboarding -> {
                emitNavigation(AuthNavigationEvent.NavigateToOnboarding(decision.role))
            }
            is NavigationDestinationDecision.CollectorStatus -> {
                emitNavigation(AuthNavigationEvent.NavigateToCollectorVerificationStatus)
            }
            is NavigationDestinationDecision.ResidentHome -> {
                emitNavigation(
                    AuthNavigationEvent.NavigateToDestination(
                        route = Destinations.RESIDENT_HOME,
                        clearBackStack = decision.clearBackStack
                    )
                )
            }
            is NavigationDestinationDecision.CollectorDashboard -> {
                emitNavigation(
                    AuthNavigationEvent.NavigateToDestination(
                        route = Destinations.COLLECTOR_DASHBOARD,
                        clearBackStack = decision.clearBackStack
                    )
                )
            }
            is NavigationDestinationDecision.AdminConsole -> {
                emitNavigation(
                    AuthNavigationEvent.NavigateToDestination(
                        route = Destinations.ADMIN_SHELL,
                        clearBackStack = decision.clearBackStack
                    )
                )
            }
            is NavigationDestinationDecision.AccountStatusLock -> {
                _uiState.update {
                    it.copy(
                        lockedAccountStatus = decision.status,
                        lockedStatusReason = decision.reason,
                        lockedSuspendedUntilEpochMs = decision.suspendedUntilEpochMs
                    )
                }
                emitNavigation(
                    AuthNavigationEvent.NavigateToDestination(
                        route = Destinations.ACCOUNT_STATUS_LOCK,
                        clearBackStack = decision.clearBackStack
                    )
                )
            }
            is NavigationDestinationDecision.SafeRecovery -> {
                emitNavigation(
                    AuthNavigationEvent.NavigateToLogin(
                        clearBackStack = decision.clearBackStack
                    )
                )
            }
        }
    }

    private fun handleRestoredSessionNavigation(session: SessionState) {
        val decision = SessionNavigationResolver.resolve(session)
        navigateFromDecision(decision)
    }

    private fun navigateForAuthenticatedUser(
        user: User,
        isEmailVerified: Boolean,
        hasAdminClaim: Boolean
    ) {
        val session = when (user.accountStatus) {
            AccountStatus.RESTRICTED -> SessionState.AuthenticatedRestricted(
                user = user,
                isEmailVerified = isEmailVerified,
                accountStatus = AccountStatus.RESTRICTED,
                statusReason = user.statusReason,
                suspendedUntilEpochMs = user.suspendedUntilEpochMs
            )
            AccountStatus.SUSPENDED,
            AccountStatus.BANNED -> SessionState.AuthenticatedRestricted(
                user = user,
                isEmailVerified = isEmailVerified,
                accountStatus = user.accountStatus,
                statusReason = user.statusReason,
                suspendedUntilEpochMs = user.suspendedUntilEpochMs
            )
            AccountStatus.ACTIVE -> SessionState.Authenticated(
                user = user,
                isEmailVerified = isEmailVerified,
                hasAdminClaim = hasAdminClaim
            )
        }
        val decision = SessionNavigationResolver.resolve(session)
        navigateFromDecision(decision)
    }

    // =========================================================================
    // 2. Login Workflow
    // =========================================================================

    fun onLoginEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                loginEmail = email,
                loginEmailError = null,
                loginBanner = if (it.loginBanner?.type == AuthBannerType.ACCOUNT_LOCKED) it.loginBanner else null
            )
        }
    }

    fun onLoginPasswordEdited() {
        if (_uiState.value.loginPasswordError != null || _uiState.value.loginBanner != null) {
            _uiState.update {
                it.copy(
                    loginPasswordError = null,
                    loginBanner = if (it.loginBanner?.type == AuthBannerType.ACCOUNT_LOCKED) it.loginBanner else null
                )
            }
        }
    }

    fun submitLogin(rawEmail: String, rawPassword: String) {
        val currentState = _uiState.value
        if (currentState.isSubmitting || currentState.loginRateLimitSeconds > 0) return

        val emailValidation = AuthValidator.validateEmail(rawEmail)
        val passwordValidation = AuthValidator.validateRequired(rawPassword, "Password")

        if (emailValidation.isInvalid || passwordValidation.isInvalid) {
            _uiState.update {
                it.copy(
                    loginEmail = rawEmail,
                    loginEmailError = emailValidation.errorMessageOrNull(),
                    loginPasswordError = passwordValidation.errorMessageOrNull(),
                    loginBanner = null
                )
            }
            return
        }

        val normalizedEmail = emailValidation.getOrNull()!!
        _uiState.update {
            it.copy(
                isSubmitting = true,
                loginEmail = normalizedEmail,
                loginEmailError = null,
                loginPasswordError = null,
                loginBanner = null,
                lockedAccountStatus = null,
                lockedStatusReason = null,
                lockedSuspendedUntilEpochMs = null
            )
        }

        viewModelScope.launch {
            val outcome = authRepository.login(normalizedEmail, rawPassword)
            _uiState.update { it.copy(isSubmitting = false) }
            handleLoginOutcome(outcome, normalizedEmail)
        }
    }

    private fun handleLoginOutcome(outcome: LoginOutcome, attemptedEmail: String) {
        when (outcome) {
            is LoginOutcome.Authenticated -> {
                navigateForAuthenticatedUser(
                    user = outcome.user,
                    isEmailVerified = outcome.isEmailVerified,
                    hasAdminClaim = outcome.hasAdminClaim
                )
            }
            is LoginOutcome.ProfileIncomplete -> {
                _uiState.update {
                    it.copy(
                        recoveryUid = outcome.uid,
                        recoveryEmail = outcome.email.ifBlank { attemptedEmail },
                        recoveryReason = outcome.reason,
                        recoveryRole = outcome.suggestedDraft?.role,
                        recoveryFullName = outcome.suggestedDraft?.fullName.orEmpty(),
                        recoveryPhone = outcome.suggestedDraft?.phoneNumber.orEmpty(),
                        recoveryBanner = null
                    )
                }
                emitNavigation(
                    AuthNavigationEvent.NavigateToCompleteProfile(
                        uid = outcome.uid,
                        email = outcome.email.ifBlank { attemptedEmail },
                        reason = outcome.reason
                    )
                )
            }
            is LoginOutcome.AccountLocked -> {
                if (outcome.accountStatus == AccountStatus.RESTRICTED) {
                    navigateForAuthenticatedUser(
                        user = outcome.user,
                        isEmailVerified = outcome.isEmailVerified,
                        hasAdminClaim = false
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            lockedAccountStatus = outcome.accountStatus,
                            lockedStatusReason = outcome.statusReason,
                            lockedSuspendedUntilEpochMs = outcome.suspendedUntilEpochMs,
                            loginBanner = buildAccountLockedBanner(
                                accountStatus = outcome.accountStatus,
                                statusReason = outcome.statusReason,
                                suspendedUntilEpochMs = outcome.suspendedUntilEpochMs
                            )
                        )
                    }
                }
            }
            is LoginOutcome.ProfileLoadFailed -> {
                _uiState.update {
                    it.copy(
                        loginBanner = buildProfileLoadFailedBanner(outcome.error)
                    )
                }
            }
            is LoginOutcome.Failure -> {
                handleLoginFailure(outcome.error)
            }
        }
    }

    private fun handleLoginFailure(error: AuthError) {
        when (error) {
            is AuthError.ValidationFailed -> {
                val emailErr = error.errors.firstOrNull { it.field == "email" }?.message
                val passwordErr = error.errors.firstOrNull { it.field == "password" }?.message
                _uiState.update {
                    it.copy(
                        loginEmailError = emailErr,
                        loginPasswordError = passwordErr,
                        loginBanner = if (emailErr == null && passwordErr == null) {
                            AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Check Your Input",
                                message = error.message
                            )
                        } else null
                    )
                }
            }
            is AuthError.InvalidCredentials,
            is AuthError.AccountNotFound -> {
                _uiState.update {
                    it.copy(
                        loginBanner = AuthBannerMessage(
                            type = AuthBannerType.ERROR,
                            title = "Incorrect Credentials",
                            message = "The email or password you entered is incorrect. Please check your credentials and try again.",
                            isRetryable = false
                        )
                    )
                }
            }
            is AuthError.UserDisabled -> {
                _uiState.update {
                    it.copy(
                        loginBanner = AuthBannerMessage(
                            type = AuthBannerType.ACCOUNT_LOCKED,
                            title = "Authentication Account Disabled",
                            message = "This account has been disabled by an administrator. Please contact WasteLink support for assistance.",
                            isRetryable = false
                        )
                    )
                }
            }
            is AuthError.RateLimited -> {
                startLoginRateLimitCountdown(error.retryAfterSeconds)
                _uiState.update {
                    it.copy(
                        loginBanner = AuthBannerMessage(
                            type = AuthBannerType.WARNING,
                            title = "Too Many Sign-In Attempts",
                            message = "Sign-in is temporarily paused to protect your account. Please wait ${error.retryAfterSeconds} seconds before trying again.",
                            isRetryable = false
                        )
                    )
                }
            }
            is AuthError.NetworkFailure -> {
                _uiState.update {
                    it.copy(
                        loginBanner = AuthBannerMessage(
                            type = AuthBannerType.ERROR,
                            title = "Connection Unavailable",
                            message = "Unable to reach WasteLink servers. Check your internet connection and tap Sign In to retry.",
                            isRetryable = true
                        )
                    )
                }
            }
            is AuthError.UnauthorizedAdminClaim -> {
                _uiState.update {
                    it.copy(
                        loginBanner = AuthBannerMessage(
                            type = AuthBannerType.ERROR,
                            title = "Administrator Claim Missing",
                            message = error.message,
                            isRetryable = false
                        )
                    )
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        loginBanner = AuthBannerMessage(
                            type = AuthBannerType.ERROR,
                            title = "Sign-In Failed",
                            message = error.message,
                            isRetryable = false
                        )
                    )
                }
            }
        }
    }

    fun retryProfileLoadAfterLogin() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, loginBanner = null) }
        viewModelScope.launch {
            val restored = authRepository.restoreSession()
            _uiState.update { it.copy(isSubmitting = false) }
            handleRestoredSessionNavigation(restored)
        }
    }

    // =========================================================================
    // 3. Registration & Same-Session Recovery Workflow
    // =========================================================================

    fun onRoleSelected(role: UserRole) {
        if (!role.isSelfRegistrable) {
            _uiState.update {
                it.copy(regRoleError = "Selected role cannot be self-registered.")
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedRole = role,
                regRoleError = null,
                regBanner = null
            )
        }
    }

    fun onRegFullNameChanged(fullName: String) {
        val inlineError = if (fullName.isNotBlank()) {
            AuthValidator.validateFullName(fullName).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                regFullName = fullName,
                regFullNameError = inlineError,
                regBanner = null
            )
        }
    }

    fun onRegEmailChanged(email: String) {
        val inlineError = if (email.isNotBlank()) {
            AuthValidator.validateEmail(email).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                regEmail = email,
                regEmailError = inlineError,
                duplicateEmailDetected = null,
                regBanner = null
            )
        }
    }

    fun onRegPhoneChanged(phone: String) {
        val inlineError = if (phone.isNotBlank()) {
            AuthValidator.validateAndNormalizePhone(phone).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                regPhone = phone,
                regPhoneError = inlineError,
                regBanner = null
            )
        }
    }

    fun onRegPasswordChanged(password: String, confirmPassword: String) {
        val passwordErr = if (password.isNotEmpty()) {
            AuthValidator.validatePassword(password).errorMessageOrNull()
        } else null
        val confirmErr = if (confirmPassword.isNotEmpty()) {
            AuthValidator.validatePasswordConfirmation(password, confirmPassword).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                regPasswordError = passwordErr,
                regConfirmPasswordError = confirmErr,
                regBanner = null
            )
        }
    }

    fun onRegConfirmPasswordChanged(password: String, confirmPassword: String) {
        val confirmErr = if (confirmPassword.isNotEmpty()) {
            AuthValidator.validatePasswordConfirmation(password, confirmPassword).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                regConfirmPasswordError = confirmErr,
                regBanner = null
            )
        }
    }

    fun submitRegistration(
        rawFullName: String,
        rawEmail: String,
        rawPhone: String,
        rawPassword: String,
        rawConfirmPassword: String,
        role: UserRole? = _uiState.value.selectedRole
    ) {
        val currentState = _uiState.value
        if (currentState.isSubmitting || currentState.regRateLimitSeconds > 0) return

        // If same-session partial registration is already active, route to retryProfileInitialization instead
        if (currentState.isPartialRegistrationPending) {
            retrySameSessionProfileInitialization()
            return
        }

        val roleValidation = AuthValidator.validateSelfRegistrationRole(role)
        val nameValidation = AuthValidator.validateFullName(rawFullName)
        val emailValidation = AuthValidator.validateEmail(rawEmail)
        val phoneValidation = AuthValidator.validateAndNormalizePhone(rawPhone)
        val passwordValidation = AuthValidator.validatePassword(rawPassword)
        val confirmValidation = AuthValidator.validatePasswordConfirmation(rawPassword, rawConfirmPassword)

        if (roleValidation.isInvalid ||
            nameValidation.isInvalid ||
            emailValidation.isInvalid ||
            phoneValidation.isInvalid ||
            passwordValidation.isInvalid ||
            confirmValidation.isInvalid
        ) {
            _uiState.update {
                it.copy(
                    selectedRole = role,
                    regFullName = rawFullName,
                    regEmail = rawEmail,
                    regPhone = rawPhone,
                    regRoleError = roleValidation.errorMessageOrNull(),
                    regFullNameError = nameValidation.errorMessageOrNull(),
                    regEmailError = emailValidation.errorMessageOrNull(),
                    regPhoneError = phoneValidation.errorMessageOrNull(),
                    regPasswordError = passwordValidation.errorMessageOrNull(),
                    regConfirmPasswordError = confirmValidation.errorMessageOrNull(),
                    regBanner = null
                )
            }
            return
        }

        val validatedRole = roleValidation.getOrNull()!!
        val normalizedName = nameValidation.getOrNull()!!
        val normalizedEmail = emailValidation.getOrNull()!!
        val normalizedPhone = phoneValidation.getOrNull()!!

        _uiState.update {
            it.copy(
                isSubmitting = true,
                selectedRole = validatedRole,
                regFullName = normalizedName,
                regEmail = normalizedEmail,
                regPhone = normalizedPhone,
                regRoleError = null,
                regFullNameError = null,
                regEmailError = null,
                regPhoneError = null,
                regPasswordError = null,
                regConfirmPasswordError = null,
                regBanner = null,
                duplicateEmailDetected = null
            )
        }

        viewModelScope.launch {
            val outcome = when (validatedRole) {
                UserRole.RESIDENT -> authRepository.registerResident(
                    fullName = normalizedName,
                    email = normalizedEmail,
                    phoneNumber = normalizedPhone,
                    password = rawPassword,
                    confirmPassword = rawConfirmPassword
                )
                UserRole.COLLECTOR -> authRepository.registerCollector(
                    fullName = normalizedName,
                    email = normalizedEmail,
                    phoneNumber = normalizedPhone,
                    password = rawPassword,
                    confirmPassword = rawConfirmPassword
                )
                UserRole.ADMIN -> RegistrationOutcome.Failure(AuthError.UnauthorizedRoleSelection())
            }
            _uiState.update { it.copy(isSubmitting = false) }
            handleRegistrationOutcome(outcome)
        }
    }

    private fun handleRegistrationOutcome(outcome: RegistrationOutcome) {
        when (outcome) {
            is RegistrationOutcome.Success -> {
                val warningBanner = if (!outcome.verificationEmailSent) {
                    AuthBannerMessage(
                        type = AuthBannerType.WARNING,
                        title = "Verification Email Pending",
                        message = "Your account was created, but we couldn't dispatch the verification email yet. Tap 'Resend Verification Email' below to receive your link.",
                        isRetryable = true
                    )
                } else {
                    AuthBannerMessage(
                        type = AuthBannerType.INFO,
                        title = "Verification Link Sent",
                        message = "We sent a verification link to ${outcome.user.email}. Open the email and click the link, then tap 'I've Verified My Email'.",
                        isRetryable = false
                    )
                }

                _uiState.update {
                    it.copy(
                        currentUser = outcome.user,
                        verificationEmailAddress = outcome.user.email,
                        initialVerificationEmailSent = outcome.verificationEmailSent,
                        verificationBanner = warningBanner,
                        isPartialRegistrationPending = false,
                        pendingRegistrationUid = null,
                        pendingRegistrationDraft = null
                    )
                }
                if (outcome.verificationEmailSent) {
                    startResendCooldown(DEFAULT_COOLDOWN_SECONDS)
                }
                emitNavigation(
                    AuthNavigationEvent.NavigateToVerifyEmail(
                        email = outcome.user.email,
                        initialEmailSent = outcome.verificationEmailSent
                    )
                )
            }
            is RegistrationOutcome.PartialSuccessProfilePending -> {
                _uiState.update {
                    it.copy(
                        isPartialRegistrationPending = true,
                        pendingRegistrationUid = outcome.uid,
                        pendingRegistrationDraft = outcome.draft,
                        regFullName = outcome.draft.fullName,
                        regEmail = outcome.draft.email,
                        regPhone = outcome.draft.phoneNumber,
                        selectedRole = outcome.draft.role,
                        regBanner = AuthBannerMessage(
                            type = AuthBannerType.WARNING,
                            title = "Account Created — Profile Setup Interrupted",
                            message = "Your authentication account (${outcome.email}) is active, but saving your profile failed: ${outcome.profileError.message} Tap 'Retry Profile Setup' to finish without creating a duplicate account.",
                            isRetryable = true
                        )
                    )
                }
            }
            is RegistrationOutcome.Failure -> {
                handleRegistrationFailure(outcome.error)
            }
        }
    }

    private fun handleRegistrationFailure(error: AuthError) {
        when (error) {
            is AuthError.ValidationFailed -> {
                applyRegistrationFieldErrors(error.errors)
            }
            is AuthError.EmailAlreadyInUse -> {
                _uiState.update {
                    it.copy(
                        duplicateEmailDetected = error.email,
                        regBanner = AuthBannerMessage(
                            type = AuthBannerType.DUPLICATE_EMAIL,
                            title = "Email Already Registered",
                            message = "An account with ${error.email} already exists. Please sign in to access or finish setting up your account.",
                            isRetryable = false
                        )
                    )
                }
            }
            is AuthError.WeakPassword -> {
                _uiState.update {
                    it.copy(
                        regPasswordError = error.message,
                        regBanner = AuthBannerMessage(
                            type = AuthBannerType.ERROR,
                            title = "Weak Password",
                            message = error.message,
                            isRetryable = false
                        )
                    )
                }
            }
            is AuthError.RateLimited -> {
                startRegistrationRateLimitCountdown(error.retryAfterSeconds)
                _uiState.update {
                    it.copy(
                        regBanner = AuthBannerMessage(
                            type = AuthBannerType.WARNING,
                            title = "Too Many Registration Attempts",
                            message = "Please wait ${error.retryAfterSeconds} seconds before trying again.",
                            isRetryable = false
                        )
                    )
                }
            }
            is AuthError.NetworkFailure -> {
                _uiState.update {
                    it.copy(
                        regBanner = AuthBannerMessage(
                            type = AuthBannerType.ERROR,
                            title = "Network Connection Error",
                            message = "Could not complete registration due to a network error. Your inputs have been preserved — check your connection and try again.",
                            isRetryable = true
                        )
                    )
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        regBanner = AuthBannerMessage(
                            type = AuthBannerType.ERROR,
                            title = "Registration Failed",
                            message = error.message,
                            isRetryable = false
                        )
                    )
                }
            }
        }
    }

    private fun applyRegistrationFieldErrors(errors: List<FieldValidationFailure>) {
        _uiState.update { state ->
            state.copy(
                regRoleError = errors.firstOrNull { it.field == "role" }?.message,
                regFullNameError = errors.firstOrNull { it.field == "fullName" }?.message,
                regEmailError = errors.firstOrNull { it.field == "email" }?.message,
                regPhoneError = errors.firstOrNull { it.field == "phoneNumber" }?.message,
                regPasswordError = errors.firstOrNull { it.field == "password" }?.message,
                regConfirmPasswordError = errors.firstOrNull { it.field == "confirmPassword" }?.message
            )
        }
    }

    fun retrySameSessionProfileInitialization() {
        val currentState = _uiState.value
        if (currentState.isSubmitting) return

        val roleValidation = AuthValidator.validateSelfRegistrationRole(currentState.selectedRole)
        val nameValidation = AuthValidator.validateFullName(currentState.regFullName)
        val phoneValidation = AuthValidator.validateAndNormalizePhone(currentState.regPhone)

        if (roleValidation.isInvalid || nameValidation.isInvalid || phoneValidation.isInvalid) {
            _uiState.update {
                it.copy(
                    regRoleError = roleValidation.errorMessageOrNull(),
                    regFullNameError = nameValidation.errorMessageOrNull(),
                    regPhoneError = phoneValidation.errorMessageOrNull()
                )
            }
            return
        }

        val updatedDraft = RegistrationDraft(
            fullName = nameValidation.getOrNull()!!,
            email = currentState.pendingRegistrationDraft?.email ?: currentState.regEmail.trim().lowercase(),
            phoneNumber = phoneValidation.getOrNull()!!,
            role = roleValidation.getOrNull()!!
        )

        _uiState.update {
            it.copy(
                isSubmitting = true,
                regBanner = null
            )
        }

        viewModelScope.launch {
            when (val outcome = authRepository.retryProfileInitialization(updatedDraft)) {
                is ProfileInitializationOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isPartialRegistrationPending = false,
                            pendingRegistrationUid = null,
                            pendingRegistrationDraft = null,
                            currentUser = outcome.user,
                            verificationEmailAddress = outcome.user.email,
                            initialVerificationEmailSent = outcome.verificationEmailSent
                        )
                    }
                    if (outcome.verificationEmailSent) {
                        startResendCooldown(DEFAULT_COOLDOWN_SECONDS)
                    }
                    emitNavigation(
                        AuthNavigationEvent.NavigateToVerifyEmail(
                            email = outcome.user.email,
                            initialEmailSent = outcome.verificationEmailSent
                        )
                    )
                }
                is ProfileInitializationOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            regBanner = AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Profile Setup Still Incomplete",
                                message = outcome.error.message,
                                isRetryable = outcome.error is AuthError.NetworkFailure
                            )
                        )
                    }
                }
            }
        }
    }

    fun navigateToLoginFromDuplicateEmail() {
        val emailToPrefill = _uiState.value.duplicateEmailDetected ?: _uiState.value.regEmail
        _uiState.update {
            it.copy(
                loginEmail = emailToPrefill,
                duplicateEmailDetected = null,
                regBanner = null
            )
        }
        emitNavigation(
            AuthNavigationEvent.NavigateToLogin(
                prefillEmail = emailToPrefill,
                clearBackStack = false
            )
        )
    }

    // =========================================================================
    // 4. Email Verification Experience
    // =========================================================================

    fun resendVerificationEmail() {
        val currentState = _uiState.value
        if (currentState.isResendingVerification ||
            currentState.isRefreshingVerification ||
            currentState.resendCooldownSeconds > 0
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isResendingVerification = true,
                verificationBanner = null
            )
        }

        viewModelScope.launch {
            when (val outcome = authRepository.resendEmailVerification()) {
                is EmailVerificationOutcome.Sent -> {
                    startResendCooldown(DEFAULT_COOLDOWN_SECONDS)
                    _uiState.update {
                        it.copy(
                            isResendingVerification = false,
                            verificationEmailAddress = outcome.email,
                            initialVerificationEmailSent = true,
                            verificationBanner = AuthBannerMessage(
                                type = AuthBannerType.SUCCESS,
                                title = "Verification Email Sent",
                                message = "A fresh verification link has been sent to ${outcome.email}. Please check your inbox and spam folder.",
                                isRetryable = false
                            )
                        )
                    }
                }
                is EmailVerificationOutcome.AlreadyVerified -> {
                    _uiState.update {
                        it.copy(
                            isResendingVerification = false,
                            isEmailVerified = true,
                            verificationBanner = AuthBannerMessage(
                                type = AuthBannerType.SUCCESS,
                                title = "Email Verified",
                                message = "Your email address (${outcome.email}) is verified. Tap Continue to proceed.",
                                isRetryable = false
                            )
                        )
                    }
                }
                is EmailVerificationOutcome.StatusRefreshed -> {
                    _uiState.update {
                        it.copy(
                            isResendingVerification = false,
                            isEmailVerified = outcome.isEmailVerified
                        )
                    }
                }
                is EmailVerificationOutcome.Failure -> {
                    if (outcome.error is AuthError.RateLimited) {
                        startResendCooldown(outcome.error.retryAfterSeconds)
                    }
                    _uiState.update {
                        it.copy(
                            isResendingVerification = false,
                            verificationBanner = AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Could Not Send Verification Email",
                                message = outcome.error.message,
                                isRetryable = outcome.error is AuthError.NetworkFailure
                            )
                        )
                    }
                }
            }
        }
    }

    fun refreshEmailVerificationStatus() {
        val currentState = _uiState.value
        if (currentState.isRefreshingVerification || currentState.isResendingVerification) return

        _uiState.update {
            it.copy(
                isRefreshingVerification = true,
                verificationBanner = null
            )
        }

        viewModelScope.launch {
            when (val outcome = authRepository.refreshEmailVerificationStatus()) {
                is EmailVerificationOutcome.StatusRefreshed -> {
                    if (outcome.isEmailVerified) {
                        _uiState.update {
                            it.copy(
                                isRefreshingVerification = false,
                                isEmailVerified = true,
                                verificationBanner = AuthBannerMessage(
                                    type = AuthBannerType.SUCCESS,
                                    title = "Email Verified!",
                                    message = "Your email address has been verified and your security token is updated. Tap Continue below.",
                                    isRetryable = false
                                )
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isRefreshingVerification = false,
                                isEmailVerified = false,
                                verificationBanner = AuthBannerMessage(
                                    type = AuthBannerType.WARNING,
                                    title = "Not Verified Yet",
                                    message = "Firebase still reports this email as unverified. Click the verification link in your email first, then tap 'Refresh Verification Status'.",
                                    isRetryable = false
                                )
                            )
                        }
                    }
                }
                is EmailVerificationOutcome.AlreadyVerified -> {
                    _uiState.update {
                        it.copy(
                            isRefreshingVerification = false,
                            isEmailVerified = true,
                            verificationBanner = AuthBannerMessage(
                                type = AuthBannerType.SUCCESS,
                                title = "Email Verified!",
                                message = "Your email address (${outcome.email}) is verified. Tap Continue below.",
                                isRetryable = false
                            )
                        )
                    }
                }
                is EmailVerificationOutcome.Sent -> {
                    _uiState.update { it.copy(isRefreshingVerification = false) }
                }
                is EmailVerificationOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isRefreshingVerification = false,
                            verificationBanner = AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Status Check Failed",
                                message = outcome.error.message,
                                isRetryable = outcome.error is AuthError.NetworkFailure
                            )
                        )
                    }
                }
            }
        }
    }

    fun continueFromEmailVerification() {
        val currentState = _uiState.value
        if (!currentState.isEmailVerified && !authRepository.isEmailVerified()) {
            _uiState.update {
                it.copy(
                    verificationBanner = AuthBannerMessage(
                        type = AuthBannerType.WARNING,
                        title = "Verification Required",
                        message = "Please verify your email address and tap 'Refresh Verification Status' before continuing."
                    )
                )
            }
            return
        }

        val user = currentState.currentUser ?: authRepository.getCurrentUser()
        if (user != null) {
            navigateForAuthenticatedUser(
                user = user,
                isEmailVerified = true,
                hasAdminClaim = currentState.hasAdminClaim
            )
        } else {
            restoreSessionOnLaunch()
        }
    }

    // =========================================================================
    // 5. Forgot Password Workflow
    // =========================================================================

    fun openForgotPasswordDialog(prefillEmail: String = _uiState.value.loginEmail) {
        val initialEmail = prefillEmail.trim().ifEmpty { _uiState.value.loginEmail.trim() }
        _uiState.update {
            it.copy(
                passwordReset = PasswordResetUiState(
                    isDialogVisible = true,
                    email = initialEmail,
                    emailError = null,
                    isLoading = false,
                    dispatchedToEmail = null,
                    errorBanner = null,
                    rateLimitSecondsRemaining = it.passwordReset.rateLimitSecondsRemaining
                )
            )
        }
    }

    fun dismissForgotPasswordDialog() {
        _uiState.update {
            it.copy(
                passwordReset = it.passwordReset.copy(
                    isDialogVisible = false,
                    isLoading = false,
                    emailError = null,
                    errorBanner = null
                )
            )
        }
    }

    fun onPasswordResetEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                passwordReset = it.passwordReset.copy(
                    email = email,
                    emailError = null,
                    errorBanner = null,
                    dispatchedToEmail = null
                )
            )
        }
    }

    fun submitPasswordReset(rawEmail: String = _uiState.value.passwordReset.email) {
        val resetState = _uiState.value.passwordReset
        if (resetState.isLoading || resetState.rateLimitSecondsRemaining > 0) return

        when (val validation = AuthValidator.validateEmail(rawEmail)) {
            is ValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        passwordReset = it.passwordReset.copy(
                            email = rawEmail,
                            emailError = validation.message,
                            errorBanner = null
                        )
                    )
                }
            }
            is ValidationResult.Valid -> {
                val normalizedEmail = validation.value
                _uiState.update {
                    it.copy(
                        passwordReset = it.passwordReset.copy(
                            email = normalizedEmail,
                            emailError = null,
                            isLoading = true,
                            errorBanner = null,
                            dispatchedToEmail = null
                        )
                    )
                }
                viewModelScope.launch {
                    when (val outcome = authRepository.sendPasswordReset(normalizedEmail)) {
                        is PasswordResetOutcome.ResetEmailDispatched -> {
                            _uiState.update {
                                it.copy(
                                    passwordReset = it.passwordReset.copy(
                                        isLoading = false,
                                        dispatchedToEmail = outcome.normalizedEmail,
                                        errorBanner = null
                                    )
                                )
                            }
                        }
                        is PasswordResetOutcome.Failure -> {
                            if (outcome.error is AuthError.RateLimited) {
                                startPasswordResetRateLimitCountdown(outcome.error.retryAfterSeconds)
                            }
                            _uiState.update {
                                it.copy(
                                    passwordReset = it.passwordReset.copy(
                                        isLoading = false,
                                        errorBanner = outcome.error.message,
                                        isRetryable = outcome.error is AuthError.NetworkFailure
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 6. Incomplete Profile Recovery (Cross-Session Ghost Account)
    // =========================================================================

    fun onRecoveryRoleSelected(role: UserRole) {
        if (!role.isSelfRegistrable) {
            _uiState.update {
                it.copy(recoveryRoleError = "Selected role cannot be self-registered.")
            }
            return
        }
        _uiState.update {
            it.copy(
                recoveryRole = role,
                recoveryRoleError = null,
                recoveryBanner = null
            )
        }
    }

    fun onRecoveryFullNameChanged(fullName: String) {
        val inlineError = if (fullName.isNotBlank()) {
            AuthValidator.validateFullName(fullName).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                recoveryFullName = fullName,
                recoveryFullNameError = inlineError,
                recoveryBanner = null
            )
        }
    }

    fun onRecoveryPhoneChanged(phone: String) {
        val inlineError = if (phone.isNotBlank()) {
            AuthValidator.validateAndNormalizePhone(phone).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                recoveryPhone = phone,
                recoveryPhoneError = inlineError,
                recoveryBanner = null
            )
        }
    }

    fun submitMissingProfileRecovery(
        rawFullName: String = _uiState.value.recoveryFullName,
        rawPhone: String = _uiState.value.recoveryPhone,
        role: UserRole? = _uiState.value.recoveryRole
    ) {
        val currentState = _uiState.value
        if (currentState.isSubmitting) return

        val roleValidation = AuthValidator.validateSelfRegistrationRole(role)
        val nameValidation = AuthValidator.validateFullName(rawFullName)
        val phoneValidation = AuthValidator.validateAndNormalizePhone(rawPhone)

        if (roleValidation.isInvalid || nameValidation.isInvalid || phoneValidation.isInvalid) {
            _uiState.update {
                it.copy(
                    recoveryRole = role,
                    recoveryFullName = rawFullName,
                    recoveryPhone = rawPhone,
                    recoveryRoleError = roleValidation.errorMessageOrNull(),
                    recoveryFullNameError = nameValidation.errorMessageOrNull(),
                    recoveryPhoneError = phoneValidation.errorMessageOrNull(),
                    recoveryBanner = null
                )
            }
            return
        }

        val recoveryInput = ProfileRecoveryInput(
            fullName = nameValidation.getOrNull()!!,
            phoneNumber = phoneValidation.getOrNull()!!,
            role = roleValidation.getOrNull()!!
        )

        _uiState.update {
            it.copy(
                isSubmitting = true,
                recoveryRole = recoveryInput.role,
                recoveryFullName = recoveryInput.fullName,
                recoveryPhone = recoveryInput.phoneNumber,
                recoveryRoleError = null,
                recoveryFullNameError = null,
                recoveryPhoneError = null,
                recoveryBanner = null
            )
        }

        viewModelScope.launch {
            when (val outcome = authRepository.completeMissingProfile(recoveryInput)) {
                is ProfileInitializationOutcome.Success -> {
                    val isVerified = authRepository.isEmailVerified()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            currentUser = outcome.user,
                            isEmailVerified = isVerified,
                            verificationEmailAddress = outcome.user.email,
                            initialVerificationEmailSent = outcome.verificationEmailSent,
                            recoveryBanner = null
                        )
                    }
                    navigateForAuthenticatedUser(
                        user = outcome.user,
                        isEmailVerified = isVerified,
                        hasAdminClaim = false
                    )
                }
                is ProfileInitializationOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            recoveryBanner = AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Could Not Complete Profile Setup",
                                message = outcome.error.message,
                                isRetryable = outcome.error is AuthError.NetworkFailure
                            )
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // 7. Role-Specific Onboarding & Collector Verification Workflows (Stage 5)
    // =========================================================================

    // --- Resident Onboarding ---

    fun onResidentZoneChanged(zone: String) {
        val validation = AuthValidator.validateServiceZoneId(zone)
        _uiState.update {
            it.copy(
                residentSelectedZone = zone,
                residentZoneError = validation.errorMessageOrNull(),
                residentOnboardingBanner = if (zone.equals(AuthValidator.UNSUPPORTED_ZONE_ID, ignoreCase = true)) {
                    AuthBannerMessage(
                        type = AuthBannerType.WARNING,
                        title = "Service Area Not Yet Supported",
                        message = "WasteLink doorstep collection is currently active in Dar es Salaam, Arusha, and Dodoma municipal zones. Pickups cannot be scheduled outside active zones.",
                        isRetryable = false
                    )
                } else null,
                onboardingError = null
            )
        }
    }

    fun onResidentAddressChanged(address: String) {
        _uiState.update {
            it.copy(
                residentAddress = address,
                onboardingError = null
            )
        }
    }

    fun completeResidentOnboarding(defaultZoneId: String? = _uiState.value.residentSelectedZone) {
        val currentState = _uiState.value
        if (currentState.isCompletingOnboarding) return

        val zoneToValidate = defaultZoneId?.takeIf { it.isNotBlank() } ?: currentState.residentSelectedZone
        val zoneValidation = AuthValidator.validateServiceZoneId(zoneToValidate)
        if (zoneValidation.isInvalid) {
            val errorMsg = zoneValidation.errorMessageOrNull() ?: "Please select your municipal service zone."
            _uiState.update {
                it.copy(
                    residentZoneError = errorMsg,
                    residentOnboardingBanner = AuthBannerMessage(
                        type = AuthBannerType.ERROR,
                        title = "Service Zone Required",
                        message = errorMsg,
                        isRetryable = false
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isCompletingOnboarding = true,
                residentZoneError = null,
                onboardingError = null,
                residentOnboardingBanner = null
            )
        }

        viewModelScope.launch {
            when (val outcome = authRepository.completeResidentOnboarding(zoneToValidate)) {
                is ProfileUpdateOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isCompletingOnboarding = false,
                            currentUser = outcome.user,
                            onboardingError = null,
                            residentOnboardingBanner = null
                        )
                    }
                    emitNavigation(
                        AuthNavigationEvent.NavigateToDestination(
                            route = Destinations.RESIDENT_HOME,
                            clearBackStack = true
                        )
                    )
                }
                is ProfileUpdateOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isCompletingOnboarding = false,
                            onboardingError = outcome.error.message,
                            residentOnboardingBanner = AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Setup Failed",
                                message = outcome.error.message,
                                isRetryable = outcome.error is AuthError.NetworkFailure
                            )
                        )
                    }
                }
            }
        }
    }

    fun completeStage4Onboarding(defaultZoneId: String? = null) {
        completeResidentOnboarding(defaultZoneId ?: _uiState.value.residentSelectedZone)
    }

    // --- Collector Onboarding KYC & Application Submission ---

    fun onCollectorVehicleTypeChanged(type: String) {
        val validation = AuthValidator.validateVehicleType(type)
        _uiState.update {
            it.copy(
                collectorVehicleType = type,
                collectorVehicleTypeError = validation.errorMessageOrNull(),
                collectorOnboardingBanner = null
            )
        }
    }

    fun onCollectorPlateNumberChanged(plate: String) {
        val inlineError = if (plate.isNotBlank()) {
            AuthValidator.validateVehiclePlateNumber(plate).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                collectorPlateNumber = plate,
                collectorPlateNumberError = inlineError,
                collectorOnboardingBanner = null
            )
        }
    }

    fun onCollectorLicenseNumberChanged(license: String) {
        val inlineError = if (license.isNotBlank()) {
            AuthValidator.validateLicenseNumber(license).errorMessageOrNull()
        } else null
        _uiState.update {
            it.copy(
                collectorLicenseNumber = license,
                collectorLicenseNumberError = inlineError,
                collectorOnboardingBanner = null
            )
        }
    }

    fun onCollectorServiceZoneChanged(zone: String) {
        val validation = AuthValidator.validateServiceZoneId(zone)
        _uiState.update {
            it.copy(
                collectorServiceZoneId = zone,
                collectorServiceZoneError = validation.errorMessageOrNull(),
                collectorOnboardingBanner = if (zone.equals(AuthValidator.UNSUPPORTED_ZONE_ID, ignoreCase = true)) {
                    AuthBannerMessage(
                        type = AuthBannerType.WARNING,
                        title = "Commercial Coverage Area Required",
                        message = "Commercial fleet operations are currently supported only in authorized municipal zones (Dar es Salaam, Arusha, Dodoma).",
                        isRetryable = false
                    )
                } else null
            )
        }
    }

    fun onCollectorReviewToggled(inReview: Boolean) {
        if (inReview) {
            val currentState = _uiState.value
            val vTypeValidation = AuthValidator.validateVehicleType(currentState.collectorVehicleType)
            val plateValidation = AuthValidator.validateVehiclePlateNumber(currentState.collectorPlateNumber)
            val licValidation = AuthValidator.validateLicenseNumber(currentState.collectorLicenseNumber)
            val zoneValidation = AuthValidator.validateServiceZoneId(currentState.collectorServiceZoneId)

            if (vTypeValidation.isInvalid || plateValidation.isInvalid || licValidation.isInvalid || zoneValidation.isInvalid) {
                _uiState.update {
                    it.copy(
                        collectorVehicleTypeError = vTypeValidation.errorMessageOrNull(),
                        collectorPlateNumberError = plateValidation.errorMessageOrNull(),
                        collectorLicenseNumberError = licValidation.errorMessageOrNull(),
                        collectorServiceZoneError = zoneValidation.errorMessageOrNull(),
                        isCollectorReviewStep = false
                    )
                }
                return
            }
        }
        _uiState.update {
            it.copy(
                isCollectorReviewStep = inReview,
                collectorOnboardingBanner = null
            )
        }
    }

    fun submitCollectorOnboarding() {
        val currentState = _uiState.value
        if (currentState.isSubmittingCollectorOnboarding) return

        val vTypeValidation = AuthValidator.validateVehicleType(currentState.collectorVehicleType)
        val plateValidation = AuthValidator.validateVehiclePlateNumber(currentState.collectorPlateNumber)
        val licValidation = AuthValidator.validateLicenseNumber(currentState.collectorLicenseNumber)
        val zoneValidation = AuthValidator.validateServiceZoneId(currentState.collectorServiceZoneId)

        if (vTypeValidation.isInvalid || plateValidation.isInvalid || licValidation.isInvalid || zoneValidation.isInvalid) {
            _uiState.update {
                it.copy(
                    collectorVehicleTypeError = vTypeValidation.errorMessageOrNull(),
                    collectorPlateNumberError = plateValidation.errorMessageOrNull(),
                    collectorLicenseNumberError = licValidation.errorMessageOrNull(),
                    collectorServiceZoneError = zoneValidation.errorMessageOrNull(),
                    isCollectorReviewStep = false
                )
            }
            return
        }

        val input = CollectorSubmissionInput(
            vehicleType = vTypeValidation.getOrNull()!!,
            vehiclePlateNumber = plateValidation.getOrNull()!!,
            licenseNumber = licValidation.getOrNull()!!,
            serviceZoneId = zoneValidation.getOrNull()!!
        )

        _uiState.update {
            it.copy(
                isSubmittingCollectorOnboarding = true,
                collectorOnboardingBanner = null
            )
        }

        viewModelScope.launch {
            when (val outcome = collectorVerificationRepository.submitInitialVerification(input)) {
                is CollectorSubmissionOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingCollectorOnboarding = false,
                            collectorSubmission = outcome.submission,
                            currentUser = outcome.updatedUser,
                            collectorOnboardingBanner = null
                        )
                    }
                    emitNavigation(AuthNavigationEvent.NavigateToCollectorVerificationStatus)
                }
                is CollectorSubmissionOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingCollectorOnboarding = false,
                            collectorOnboardingBanner = AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Submission Failed",
                                message = outcome.error.message,
                                isRetryable = outcome.error is AuthError.NetworkFailure
                            )
                        )
                    }
                }
            }
        }
    }

    fun resubmitCollectorOnboarding() {
        val currentState = _uiState.value
        if (currentState.isSubmittingCollectorOnboarding) return

        val vTypeValidation = AuthValidator.validateVehicleType(currentState.collectorVehicleType)
        val plateValidation = AuthValidator.validateVehiclePlateNumber(currentState.collectorPlateNumber)
        val licValidation = AuthValidator.validateLicenseNumber(currentState.collectorLicenseNumber)
        val zoneValidation = AuthValidator.validateServiceZoneId(currentState.collectorServiceZoneId)

        if (vTypeValidation.isInvalid || plateValidation.isInvalid || licValidation.isInvalid || zoneValidation.isInvalid) {
            _uiState.update {
                it.copy(
                    collectorVehicleTypeError = vTypeValidation.errorMessageOrNull(),
                    collectorPlateNumberError = plateValidation.errorMessageOrNull(),
                    collectorLicenseNumberError = licValidation.errorMessageOrNull(),
                    collectorServiceZoneError = zoneValidation.errorMessageOrNull(),
                    isCollectorReviewStep = false
                )
            }
            return
        }

        val input = CollectorSubmissionInput(
            vehicleType = vTypeValidation.getOrNull()!!,
            vehiclePlateNumber = plateValidation.getOrNull()!!,
            licenseNumber = licValidation.getOrNull()!!,
            serviceZoneId = zoneValidation.getOrNull()!!
        )

        _uiState.update {
            it.copy(
                isSubmittingCollectorOnboarding = true,
                collectorOnboardingBanner = null
            )
        }

        viewModelScope.launch {
            when (val outcome = collectorVerificationRepository.resubmitVerification(input)) {
                is CollectorSubmissionOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingCollectorOnboarding = false,
                            collectorSubmission = outcome.submission,
                            currentUser = outcome.updatedUser,
                            collectorOnboardingBanner = null
                        )
                    }
                    emitNavigation(AuthNavigationEvent.NavigateToCollectorVerificationStatus)
                }
                is CollectorSubmissionOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingCollectorOnboarding = false,
                            collectorOnboardingBanner = AuthBannerMessage(
                                type = AuthBannerType.ERROR,
                                title = "Resubmission Failed",
                                message = outcome.error.message,
                                isRetryable = outcome.error is AuthError.NetworkFailure
                            )
                        )
                    }
                }
            }
        }
    }

    // --- Collector Verification Status Screen ---

    fun loadCollectorSubmission() {
        val uid = _uiState.value.currentUser?.id ?: authRepository.getCurrentUser()?.id ?: return
        _uiState.update { it.copy(isLoadingCollectorSubmission = true) }
        viewModelScope.launch {
            val submission = collectorVerificationRepository.getSubmission(uid).getOrNull()
            _uiState.update {
                it.copy(
                    isLoadingCollectorSubmission = false,
                    collectorSubmission = submission
                )
            }
        }
    }

    fun refreshCollectorVerificationStatus() {
        if (_uiState.value.isLoadingCollectorSubmission) return
        _uiState.update { it.copy(isLoadingCollectorSubmission = true) }
        viewModelScope.launch {
            authRepository.restoreSession()
            val user = authRepository.getCurrentUser()
            val submission = user?.let { collectorVerificationRepository.getSubmission(it.id).getOrNull() }
            _uiState.update {
                it.copy(
                    isLoadingCollectorSubmission = false,
                    currentUser = user ?: it.currentUser,
                    collectorSubmission = submission ?: it.collectorSubmission
                )
            }
            if (user?.collectorVerificationStatus == CollectorVerificationStatus.APPROVED) {
                emitNavigation(
                    AuthNavigationEvent.NavigateToDestination(
                        route = Destinations.COLLECTOR_DASHBOARD,
                        clearBackStack = true
                    )
                )
            }
        }
    }

    fun startCollectorEditAndResubmit() {
        val currentSub = _uiState.value.collectorSubmission
        val currentUser = _uiState.value.currentUser
        viewModelScope.launch {
            val sub = currentSub ?: currentUser?.let { collectorVerificationRepository.getSubmission(it.id).getOrNull() }
            _uiState.update {
                it.copy(
                    collectorSubmission = sub ?: it.collectorSubmission,
                    collectorVehicleType = sub?.vehicleType ?: currentUser?.vehicleType.orEmpty(),
                    collectorPlateNumber = sub?.vehiclePlateNumber ?: currentUser?.vehiclePlateNumber.orEmpty(),
                    collectorLicenseNumber = sub?.licenseNumber ?: it.collectorLicenseNumber,
                    collectorServiceZoneId = sub?.serviceZoneId ?: currentUser?.defaultZoneId.orEmpty(),
                    collectorVehicleTypeError = null,
                    collectorPlateNumberError = null,
                    collectorLicenseNumberError = null,
                    collectorServiceZoneError = null,
                    isCollectorReviewStep = false,
                    collectorOnboardingBanner = null
                )
            }
            emitNavigation(AuthNavigationEvent.NavigateToOnboarding(UserRole.COLLECTOR))
        }
    }

    // =========================================================================
    // 8. Logout & Session Reset
    // =========================================================================

    fun logout() {
        currentNavigationTarget = null
        loginRateLimitJob?.cancel()
        regRateLimitJob?.cancel()
        resendCooldownJob?.cancel()
        resetRateLimitJob?.cancel()

        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState(
                sessionState = SessionState.Unauthenticated,
                isInitialSessionLoading = false
            )
            emitNavigation(
                AuthNavigationEvent.NavigateToLogin(
                    prefillEmail = null,
                    clearBackStack = true
                )
            )
        }
    }

    // =========================================================================
    // 9. Cooldown Timers & Banner Builders
    // =========================================================================

    private fun startLoginRateLimitCountdown(seconds: Int) {
        loginRateLimitJob?.cancel()
        val initialSeconds = seconds.coerceAtLeast(1)
        _uiState.update { it.copy(loginRateLimitSeconds = initialSeconds) }
        loginRateLimitJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _uiState.update { it.copy(loginRateLimitSeconds = remaining) }
            }
        }
    }

    private fun startRegistrationRateLimitCountdown(seconds: Int) {
        regRateLimitJob?.cancel()
        val initialSeconds = seconds.coerceAtLeast(1)
        _uiState.update { it.copy(regRateLimitSeconds = initialSeconds) }
        regRateLimitJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _uiState.update { it.copy(regRateLimitSeconds = remaining) }
            }
        }
    }

    private fun startResendCooldown(seconds: Int) {
        resendCooldownJob?.cancel()
        val initialSeconds = seconds.coerceAtLeast(1)
        _uiState.update { it.copy(resendCooldownSeconds = initialSeconds) }
        resendCooldownJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
            }
        }
    }

    private fun startPasswordResetRateLimitCountdown(seconds: Int) {
        resetRateLimitJob?.cancel()
        val initialSeconds = seconds.coerceAtLeast(1)
        _uiState.update {
            it.copy(
                passwordReset = it.passwordReset.copy(
                    rateLimitSecondsRemaining = initialSeconds
                )
            )
        }
        resetRateLimitJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _uiState.update {
                    it.copy(
                        passwordReset = it.passwordReset.copy(
                            rateLimitSecondsRemaining = remaining
                        )
                    )
                }
            }
        }
    }

    private fun buildAccountLockedBanner(
        accountStatus: AccountStatus,
        statusReason: String?,
        suspendedUntilEpochMs: Long?
    ): AuthBannerMessage {
        val reasonSuffix = statusReason?.takeIf { it.isNotBlank() }?.let { " Reason: $it." } ?: ""
        return when (accountStatus) {
            AccountStatus.SUSPENDED -> AuthBannerMessage(
                type = AuthBannerType.ACCOUNT_LOCKED,
                title = "Account Temporarily Suspended",
                message = "Your WasteLink account is currently suspended.$reasonSuffix" +
                    (suspendedUntilEpochMs?.let { " Suspension active until epoch $it." } ?: "") +
                    " You may refresh status or sign out below.",
                isRetryable = true
            )
            AccountStatus.BANNED -> AuthBannerMessage(
                type = AuthBannerType.ACCOUNT_LOCKED,
                title = "Account Terminated (Banned)",
                message = "Your WasteLink account has been permanently banned for a policy violation.$reasonSuffix",
                isRetryable = false
            )
            AccountStatus.RESTRICTED -> AuthBannerMessage(
                type = AuthBannerType.WARNING,
                title = "Account Restricted",
                message = "Your account has an operational restriction.$reasonSuffix",
                isRetryable = false
            )
            AccountStatus.ACTIVE -> AuthBannerMessage(
                type = AuthBannerType.INFO,
                title = "Account Active",
                message = "Your account is active.",
                isRetryable = false
            )
        }
    }

    private fun buildProfileLoadFailedBanner(error: AuthError): AuthBannerMessage {
        return when (error) {
            is AuthError.NetworkFailure -> AuthBannerMessage(
                type = AuthBannerType.PROFILE_LOAD_FAILED,
                title = "Profile Could Not Be Loaded",
                message = "Authentication succeeded, but we could not load your profile from the server due to a network error. Tap 'Retry Profile Load' to continue.",
                isRetryable = true
            )
            is AuthError.PermissionDenied -> AuthBannerMessage(
                type = AuthBannerType.PROFILE_LOAD_FAILED,
                title = "Profile Access Denied",
                message = "Your authentication succeeded, but security policy blocked access to your profile document.",
                isRetryable = false
            )
            is AuthError.MalformedProfile -> AuthBannerMessage(
                type = AuthBannerType.PROFILE_LOAD_FAILED,
                title = "Profile Data Corrupted",
                message = error.message,
                isRetryable = false
            )
            else -> AuthBannerMessage(
                type = AuthBannerType.PROFILE_LOAD_FAILED,
                title = "Profile Load Failed",
                message = error.message,
                isRetryable = false
            )
        }
    }

    private fun emitNavigation(event: AuthNavigationEvent) {
        _navigationEvents.trySend(event)
    }
}
