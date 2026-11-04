package com.waste.link.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.waste.link.domain.model.AuthError
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of the currently authenticated Firebase user and their authoritative verification
 * and custom-claim attributes.
 */
data class RemoteAuthUser(
    val uid: String,
    val email: String?,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val isEmailVerified: Boolean = false,
    val hasAdminClaim: Boolean = false
)

/**
 * Result of reading a Firestore document with explicit cache/server provenance.
 */
sealed interface RemoteDocumentResult {
    data class Found(
        val data: Map<String, Any?>,
        val isFromCache: Boolean = false
    ) : RemoteDocumentResult

    data class NotFound(
        val isFromCache: Boolean = false
    ) : RemoteDocumentResult
}

/**
 * Standardized error codes for remote authentication and Firestore profile operations,
 * enabling deterministic mapping in both production Firebase calls and JVM unit tests.
 */
enum class RemoteAuthErrorCode {
    INVALID_CREDENTIALS,
    USER_NOT_FOUND,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    USER_DISABLED,
    TOKEN_EXPIRED,
    UNAUTHENTICATED,
    TOO_MANY_REQUESTS,
    NETWORK_UNAVAILABLE,
    PERMISSION_DENIED,
    DOCUMENT_NOT_FOUND,
    UNKNOWN
}

/**
 * Domain-neutral remote exception used by data sources and unit-test fakes.
 */
