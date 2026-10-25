package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Switch
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.util.BackgroundAccess
import com.example.util.NotificationSound
import com.example.util.UpdateChecker
import com.example.util.UpdateResult
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.School
import com.example.data.local.AppDatabase
import com.example.reminder.ClassReminderScheduler
import kotlinx.coroutines.Dispatchers

private const val SOURCE_CODE_URL = "https://github.com/ahmad-maqsood/unitask"

/**
 * The gear button in the top bar. It opens a Settings sheet with:
 * account (sign in / cloud backup), appearance (system / light / dark) and check for updates.
 * It also checks GitHub once when the app opens and shows a dot on the gear if a newer
 * release exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsButton(
    currentUser: FirebaseUser?,
    onOpenCloudSheet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var useSystemSound by remember { mutableStateOf(NotificationSound.useSystem(context)) }
    var isBatteryIgnoring by remember { mutableStateOf(BackgroundAccess.isIgnoringBatteryOptimizations(context)) }
    var showBackgroundSetupDialog by remember { mutableStateOf(false) }
    var classStartEnabled by remember { mutableStateOf(ClassReminderScheduler.isEnabled(context)) }
    val setClassStartEnabled: (Boolean) -> Unit = { enabled ->
        classStartEnabled = enabled
        ClassReminderScheduler.setEnabled(context, enabled)
        // Turning it on sets the alarms, turning it off removes them.
        scope.launch(Dispatchers.IO) {
            try {
                ClassReminderScheduler(context).rescheduleAll(AppDatabase.getDatabase(context))
            } catch (_: Exception) {
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        isBatteryIgnoring = BackgroundAccess.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    // Silent check when the app opens: only a found update is remembered.
    LaunchedEffect(Unit) {
        val result = UpdateChecker.check(context)
        if (result is UpdateResult.Available) updateResult = result
    }

    val available = updateResult as? UpdateResult.Available

    IconButton(
        onClick = { showSheet = true },
        modifier = Modifier.testTag("top_bar_settings_button")
    ) {
        BadgedBox(badge = { if (available != null) Badge() }) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }

    if (showBackgroundSetupDialog) {
        BackgroundSetupDialog(
            onFinished = {
                showBackgroundSetupDialog = false
                isBatteryIgnoring = BackgroundAccess.isIgnoringBatteryOptimizations(context)
            }
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                SettingsSectionLabel("Account")
                SettingsRow(
                    icon = Icons.Default.CloudSync,
                    title = if (currentUser == null) "Sign in to back up data" else "Cloud backup",
                    subtitle = currentUser?.email ?: "Not signed in",
                    onClick = {
                        showSheet = false
                        onOpenCloudSheet()
                    },
                    testTag = "settings_account_row"
                )

                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("Appearance")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { themeMode ->
                        FilterChip(
                            selected = ThemePreference.mode == themeMode,
                            onClick = { ThemePreference.set(context, themeMode) },
                            label = { Text(themeMode.label) },
                            modifier = Modifier.testTag("theme_chip_${themeMode.name}")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("Notifications")
                SettingsRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Use system notification sound",
                    subtitle = if (useSystemSound) "Using your phone's default sound" else "Using the UniTask sound",
                    onClick = {
                        useSystemSound = !useSystemSound
                        NotificationSound.setUseSystem(context, useSystemSound)
                    },
                    trailing = {
                        Switch(
                            checked = useSystemSound,
                            onCheckedChange = {
                                useSystemSound = it
                                NotificationSound.setUseSystem(context, it)
                            }
                        )
                    },
                    testTag = "settings_system_sound_row"
                )
                SettingsRow(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Background alerts",
                    subtitle = if (isBatteryIgnoring) "Battery restrictions are off. Tap to review." else "Tap to keep alerts reliable",
                    onClick = {
                        showBackgroundSetupDialog = true
                    },
                    testTag = "settings_background_row"
                )
                SettingsRow(
                    icon = Icons.Default.School,
                    title = "Class start notifications",
                    subtitle = if (classStartEnabled) "You get an alert 10 minutes before and when each class starts" else "Off",
                    onClick = { setClassStartEnabled(!classStartEnabled) },
                    trailing = {
                        Switch(
                            checked = classStartEnabled,
                            onCheckedChange = { setClassStartEnabled(it) }
                        )
                    },
                    testTag = "settings_class_start_row"
                )

                Spacer(Modifier.height(8.dp))
                LocalBackupSection()
                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("About")
                SettingsRow(
                    icon = Icons.Default.Code,
                    title = "Source code",
                    subtitle = "github.com/ahmad-maqsood/unitask",
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_CODE_URL)))
                        } catch (_: Exception) {}
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    testTag = "settings_source_code_row"
                )
                val currentVersion = UpdateChecker.currentVersion(context)
                val statusText = when (val r = updateResult) {
                    is UpdateResult.Available -> "Update available: ${r.latestVersion} (you have $currentVersion)"
                    is UpdateResult.UpToDate -> "You're on the latest version ($currentVersion)"
                    is UpdateResult.Failed -> r.message
                    null -> "Version $currentVersion"
                }
                SettingsRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for updates",
                    subtitle = statusText,
                    onClick = {
                        if (!isChecking) {
                            isChecking = true
                            scope.launch {
                                updateResult = UpdateChecker.check(context)
                                isChecking = false
                            }
                        }
                    },
                    trailing = if (isChecking) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else null,
                    testTag = "settings_update_row"
                )
                if (available != null) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.pageUrl)))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_github_button")
                    ) {
                        Text("Open GitHub to download")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) trailing()
    }
}
