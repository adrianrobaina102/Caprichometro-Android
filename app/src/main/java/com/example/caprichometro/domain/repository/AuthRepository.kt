package com.example.caprichometro.domain.repository

import android.content.Context
import com.example.caprichometro.domain.model.User

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun register(email: String, password: String, name: String): Result<User>
    suspend fun signInWithGoogle(context: Context): Result<User>
    suspend fun signOut(): Result<Unit>
    suspend fun ensureRecentLoginForDeletion(): Result<Unit> = Result.success(Unit)
    suspend fun deleteAccount(): Result<Unit>
    fun getCurrentUser(): User?
}
