package com.waste.link.data.repository

import com.waste.link.data.models.AppUser

interface AuthRepository {
    suspend fun signUp(email: String, password: String, name: String, phone: String): Result<AppUser>
    suspend fun setRole(uid: String, role: String): Result<AppUser>
    suspend fun signIn(email: String, password: String): Result<AppUser>
    suspend fun getUserRole(uid: String): Result<AppUser>
    suspend fun signOut()
    fun isUserSignedIn(): Boolean
    fun getCurrentUserId(): String?
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
}
