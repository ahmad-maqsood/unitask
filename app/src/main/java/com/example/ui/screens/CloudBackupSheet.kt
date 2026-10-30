package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteForever
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.sync.SyncScheduler
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

import com.example.util.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.BuildConfig
import com.example.util.AppUser
import com.example.ui.theme.StatusDoneBgDark
import com.example.ui.theme.StatusDoneBgLight
import com.example.ui.theme.StatusDoneFgDark
import com.example.ui.theme.StatusDoneFgLight
import com.example.ui.theme.isDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupSheet(
    currentUser: AppUser?,
    onDismiss: () -> Unit,
    onAuthChanged: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 40.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Icon & Title
            Surface(
                shape = CircleShape,
                color = if (currentUser != null) (if (isDarkTheme()) StatusDoneBgDark else StatusDoneBgLight) else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (currentUser != null) (if (isDarkTheme()) StatusDoneFgDark else StatusDoneFgLight) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (currentUser != null) "Cloud Backup Active" else "Cloud Backup & Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentUser != null)
                        "Your assignments, courses, and timetable automatically sync with Google Cloud."
                    else
                        "UniTask works 100% offline. Sign in to optionally back up and sync across devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (syncMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = syncMessage ?: "",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (currentUser != null) {
                // Account Info Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (currentUser.displayName?.takeIf { it.isNotBlank() } ?: currentUser.email)
                                        ?.take(1)?.uppercase() ?: "U",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser.displayName ?: "Google Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUser.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val lastSynced = remember(syncMessage) {
                                com.example.util.AuthStorage.getLastSyncedTimestamp(context)
                            }
                            if (lastSynced > 0L) {
                                val relative = android.text.format.DateUtils.getRelativeTimeSpanString(
                                    lastSynced,
                                    System.currentTimeMillis(),
                                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Last synced: $relative",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Sync Now Button
                Button(
                    onClick = {
                        isLoading = true
                        syncMessage = null
                        errorMessage = null
                        coroutineScope.launch {
                            val db = com.example.data.local.AppDatabase.getDatabase(context)
                            val success = com.example.sync.SupabaseSyncManager.syncAllDataNow(context, db)
                            com.example.sync.SyncScheduler.scheduleSync(context)
                            isLoading = false
                            syncMessage = if (success) {
                                "All courses, timetable slots, and assignments are backed up and synced!"
                            } else {
                                "Sync completed with offline retry scheduled."
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_now_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Sync Now")
                    }
                }

                // Export Data Button
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        exportUserData(context, coroutineScope) {
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_export_data_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export My Data (JSON)")
                }

                // Sign Out Button
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        signOut(
                            context = context,
                            credentialManager = credentialManager,
                            onSignOutComplete = {
                                isLoading = false
                                onAuthChanged()
                            },
                            scope = coroutineScope
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_sign_out_button")
                ) {
                    Text("Sign Out (Keep Offline)")
                }

                // Delete Account & Cloud Data Button (GDPR)
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    enabled = !isLoading,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_delete_account_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account & Cloud Data")
                }
            } else {
                // Not logged in: Show Google Sign-In Button labeled "Sign in to back up data"
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        signInWithGoogle(
                            context = context,
                            credentialManager = credentialManager,
                            onSuccess = {
                                coroutineScope.launch {
                                    val db = com.example.data.local.AppDatabase.getDatabase(context)
                                    com.example.sync.SupabaseSyncManager.syncAllDataNow(context, db)
                                    com.example.sync.SyncScheduler.scheduleSync(context)
                                    isLoading = false
                                    onAuthChanged()
                                }
                            },
                            onError = { err ->
                                isLoading = false
                                errorMessage = err
                            },
                            onCancelled = {
                                isLoading = false
                            },
                            scope = coroutineScope
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_signin_backup_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Sign in to back up data",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Privacy guarantee note
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "100% offline-ready. Room Database stores all data on device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("backup_sheet_close_button")
            ) {
                Text("Close")
            }
        }
    }

    if (showDeleteConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Account & Cloud Data?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently purge your tasks, courses, and timetable from the cloud, remove your group memberships, clear your local database, and sign you out. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
                                if (uid != null) {
                                    runCatching { SupabaseProvider.client.postgrest["tasks"].delete { filter { eq("user_id", uid) } } }
                                    runCatching { SupabaseProvider.client.postgrest["courses"].delete { filter { eq("user_id", uid) } } }
                                    runCatching { SupabaseProvider.client.postgrest["slots"].delete { filter { eq("user_id", uid) } } }
                                    runCatching { SupabaseProvider.client.postgrest["group_members"].delete { filter { eq("user_id", uid) } } }
                                }
                                val db = com.example.data.local.AppDatabase.getDatabase(context)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    db.clearAllTables()
                                }
                                signOut(context, credentialManager, onSignOutComplete = {
                                    isLoading = false
                                    onAuthChanged()
                                    onDismiss()
                                }, scope = coroutineScope)
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Delete failed: ${e.message}"
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Everything", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun signInWithGoogle(
    context: Context,
    credentialManager: CredentialManager,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onCancelled: () -> Unit,
    scope: CoroutineScope
) {
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                SupabaseProvider.client.auth.signInWith(IDToken) {
                    idToken = googleIdToken
                    provider = Google
                }
                onSuccess()
            } else {
                onError("Unexpected credential type received")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled: ${e.message}", e)
            onCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onError(e.localizedMessage ?: "Sign-in failed")
        }
    }
}

