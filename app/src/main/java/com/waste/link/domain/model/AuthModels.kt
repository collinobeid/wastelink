package com.waste.link.domain.model

import com.waste.link.domain.validation.ValidationError

/**
 * Validated, normalized registration draft preserved in memory when Firebase Authentication
 * succeeds (`createUserWithEmailAndPassword`) before `/users/{uid}` is confirmed in Firestore.
 *
 * Security Invariant:
 * - Never stores raw or hashed passwords.
 * - Used exclusively to allow idempotent same-session profile initialization retries without
 *   calling `createUserWithEmailAndPassword` a second time.
 */
data class RegistrationDraft(
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val role: UserRole
)

/**
 * User-supplied profile completion inputs required when recovering a cross-session Ghost Account
 * (an authenticated Firebase user whose `/users/{uid}` Firestore profile is missing or incomplete).
 */
data class ProfileRecoveryInput(
    val fullName: String,
    val phoneNumber: String,
    val role: UserRole
)

/**
 * Classification of why an authenticated Firebase user requires profile initialization/recovery.
 */
enum class ProfileIncompleteReason {
    /** `/users/{uid}` document does not exist in Firestore. */
    DOCUMENT_MISSING,

    /** Same-session registration succeeded in Firebase Auth, but the Firestore `/users/{uid}` write failed. */
    PROFILE_WRITE_FAILED,

    /** Legacy `/users/{uid}` document exists with `role == "unassigned"`. */
    LEGACY_UNASSIGNED_ROLE,

    /** Legacy `/users/{uid}` document contains fabricated placeholder values (`name = "User"`, `phone = "Not provided"`). */
    LEGACY_FABRICATED_PLACEHOLDER
}

/**
 * Field-level validation error reported before any network or Firebase call is executed.
 */
data class FieldValidationFailure(
    val field: String,
    val error: ValidationError,
    val message: String
)

/**
 * Strongly typed domain error hierarchy for authentication, registration, profile resolution,
 * email verification, password reset, and session management.
 *
 * Raw Firebase exceptions are never exposed directly to UI consumers.
 */
sealed interface AuthError {
    val message: String

    data class ValidationFailed(
        val errors: List<FieldValidationFailure>,
        override val message: String = errors.firstOrNull()?.message ?: "Invalid input."
    ) : AuthError

    data class UnauthorizedRoleSelection(
        override val message: String = "Selected role cannot be self-registered."
    ) : AuthError

    data class UnauthorizedAdminClaim(
        override val message: String = "Administrator access requires a verified server-side admin claim."
    ) : AuthError

    data class InvalidCredentials(
        override val message: String = "Invalid email or password."
    ) : AuthError

    data class AccountNotFound(
        override val message: String = "Invalid email or password."
    ) : AuthError

    data class EmailAlreadyInUse(
        val email: String,
        override val message: String = "An account with this email already exists. Please sign in to continue."
    ) : AuthError

    data class WeakPassword(
        override val message: String = "Password does not meet minimum security requirements."
    ) : AuthError

    data class UserDisabled(
        override val message: String = "This account has been disabled. Please contact support."
    ) : AuthError

    data class SessionRevoked(
        override val message: String = "Your session has expired or been revoked. Please sign in again."
    ) : AuthError

    data class Unauthenticated(
        override val message: String = "You must be signed in to perform this action."
    ) : AuthError

    data class EmailNotVerified(
        override val message: String = "Please verify your email address before continuing."
    ) : AuthError

    data class RateLimited(
        val retryAfterSeconds: Int = 60,
        override val message: String = "Too many attempts. Please wait before trying again."
    ) : AuthError

    data class NetworkFailure(
        override val message: String = "Network error. Please check your internet connection and try again.",
        val isRetryable: Boolean = true
    ) : AuthError

    data class PermissionDenied(
        override val message: String = "You do not have permission to perform this operation."
    ) : AuthError

    data class ProfileNotFound(
        val uid: String,
        val email: String,
        override val message: String = "User profile is missing and requires initialization."
    ) : AuthError

    data class MalformedProfile(
        val uid: String,
        val reason: String,
        override val message: String = "User profile data is corrupted or invalid: $reason"
    ) : AuthError

