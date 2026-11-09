package com.example.util.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Scope used for Google Drive backup. Only accesses the hidden appDataFolder.
 */
const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

/**
 * Result of requesting Google Drive authorization.
 */
sealed class DriveAuthResult {
    data class Granted(val accessToken: String, val accountEmail: String?) : DriveAuthResult()
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult()
    data class Failed(val message: String, val retryable: Boolean) : DriveAuthResult()
}

/**
 * Authorization contract for Google Drive operations.
 * Allows fakes in tests and previews.
 */
interface DriveAuthApi {
    suspend fun authorize(): DriveAuthResult
    fun resultFromIntent(data: Intent?): DriveAuthResult
    suspend fun clearToken(token: String)
}

/**
 * Bridges Google Tasks to suspending coroutines.
 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { exception ->
        if (continuation.isActive) {
            continuation.resumeWithException(exception)
        }
    }
    addOnCanceledListener {
        if (continuation.isActive) {
            continuation.cancel()
        }
    }
}

/**
 * Real Google Drive authorization backed by Google Play services Identity AuthorizationClient.
 */
class GoogleDriveAuth(private val context: Context) : DriveAuthApi {

    private val authClient by lazy {
        Identity.getAuthorizationClient(context.applicationContext)
    }

    override suspend fun authorize(): DriveAuthResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_APPDATA)))
            .build()
        return try {
            val result = authClient.authorize(request).awaitTask()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    DriveAuthResult.NeedsConsent(pendingIntent)
                } else {
                    DriveAuthResult.Failed("Consent required but pending intent missing", retryable = false)
                }
            } else {
                val token = result.accessToken
                if (token != null) {
                    val email = try {
                        result.toGoogleSignInAccount()?.email
                    } catch (_: Exception) {
                        null
                    }
                    DriveAuthResult.Granted(accessToken = token, accountEmail = email)
                } else {
                    DriveAuthResult.Failed("No access token returned", retryable = false)
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            mapAuthException(e)
        }
    }

    override fun resultFromIntent(data: Intent?): DriveAuthResult {
        if (data == null) {
            return DriveAuthResult.Failed("Connection cancelled", retryable = false)
        }
        return try {
            val result = authClient.getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token != null) {
                val email = try {
                    result.toGoogleSignInAccount()?.email
                } catch (_: Exception) {
                    null
                }
                DriveAuthResult.Granted(accessToken = token, accountEmail = email)
            } else {
                DriveAuthResult.Failed("Connection cancelled", retryable = false)
            }
        } catch (e: Exception) {
            // A real error (no Play services, network) must not be shown as if the person cancelled.
            android.util.Log.w("UniTask", "Drive authorization result failed", e)
            mapAuthException(e)
        }
    }

    override suspend fun clearToken(token: String) {
        if (token.isBlank()) return
        try {
            val request = ClearTokenRequest.builder().setToken(token).build()
            authClient.clearToken(request).awaitTask()
        } catch (_: Exception) {
            // Best effort token clearance
        }
    }

    private fun mapAuthException(e: Exception): DriveAuthResult.Failed {
        if (e is CancellationException) {
            return DriveAuthResult.Failed("Authorization cancelled", retryable = false)
        }
        if (e is IOException) {
            return DriveAuthResult.Failed("Network error during authorization", retryable = true)
        }
        if (e is ApiException) {
            return when (e.statusCode) {
                CommonStatusCodes.CANCELED -> {
                    DriveAuthResult.Failed("Authorization cancelled", retryable = false)
                }
                CommonStatusCodes.NETWORK_ERROR,
                CommonStatusCodes.TIMEOUT -> {
                    DriveAuthResult.Failed("Network error during authorization", retryable = true)
                }
                ConnectionResult.SERVICE_MISSING,
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
                ConnectionResult.SERVICE_DISABLED,
                ConnectionResult.SERVICE_INVALID -> {
                    DriveAuthResult.Failed("Google Play services is unavailable or needs update", retryable = false)
                }
                else -> {
                    val msg = e.localizedMessage ?: "Authorization failed"
                    DriveAuthResult.Failed(msg, retryable = false)
                }
            }
        }
        val msg = e.localizedMessage ?: "Authorization failed"
        return DriveAuthResult.Failed(msg, retryable = false)
    }
}
