package com.example.authentication

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failure(val message: String) : GoogleSignInOutcome
}

class AuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    val authUserFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun attemptSilentAutoSignIn(): Boolean {
        if (auth.currentUser != null) return true
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return false
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val credential = credentialManager.getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun signInWithGoogleInteractive(activity: Activity): GoogleSignInOutcome {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return GoogleSignInOutcome.Failure(
                "Google Sign-In configuration missing (default_web_client_id)."
            )
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                val user = result.user
                if (user != null) {
                    GoogleSignInOutcome.Success(user)
                } else {
                    GoogleSignInOutcome.Failure("Google Sign-In succeeded but returned no user session.")
                }
            } else {
                GoogleSignInOutcome.Failure("Unexpected credential response type.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            GoogleSignInOutcome.Failure(
                e.localizedMessage ?: "Google Sign-In failed. Verify Play Services & SHA-1 setup."
            )
        }
    }

    suspend fun updateDisplayName(newName: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in."))
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Display name cannot be empty."))
        return try {
            val req = UserProfileChangeRequest.Builder()
                .setDisplayName(trimmed.take(80))
                .build()
            user.updateProfile(req).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in account."))
        return try {
            user.delete().await()
            withContext(NonCancellable) {
                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
