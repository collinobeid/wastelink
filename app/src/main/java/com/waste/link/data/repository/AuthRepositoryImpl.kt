package com.waste.link.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.models.AppUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override suspend fun signUp(
        email: String,
        password: String,
        name: String,
        phone: String
    ): Result<AppUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("User creation failed")

            // Standard registration default role (Admin cannot be self-assigned from client)
            val role = "unassigned"
            val status = "UNVERIFIED"

            val appUser = AppUser(
                uid = uid,
                name = name,
                email = email,
                phone = phone,
                role = role,
                status = status
            )

            firestore.collection("users").document(uid).set(appUser).await()
            Result.success(appUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setRole(uid: String, role: String): Result<AppUser> {
        return try {
            if (role != "resident" && role != "collector") {
                throw IllegalArgumentException("Invalid client role: $role. Admin role cannot be self-assigned.")
            }
            val document = firestore.collection("users").document(uid).get().await()
            val user = document.toObject(AppUser::class.java) ?: throw Exception("User not found")
            val updatedUser = user.copy(role = role, status = "ACTIVE")
            firestore.collection("users").document(uid).set(updatedUser).await()
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AppUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Sign in failed")
            val roleResult = getUserRole(uid)
            
            if (roleResult.isFailure) {
                // Auto-create fallback document if missing
                val appUser = AppUser(
                    uid = uid,
                    name = "User",
                    email = email,
                    phone = "Not provided",
                    role = "resident",
                    status = "ACTIVE"
                )
                firestore.collection("users").document(uid).set(appUser).await()
                Result.success(appUser)
            } else {
                roleResult
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserRole(uid: String): Result<AppUser> {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            val appUser = document.toObject(AppUser::class.java)
                ?: throw Exception("User document not found")
                
            // Hard Ban Check
            if (appUser.status == "BANNED" || appUser.deletedAt != null) {
                auth.signOut()
                throw Exception("Account has been permanently banned or deleted.")
            }
            
            // Suspension Check
            if (appUser.status == "SUSPENDED" && appUser.bannedUntil != null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime < appUser.bannedUntil) {
                    auth.signOut()
                    val date = java.util.Date(appUser.bannedUntil)
                    throw Exception("Account suspended until $date")
                } else {
                    // Suspension expired, auto-restore
                    val restoredUser = appUser.copy(status = "RESTRICTED", bannedUntil = null)
                    firestore.collection("users").document(uid).set(restoredUser).await()
                    return Result.success(restoredUser)
                }
            }
            
            // Sponsor Downgrade Check
            if (appUser.role == "collector" && appUser.contractUntil != null && appUser.status == "ACTIVE") {
                val currentTime = System.currentTimeMillis()
                if (currentTime > appUser.contractUntil) {
                    val downgradedUser = appUser.copy(status = "RESTRICTED")
                    firestore.collection("users").document(uid).set(downgradedUser).await()
                    return Result.success(downgradedUser)
                }
            }
            
            Result.success(appUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw Exception("User not logged in")
            val uid = user.uid
            
            // GDPR Export stub (just an audit log for now)
            firestore.collection("audit_logs").add(
                mapOf(
                    "action" to "ACCOUNT_DELETED",
                    "uid" to uid,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()

            // Delete Firestore document
            firestore.collection("users").document(uid).delete().await()
            
            // Delete Firebase Auth user
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