    data class AccountLockedError(
        val accountStatus: AccountStatus,
        val statusReason: String?,
        val suspendedUntilEpochMs: Long?,
        override val message: String = "Account access is restricted ($accountStatus)."
    ) : AuthError

    data class UnknownError(
        override val message: String = "An unexpected authentication error occurred."
    ) : AuthError
}

/**
 * Structured outcome of a Resident or Collector registration operation.
 *
 * Registration is only treated as [Success] when BOTH the Firebase Authentication account
 * AND the canonical `/users/{uid}` Firestore document have been created and validated.
 */
sealed interface RegistrationOutcome {
    /**
     * Both Firebase Auth account creation and canonical `/users/{uid}` Firestore initialization succeeded.
     * If sending the initial verification email failed, [verificationEmailSent] is `false` and
     * [emailSendError] describes the non-fatal email error so the user can retry from the verification gate.
     */
    data class Success(
        val user: User,
        val verificationEmailSent: Boolean,
        val emailSendError: AuthError? = null
    ) : RegistrationOutcome

    /**
     * Same-session Ghost Account state: Firebase Auth account creation succeeded ([uid] is authenticated),
     * but writing `/users/{uid}` to Firestore failed.
     *
     * The authenticated session and [draft] are preserved in memory so `retryProfileInitialization()`
     * can complete `/users/{uid}` creation idempotently without calling `createUserWithEmailAndPassword` again.
     */
    data class PartialSuccessProfilePending(
        val uid: String,
        val email: String,
        val draft: RegistrationDraft,
        val profileError: AuthError
    ) : RegistrationOutcome

    /**
     * Registration failed prior to or during Firebase Authentication (validation failure,
     * unauthorized role, email collision, weak password, network error, etc.).
     */
    data class Failure(
        val error: AuthError
    ) : RegistrationOutcome
}

/**
 * Structured outcome of same-session (`retryProfileInitialization`) or cross-session
 * (`completeMissingProfile`) Ghost Account profile initialization.
 */
sealed interface ProfileInitializationOutcome {
    data class Success(
        val user: User,
        val verificationEmailSent: Boolean,
        val emailSendError: AuthError? = null,
        val wasAlreadyInitialized: Boolean = false
    ) : ProfileInitializationOutcome

    data class Failure(
        val error: AuthError,
        val draft: RegistrationDraft? = null
    ) : ProfileInitializationOutcome
}

/**
 * Structured outcome of a login and profile resolution operation.
 */
sealed interface LoginOutcome {
    /**
     * Authentication succeeded and a valid canonical [User] profile with `AccountStatus.ACTIVE`
     * (or `RESTRICTED` when operational access is evaluated downstream) was resolved.
     */
    data class Authenticated(
        val user: User,
        val isEmailVerified: Boolean,
        val hasAdminClaim: Boolean = false,
        val isFromCache: Boolean = false
    ) : LoginOutcome

    /**
     * Cross-session Ghost Account detected: Firebase Authentication succeeded, but `/users/{uid}`
     * is missing or contains a legacy incomplete/unassigned placeholder.
     *
     * The user remains authenticated so they can complete profile recovery via `completeMissingProfile`.
     */
    data class ProfileIncomplete(
        val uid: String,
        val email: String,
        val isEmailVerified: Boolean,
        val reason: ProfileIncompleteReason,
        val suggestedDraft: RegistrationDraft? = null
    ) : LoginOutcome

    /**
     * Authentication succeeded and `/users/{uid}` was loaded, but the user's account standing is
     * [AccountStatus.RESTRICTED], [AccountStatus.SUSPENDED], or [AccountStatus.BANNED].
     */
    data class AccountLocked(
        val user: User,
        val isEmailVerified: Boolean,
        val accountStatus: AccountStatus,
        val statusReason: String?,
        val suspendedUntilEpochMs: Long?
    ) : LoginOutcome

