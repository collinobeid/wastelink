package com.waste.link.data.repository

import com.waste.link.data.models.AppUser
import com.waste.link.data.models.ProfileMappingResult
import com.waste.link.data.models.UserMapper
import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.remote.FirebaseErrorMapper
import com.waste.link.data.remote.RemoteAuthUser
import com.waste.link.data.remote.RemoteDocumentResult
import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.AuthError
import androidx.annotation.VisibleForTesting
import com.waste.link.domain.model.CollectorSubmission
import com.waste.link.domain.model.CollectorSubmissionInput
import com.waste.link.domain.model.CollectorSubmissionMapper
import com.waste.link.domain.model.CollectorSubmissionOutcome
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.EmailVerificationOutcome
import com.waste.link.domain.model.FieldValidationFailure
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
import com.waste.link.domain.validation.AuthValidator
import com.waste.link.domain.validation.ValidationError
import com.waste.link.domain.validation.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [AuthRepository] for WasteLink Module 1, Stage 3.1.
 *
 * Security & Architectural Guarantees:
 * 1. Writes `/users/{uid}` exclusively using the canonical [User] domain model and [UserMapper.toFirestoreMap]
 *    conforming to Stage 2.1 `firestore.rules`.
 * 2. Never allows client self-registration as [UserRole.ADMIN].
 * 3. Preserves authenticated session and [RegistrationDraft] on same-session Firestore write failure
 *    so `retryProfileInitialization()` can complete `/users/{uid}` creation idempotently without
 *    calling `createUserWithEmailAndPassword` a second time.
 * 4. Detects missing or legacy incomplete `/users/{uid}` profiles on login and session restoration
 *    (`ProfileIncomplete`) without fabricating placeholder user data and without signing the user out.
 * 5. Explicitly separates server-verified sessions ([SessionState.Authenticated]) from read-only
 *    offline cached sessions ([SessionState.AuthenticatedCachedOffline]); a cached `ACTIVE` profile
 *    is never treated as proof that the account has not since been suspended or banned on the server.
 * 6. Actively observes `FirebaseAuth` state and token changes (`registerAuthStateListener`) without
 *    leaking listeners or retaining stale user/draft state across sign-outs or account switches.
 */
