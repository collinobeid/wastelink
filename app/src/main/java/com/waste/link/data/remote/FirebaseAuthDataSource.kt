package com.waste.link.data.remote

import androidx.annotation.VisibleForTesting
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [AuthRemoteDataSource] backed by [FirebaseAuth] and [FirebaseFirestore].
 */
@Singleton
class FirebaseAuthDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRemoteDataSource {

    companion object {
        const val USERS_COLLECTION = "users"
        const val COLLECTOR_SUBMISSIONS_COLLECTION = "collector_submissions"
        const val HISTORY_SUBCOLLECTION = "history"
    }

    private val simulatedNextWriteFailure = AtomicReference<Throwable?>(null)

    @Volatile
    private var activeListenerRegistration: AutoCloseable? = null

    override fun getCurrentAuthUser(): RemoteAuthUser? {
        val current = firebaseAuth.currentUser ?: return null
        return current.toRemoteAuthUser(hasAdminClaim = false)
    }

    @Synchronized
    override fun registerAuthStateListener(listener: (RemoteAuthUser?) -> Unit): AutoCloseable {
        // Ensure any previously registered listener on this singleton is closed first to prevent leaks
        activeListenerRegistration?.close()

        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val current = auth.currentUser
            listener(current?.toRemoteAuthUser(hasAdminClaim = false))
        }
        val idTokenListener = FirebaseAuth.IdTokenListener { auth ->
            val current = auth.currentUser
            listener(current?.toRemoteAuthUser(hasAdminClaim = false))
        }

        firebaseAuth.addAuthStateListener(authStateListener)
        firebaseAuth.addIdTokenListener(idTokenListener)