    /**
     * Authentication succeeded, but loading `/users/{uid}` failed due to a transient network outage
     * (`NetworkFailure`), `PermissionDenied`, or a genuinely corrupted document (`MalformedProfile`).
     * Never fabricates a fallback user.
     */
    data class ProfileLoadFailed(
        val uid: String,
        val email: String,
        val error: AuthError
    ) : LoginOutcome

    /**
     * Firebase Authentication failed (invalid credentials, unknown user, disabled Auth account,
     * validation failure, rate limit, or network failure during sign-in).
     */
    data class Failure(
        val error: AuthError
    ) : LoginOutcome
}

/**
 * Authoritative session state resolved on startup (`restoreSession`) and updated across
 * authentication lifecycle transitions.
 */
sealed interface SessionState {
    /** No active Firebase Authentication user session exists. */
    data object Unauthenticated : SessionState

    /**
     * Active Firebase Authentication session with a valid, `ACTIVE` canonical [User] profile.
     */
    data class Authenticated(
        val user: User,
        val isEmailVerified: Boolean,
        val hasAdminClaim: Boolean = false,
        val isFromCache: Boolean = false
    ) : SessionState

    /**
     * Active Firebase Authentication session where the user's `/users/{uid}` profile has
     * [AccountStatus.RESTRICTED], [AccountStatus.SUSPENDED], or [AccountStatus.BANNED].
     */
    data class AuthenticatedRestricted(
        val user: User,
        val isEmailVerified: Boolean,
        val accountStatus: AccountStatus,
        val statusReason: String?,
        val suspendedUntilEpochMs: Long?
    ) : SessionState

    /**
     * Active Firebase Authentication session where `/users/{uid}` is missing or incomplete
     * (Ghost Account requiring profile initialization).
     */
    data class AuthenticatedProfileIncomplete(
        val uid: String,
        val email: String,
        val isEmailVerified: Boolean,
        val reason: ProfileIncompleteReason,
        val pendingDraft: RegistrationDraft? = null
    ) : SessionState

    /**
     * Active Firebase Authentication session where `/users/{uid}` could not be loaded due to
     * temporary network unavailability (`NetworkFailure`), `PermissionDenied`, or `MalformedProfile`.
     * Preserves the Firebase session without signing the user out.
     */
    data class AuthenticatedProfileLoadError(
        val uid: String,
        val email: String,
        val error: AuthError
    ) : SessionState

    /**
     * The previous Firebase Authentication token was revoked, disabled (`ERROR_USER_DISABLED`),
     * or deleted on the server (`ERROR_USER_NOT_FOUND`), and local sign-out was executed.
     */
    data class AuthenticationRevoked(
        val error: AuthError
    ) : SessionState
}

/**
 * Structured outcome of email verification operations (`sendEmailVerification`,
 * `resendEmailVerification`, `refreshEmailVerificationStatus`).
 */
sealed interface EmailVerificationOutcome {
    data class Sent(val email: String) : EmailVerificationOutcome
    data class AlreadyVerified(val email: String) : EmailVerificationOutcome
    data class StatusRefreshed(
        val isEmailVerified: Boolean,
        val tokenRefreshed: Boolean
    ) : EmailVerificationOutcome
    data class Failure(val error: AuthError) : EmailVerificationOutcome
}

/**
 * Structured outcome of password recovery operations (`sendPasswordReset`).
 *
 * Security Guarantee:
 * - [ResetEmailDispatched] is returned both when the email corresponds to an existing account
 *   and when Firebase reports `ERROR_USER_NOT_FOUND`, preventing account enumeration attacks.
 */
sealed interface PasswordResetOutcome {
    data class ResetEmailDispatched(val normalizedEmail: String) : PasswordResetOutcome
    data class Failure(val error: AuthError) : PasswordResetOutcome
}

/**
 * Structured outcome of `logout()`.
 */
sealed interface LogoutOutcome {
    data object Success : LogoutOutcome
    data class Failure(val error: AuthError) : LogoutOutcome
}

/**
 * Structured outcome of self-service profile updates (`completeResidentOnboarding`, `updateProfile`).
 */
sealed interface ProfileUpdateOutcome {
    data class Success(val user: User) : ProfileUpdateOutcome
    data class Failure(val error: AuthError) : ProfileUpdateOutcome
}