@Singleton
class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val clock: () -> Long
) : AuthRepository, AutoCloseable {

    @Inject
    constructor(remoteDataSource: AuthRemoteDataSource) : this(
        remoteDataSource = remoteDataSource,
        clock = { System.currentTimeMillis() }
    )

    private data class PendingRegistrationState(
        val uid: String,
        val draft: RegistrationDraft
    )

    private data class PendingLegacySignUp(
        val uid: String,
        val fullName: String,
        val email: String,
        val phoneNumber: String
    )

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    @Volatile
    private var pendingRegistrationState: PendingRegistrationState? = null

    @Volatile
    private var pendingLegacySignUp: PendingLegacySignUp? = null

    private val isHandlingRevocation = AtomicBoolean(false)

    private val authStateSubscription: AutoCloseable =
        remoteDataSource.registerAuthStateListener(::onRemoteAuthStateChanged)

    /**
     * Unregisters the underlying Firebase Auth listener when this repository instance is disposed.
     */
    override fun close() {
        authStateSubscription.close()
    }

    /**
     * Synchronizes in-memory session and draft state whenever Firebase Authentication
     * notifies of a sign-in, sign-out, user switch, or ID token update.
     */
    private fun onRemoteAuthStateChanged(remoteUser: RemoteAuthUser?) {
        if (remoteUser == null) {
            pendingRegistrationState = null
            pendingLegacySignUp = null
            // Preserve AuthenticationRevoked when signOut() was triggered by a revocation/disabled check
            if (!isHandlingRevocation.get() && _sessionState.value !is SessionState.AuthenticationRevoked) {
                _sessionState.value = SessionState.Unauthenticated
            }
            return
        }

        // Clear any pending drafts that belonged to a different UID
        if (pendingRegistrationState != null && pendingRegistrationState?.uid != remoteUser.uid) {
            pendingRegistrationState = null
        }
        if (pendingLegacySignUp != null && pendingLegacySignUp?.uid != remoteUser.uid) {
            pendingLegacySignUp = null
        }

        // Synchronize current SessionState with the active Firebase user
        when (val current = _sessionState.value) {
            is SessionState.Authenticated -> {
                if (current.user.id != remoteUser.uid) {
                    // User switched accounts externally; invalidate stale profile immediately
                    _sessionState.value = SessionState.Unauthenticated
                } else if (current.isEmailVerified != remoteUser.isEmailVerified) {
                    _sessionState.value = current.copy(isEmailVerified = remoteUser.isEmailVerified)
                }
            }
            is SessionState.AuthenticatedCachedOffline -> {
                if (current.cachedUser.id != remoteUser.uid) {
                    _sessionState.value = SessionState.Unauthenticated
                } else if (current.isEmailVerified != remoteUser.isEmailVerified) {
                    _sessionState.value = current.copy(isEmailVerified = remoteUser.isEmailVerified)
                }
            }
            is SessionState.AuthenticatedRestricted -> {
                if (current.user.id != remoteUser.uid) {
                    _sessionState.value = SessionState.Unauthenticated
                } else if (current.isEmailVerified != remoteUser.isEmailVerified) {
                    _sessionState.value = current.copy(isEmailVerified = remoteUser.isEmailVerified)
                }
            }
            is SessionState.AuthenticatedProfileIncomplete -> {
                if (current.uid != remoteUser.uid) {
                    _sessionState.value = SessionState.Unauthenticated
                } else if (current.isEmailVerified != remoteUser.isEmailVerified) {
                    _sessionState.value = current.copy(isEmailVerified = remoteUser.isEmailVerified)
                }
            }
            is SessionState.AuthenticatedProfileLoadError -> {
                if (current.uid != remoteUser.uid) {
                    _sessionState.value = SessionState.Unauthenticated
                }
            }
            is SessionState.AuthenticationRevoked,
            SessionState.Unauthenticated -> Unit
        }
    }

    private fun handleRevokedOrDisabledSession(error: AuthError): SessionState.AuthenticationRevoked {
        isHandlingRevocation.set(true)
        try {
            pendingRegistrationState = null
            pendingLegacySignUp = null
            val revokedState = SessionState.AuthenticationRevoked(error)
            _sessionState.value = revokedState
            remoteDataSource.signOut()
            return revokedState
        } finally {
            isHandlingRevocation.set(false)
        }
    }

    // =========================================================================
    // 1. Canonical State & Inspection
    // =========================================================================

    override fun getCurrentUser(): User? {
        return when (val current = _sessionState.value) {
            is SessionState.Authenticated -> current.user
            is SessionState.AuthenticatedRestricted -> current.user
            else -> null
        }
    }

    override fun getCachedOrVerifiedUser(): User? {
        return when (val current = _sessionState.value) {
            is SessionState.Authenticated -> current.user
            is SessionState.AuthenticatedCachedOffline -> current.cachedUser
            is SessionState.AuthenticatedRestricted -> current.user
            else -> null
        }
    }

    override fun isServerVerifiedSession(): Boolean {
        return _sessionState.value is SessionState.Authenticated
    }

    override fun getPendingRegistrationDraft(): RegistrationDraft? {
        val currentUid = remoteDataSource.getCurrentAuthUser()?.uid
        val pending = pendingRegistrationState
        if (pending != null && (currentUid == null || pending.uid == currentUid)) {
            return pending.draft
        }
        return (_sessionState.value as? SessionState.AuthenticatedProfileIncomplete)?.pendingDraft
    }

    override fun isEmailVerified(): Boolean {
        val remoteVerified = remoteDataSource.getCurrentAuthUser()?.isEmailVerified
        if (remoteVerified != null) return remoteVerified
        return when (val state = _sessionState.value) {
            is SessionState.Authenticated -> state.isEmailVerified
            is SessionState.AuthenticatedCachedOffline -> state.isEmailVerified
            is SessionState.AuthenticatedRestricted -> state.isEmailVerified
            is SessionState.AuthenticatedProfileIncomplete -> state.isEmailVerified
            else -> false
        }
    }

    // =========================================================================
    // 2. Canonical Registration Engine
    // =========================================================================

    override suspend fun registerResident(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String
    ): RegistrationOutcome {
        return registerUser(
            fullName = fullName,
            email = email,
            phoneNumber = phoneNumber,
            password = password,
            confirmPassword = confirmPassword,
            role = UserRole.RESIDENT
        )
    }

    override suspend fun registerCollector(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String
    ): RegistrationOutcome {
        return registerUser(
            fullName = fullName,
            email = email,
            phoneNumber = phoneNumber,
            password = password,
            confirmPassword = confirmPassword,
            role = UserRole.COLLECTOR
        )
    }

    override suspend fun registerUser(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
        role: UserRole
    ): RegistrationOutcome {
        // 1. Role security check before any other processing or network call
        val roleCheck = AuthValidator.validateSelfRegistrationRole(role)
        if (roleCheck is ValidationResult.Invalid) {
            return RegistrationOutcome.Failure(
                AuthError.UnauthorizedRoleSelection(roleCheck.message)
            )
        }

        // 2. Comprehensive input validation via AuthValidator before any Firebase call
        val validationFailures = mutableListOf<FieldValidationFailure>()

        val nameResult = AuthValidator.validateFullName(fullName)
        if (nameResult is ValidationResult.Invalid) {
            validationFailures.add(
                FieldValidationFailure("fullName", nameResult.error, nameResult.message)
            )
        }

        val emailResult = AuthValidator.validateEmail(email)
        if (emailResult is ValidationResult.Invalid) {
            validationFailures.add(
                FieldValidationFailure("email", emailResult.error, emailResult.message)
            )
        }

        val phoneResult = AuthValidator.validateAndNormalizePhone(phoneNumber)
        if (phoneResult is ValidationResult.Invalid) {
            validationFailures.add(
                FieldValidationFailure("phoneNumber", phoneResult.error, phoneResult.message)
            )
        }

        val passwordResult = AuthValidator.validatePassword(password)
        if (passwordResult is ValidationResult.Invalid) {
            validationFailures.add(
                FieldValidationFailure("password", passwordResult.error, passwordResult.message)
            )
        }

        val confirmResult = AuthValidator.validatePasswordConfirmation(password, confirmPassword)
        if (confirmResult is ValidationResult.Invalid) {
            validationFailures.add(
                FieldValidationFailure("confirmPassword", confirmResult.error, confirmResult.message)
            )
        }

        if (validationFailures.isNotEmpty()) {
            return RegistrationOutcome.Failure(
                AuthError.ValidationFailed(errors = validationFailures)
            )
        }

        val normalizedName = (nameResult as ValidationResult.Valid).value
        val normalizedEmail = (emailResult as ValidationResult.Valid).value
        val normalizedPhone = (phoneResult as ValidationResult.Valid).value

        val draft = RegistrationDraft(
            fullName = normalizedName,
            email = normalizedEmail,
            phoneNumber = normalizedPhone,
            role = role
        )

        // 3. Create Firebase Auth user (or reuse already-created session from same-session partial failure)
        val existingSessionUser = remoteDataSource.getCurrentAuthUser()
        val pendingState = pendingRegistrationState
        val authUser = if (
            existingSessionUser != null &&
            existingSessionUser.email?.lowercase() == normalizedEmail &&
            pendingState != null &&
            pendingState.uid == existingSessionUser.uid
        ) {
            existingSessionUser
        } else {
            try {
                remoteDataSource.createAuthUser(normalizedEmail, password)
            } catch (e: Throwable) {
                val mappedError = FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = normalizedEmail
                )
                return RegistrationOutcome.Failure(mappedError)
            }
        }

        // Preserve authenticated session + registration draft immediately in case Firestore fails
        pendingRegistrationState = PendingRegistrationState(
            uid = authUser.uid,
            draft = draft
        )

        // Best-effort Firebase Auth display name update (non-fatal if offline)
        try {
            remoteDataSource.updateAuthDisplayName(normalizedName)
        } catch (_: Throwable) {
            // Non-fatal; Firestore /users/{uid}.fullName is the canonical display name
        }

        // 4. Initialize canonical /users/{uid} Firestore document and trigger verification email
        return when (val initOutcome = initializeProfileForAuthenticatedUser(authUser, draft)) {
            is ProfileInitializationOutcome.Success -> {
                RegistrationOutcome.Success(
                    user = initOutcome.user,
                    verificationEmailSent = initOutcome.verificationEmailSent,
                    emailSendError = initOutcome.emailSendError
                )
            }
            is ProfileInitializationOutcome.Failure -> {
                _sessionState.value = SessionState.AuthenticatedProfileIncomplete(
                    uid = authUser.uid,
                    email = normalizedEmail,
                    isEmailVerified = authUser.isEmailVerified,
                    reason = ProfileIncompleteReason.PROFILE_WRITE_FAILED,
                    pendingDraft = draft
                )
                RegistrationOutcome.PartialSuccessProfilePending(
                    uid = authUser.uid,
                    email = normalizedEmail,
                    draft = draft,
                    profileError = initOutcome.error
                )
            }
        }
    }

    // =========================================================================
    // 3. Ghost Account Recovery (Same-Session & Cross-Session)
    // =========================================================================

    override suspend fun retryProfileInitialization(
        overrideDraft: RegistrationDraft?
    ): ProfileInitializationOutcome {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return ProfileInitializationOutcome.Failure(AuthError.Unauthenticated())

        val candidateDraft = overrideDraft
            ?: pendingRegistrationState?.takeIf { it.uid == authUser.uid }?.draft
            ?: (_sessionState.value as? SessionState.AuthenticatedProfileIncomplete)?.pendingDraft
            ?: return ProfileInitializationOutcome.Failure(
                error = AuthError.ProfileNotFound(
                    uid = authUser.uid,
                    email = authUser.email.orEmpty(),
                    message = "No pending registration draft found. Please provide profile details to complete recovery."
                )
            )

        // Validate the draft before writing to Firestore
        val validatedDraftResult = validateDraftForAuthenticatedUser(
            authUser = authUser,
            rawFullName = candidateDraft.fullName,
            rawPhoneNumber = candidateDraft.phoneNumber,
            role = candidateDraft.role
        )
        if (validatedDraftResult is ProfileDraftValidation.Invalid) {
            return ProfileInitializationOutcome.Failure(
                error = validatedDraftResult.error,
                draft = candidateDraft
            )
        }

        val validatedDraft = (validatedDraftResult as ProfileDraftValidation.Valid).draft
        pendingRegistrationState = PendingRegistrationState(uid = authUser.uid, draft = validatedDraft)
        return initializeProfileForAuthenticatedUser(authUser, validatedDraft)
    }

    override suspend fun completeMissingProfile(
        input: ProfileRecoveryInput
    ): ProfileInitializationOutcome {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return ProfileInitializationOutcome.Failure(AuthError.Unauthenticated())

        val validatedDraftResult = validateDraftForAuthenticatedUser(
            authUser = authUser,
            rawFullName = input.fullName,
            rawPhoneNumber = input.phoneNumber,
            role = input.role
        )
        if (validatedDraftResult is ProfileDraftValidation.Invalid) {
            return ProfileInitializationOutcome.Failure(error = validatedDraftResult.error)
        }

        val validatedDraft = (validatedDraftResult as ProfileDraftValidation.Valid).draft
        pendingRegistrationState = PendingRegistrationState(uid = authUser.uid, draft = validatedDraft)
        return initializeProfileForAuthenticatedUser(authUser, validatedDraft)
    }

    private sealed interface ProfileDraftValidation {
        data class Valid(val draft: RegistrationDraft) : ProfileDraftValidation
        data class Invalid(val error: AuthError) : ProfileDraftValidation
    }

    private fun validateDraftForAuthenticatedUser(
        authUser: RemoteAuthUser,
        rawFullName: String,
        rawPhoneNumber: String,
        role: UserRole
    ): ProfileDraftValidation {
        val roleResult = AuthValidator.validateSelfRegistrationRole(role)
        if (roleResult is ValidationResult.Invalid) {
            return ProfileDraftValidation.Invalid(
                AuthError.UnauthorizedRoleSelection(roleResult.message)
            )
        }

        val failures = mutableListOf<FieldValidationFailure>()

        val nameResult = AuthValidator.validateFullName(rawFullName)
        if (nameResult is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("fullName", nameResult.error, nameResult.message))
        }

        val phoneResult = AuthValidator.validateAndNormalizePhone(rawPhoneNumber)
        if (phoneResult is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("phoneNumber", phoneResult.error, phoneResult.message))
        }

        val emailToValidate = authUser.email
            ?: pendingRegistrationState?.takeIf { it.uid == authUser.uid }?.draft?.email
        val emailResult = AuthValidator.validateEmail(emailToValidate)
        if (emailResult is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("email", emailResult.error, emailResult.message))
        }

        if (failures.isNotEmpty()) {
            return ProfileDraftValidation.Invalid(AuthError.ValidationFailed(failures))
        }

        return ProfileDraftValidation.Valid(
            RegistrationDraft(
                fullName = (nameResult as ValidationResult.Valid).value,
                email = (emailResult as ValidationResult.Valid).value,
                phoneNumber = (phoneResult as ValidationResult.Valid).value,
                role = role
            )
        )
    }

    /**
     * Idempotently ensures `/users/{uid}` exists with a valid canonical [User] profile.
     *
     * Idempotency Mechanism:
     * 1. Queries `/users/{uid}` on the server first (`fetchUserDocumentServerFirst`).
     * 2. If a valid canonical profile already exists at `/users/{uid}`, returns it immediately
     *    with `wasAlreadyInitialized = true` without issuing a redundant overwrite.
     * 3. Otherwise builds the canonical [User] via [UserMapper.createInitialProfile], writes
     *    `/users/{uid}` via [UserMapper.toFirestoreMap], and triggers verification email if needed.
     */
    private suspend fun initializeProfileForAuthenticatedUser(
        authUser: RemoteAuthUser,
        draft: RegistrationDraft
    ): ProfileInitializationOutcome {
        // Step 1: Check if /users/{uid} was already created on a previous attempt
        val existingCheck = try {
            remoteDataSource.fetchUserDocumentServerFirst(authUser.uid)
        } catch (_: Throwable) {
            null
        }

        if (existingCheck is RemoteDocumentResult.Found && !existingCheck.isFromCache) {
            val inspected = UserMapper.inspectFirestoreMap(existingCheck.data, authUser.uid)
            if (inspected is ProfileMappingResult.Valid) {
                pendingRegistrationState = null
                pendingLegacySignUp = null
                applyServerVerifiedUserToSessionState(
                    user = inspected.user,
                    authUser = authUser
                )
                return ProfileInitializationOutcome.Success(
                    user = inspected.user,
                    verificationEmailSent = authUser.isEmailVerified,
                    emailSendError = null,
                    wasAlreadyInitialized = true
                )
            }
        }

        // Step 2: Construct canonical User and write /users/{uid}
        val newUser = UserMapper.createInitialProfile(
            uid = authUser.uid,
            fullName = draft.fullName,
            email = draft.email,
            phoneNumber = draft.phoneNumber,
            role = draft.role,
            nowEpochMs = clock()
        )
        val firestoreMap = UserMapper.toFirestoreMap(newUser)

        try {
            remoteDataSource.writeUserDocument(authUser.uid, firestoreMap)
        } catch (e: Throwable) {
            val mappedError = FirebaseErrorMapper.toAuthError(
                throwable = e,
                emailContext = draft.email,
                uidContext = authUser.uid
            )
            _sessionState.value = SessionState.AuthenticatedProfileIncomplete(
                uid = authUser.uid,
                email = draft.email,
                isEmailVerified = authUser.isEmailVerified,
                reason = ProfileIncompleteReason.PROFILE_WRITE_FAILED,
                pendingDraft = draft
            )
            return ProfileInitializationOutcome.Failure(
                error = mappedError,
                draft = draft
            )
        }

        // Step 3: Profile creation succeeded; clear pending drafts and update session state
        pendingRegistrationState = null
        pendingLegacySignUp = null

        var emailSent = false
        var emailSendError: AuthError? = null
        if (!authUser.isEmailVerified) {
            try {
                remoteDataSource.sendEmailVerification()
                emailSent = true
            } catch (e: Throwable) {
                emailSent = false
                emailSendError = FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = draft.email,
                    uidContext = authUser.uid
                )
            }
        } else {
            emailSent = true
        }

        applyServerVerifiedUserToSessionState(
            user = newUser,
            authUser = authUser
        )

        return ProfileInitializationOutcome.Success(
            user = newUser,
            verificationEmailSent = emailSent,
            emailSendError = emailSendError,
            wasAlreadyInitialized = false
        )
    }

    // =========================================================================
    // 4. Login & Profile Resolution
    // =========================================================================

    override suspend fun login(
        email: String,
        password: String
    ): LoginOutcome {
        // 1. Validate inputs before calling Firebase Auth
        val failures = mutableListOf<FieldValidationFailure>()

        val emailResult = AuthValidator.validateEmail(email)
        if (emailResult is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("email", emailResult.error, emailResult.message))
        }

        val passwordRequired = AuthValidator.validateRequired(password, "Password")
        if (passwordRequired is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("password", passwordRequired.error, passwordRequired.message))
        }

        if (failures.isNotEmpty()) {
            return LoginOutcome.Failure(AuthError.ValidationFailed(failures))
        }

        val normalizedEmail = (emailResult as ValidationResult.Valid).value

        // 2. Authenticate with Firebase Auth
        val signedInUser = try {
            remoteDataSource.signInWithEmailAndPassword(normalizedEmail, password)
        } catch (e: Throwable) {
            val mappedError = FirebaseErrorMapper.toAuthError(
                throwable = e,
                emailContext = normalizedEmail
            )
            return LoginOutcome.Failure(mappedError)
        }

        // 3. Reload user to obtain freshest email verification and custom claims state
        val effectiveAuthUser = try {
            remoteDataSource.reloadCurrentAuthUser(
                forceTokenRefresh = signedInUser.isEmailVerified
            ) ?: signedInUser
        } catch (e: Throwable) {
            val mappedError = FirebaseErrorMapper.toAuthError(
                throwable = e,
                emailContext = normalizedEmail,
                uidContext = signedInUser.uid
            )
            if (FirebaseErrorMapper.isSessionRevokedOrDisabled(mappedError)) {
                handleRevokedOrDisabledSession(mappedError)
                return LoginOutcome.Failure(mappedError)
            }
            // On transient network failure during reload, proceed with signedInUser
            signedInUser
        }

        // 4. Resolve /users/{uid} strictly from the server (allowCacheFallback = false during login
        //    so stale local cache can never mask a server-side suspension or ban).
        return when (val session = resolveProfileAndUpdateSession(effectiveAuthUser, allowCacheFallback = false)) {
            is SessionState.Authenticated -> LoginOutcome.Authenticated(
                user = session.user,
                isEmailVerified = session.isEmailVerified,
                hasAdminClaim = session.hasAdminClaim
            )
            is SessionState.AuthenticatedRestricted -> LoginOutcome.AccountLocked(
                user = session.user,
                isEmailVerified = session.isEmailVerified,
                accountStatus = session.accountStatus,
                statusReason = session.statusReason,
                suspendedUntilEpochMs = session.suspendedUntilEpochMs
            )
            is SessionState.AuthenticatedProfileIncomplete -> LoginOutcome.ProfileIncomplete(
                uid = session.uid,
                email = session.email,
                isEmailVerified = session.isEmailVerified,
                reason = session.reason,
                suggestedDraft = session.pendingDraft
            )
            is SessionState.AuthenticatedProfileLoadError -> LoginOutcome.ProfileLoadFailed(
                uid = session.uid,
                email = session.email,
                error = session.error
            )
            is SessionState.AuthenticatedCachedOffline -> LoginOutcome.ProfileLoadFailed(
                uid = effectiveAuthUser.uid,
                email = normalizedEmail,
                error = session.networkError
            )
            is SessionState.AuthenticationRevoked -> LoginOutcome.Failure(session.error)
            SessionState.Unauthenticated -> LoginOutcome.Failure(AuthError.Unauthenticated())
        }
    }

    // =========================================================================
    // 5. Email Verification Operations
    // =========================================================================

    override suspend fun sendEmailVerification(): EmailVerificationOutcome {
        return dispatchEmailVerificationInternal()
    }

    override suspend fun resendEmailVerification(): EmailVerificationOutcome {
        return dispatchEmailVerificationInternal()
    }

    private suspend fun dispatchEmailVerificationInternal(): EmailVerificationOutcome {
        val currentUser = remoteDataSource.getCurrentAuthUser()
            ?: return EmailVerificationOutcome.Failure(AuthError.Unauthenticated())

        val userEmail = currentUser.email.orEmpty()
        if (currentUser.isEmailVerified) {
            return EmailVerificationOutcome.AlreadyVerified(userEmail)
        }

        return try {
            remoteDataSource.sendEmailVerification()
            EmailVerificationOutcome.Sent(userEmail)
        } catch (e: Throwable) {
            val mappedError = FirebaseErrorMapper.toAuthError(
                throwable = e,
                emailContext = userEmail,
                uidContext = currentUser.uid
            )
            EmailVerificationOutcome.Failure(mappedError)
        }
    }

    override suspend fun refreshEmailVerificationStatus(): EmailVerificationOutcome {
        val currentUser = remoteDataSource.getCurrentAuthUser()
            ?: return EmailVerificationOutcome.Failure(AuthError.Unauthenticated())

        val reloadedUser = try {
            remoteDataSource.reloadCurrentAuthUser(forceTokenRefresh = false)
                ?: return EmailVerificationOutcome.Failure(AuthError.Unauthenticated())
        } catch (e: Throwable) {
            val mappedError = FirebaseErrorMapper.toAuthError(
                throwable = e,
                emailContext = currentUser.email,
                uidContext = currentUser.uid
            )
            if (FirebaseErrorMapper.isSessionRevokedOrDisabled(mappedError)) {
                handleRevokedOrDisabledSession(mappedError)
            }
            return EmailVerificationOutcome.Failure(mappedError)
        }

        var tokenRefreshed = false
        var finalUser = reloadedUser

        if (reloadedUser.isEmailVerified) {
            try {
                finalUser = remoteDataSource.refreshIdToken() ?: reloadedUser
                tokenRefreshed = true
            } catch (e: Throwable) {
                val mappedError = FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = reloadedUser.email,
                    uidContext = reloadedUser.uid
                )
                return EmailVerificationOutcome.Failure(mappedError)
            }
        }

        // Synchronize in-memory SessionState with the refreshed verification & claim status
        when (val existingState = _sessionState.value) {
            is SessionState.Authenticated -> {
                _sessionState.value = existingState.copy(
                    isEmailVerified = finalUser.isEmailVerified,
                    hasAdminClaim = finalUser.hasAdminClaim
                )
            }
            is SessionState.AuthenticatedCachedOffline -> {
                _sessionState.value = existingState.copy(
                    isEmailVerified = finalUser.isEmailVerified
                )
            }
            is SessionState.AuthenticatedRestricted -> {
                _sessionState.value = existingState.copy(
                    isEmailVerified = finalUser.isEmailVerified
                )
            }
            is SessionState.AuthenticatedProfileIncomplete -> {
                _sessionState.value = existingState.copy(
                    isEmailVerified = finalUser.isEmailVerified
                )
            }
            else -> Unit
        }

        return EmailVerificationOutcome.StatusRefreshed(
            isEmailVerified = finalUser.isEmailVerified,
            tokenRefreshed = tokenRefreshed
        )
    }

    // =========================================================================
    // 6. Password Recovery
    // =========================================================================

    override suspend fun sendPasswordReset(email: String): PasswordResetOutcome {
        val emailResult = AuthValidator.validateEmail(email)
        if (emailResult is ValidationResult.Invalid) {
            return PasswordResetOutcome.Failure(
                AuthError.ValidationFailed(
                    listOf(FieldValidationFailure("email", emailResult.error, emailResult.message))
                )
            )
        }

        val normalizedEmail = (emailResult as ValidationResult.Valid).value

        return try {
            remoteDataSource.sendPasswordResetEmail(normalizedEmail)
            PasswordResetOutcome.ResetEmailDispatched(normalizedEmail)
        } catch (e: Throwable) {
            val mappedError = FirebaseErrorMapper.toAuthError(
                throwable = e,
                emailContext = normalizedEmail
            )
            // Enumeration safety: never reveal whether an email address is registered
            if (mappedError is AuthError.AccountNotFound) {
                PasswordResetOutcome.ResetEmailDispatched(normalizedEmail)
            } else {
                PasswordResetOutcome.Failure(mappedError)
            }
        }
    }

    // =========================================================================
    // 7. Session Management & Logout
    // =========================================================================

    override suspend fun restoreSession(): SessionState {
        val currentAuthUser = remoteDataSource.getCurrentAuthUser()
        if (currentAuthUser == null) {
            pendingRegistrationState = null
            pendingLegacySignUp = null
            _sessionState.value = SessionState.Unauthenticated
            return SessionState.Unauthenticated
        }

        // Reload Firebase user to detect server-side revocation/disabling or updated verification status.
        // Do NOT destroy a valid session if reload fails due to temporary offline/network error.
        val effectiveAuthUser = try {
            val reloaded = remoteDataSource.reloadCurrentAuthUser(forceTokenRefresh = false)
            if (reloaded == null) {
                pendingRegistrationState = null
                pendingLegacySignUp = null
                _sessionState.value = SessionState.Unauthenticated
                return SessionState.Unauthenticated
            }
            if (reloaded.isEmailVerified && !currentAuthUser.isEmailVerified) {
                remoteDataSource.refreshIdToken() ?: reloaded
            } else {
                reloaded
            }
        } catch (e: Throwable) {
            val mappedError = FirebaseErrorMapper.toAuthError(
                throwable = e,
                emailContext = currentAuthUser.email,
                uidContext = currentAuthUser.uid
            )
            if (FirebaseErrorMapper.isSessionRevokedOrDisabled(mappedError)) {
                return handleRevokedOrDisabledSession(mappedError)
            }
            // Temporary network failure during reload: keep currentAuthUser and resolve profile
            currentAuthUser
        }

        return resolveProfileAndUpdateSession(effectiveAuthUser, allowCacheFallback = true)
    }

    override suspend fun logout(): LogoutOutcome {
        return try {
            pendingRegistrationState = null
            pendingLegacySignUp = null
            _sessionState.value = SessionState.Unauthenticated
            remoteDataSource.signOut()
            LogoutOutcome.Success
        } catch (e: Throwable) {
            LogoutOutcome.Failure(FirebaseErrorMapper.toAuthError(e))
        }
    }

    // =========================================================================
    // 8. Self-Service Profile Lifecycle Operations
    // =========================================================================

    override suspend fun completeResidentOnboarding(
        defaultZoneId: String?
    ): ProfileUpdateOutcome {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return ProfileUpdateOutcome.Failure(AuthError.Unauthenticated())

        if (!authUser.isEmailVerified) {
            return ProfileUpdateOutcome.Failure(AuthError.EmailNotVerified())
        }

        val serverVerifiedProfile = requireServerVerifiedActiveProfile(authUser)
            ?: return ProfileUpdateOutcome.Failure(resolveNonVerifiedProfileUpdateError(authUser))

        if (serverVerifiedProfile.role != UserRole.RESIDENT) {
            return ProfileUpdateOutcome.Failure(
                AuthError.ValidationFailed(
                    listOf(
                        FieldValidationFailure(
                            field = "role",
                            error = ValidationError.ROLE_NOT_SELECTED,
                            message = "Only resident accounts can complete resident onboarding."
                        )
                    )
                )
            )
        }

        val normalizedZone = defaultZoneId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedZone != null) {
            val zoneRes = AuthValidator.validateServiceZoneId(normalizedZone)
            if (zoneRes is ValidationResult.Invalid) {
                return ProfileUpdateOutcome.Failure(
                    AuthError.ValidationFailed(
                        listOf(FieldValidationFailure("defaultZoneId", zoneRes.error, zoneRes.message))
                    )
                )
            }
        }
        val updatedAt = maxOf(clock(), serverVerifiedProfile.updatedAtEpochMs + 1L)

        val updateFields = mutableMapOf<String, Any?>(
            "onboardingStatus" to OnboardingStatus.COMPLETED.name,
            "defaultZoneId" to normalizedZone,
            "updatedAtEpochMs" to updatedAt
        )

        return try {
            remoteDataSource.updateUserDocument(authUser.uid, updateFields)
            val updatedUser = serverVerifiedProfile.copy(
                onboardingStatus = OnboardingStatus.COMPLETED,
                defaultZoneId = normalizedZone,
                updatedAtEpochMs = updatedAt
            )
            applyServerVerifiedUserToSessionState(updatedUser, authUser)
            ProfileUpdateOutcome.Success(updatedUser)
        } catch (e: Throwable) {
            ProfileUpdateOutcome.Failure(
                FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = authUser.email,
                    uidContext = authUser.uid
                )
            )
        }
    }

    override suspend fun updateProfile(
        fullName: String,
        phoneNumber: String,
        defaultZoneId: String?,
        preferredLanguage: String?
    ): ProfileUpdateOutcome {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return ProfileUpdateOutcome.Failure(AuthError.Unauthenticated())

        if (!authUser.isEmailVerified) {
            return ProfileUpdateOutcome.Failure(AuthError.EmailNotVerified())
        }

        val failures = mutableListOf<FieldValidationFailure>()
        val nameResult = AuthValidator.validateFullName(fullName)
        if (nameResult is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("fullName", nameResult.error, nameResult.message))
        }
        val phoneResult = AuthValidator.validateAndNormalizePhone(phoneNumber)
        if (phoneResult is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("phoneNumber", phoneResult.error, phoneResult.message))
        }
        if (failures.isNotEmpty()) {
            return ProfileUpdateOutcome.Failure(AuthError.ValidationFailed(failures))
        }

        val normalizedName = (nameResult as ValidationResult.Valid).value
        val normalizedPhone = (phoneResult as ValidationResult.Valid).value
        val normalizedZone = defaultZoneId?.trim()?.takeIf { it.isNotEmpty() }

        val serverVerifiedProfile = requireServerVerifiedActiveProfile(authUser)
            ?: return ProfileUpdateOutcome.Failure(resolveNonVerifiedProfileUpdateError(authUser))

        val normalizedLang = preferredLanguage?.trim()?.takeIf { it.isNotEmpty() }
            ?: serverVerifiedProfile.preferredLanguage
        val updatedAt = maxOf(clock(), serverVerifiedProfile.updatedAtEpochMs + 1L)

        val updateFields = mapOf<String, Any?>(
            "fullName" to normalizedName,
            "name" to normalizedName,
            "phoneNumber" to normalizedPhone,
            "phone" to normalizedPhone,
            "defaultZoneId" to normalizedZone,
            "preferredLanguage" to normalizedLang,
            "updatedAtEpochMs" to updatedAt
        )

        return try {
            remoteDataSource.updateUserDocument(authUser.uid, updateFields)
            val updatedUser = serverVerifiedProfile.copy(
                fullName = normalizedName,
                phoneNumber = normalizedPhone,
                defaultZoneId = normalizedZone,
                preferredLanguage = normalizedLang,
                updatedAtEpochMs = updatedAt
            )
            applyServerVerifiedUserToSessionState(updatedUser, authUser)
            ProfileUpdateOutcome.Success(updatedUser)
        } catch (e: Throwable) {
            ProfileUpdateOutcome.Failure(
                FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = authUser.email,
                    uidContext = authUser.uid
                )
            )
        }
    }

    // =========================================================================
    // 8a. Collector Onboarding & Vetting Operations (Stage 5)
    // =========================================================================

    override suspend fun submitCollectorOnboarding(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return CollectorSubmissionOutcome.Failure(AuthError.Unauthenticated())

        if (!authUser.isEmailVerified) {
            return CollectorSubmissionOutcome.Failure(AuthError.EmailNotVerified())
        }

        val serverVerifiedProfile = requireServerVerifiedActiveProfile(authUser)
            ?: return CollectorSubmissionOutcome.Failure(resolveNonVerifiedProfileUpdateError(authUser))

        if (serverVerifiedProfile.role != UserRole.COLLECTOR) {
            return CollectorSubmissionOutcome.Failure(
                AuthError.UnauthorizedRoleSelection("Only accounts with role COLLECTOR can submit fleet verification.")
            )
        }

        if (serverVerifiedProfile.collectorVerificationStatus != CollectorVerificationStatus.NOT_SUBMITTED) {
            return CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Collector fleet verification has already been submitted or processed.")
            )
        }

        val failures = mutableListOf<FieldValidationFailure>()
        val vehicleTypeRes = AuthValidator.validateVehicleType(input.vehicleType)
        if (vehicleTypeRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehicleType", vehicleTypeRes.error, vehicleTypeRes.message))
        }
        val plateRes = AuthValidator.validateVehiclePlateNumber(input.vehiclePlateNumber)
        if (plateRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehiclePlateNumber", plateRes.error, plateRes.message))
        }
        val licenseRes = AuthValidator.validateLicenseNumber(input.licenseNumber)
        if (licenseRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("licenseNumber", licenseRes.error, licenseRes.message))
        }
        val zoneRes = AuthValidator.validateServiceZoneId(input.serviceZoneId)
        if (zoneRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("serviceZoneId", zoneRes.error, zoneRes.message))
        }

        if (failures.isNotEmpty()) {
            return CollectorSubmissionOutcome.Failure(AuthError.ValidationFailed(failures))
        }

        val normalizedVehicleType = (vehicleTypeRes as ValidationResult.Valid).value
        val normalizedPlate = (plateRes as ValidationResult.Valid).value
        val normalizedLicense = (licenseRes as ValidationResult.Valid).value
        val normalizedZone = (zoneRes as ValidationResult.Valid).value

        val now = maxOf(clock(), serverVerifiedProfile.updatedAtEpochMs + 1L)

        val submissionData = mapOf<String, Any?>(
            "userId" to authUser.uid,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "status" to "PENDING_REVIEW",
            "submissionAttempt" to 1,
            "submittedAtEpochMs" to now,
            "reviewedByUid" to null,
            "reviewedAtEpochMs" to null,
            "rejectionReason" to null,
            "adminNotes" to null,
            "lastRejectionReason" to null,
            "lastReviewedByUid" to null,
            "lastReviewedAtEpochMs" to null
        )

        val historyData = mapOf<String, Any?>(
            "attemptId" to "attempt_1",
            "userId" to authUser.uid,
            "eventType" to "SUBMITTED",
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "submissionAttempt" to 1,
            "timestampEpochMs" to now,
            "actorUid" to authUser.uid,
            "rejectionReason" to null
        )

        val userUpdateFields = mapOf<String, Any?>(
            "collectorVerificationStatus" to "PENDING_REVIEW",
            "onboardingStatus" to OnboardingStatus.COMPLETED.name,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "defaultZoneId" to normalizedZone,
            "updatedAtEpochMs" to now
        )

        return try {
            remoteDataSource.executeCollectorInitialSubmissionBatch(
                uid = authUser.uid,
                submissionData = submissionData,
                historyData = historyData,
                attemptId = "attempt_1",
                userUpdateFields = userUpdateFields
            )

            val updatedUser = serverVerifiedProfile.copy(
                collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
                onboardingStatus = OnboardingStatus.COMPLETED,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                defaultZoneId = normalizedZone,
                updatedAtEpochMs = now
            )
            applyServerVerifiedUserToSessionState(updatedUser, authUser)

            val submission = CollectorSubmission(
                userId = authUser.uid,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                licenseNumber = normalizedLicense,
                serviceZoneId = normalizedZone,
                status = CollectorVerificationStatus.PENDING_REVIEW,
                submissionAttempt = 1,
                submittedAtEpochMs = now
            )
            CollectorSubmissionOutcome.Success(submission, updatedUser)
        } catch (e: Throwable) {
            CollectorSubmissionOutcome.Failure(
                FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = authUser.email,
                    uidContext = authUser.uid
                )
            )
        }
    }

    override suspend fun resubmitCollectorOnboarding(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return CollectorSubmissionOutcome.Failure(AuthError.Unauthenticated())

        if (!authUser.isEmailVerified) {
            return CollectorSubmissionOutcome.Failure(AuthError.EmailNotVerified())
        }

        val serverVerifiedProfile = requireServerVerifiedActiveProfile(authUser)
            ?: return CollectorSubmissionOutcome.Failure(resolveNonVerifiedProfileUpdateError(authUser))

        if (serverVerifiedProfile.role != UserRole.COLLECTOR) {
            return CollectorSubmissionOutcome.Failure(
                AuthError.UnauthorizedRoleSelection("Only accounts with role COLLECTOR can resubmit fleet verification.")
            )
        }

        if (serverVerifiedProfile.collectorVerificationStatus != CollectorVerificationStatus.REJECTED) {
            return CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Resubmission is only permitted for REJECTED applications.")
            )
        }

        val existingResult = remoteDataSource.fetchCollectorSubmission(authUser.uid)
        val existingData = (existingResult as? RemoteDocumentResult.Found)?.data
            ?: return CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Previous submission record not found.")
            )

        if ((existingData["status"] as? String) != "REJECTED") {
            return CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Existing submission status is not REJECTED.")
            )
        }

        val failures = mutableListOf<FieldValidationFailure>()
        val vehicleTypeRes = AuthValidator.validateVehicleType(input.vehicleType)
        if (vehicleTypeRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehicleType", vehicleTypeRes.error, vehicleTypeRes.message))
        }
        val plateRes = AuthValidator.validateVehiclePlateNumber(input.vehiclePlateNumber)
        if (plateRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehiclePlateNumber", plateRes.error, plateRes.message))
        }
        val licenseRes = AuthValidator.validateLicenseNumber(input.licenseNumber)
        if (licenseRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("licenseNumber", licenseRes.error, licenseRes.message))
        }
        val zoneRes = AuthValidator.validateServiceZoneId(input.serviceZoneId)
        if (zoneRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("serviceZoneId", zoneRes.error, zoneRes.message))
        }

        if (failures.isNotEmpty()) {
            return CollectorSubmissionOutcome.Failure(AuthError.ValidationFailed(failures))
        }

        val normalizedVehicleType = (vehicleTypeRes as ValidationResult.Valid).value
        val normalizedPlate = (plateRes as ValidationResult.Valid).value
        val normalizedLicense = (licenseRes as ValidationResult.Valid).value
        val normalizedZone = (zoneRes as ValidationResult.Valid).value

        val previousAttempt = (existingData["submissionAttempt"] as? Number)?.toInt() ?: 1
        val newAttempt = previousAttempt + 1
        val attemptId = "attempt_$newAttempt"
        val now = maxOf(clock(), serverVerifiedProfile.updatedAtEpochMs + 1L)

        val submissionUpdateData = mapOf<String, Any?>(
            "userId" to authUser.uid,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "status" to "PENDING_REVIEW",
            "submissionAttempt" to newAttempt,
            "submittedAtEpochMs" to now,
            "reviewedByUid" to null,
            "reviewedAtEpochMs" to null,
            "rejectionReason" to null,
            "adminNotes" to existingData["adminNotes"],
            "lastRejectionReason" to existingData["rejectionReason"],
            "lastReviewedByUid" to existingData["reviewedByUid"],
            "lastReviewedAtEpochMs" to existingData["reviewedAtEpochMs"]
        )

        val historyData = mapOf<String, Any?>(
            "attemptId" to attemptId,
            "userId" to authUser.uid,
            "eventType" to "RESUBMITTED",
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "submissionAttempt" to newAttempt,
            "timestampEpochMs" to now,
            "actorUid" to authUser.uid,
            "rejectionReason" to null
        )

        val userUpdateFields = mapOf<String, Any?>(
            "collectorVerificationStatus" to "PENDING_REVIEW",
            "collectorRejectionReason" to null,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "defaultZoneId" to normalizedZone,
            "updatedAtEpochMs" to now
        )

        return try {
            remoteDataSource.executeCollectorResubmissionBatch(
                uid = authUser.uid,
                submissionUpdateData = submissionUpdateData,
                historyData = historyData,
                attemptId = attemptId,
                userUpdateFields = userUpdateFields
            )

            val updatedUser = serverVerifiedProfile.copy(
                collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
                collectorRejectionReason = null,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                defaultZoneId = normalizedZone,
                updatedAtEpochMs = now
            )
            applyServerVerifiedUserToSessionState(updatedUser, authUser)

            val submission = CollectorSubmission(
                userId = authUser.uid,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                licenseNumber = normalizedLicense,
                serviceZoneId = normalizedZone,
                status = CollectorVerificationStatus.PENDING_REVIEW,
                submissionAttempt = newAttempt,
                submittedAtEpochMs = now,
                lastRejectionReason = existingData["rejectionReason"] as? String,
                lastReviewedByUid = existingData["reviewedByUid"] as? String,
                lastReviewedAtEpochMs = (existingData["reviewedAtEpochMs"] as? Number)?.toLong()
            )
            CollectorSubmissionOutcome.Success(submission, updatedUser)
        } catch (e: Throwable) {
            CollectorSubmissionOutcome.Failure(
                FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = authUser.email,
                    uidContext = authUser.uid
                )
            )
        }
    }

    override suspend fun getCollectorSubmission(uid: String): Result<CollectorSubmission?> {
        return try {
            when (val res = remoteDataSource.fetchCollectorSubmission(uid)) {
                is RemoteDocumentResult.Found -> {
                    val submission = CollectorSubmissionMapper.fromFirestoreMap(res.data)
                    Result.success(submission)
                }
                is RemoteDocumentResult.NotFound -> Result.success(null)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    @VisibleForTesting
    override suspend fun adminApproveCollectorSubmission(
        collectorUid: String,
        adminUid: String
    ): Result<Unit> {
        return try {
            val subResult = remoteDataSource.fetchCollectorSubmission(collectorUid)
            val subData = (subResult as? RemoteDocumentResult.Found)?.data
                ?: return Result.failure(IllegalStateException("No submission found for $collectorUid"))

            val attempt = (subData["submissionAttempt"] as? Number)?.toInt() ?: 1
            val attemptId = "attempt_${attempt}_approved"
            val now = clock()

            val historyData = mapOf<String, Any?>(
                "attemptId" to attemptId,
                "userId" to collectorUid,
                "eventType" to "APPROVED",
                "vehicleType" to (subData["vehicleType"] as? String ?: ""),
                "vehiclePlateNumber" to (subData["vehiclePlateNumber"] as? String ?: ""),
                "licenseNumber" to (subData["licenseNumber"] as? String ?: ""),
                "serviceZoneId" to (subData["serviceZoneId"] as? String ?: ""),
                "submissionAttempt" to attempt,
                "timestampEpochMs" to now,
                "actorUid" to adminUid,
                "rejectionReason" to null
            )

            val submissionUpdateData = mapOf<String, Any?>(
                "status" to "APPROVED",
                "reviewedByUid" to adminUid,
                "reviewedAtEpochMs" to now,
                "rejectionReason" to null
            )

            val userUpdateFields = mapOf<String, Any?>(
                "collectorVerificationStatus" to "APPROVED",
                "isVerified" to true,
                "status" to "ACTIVE",
                "collectorRejectionReason" to null,
                "updatedAtEpochMs" to now
            )

            remoteDataSource.executeAdminCollectorApprovalBatch(
                uid = collectorUid,
                attemptId = attemptId,
                historyData = historyData,
                submissionUpdateData = submissionUpdateData,
                userUpdateFields = userUpdateFields
            )
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    @VisibleForTesting
    override suspend fun adminRejectCollectorSubmission(
        collectorUid: String,
        adminUid: String,
        rejectionReason: String
    ): Result<Unit> {
        return try {
            val subResult = remoteDataSource.fetchCollectorSubmission(collectorUid)
            val subData = (subResult as? RemoteDocumentResult.Found)?.data
                ?: return Result.failure(IllegalStateException("No submission found for $collectorUid"))

            val attempt = (subData["submissionAttempt"] as? Number)?.toInt() ?: 1
            val attemptId = "attempt_${attempt}_rejected"
            val now = clock()

            val historyData = mapOf<String, Any?>(
                "attemptId" to attemptId,
                "userId" to collectorUid,
                "eventType" to "REJECTED",
                "vehicleType" to (subData["vehicleType"] as? String ?: ""),
                "vehiclePlateNumber" to (subData["vehiclePlateNumber"] as? String ?: ""),
                "licenseNumber" to (subData["licenseNumber"] as? String ?: ""),
                "serviceZoneId" to (subData["serviceZoneId"] as? String ?: ""),
                "submissionAttempt" to attempt,
                "timestampEpochMs" to now,
                "actorUid" to adminUid,
                "rejectionReason" to rejectionReason
            )

            val submissionUpdateData = mapOf<String, Any?>(
                "status" to "REJECTED",
                "reviewedByUid" to adminUid,
                "reviewedAtEpochMs" to now,
                "rejectionReason" to rejectionReason
            )

            val userUpdateFields = mapOf<String, Any?>(
                "collectorVerificationStatus" to "REJECTED",
                "isVerified" to false,
                "collectorRejectionReason" to rejectionReason,
                "updatedAtEpochMs" to now
            )

            remoteDataSource.executeAdminCollectorRejectionBatch(
                uid = collectorUid,
                attemptId = attemptId,
                historyData = historyData,
                submissionUpdateData = submissionUpdateData,
                userUpdateFields = userUpdateFields
            )
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Ensures protected operations only execute when the user's `ACTIVE` standing has been
     * verified against `Source.SERVER` (never trusting [SessionState.AuthenticatedCachedOffline]).
     */
    private suspend fun requireServerVerifiedActiveProfile(authUser: RemoteAuthUser): User? {
        val current = _sessionState.value
        if (current is SessionState.Authenticated && current.user.id == authUser.uid) {
            return current.user
        }
        val resolved = resolveProfileAndUpdateSession(authUser, allowCacheFallback = false)
        return (resolved as? SessionState.Authenticated)?.user
    }

    private fun resolveNonVerifiedProfileUpdateError(authUser: RemoteAuthUser): AuthError {
        return when (val state = _sessionState.value) {
            is SessionState.AuthenticatedCachedOffline -> state.networkError
            is SessionState.AuthenticatedProfileLoadError -> state.error
            is SessionState.AuthenticatedRestricted -> AuthError.AccountLockedError(
                accountStatus = state.accountStatus,
                statusReason = state.statusReason,
                suspendedUntilEpochMs = state.suspendedUntilEpochMs
            )
            is SessionState.AuthenticationRevoked -> state.error
            else -> AuthError.ProfileNotFound(authUser.uid, authUser.email.orEmpty())
        }
    }

    // =========================================================================
    // 9. Internal Profile Resolution & Bounded Retry Engine
    // =========================================================================

    /**
     * Fetches `/users/{uid}` from Firestore with a bounded retry on transient network failure,
     * then maps the document into the authoritative [SessionState].
     *
     * Session Safety Rules:
     * - When [allowCacheFallback] is `false` (during `login()` and before protected writes),
     *   stale local cache is never consulted.
     * - When [allowCacheFallback] is `true` (during `restoreSession()`), a cached `ACTIVE` document
     *   maps strictly to [SessionState.AuthenticatedCachedOffline] (`isServerVerified == false`),
     *   never [SessionState.Authenticated].
     * - Never fabricates a synthetic default user when `/users/{uid}` is missing, malformed, or unreachable.
     */
    private suspend fun resolveProfileAndUpdateSession(
        authUser: RemoteAuthUser,
        allowCacheFallback: Boolean
    ): SessionState {
        val resolvedEmail = authUser.email?.trim()?.lowercase().orEmpty()

        val documentResult = fetchUserDocumentWithBoundedRetry(
            uid = authUser.uid,
            emailContext = resolvedEmail,
            allowCacheFallback = allowCacheFallback
        )

        val (rawDocResult, serverNetworkError) = when (documentResult) {
            is DocumentFetchOutcome.Success -> documentResult.result to documentResult.fallbackReason
            is DocumentFetchOutcome.Failure -> {
                if (FirebaseErrorMapper.isSessionRevokedOrDisabled(documentResult.error)) {
                    return handleRevokedOrDisabledSession(documentResult.error)
                }
                val errorState = SessionState.AuthenticatedProfileLoadError(
                    uid = authUser.uid,
                    email = resolvedEmail,
                    error = documentResult.error
                )
                _sessionState.value = errorState
                return errorState
            }
        }

        return when (rawDocResult) {
            is RemoteDocumentResult.NotFound -> {
                val suggestedDraft = pendingRegistrationState
                    ?.takeIf { it.uid == authUser.uid }
                    ?.draft
                val incompleteState = SessionState.AuthenticatedProfileIncomplete(
                    uid = authUser.uid,
                    email = resolvedEmail,
                    isEmailVerified = authUser.isEmailVerified,
                    reason = ProfileIncompleteReason.DOCUMENT_MISSING,
                    pendingDraft = suggestedDraft
                )
                _sessionState.value = incompleteState
                incompleteState
            }

            is RemoteDocumentResult.Found -> {
                when (val inspected = UserMapper.inspectFirestoreMap(rawDocResult.data, authUser.uid)) {
                    is ProfileMappingResult.Incomplete -> {
                        val suggestedDraft = pendingRegistrationState
                            ?.takeIf { it.uid == authUser.uid }
                            ?.draft
                        val incompleteState = SessionState.AuthenticatedProfileIncomplete(
                            uid = authUser.uid,
                            email = resolvedEmail,
                            isEmailVerified = authUser.isEmailVerified,
                            reason = inspected.reason,
                            pendingDraft = suggestedDraft
                        )
                        _sessionState.value = incompleteState
                        incompleteState
                    }

                    is ProfileMappingResult.Malformed -> {
                        val malformedError = AuthError.MalformedProfile(
                            uid = authUser.uid,
                            reason = inspected.reason
                        )
                        val errorState = SessionState.AuthenticatedProfileLoadError(
                            uid = authUser.uid,
                            email = resolvedEmail,
                            error = malformedError
                        )
                        _sessionState.value = errorState
                        errorState
                    }

                    is ProfileMappingResult.Valid -> {
                        val domainUser = inspected.user
                        var effectiveAuth = authUser

                        // Enforce server-side custom claim verification for ADMIN profiles
                        if (domainUser.role == UserRole.ADMIN && !effectiveAuth.hasAdminClaim) {
                            val refreshed = try {
                                remoteDataSource.refreshIdToken()
                            } catch (_: Throwable) {
                                null
                            }
                            if (refreshed != null) {
                                effectiveAuth = refreshed
                            }
                            if (!effectiveAuth.hasAdminClaim) {
                                val claimError = AuthError.UnauthorizedAdminClaim()
                                val errorState = SessionState.AuthenticatedProfileLoadError(
                                    uid = effectiveAuth.uid,
                                    email = resolvedEmail,
                                    error = claimError
                                )
                                _sessionState.value = errorState
                                return errorState
                            }
                        }

                        if (pendingRegistrationState?.uid == effectiveAuth.uid) {
                            pendingRegistrationState = null
                        }
                        if (pendingLegacySignUp?.uid == effectiveAuth.uid) {
                            pendingLegacySignUp = null
                        }

                        // If the document came from local cache because the server was unreachable:
                        // - If the cached document is already RESTRICTED/SUSPENDED/BANNED, enforce restriction immediately.
                        // - If the cached document is ACTIVE, return AuthenticatedCachedOffline (NEVER Authenticated)
                        //   so cached ACTIVE standing is never treated as proof that the account wasn't suspended/banned.
                        if (rawDocResult.isFromCache) {
                            return applyCachedOfflineUserToSessionState(
                                user = domainUser,
                                authUser = effectiveAuth,
                                networkError = serverNetworkError
                                    ?: AuthError.NetworkFailure("Offline: account standing has not been verified with the server.")
                            )
                        }

                        applyServerVerifiedUserToSessionState(
                            user = domainUser,
                            authUser = effectiveAuth
                        )
                    }
                }
            }
        }
    }

    private fun applyServerVerifiedUserToSessionState(
        user: User,
        authUser: RemoteAuthUser
    ): SessionState {
        val nextState = when (user.accountStatus) {
            AccountStatus.ACTIVE -> SessionState.Authenticated(
                user = user,
                isEmailVerified = authUser.isEmailVerified,
                hasAdminClaim = authUser.hasAdminClaim
            )
            AccountStatus.RESTRICTED,
            AccountStatus.SUSPENDED,
            AccountStatus.BANNED -> SessionState.AuthenticatedRestricted(
                user = user,
                isEmailVerified = authUser.isEmailVerified,
                accountStatus = user.accountStatus,
                statusReason = user.statusReason,
                suspendedUntilEpochMs = user.suspendedUntilEpochMs
            )
        }
        _sessionState.value = nextState
        return nextState
    }

    private fun applyCachedOfflineUserToSessionState(
        user: User,
        authUser: RemoteAuthUser,
        networkError: AuthError.NetworkFailure
    ): SessionState {
        val nextState = when (user.accountStatus) {
            AccountStatus.ACTIVE -> SessionState.AuthenticatedCachedOffline(
                cachedUser = user,
                isEmailVerified = authUser.isEmailVerified,
                networkError = networkError
            )
            AccountStatus.RESTRICTED,
            AccountStatus.SUSPENDED,
            AccountStatus.BANNED -> SessionState.AuthenticatedRestricted(
                user = user,
                isEmailVerified = authUser.isEmailVerified,
                accountStatus = user.accountStatus,
                statusReason = user.statusReason,
                suspendedUntilEpochMs = user.suspendedUntilEpochMs
            )
        }
        _sessionState.value = nextState
        return nextState
    }

    private sealed interface DocumentFetchOutcome {
        data class Success(
            val result: RemoteDocumentResult,
            val fallbackReason: AuthError.NetworkFailure? = null
        ) : DocumentFetchOutcome

        data class Failure(val error: AuthError) : DocumentFetchOutcome
    }

    private suspend fun fetchUserDocumentWithBoundedRetry(
        uid: String,
        emailContext: String,
        allowCacheFallback: Boolean
    ): DocumentFetchOutcome {
        var lastError: AuthError? = null

        for (attempt in 1..2) {
            try {
                val doc = remoteDataSource.fetchUserDocumentServerFirst(uid)
                return DocumentFetchOutcome.Success(doc)
            } catch (e: Throwable) {
                val mapped = FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = emailContext,
                    uidContext = uid
                )
                lastError = mapped
                // Only retry on transient network failures
                if (mapped !is AuthError.NetworkFailure) {
                    return DocumentFetchOutcome.Failure(mapped)
                }
            }
        }

        if (allowCacheFallback) {
            val cached = try {
                remoteDataSource.fetchUserDocumentFromCache(uid)
            } catch (_: Throwable) {
                RemoteDocumentResult.NotFound(isFromCache = true)
            }

            if (cached is RemoteDocumentResult.Found) {
                return DocumentFetchOutcome.Success(
                    result = cached.copy(isFromCache = true),
                    fallbackReason = lastError as? AuthError.NetworkFailure
                        ?: AuthError.NetworkFailure()
                )
            }
        }

        return DocumentFetchOutcome.Failure(
            lastError ?: AuthError.NetworkFailure()
        )
    }

    // =========================================================================
    // 10. Legacy Compatibility Bridge Implementations (for Stage 4+ ViewModels)
    // =========================================================================

    @Deprecated("Use registerResident / registerCollector / registerUser in new code")
    override suspend fun signUp(
        email: String,
        password: String,
        name: String,
        phone: String
    ): Result<AppUser> {
        val nameResult = AuthValidator.validateFullName(name)
        if (nameResult is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(nameResult.message))
        }
        val emailResult = AuthValidator.validateEmail(email)
        if (emailResult is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(emailResult.message))
        }
        val phoneResult = AuthValidator.validateAndNormalizePhone(phone)
        if (phoneResult is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(phoneResult.message))
        }
        val passwordResult = AuthValidator.validatePassword(password)
        if (passwordResult is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(passwordResult.message))
        }

        val normalizedName = (nameResult as ValidationResult.Valid).value
        val normalizedEmail = (emailResult as ValidationResult.Valid).value
        val normalizedPhone = (phoneResult as ValidationResult.Valid).value

        return try {
            val createdAuthUser = remoteDataSource.createAuthUser(normalizedEmail, password)
            try {
                remoteDataSource.updateAuthDisplayName(normalizedName)
            } catch (_: Throwable) {
                // Non-fatal
            }
            // Cache pending signup in memory until setRole() is called; NEVER write role="unassigned" to Firestore
            pendingLegacySignUp = PendingLegacySignUp(
                uid = createdAuthUser.uid,
                fullName = normalizedName,
                email = normalizedEmail,
                phoneNumber = normalizedPhone
            )
            Result.success(
                AppUser(
                    uid = createdAuthUser.uid,
                    id = createdAuthUser.uid,
                    name = normalizedName,
                    fullName = normalizedName,
                    email = normalizedEmail,
                    phone = normalizedPhone,
                    phoneNumber = normalizedPhone,
                    role = "unassigned"
                )
            )
        } catch (e: Throwable) {
            val mapped = FirebaseErrorMapper.toAuthError(e, emailContext = normalizedEmail)
            Result.failure(IllegalStateException(mapped.message, e))
        }
    }

    @Deprecated("Use registerUser or completeMissingProfile in new code")
    override suspend fun setRole(uid: String, role: String): Result<AppUser> {
        val parsedRole = UserRole.fromClientSelection(role)
            ?: return Result.failure(IllegalArgumentException("Invalid or unauthorized role selection: $role"))

        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return Result.failure(IllegalStateException("No authenticated user session found."))

        if (authUser.uid != uid) {
            return Result.failure(IllegalStateException("Authenticated UID does not match requested UID."))
        }

        val pendingLegacy = pendingLegacySignUp?.takeIf { it.uid == uid }
        val draft = if (pendingLegacy != null) {
            RegistrationDraft(
                fullName = pendingLegacy.fullName,
                email = pendingLegacy.email,
                phoneNumber = pendingLegacy.phoneNumber,
                role = parsedRole
            )
        } else {
            val fallbackName = authUser.displayName?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Missing full name for profile initialization."))
            val fallbackEmail = authUser.email?.trim()?.lowercase()
                ?: return Result.failure(IllegalStateException("Missing email for profile initialization."))
            val fallbackPhone = authUser.phoneNumber?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Missing phone number for profile initialization."))
            RegistrationDraft(
                fullName = fallbackName,
                email = fallbackEmail,
                phoneNumber = fallbackPhone,
                role = parsedRole
            )
        }

        return when (val outcome = initializeProfileForAuthenticatedUser(authUser, draft)) {
            is ProfileInitializationOutcome.Success -> Result.success(
                AppUser.fromDomainUser(outcome.user)
            )
            is ProfileInitializationOutcome.Failure -> Result.failure(
                IllegalStateException(outcome.error.message)
            )
        }
    }

    @Deprecated("Use login(email, password) returning LoginOutcome in new code")
    override suspend fun signIn(email: String, password: String): Result<AppUser> {
        return when (val outcome = login(email, password)) {
            is LoginOutcome.Authenticated -> Result.success(AppUser.fromDomainUser(outcome.user))
            is LoginOutcome.AccountLocked -> Result.success(AppUser.fromDomainUser(outcome.user))
            is LoginOutcome.ProfileIncomplete -> Result.failure(
                IllegalStateException("Profile setup is incomplete (${outcome.reason}). Please complete your profile.")
            )
            is LoginOutcome.ProfileLoadFailed -> Result.failure(
                IllegalStateException(outcome.error.message)
            )
            is LoginOutcome.Failure -> Result.failure(
                IllegalStateException(outcome.error.message)
            )
        }
    }

    @Deprecated("Use restoreSession() or login() returning canonical User in new code")
    override suspend fun getUserRole(uid: String): Result<AppUser> {
        val trimmedUid = uid.trim()
        if (trimmedUid.isEmpty()) {
            return Result.failure(IllegalArgumentException("User ID must not be blank."))
        }

        val fetchOutcome = fetchUserDocumentWithBoundedRetry(
            uid = trimmedUid,
            emailContext = "",
            allowCacheFallback = false
        )
        val docResult = when (fetchOutcome) {
            is DocumentFetchOutcome.Success -> fetchOutcome.result
            is DocumentFetchOutcome.Failure -> return Result.failure(
                IllegalStateException(fetchOutcome.error.message)
            )
        }

        return when (docResult) {
            is RemoteDocumentResult.NotFound -> Result.failure(
                IllegalStateException("User profile not found for UID $trimmedUid")
            )
            is RemoteDocumentResult.Found -> {
                when (val inspected = UserMapper.inspectFirestoreMap(docResult.data, trimmedUid)) {
                    is ProfileMappingResult.Valid -> Result.success(
                        AppUser.fromDomainUser(inspected.user)
                    )
                    is ProfileMappingResult.Incomplete -> Result.failure(
                        IllegalStateException("User profile is incomplete (${inspected.reason}).")
                    )
                    is ProfileMappingResult.Malformed -> Result.failure(
                        IllegalStateException("User profile is malformed: ${inspected.reason}")
                    )
                }
            }
        }
    }

    @Deprecated("Use logout() in new code")
    override fun signOut() {
        pendingRegistrationState = null
        pendingLegacySignUp = null
        _sessionState.value = SessionState.Unauthenticated
        remoteDataSource.signOut()
    }

    override fun isUserSignedIn(): Boolean {
        return remoteDataSource.getCurrentAuthUser() != null
    }

    override fun getCurrentUserId(): String? {
        return remoteDataSource.getCurrentAuthUser()?.uid
    }

    @Deprecated("Use sendPasswordReset(email) in new code")
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return when (val outcome = sendPasswordReset(email)) {
            is PasswordResetOutcome.ResetEmailDispatched -> Result.success(Unit)
            is PasswordResetOutcome.Failure -> Result.failure(
                IllegalStateException(outcome.error.message)
            )
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            pendingRegistrationState = null
            pendingLegacySignUp = null
            _sessionState.value = SessionState.Unauthenticated
            remoteDataSource.deleteCurrentAuthUser()
            Result.success(Unit)
        } catch (e: Throwable) {
            val mapped = FirebaseErrorMapper.toAuthError(e)
            Result.failure(IllegalStateException(mapped.message, e))
        }
    }
}
