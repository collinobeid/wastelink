package com.waste.link.data.remote

import androidx.annotation.VisibleForTesting

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
 * Result of reading a Firestore document with explicit cache/server provenance
 * and pending write metadata.
 */
sealed interface RemoteDocumentResult {
    val isFromCache: Boolean
    val hasPendingWrites: Boolean get() = false

    /**
     * Returns true only when the document result is definitively confirmed by the server
     * with no uncommitted local mutations.
     */
    val isServerAuthoritative: Boolean
        get() = !isFromCache && !hasPendingWrites

    data class Found(
        val data: Map<String, Any?>,
        override val isFromCache: Boolean = false,
        override val hasPendingWrites: Boolean = false
    ) : RemoteDocumentResult

    data class NotFound(
        override val isFromCache: Boolean = false,
        override val hasPendingWrites: Boolean = false
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
 * Domain-neutral remote exception used by data sources and test failure interceptors.
 */
class RemoteAuthException(
    val code: RemoteAuthErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Abstraction over Firebase Authentication and Firestore `/users/{uid}` operations.
 */
interface AuthRemoteDataSource {

    fun getCurrentAuthUser(): RemoteAuthUser?

    /**
     * Registers a listener for Firebase Authentication state and token changes.
     * Returns an [AutoCloseable] handle that unregisters the listener to prevent leaks.
     */
    fun registerAuthStateListener(listener: (RemoteAuthUser?) -> Unit): AutoCloseable

    /**
     * Registers a live snapshot listener for the user's Firestore document (`/users/{uid}`).
     * Emits the updated [RemoteDocumentResult] whenever the document changes on the server.
     * Returns an [AutoCloseable] to detach the snapshot listener and prevent leaks.
     */
    fun registerUserDocumentSnapshotListener(
        uid: String,
        listener: (RemoteDocumentResult) -> Unit
    ): AutoCloseable

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

    suspend fun fetchCollectorSubmission(uid: String): RemoteDocumentResult

    suspend fun fetchCollectorSubmissionHistory(uid: String): List<Map<String, Any?>>

    suspend fun executeCollectorInitialSubmissionBatch(
        uid: String,
        submissionData: Map<String, Any?>,
        historyData: Map<String, Any?>,
        attemptId: String,
        userUpdateFields: Map<String, Any?>
    )

    suspend fun executeCollectorResubmissionBatch(
        uid: String,
        submissionUpdateData: Map<String, Any?>,
        historyData: Map<String, Any?>,
        attemptId: String,
        userUpdateFields: Map<String, Any?>
    )

    suspend fun executeAdminCollectorApprovalBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    )

    suspend fun executeAdminCollectorRejectionBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    )

    /**
     * Controlled integration-test hook to simulate a one-shot failure on the next
     * [writeUserDocument] call after `createAuthUser` succeeds.
     *
     * Guarded in [FirebaseAuthDataSource] so it can only be armed when connected to
     * the local Firebase Emulator (`FirebaseEmulatorConfig.isEmulatorConnected() == true`).
     */
    @VisibleForTesting
    fun simulateNextProfileWriteFailureOnce(failure: Throwable)
}
