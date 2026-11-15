package com.example.caprichometro.infrastructure.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import com.example.caprichometro.domain.repository.AuthRepository
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): Result<User> = withContext(ioDispatcher) {
        try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Error al obtener el usuario de Firebase")

            Result.success(mapToDomain(firebaseUser))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, password: String, name: String): Result<User> = withContext(ioDispatcher) {
        try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Error al crear el usuario en Firebase")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            Result.success(mapToDomain(firebaseUser, name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(context: Context): Result<User> {
        return try {
            val credentialManager = CredentialManager.create(context)

            // 1. Configuramos la petición usando tu clave segura del BuildConfig
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // 2. Lanzamos la ventana nativa de Android para que el usuario elija cuenta
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            // 3. Verificamos que es un token de Google válido
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                // 4. Se lo entregamos a Firebase para que valide la sesión
                val firebaseAuthCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = withContext(ioDispatcher) {
                    firebaseAuth.signInWithCredential(firebaseAuthCredential).await()
                }

                val firebaseUser = authResult.user ?: throw Exception("Error al obtener usuario de Firebase")

                Result.success(
                    User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        name = firebaseUser.displayName ?: "Usuario"
                    )
                )
            } else {
                Result.failure(Exception("Credencial no válida o cancelada"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Inicio de sesión con Google cancelado", e))
        } catch (e: NoCredentialException) {
            Result.failure(Exception("No hay cuentas de Google disponibles en este dispositivo", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(ioDispatcher) {
        try {
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun ensureRecentLoginForDeletion(): Result<Unit> = withContext(ioDispatcher) {
        val currentUser = firebaseAuth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("No hay usuario autenticado"))
        val lastSignInMs = currentUser.metadata?.lastSignInTimestamp ?: 0L
        if (lastSignInMs > 0L && (System.currentTimeMillis() - lastSignInMs) > RECENT_LOGIN_WINDOW_MS) {
            return@withContext Result.failure(
                IllegalStateException("Por seguridad, cierra sesión y vuelve a iniciar sesión antes de eliminar tu cuenta.")
            )
        }
        Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> = withContext(ioDispatcher) {
        try {
            val currentUser = firebaseAuth.currentUser ?: return@withContext Result.failure(Exception("No hay usuario autenticado"))
            currentUser.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val RECENT_LOGIN_WINDOW_MS = 4 * 60 * 1000L
    }

    override fun getCurrentUser(): User? {
        val firebaseUser = firebaseAuth.currentUser ?: return null
        return mapToDomain(firebaseUser)
    }

    private fun mapToDomain(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        overrideName: String? = null
    ): User {
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = overrideName ?: firebaseUser.displayName ?: "Usuario",
            birthday = null // Firebase Auth no guarda fechas. Si necesitas el cumpleaños, lo añadiremos luego con Firebase Firestore.
        )
    }
}
