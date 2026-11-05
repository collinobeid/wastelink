package com.waste.link.data.repository

import androidx.annotation.VisibleForTesting
import com.waste.link.data.auth.SessionStateProvider
import com.waste.link.data.models.AppUser
import com.waste.link.domain.model.CollectorSubmission
import com.waste.link.domain.model.CollectorSubmissionInput
import com.waste.link.domain.model.CollectorSubmissionOutcome
import com.waste.link.domain.model.EmailVerificationOutcome
import com.waste.link.domain.model.LoginOutcome
import com.waste.link.domain.model.LogoutOutcome
import com.waste.link.domain.model.PasswordResetOutcome
import com.waste.link.domain.model.ProfileInitializationOutcome
import com.waste.link.domain.model.ProfileRecoveryInput
import com.waste.link.domain.model.ProfileUpdateOutcome
import com.waste.link.domain.model.RegistrationDraft
import com.waste.link.domain.model.RegistrationOutcome
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.User
import com.waste.link.domain.model.UserRole
import kotlinx.coroutines.flow.StateFlow

/**
 * Canonical Authentication & User Profile Repository for WasteLink (Module 1, Stage 3.1).
 *
 * Responsibilities:
 * - Validated Resident and Collector registration with canonical `/users/{uid}` creation.
 * - Same-session and cross-session Ghost Account detection and idempotent recovery.
 * - Login with deterministic separation of authentication errors, missing profiles,
 *   malformed profiles, account restrictions (`RESTRICTED`, `SUSPENDED`, `BANNED`), and network failures.
 * - Explicit separation between server-verified sessions ([SessionState.Authenticated]) and
 *   read-only offline cached sessions ([SessionState.AuthenticatedCachedOffline]).
 * - Active observation of Firebase Auth state and ID token changes without leaking listeners.
 * - Authoritative Firebase Auth email verification and ID token refresh.
 * - Enumeration-safe password reset.
 * - Clean logout clearing in-memory draft and session state without deleting Firestore documents.
 */
interface AuthRepository : SessionStateProvider {

    // =========================================================================
    // 1. Canonical Stage 3 State & Inspection
    // =========================================================================

    /** Reactive stream of the current authoritative [SessionState]. */
    override val sessionState: StateFlow<SessionState>

    /**
     * Returns the currently resolved canonical [User] ONLY when the session has been verified
     * against the server ([SessionState.Authenticated] or [SessionState.AuthenticatedRestricted]).
     * Returns `null` when unauthenticated or when only an unverified offline cache is available.
     */
    fun getCurrentUser(): User?

    /**
     * Returns the cached or server-verified [User] for read-only display purposes when offline.
     * Must never be used as authorization proof that the account is still `ACTIVE` on the server.
     */
    fun getCachedOrVerifiedUser(): User?

    /**
     * Returns `true` only when the current session is [SessionState.Authenticated] (`isServerVerified == true`).
     */
    fun isServerVerifiedSession(): Boolean

    /**
     * Updates the current in-memory [SessionState] with an authoritative, server-verified [User] profile.
     * Used by collaborating repositories (such as [CollectorVerificationRepository]) to update the session.
     */
    fun applyServerVerifiedUserToSession(user: User)

    /** Returns the cached [RegistrationDraft] if a same-session partial registration is pending recovery. */
    fun getPendingRegistrationDraft(): RegistrationDraft?

    /** Returns whether the currently authenticated Firebase user's email is verified. */
    fun isEmailVerified(): Boolean

    /**
     * Intercepts an authorization failure (such as PERMISSION_DENIED on a protected Firestore write)
     * and refreshes the user's live session standing from the server.
     * Returns `true` if the account was found to be restricted, suspended, or banned.
     */
    suspend fun handleAuthorizationFailure(throwable: Throwable): Boolean

    // =========================================================================
    // 2. Canonical Registration Engine
    // =========================================================================

    /**
     * Registers a new [UserRole.RESIDENT] account and initializes `/users/{uid}` with
     * `collectorVerificationStatus = NOT_APPLICABLE`.
     */
    suspend fun registerResident(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String = password
    ): RegistrationOutcome

    /**
     * Registers a new [UserRole.COLLECTOR] account and initializes `/users/{uid}` with
     * `collectorVerificationStatus = NOT_SUBMITTED`.
     */
    suspend fun registerCollector(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String = password
    ): RegistrationOutcome

    /**
     * Registers a new user with an explicit [UserRole].
     * Rejects [UserRole.ADMIN] before any network or Firebase call is made.
     */
    suspend fun registerUser(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String = password,
        role: UserRole
    ): RegistrationOutcome

    // =========================================================================
    // 3. Ghost Account Recovery (Same-Session & Cross-Session)
    // =========================================================================

    /**
     * Retries `/users/{uid}` Firestore profile creation after a same-session partial registration
     * failure (`RegistrationOutcome.PartialSuccessProfilePending`).
     *
     * Never calls `createUserWithEmailAndPassword` a second time, and is idempotent if repeated.
     */
    suspend fun retryProfileInitialization(
        overrideDraft: RegistrationDraft? = null
    ): ProfileInitializationOutcome

    /**
     * Completes `/users/{uid}` Firestore profile creation for a cross-session Ghost Account
     * detected during `login()` or `restoreSession()`.
     *
     * Requires an active authenticated Firebase user, validates [input] via `AuthValidator`,
     * never fabricates placeholder data, and is idempotent if repeated.
     */
    suspend fun completeMissingProfile(
        input: ProfileRecoveryInput
    ): ProfileInitializationOutcome

    // =========================================================================
    // 4. Login & Profile Resolution
    // =========================================================================