private fun signOut(
    context: Context,
    credentialManager: CredentialManager,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {

    scope.launch {
        try {
            try { SupabaseProvider.client.auth.signOut() } catch (e: Exception) { Log.e("Auth", "Supabase sign-out failed", e) }
            com.example.util.AuthStorage.setLastUid(context, null)
            com.example.data.groups.GroupRepository.clearCache(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("Auth", "Failed to clear credential state", e)
        } finally {
            onSignOutComplete()
        }
    }
}

private fun exportUserData(context: Context, scope: CoroutineScope, onDone: () -> Unit) {
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            val courses = db.courseDao().getAllCoursesList()
            val tasks = db.taskDao().getAllActiveTasksSync()
            val slots = db.lectureSlotDao().getAllSlotsSync()

            val jsonObj = buildJsonObject {
                put("exportedAt", java.time.Instant.now().toString())
                put("appVersion", BuildConfig.VERSION_NAME)
                put("coursesCount", courses.size)
                put("tasksCount", tasks.size)
                put("timetableSlotsCount", slots.size)
                put("courses", Json.encodeToJsonElement(courses.map { mapOf("code" to it.code, "name" to it.name, "colorHex" to it.colorHex) }))
                put("tasks", Json.encodeToJsonElement(tasks.map { 
                    mapOf(
                        "title" to it.title,
                        "description" to (it.description ?: ""),
                        "priority" to it.priority.name,
                        "status" to it.status.name,
                        "dueDateEpochMillis" to it.dueDateEpochMillis.toString()
                    )
                }))
                put("slots", Json.encodeToJsonElement(slots.map {
                    mapOf(
                        "dayOfWeek" to it.dayOfWeek.toString(),
                        "startHour" to it.startHour.toString(),
                        "startMinute" to it.startMinute.toString(),
                        "endHour" to it.endHour.toString(),
                        "endMinute" to it.endMinute.toString(),
                        "classroom" to (it.classroom ?: "")
                    )
                }))
            }

            val jsonStr = jsonObj.toString()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, jsonStr)
                    putExtra(android.content.Intent.EXTRA_TITLE, "UniTask_Data_Backup.json")
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "Export UniTask Data"))
                onDone()
            }
        } catch (e: Exception) {
            Log.e("CloudBackupSheet", "Data export failed: ${e.message}", e)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone()
            }
        }
    }
}