class RemoteAuthException(
    val code: RemoteAuthErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Maps raw Firebase Auth, Firestore, network, and [RemoteAuthException] throwables into
 * strongly typed [AuthError] domain errors so raw SDK exceptions never leak to UI consumers.
 */
object FirebaseErrorMapper {

    fun toAuthError(
        throwable: Throwable,
        emailContext: String? = null,
        uidContext: String? = null
    ): AuthError {
        return when (throwable) {
            is RemoteAuthException -> mapRemoteCode(
                code = throwable.code,
                message = throwable.message,
                emailContext = emailContext,
                uidContext = uidContext
            )

            is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword(
                message = throwable.reason?.takeIf { it.isNotBlank() }
                    ?: "Password does not meet minimum security requirements."
            )

            is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse(
                email = emailContext?.trim()?.lowercase().orEmpty()
            )

            is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredentials()

            is FirebaseAuthInvalidUserException -> when (throwable.errorCode) {
                "ERROR_USER_DISABLED" -> AuthError.UserDisabled()
                "ERROR_USER_TOKEN_EXPIRED",
                "ERROR_INVALID_USER_TOKEN" -> AuthError.SessionRevoked()
                "ERROR_USER_NOT_FOUND" -> AuthError.AccountNotFound()
                else -> AuthError.InvalidCredentials()
            }

            is FirebaseAuthException -> when (throwable.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE",
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AuthError.EmailAlreadyInUse(
                    email = emailContext?.trim()?.lowercase().orEmpty()
                )
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_EMAIL" -> AuthError.InvalidCredentials()
                "ERROR_USER_NOT_FOUND" -> AuthError.AccountNotFound()
                "ERROR_USER_DISABLED" -> AuthError.UserDisabled()
                "ERROR_USER_TOKEN_EXPIRED",
                "ERROR_INVALID_USER_TOKEN" -> AuthError.SessionRevoked()
                "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword()
                "ERROR_TOO_MANY_REQUESTS" -> AuthError.RateLimited()
                "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NetworkFailure(isRetryable = true)
                else -> AuthError.UnknownError(
                    message = throwable.message ?: "Authentication error (${throwable.errorCode})."
                )
            }

            is FirebaseTooManyRequestsException -> AuthError.RateLimited()

            is FirebaseNetworkException,
            is IOException,
            is TimeoutException -> AuthError.NetworkFailure(isRetryable = true)

            is FirebaseFirestoreException -> when (throwable.code.name) {
                "PERMISSION_DENIED" -> AuthError.PermissionDenied()
                "UNAUTHENTICATED" -> AuthError.SessionRevoked()
                "UNAVAILABLE",
                "DEADLINE_EXCEEDED",
                "ABORTED",
                "INTERNAL" -> AuthError.NetworkFailure(isRetryable = true)
                "RESOURCE_EXHAUSTED" -> AuthError.RateLimited()
                "NOT_FOUND" -> AuthError.ProfileNotFound(
                    uid = uidContext.orEmpty(),
                    email = emailContext.orEmpty()
                )
                else -> AuthError.UnknownError(
                    message = throwable.message ?: "Database error (${throwable.code.name})."
                )
            }

            else -> AuthError.UnknownError(
                message = throwable.message ?: "An unexpected error occurred."
            )
        }
    }

    fun isTransientNetworkError(throwable: Throwable): Boolean {
        return toAuthError(throwable) is AuthError.NetworkFailure
    }

    fun isSessionRevokedOrDisabled(error: AuthError): Boolean {
        return error is AuthError.UserDisabled ||
            error is AuthError.SessionRevoked ||
            error is AuthError.AccountNotFound
    }

    private fun mapRemoteCode(
        code: RemoteAuthErrorCode,
        message: String,
        emailContext: String?,
        uidContext: String?
    ): AuthError {
        return when (code) {
            RemoteAuthErrorCode.INVALID_CREDENTIALS -> AuthError.InvalidCredentials(message)
            RemoteAuthErrorCode.USER_NOT_FOUND -> AuthError.AccountNotFound(message)
            RemoteAuthErrorCode.EMAIL_ALREADY_IN_USE -> AuthError.EmailAlreadyInUse(
                email = emailContext?.trim()?.lowercase().orEmpty(),
                message = message
            )
            RemoteAuthErrorCode.WEAK_PASSWORD -> AuthError.WeakPassword(message)
            RemoteAuthErrorCode.USER_DISABLED -> AuthError.UserDisabled(message)
            RemoteAuthErrorCode.TOKEN_EXPIRED -> AuthError.SessionRevoked(message)
            RemoteAuthErrorCode.UNAUTHENTICATED -> AuthError.Unauthenticated(message)
            RemoteAuthErrorCode.TOO_MANY_REQUESTS -> AuthError.RateLimited(message = message)
            RemoteAuthErrorCode.NETWORK_UNAVAILABLE -> AuthError.NetworkFailure(
                message = message,
                isRetryable = true
            )
            RemoteAuthErrorCode.PERMISSION_DENIED -> AuthError.PermissionDenied(message)
            RemoteAuthErrorCode.DOCUMENT_NOT_FOUND -> AuthError.ProfileNotFound(
                uid = uidContext.orEmpty(),
                email = emailContext.orEmpty(),
                message = message
            )
            RemoteAuthErrorCode.UNKNOWN -> AuthError.UnknownError(message)
        }
    }
}

/**
 * Abstraction over Firebase Authentication and Firestore `/users/{uid}` operations.
 *
 * Enables:
 * 1. Deterministic JVM unit tests of all 24 mandatory Stage 3 authentication and recovery scenarios.
 * 2. Production and Emulator execution backed by [FirebaseAuth] and [FirebaseFirestore].
 */
interface AuthRemoteDataSource {
    fun getCurrentAuthUser(): RemoteAuthUser?

    suspend fun createAuthUser(email: String, password: String): RemoteAuthUser

    suspend fun signInWithEmailAndPassword(email: String, password: String): RemoteAuthUser

    suspend fun reloadCurrentAuthUser(forceTokenRefresh: Boolean = false): RemoteAuthUser?

    suspend fun refreshIdToken(): RemoteAuthUser?

    suspend fun updateAuthDisplayName(fullName: String)

    suspend fun sendEmailVerification()

    suspend fun sendPasswordResetEmail(email: String)

    fun signOut()

    suspend fun deleteCurrentAuthUser()

    suspend fun fetchUserDocumentServerFirst(uid: String): RemoteDocumentResult

    suspend fun fetchUserDocumentFromCache(uid: String): RemoteDocumentResult

    suspend fun writeUserDocument(uid: String, data: Map<String, Any?>)

    suspend fun updateUserDocument(uid: String, fields: Map<String, Any?>)
}

/**
 * Production implementation of [AuthRemoteDataSource] backed by [FirebaseAuth] and [FirebaseFirestore].
 */
@Singleton
class FirebaseAuthRemoteDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRemoteDataSource {

    companion object {
        const val USERS_COLLECTION = "users"
    }

    override fun getCurrentAuthUser(): RemoteAuthUser? {
        val current = firebaseAuth.currentUser ?: return null
        return current.toRemoteAuthUser(hasAdminClaim = false)
    }

    override suspend fun createAuthUser(email: String, password: String): RemoteAuthUser {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user
            ?: throw RemoteAuthException(
                code = RemoteAuthErrorCode.UNKNOWN,
                message = "Firebase Auth user creation returned a null user."
            )
        return user.toRemoteAuthUserWithClaims(forceTokenRefresh = false)
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String): RemoteAuthUser {
        val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = authResult.user
            ?: throw RemoteAuthException(
                code = RemoteAuthErrorCode.UNKNOWN,
                message = "Firebase Auth sign-in returned a null user."
            )
        return user.toRemoteAuthUserWithClaims(forceTokenRefresh = false)
    }

    override suspend fun reloadCurrentAuthUser(forceTokenRefresh: Boolean): RemoteAuthUser? {
        val current = firebaseAuth.currentUser ?: return null
        current.reload().await()
        val reloaded = firebaseAuth.currentUser ?: return null
        return reloaded.toRemoteAuthUserWithClaims(forceTokenRefresh = forceTokenRefresh)
    }

    override suspend fun refreshIdToken(): RemoteAuthUser? {
        val current = firebaseAuth.currentUser ?: return null
        return current.toRemoteAuthUserWithClaims(forceTokenRefresh = true)
    }

    override suspend fun updateAuthDisplayName(fullName: String) {
        val current = firebaseAuth.currentUser ?: return
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(fullName)
            .build()
        current.updateProfile(profileUpdates).await()
    }

    override suspend fun sendEmailVerification() {
        val current = firebaseAuth.currentUser
            ?: throw RemoteAuthException(
                code = RemoteAuthErrorCode.UNAUTHENTICATED,
                message = "No authenticated Firebase user to send verification email."
            )
        current.sendEmailVerification().await()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun deleteCurrentAuthUser() {
        val current = firebaseAuth.currentUser
            ?: throw RemoteAuthException(
                code = RemoteAuthErrorCode.UNAUTHENTICATED,
                message = "No authenticated Firebase user to delete."
            )
        current.delete().await()
    }

    override suspend fun fetchUserDocumentServerFirst(uid: String): RemoteDocumentResult {
        val snapshot = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .get(Source.SERVER)
            .await()

        return if (snapshot.exists() && snapshot.data != null) {
            RemoteDocumentResult.Found(
                data = snapshot.data!!,
                isFromCache = snapshot.metadata.isFromCache
            )
        } else {
            RemoteDocumentResult.NotFound(isFromCache = snapshot.metadata.isFromCache)
        }
    }

    override suspend fun fetchUserDocumentFromCache(uid: String): RemoteDocumentResult {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get(Source.CACHE)
                .await()

            if (snapshot.exists() && snapshot.data != null) {
                RemoteDocumentResult.Found(
                    data = snapshot.data!!,
                    isFromCache = true
                )
            } else {
                RemoteDocumentResult.NotFound(isFromCache = true)
            }
        } catch (_: Exception) {
            RemoteDocumentResult.NotFound(isFromCache = true)
        }
    }

    override suspend fun writeUserDocument(uid: String, data: Map<String, Any?>) {
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .set(data)
            .await()
    }

    override suspend fun updateUserDocument(uid: String, fields: Map<String, Any?>) {
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .update(fields)
            .await()
    }

    private suspend fun FirebaseUser.toRemoteAuthUserWithClaims(
        forceTokenRefresh: Boolean
    ): RemoteAuthUser {
        val hasAdminClaim = try {
            val tokenResult = getIdToken(forceTokenRefresh).await()
            (tokenResult.claims["admin"] as? Boolean) == true
        } catch (_: Exception) {
            false
        }
        return toRemoteAuthUser(hasAdminClaim = hasAdminClaim)
    }

    private fun FirebaseUser.toRemoteAuthUser(hasAdminClaim: Boolean): RemoteAuthUser {
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