    /**
     * Authenticates a user with email and password, fetches `/users/{uid}` from Firestore (`Source.SERVER`),
     * and resolves their canonical [User] profile, account standing, and onboarding status.
     */
    suspend fun login(
        email: String,
        password: String
    ): LoginOutcome

    // =========================================================================
    // 5. Email Verification Operations
    // =========================================================================

    /** Sends a verification email to the currently authenticated Firebase user. */
    suspend fun sendEmailVerification(): EmailVerificationOutcome

    /** Resends a verification email to the currently authenticated Firebase user. */
    suspend fun resendEmailVerification(): EmailVerificationOutcome

    /**
     * Reloads the current `FirebaseUser` and, when `isEmailVerified` is `true`, forces an
     * ID token refresh (`getIdToken(true)`) so `request.auth.token.email_verified == true`
     * is immediately available to Firestore Security Rules.
     */
    suspend fun refreshEmailVerificationStatus(): EmailVerificationOutcome

    // =========================================================================
    // 6. Password Recovery
    // =========================================================================

    /**
     * Validates [email] and dispatches a Firebase password reset email.
     * Returns a privacy-preserving result that prevents account enumeration.
     */
    suspend fun sendPasswordReset(email: String): PasswordResetOutcome

    // =========================================================================
    // 7. Session Management & Logout
    // =========================================================================

    /**
     * Inspects and restores the current Firebase session and `/users/{uid}` profile.
     * Distinguishes server-verified [SessionState.Authenticated] from offline cached
     * [SessionState.AuthenticatedCachedOffline].
     */
    suspend fun restoreSession(): SessionState

    /**
     * Signs out of Firebase Authentication and clears all in-memory session and registration draft state.
     * Never deletes `/users/{uid}` in Firestore.
     */
    suspend fun logout(): LogoutOutcome

    // =========================================================================
    // 8. Self-Service Profile Lifecycle Operations
    // =========================================================================

    /** Marks Resident onboarding as `COMPLETED` and optionally sets `defaultZoneId`. Requires server-verified session. */
    suspend fun completeResidentOnboarding(
        defaultZoneId: String? = null
    ): ProfileUpdateOutcome

    /** Updates mutable self-service profile fields (`fullName`, `phoneNumber`, `defaultZoneId`, `preferredLanguage`). Requires server-verified session. */
    suspend fun updateProfile(
        fullName: String,
        phoneNumber: String,
        defaultZoneId: String? = null,
        preferredLanguage: String? = null
    ): ProfileUpdateOutcome

    // =========================================================================
    // 8a. Collector Onboarding & Vetting Compatibility Delegation (Stage 5.1 Decoupling)
    // NOTE: Responsibilities have migrated to CollectorVerificationRepository.
    // These methods provide temporary backward compatibility and delegate directly.
    // =========================================================================

    /**
     * Executes atomic WriteBatch for initial Collector KYC onboarding.
     * Temporary delegation to [CollectorVerificationRepository.submitInitialVerification].
     */
    @Deprecated("Migrate caller to CollectorVerificationRepository.submitInitialVerification directly.")
    suspend fun submitCollectorOnboarding(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome

    /**
     * Executes atomic WriteBatch for Collector KYC resubmission after rejection.
     * Temporary delegation to [CollectorVerificationRepository.resubmitVerification].
     */
    @Deprecated("Migrate caller to CollectorVerificationRepository.resubmitVerification directly.")
    suspend fun resubmitCollectorOnboarding(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome

    /**
     * Fetches current [CollectorSubmission] for [uid] from `/collector_submissions/{uid}`.
     * Temporary delegation to [CollectorVerificationRepository.getSubmission].
     */
    @Deprecated("Migrate caller to CollectorVerificationRepository.getSubmission directly.")
    suspend fun getCollectorSubmission(
        uid: String
    ): Result<CollectorSubmission?>

    /**
     * Test-only administrative helper to approve a collector's submission atomically.
     * Temporary delegation to [CollectorVerificationRepository.adminApproveCollectorSubmission].
     */
    @VisibleForTesting
    @Deprecated("Migrate caller to CollectorVerificationRepository.adminApproveCollectorSubmission directly.")
    suspend fun adminApproveCollectorSubmission(
        collectorUid: String,
        adminUid: String
    ): Result<Unit>

    /**
     * Test-only administrative helper to reject a collector's submission atomically.
     * Temporary delegation to [CollectorVerificationRepository.adminRejectCollectorSubmission].
     */
    @VisibleForTesting
    @Deprecated("Migrate caller to CollectorVerificationRepository.adminRejectCollectorSubmission directly.")
    suspend fun adminRejectCollectorSubmission(
        collectorUid: String,
        adminUid: String,
        rejectionReason: String
    ): Result<Unit>

    // =========================================================================
    // 9. Legacy Compatibility Bridge (Preserved for unmigrated Stage 4+ ViewModels)
    // =========================================================================

    @Deprecated("Use registerResident / registerCollector / registerUser in new code")
    suspend fun signUp(email: String, password: String, name: String, phone: String): Result<AppUser>

    @Deprecated("Use registerUser or completeMissingProfile in new code")
    suspend fun setRole(uid: String, role: String): Result<AppUser>

    @Deprecated("Use login(email, password) returning LoginOutcome in new code")
    suspend fun signIn(email: String, password: String): Result<AppUser>

    @Deprecated("Use restoreSession() or login() returning canonical User in new code")
    suspend fun getUserRole(uid: String): Result<AppUser>

    @Deprecated("Use logout() in new code")
    fun signOut()

    fun isUserSignedIn(): Boolean

    fun getCurrentUserId(): String?

    @Deprecated("Use sendPasswordReset(email) in new code")
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>
}