        val closed = AtomicBoolean(false)
        val handle = AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                firebaseAuth.removeAuthStateListener(authStateListener)
                firebaseAuth.removeIdTokenListener(idTokenListener)
            }
        }
        activeListenerRegistration = handle
        return handle
    }

    @Synchronized
    override fun registerUserDocumentSnapshotListener(
        uid: String,
        listener: (RemoteDocumentResult) -> Unit
    ): AutoCloseable {
        val registration = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    listener(RemoteDocumentResult.Error(cause = error))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val hasPendingWrites = snapshot.metadata.hasPendingWrites()
                val isFromCache = snapshot.metadata.isFromCache

                if (snapshot.exists() && snapshot.data != null) {
                    listener(
                        RemoteDocumentResult.Found(
                            data = snapshot.data!!,
                            isFromCache = isFromCache,
                            hasPendingWrites = hasPendingWrites
                        )
                    )
                } else {
                    listener(
                        RemoteDocumentResult.NotFound(
                            isFromCache = isFromCache,
                            hasPendingWrites = hasPendingWrites
                        )
                    )
                }
            }

        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                registration.remove()
            }
        }
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
                isFromCache = snapshot.metadata.isFromCache,
                hasPendingWrites = snapshot.metadata.hasPendingWrites()
            )
        } else {
            RemoteDocumentResult.NotFound(
                isFromCache = snapshot.metadata.isFromCache,
                hasPendingWrites = snapshot.metadata.hasPendingWrites()
            )
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
                    isFromCache = true,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites()
                )
            } else {
                RemoteDocumentResult.NotFound(
                    isFromCache = true,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites()
                )
            }
        } catch (_: Exception) {
            RemoteDocumentResult.NotFound(isFromCache = true)
        }
    }

    override suspend fun writeUserDocument(uid: String, data: Map<String, Any?>) {
        // Consume one-shot simulated failure if armed in an Emulator test
        simulatedNextWriteFailure.getAndSet(null)?.let { throw it }

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

    override suspend fun fetchCollectorSubmission(uid: String): RemoteDocumentResult {
        return try {
            val snapshot = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION)
                .document(uid)
                .get(Source.SERVER)
                .await()

            if (snapshot.exists() && snapshot.data != null) {
                RemoteDocumentResult.Found(
                    data = snapshot.data!!,
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites()
                )
            } else {
                RemoteDocumentResult.NotFound(
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites()
                )
            }
        } catch (_: Exception) {
            try {
                val cached = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION)
                    .document(uid)
                    .get(Source.CACHE)
                    .await()
                if (cached.exists() && cached.data != null) {
                    RemoteDocumentResult.Found(
                        data = cached.data!!,
                        isFromCache = true,
                        hasPendingWrites = cached.metadata.hasPendingWrites()
                    )
                } else {
                    RemoteDocumentResult.NotFound(
                        isFromCache = true,
                        hasPendingWrites = cached.metadata.hasPendingWrites()
                    )
                }
            } catch (_: Exception) {
                RemoteDocumentResult.NotFound(isFromCache = true)
            }
        }
    }

    override suspend fun fetchCollectorSubmissionHistory(uid: String): List<Map<String, Any?>> {
        return try {
            val snapshot = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION)
                .document(uid)
                .collection(HISTORY_SUBCOLLECTION)
                .get(Source.SERVER)
                .await()
            snapshot.documents.mapNotNull { it.data }
        } catch (_: Exception) {
            try {
                val cached = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION)
                    .document(uid)
                    .collection(HISTORY_SUBCOLLECTION)
                    .get(Source.CACHE)
                    .await()
                cached.documents.mapNotNull { it.data }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun executeCollectorInitialSubmissionBatch(
        uid: String,
        submissionData: Map<String, Any?>,
        historyData: Map<String, Any?>,
        attemptId: String,
        userUpdateFields: Map<String, Any?>
    ) {
        val batch = firestore.batch()
        val submissionRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
        val historyRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
            .collection(HISTORY_SUBCOLLECTION).document(attemptId)
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)

        batch.set(submissionRef, submissionData)
        batch.set(historyRef, historyData)
        batch.update(userRef, userUpdateFields)
        batch.commit().await()
    }

    override suspend fun executeCollectorResubmissionBatch(
        uid: String,
        submissionUpdateData: Map<String, Any?>,
        historyData: Map<String, Any?>,
        attemptId: String,
        userUpdateFields: Map<String, Any?>
    ) {
        val batch = firestore.batch()
        val submissionRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
        val historyRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
            .collection(HISTORY_SUBCOLLECTION).document(attemptId)
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)

        batch.update(submissionRef, submissionUpdateData)
        batch.set(historyRef, historyData)
        batch.update(userRef, userUpdateFields)
        batch.commit().await()
    }

    override suspend fun executeAdminCollectorApprovalBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    ) {
        val batch = firestore.batch()
        val submissionRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
        val historyRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
            .collection(HISTORY_SUBCOLLECTION).document(attemptId)
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)

        batch.update(submissionRef, submissionUpdateData)
        batch.set(historyRef, historyData)
        batch.update(userRef, userUpdateFields)
        batch.commit().await()
    }

    override suspend fun executeAdminCollectorRejectionBatch(
        uid: String,
        attemptId: String,
        historyData: Map<String, Any?>,
        submissionUpdateData: Map<String, Any?>,
        userUpdateFields: Map<String, Any?>
    ) {
        val batch = firestore.batch()
        val submissionRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
        val historyRef = firestore.collection(COLLECTOR_SUBMISSIONS_COLLECTION).document(uid)
            .collection(HISTORY_SUBCOLLECTION).document(attemptId)
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)

        batch.update(submissionRef, submissionUpdateData)
        batch.set(historyRef, historyData)
        batch.update(userRef, userUpdateFields)
        batch.commit().await()
    }

    @VisibleForTesting
    override fun simulateNextProfileWriteFailureOnce(failure: Throwable) {
        check(FirebaseEmulatorConfig.isEmulatorConnected()) {
            "simulateNextProfileWriteFailureOnce may only be used when connected to the Firebase Emulator."
        }
        simulatedNextWriteFailure.set(failure)
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
