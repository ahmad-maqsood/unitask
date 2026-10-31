package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.data.local.AppDatabase
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.DailySummaryScheduler
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import com.example.util.AppUser
import com.example.util.BackgroundAccess
import com.example.util.HighPriorityAlarm
import com.example.util.NotificationSound
import com.example.util.TimeFormatter
import com.example.util.UpdateChecker
import com.example.util.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val SOURCE_CODE_URL = "https://github.com/ahmad-maqsood/unitask"
private const val REPORT_FORM_URL =
    "https://docs.google.com/forms/d/e/1FAIpQLSdJpx67ioTDiYOvbntGTh-5AGFb213DFMJNitEl12MYYZSYNw/viewform?usp=pp_url&entry.1106186054="
private const val FEATURE_FORM_URL =
    "https://docs.google.com/forms/d/e/1FAIpQLSc1G4_iMtG3hcoowIswyG9VNq21P11Gqy5MKMRaXI0OXn6RZA/viewform?usp=pp_url&entry.335656724="

enum class SettingsPage {
    Account,
    Appearance,
    Notifications,
    Timetable,
    About
}

/**
 * The gear button in the top bar. It opens a Settings sheet with:
 * account (sign in / cloud backup), appearance (system / light / dark) and check for updates.
 * It also checks GitHub once when the app opens and shows a dot on the gear if a newer
 * release exists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsButton(
    currentUser: AppUser?,
    onOpenCloudSheet: () -> Unit,
    onRescheduleAllReminders: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    var showFeatureGuide by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var soundLabel by remember { mutableStateOf(NotificationSound.label(context)) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var isBatteryIgnoring by remember { mutableStateOf(BackgroundAccess.isIgnoringBatteryOptimizations(context)) }
    var showBackgroundSetupDialog by remember { mutableStateOf(false) }
    var showWeekParityDialog by remember { mutableStateOf(false) }
    var weekParityType by remember { mutableStateOf(com.example.util.WeekParity.currentWeekType(context)) }
    var classStartEnabled by remember { mutableStateOf(ClassReminderScheduler.isEnabled(context)) }
    var summaryEnabled by remember { mutableStateOf(DailySummaryScheduler.isEnabled(context)) }
    var summaryHour by remember { mutableStateOf(DailySummaryScheduler.hour(context)) }
    var summaryMinute by remember { mutableStateOf(DailySummaryScheduler.minute(context)) }
    var showSummaryTimePicker by remember { mutableStateOf(false) }
    val setSummaryEnabled: (Boolean) -> Unit = { enabled ->
        summaryEnabled = enabled
        DailySummaryScheduler.setEnabled(context, enabled)
        DailySummaryScheduler(context).reschedule()
    }
    var highAlarmEnabled by remember { mutableStateOf(HighPriorityAlarm.isEnabled(context)) }
    val setHighAlarmEnabled: (Boolean) -> Unit = { enabled ->
        highAlarmEnabled = enabled
        HighPriorityAlarm.setEnabled(context, enabled)
        if (enabled) HighPriorityAlarm.ensureChannel(context)
    }
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
    val currentVersion = remember(context) { UpdateChecker.currentVersion(context) }

    IconButton(
        onClick = { showSheet = true },
        modifier = Modifier.testTag("top_bar_settings_button")
    ) {
        BadgedBox(badge = { if (available != null) Badge() }) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }

    if (showSoundDialog) {
        SoundPickerDialog(
            onChanged = { soundLabel = NotificationSound.label(context) },
            onDismiss = { showSoundDialog = false }
        )
    }

    if (showBackgroundSetupDialog) {
        BackgroundSetupDialog(
            reviewMode = true,
            onFinished = {
                showBackgroundSetupDialog = false
                isBatteryIgnoring = BackgroundAccess.isIgnoringBatteryOptimizations(context)
            }
        )
    }
    if (showSummaryTimePicker) {
        Material3TimePickerDialog(
            title = "Daily summary time",
            initialHour = summaryHour,
            initialMinute = summaryMinute,
            onDismiss = { showSummaryTimePicker = false },
            onConfirm = { hour, minute ->
                summaryHour = hour
                summaryMinute = minute
                DailySummaryScheduler.setTime(context, hour, minute)
                DailySummaryScheduler(context).reschedule()
                showSummaryTimePicker = false
            }
        )
    }
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                page = null
            },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            BackHandler(enabled = page != null) {
                page = null
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Crossfade(
                    targetState = page,
                    label = "settings_page_crossfade"
                ) { currentPage ->
                    if (currentPage == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            SettingsSectionRow(
                                icon = Icons.Default.CloudSync,
                                title = "Account & backup",
                                summary = currentUser?.email ?: "Not signed in",
                                onClick = { page = SettingsPage.Account },
                                testTag = "settings_section_account"
                            )
                            SettingsSectionRow(
                                icon = Icons.Default.Palette,
                                title = "Appearance",
                                summary = ThemePreference.mode.label,
                                onClick = { page = SettingsPage.Appearance },
                                testTag = "settings_section_appearance"
                            )
                            SettingsSectionRow(
                                icon = Icons.Default.NotificationsActive,
                                title = "Notifications",
                                summary = "Sound: $soundLabel",
                                onClick = { page = SettingsPage.Notifications },
                                testTag = "settings_section_notifications"
                            )
                            SettingsSectionRow(
                                icon = Icons.Default.Schedule,
                                title = "Timetable",
                                summary = when (weekParityType) {
                                    com.example.util.WeekType.A -> "Week A"
                                    com.example.util.WeekType.B -> "Week B"
                                    else -> "Not set"
                                },
                                onClick = { page = SettingsPage.Timetable },
                                testTag = "settings_section_timetable"
                            )
                            SettingsSectionRow(
                                icon = Icons.Default.Info,
                                title = "About & help",
                                summary = if (available != null) "Update available" else "Version $currentVersion",
                                onClick = { page = SettingsPage.About },
                                testTag = "settings_section_about",
                                showBadge = available != null
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { page = null },
                                    modifier = Modifier.testTag("settings_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                                Text(
                                    text = when (currentPage) {
                                        SettingsPage.Account -> "Account & backup"
                                        SettingsPage.Appearance -> "Appearance"
                                        SettingsPage.Notifications -> "Notifications"
                                        SettingsPage.Timetable -> "Timetable"
                                        SettingsPage.About -> "About & help"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            when (currentPage) {
                                SettingsPage.Account -> {
                                    SettingsRow(
                                        icon = Icons.Default.CloudSync,
                                        title = if (currentUser == null) "Sign in to back up data" else "Cloud backup",
                                        subtitle = currentUser?.email ?: "Not signed in",
                                        onClick = {
                                            showSheet = false
                                            page = null
                                            onOpenCloudSheet()
                                        },
                                        testTag = "settings_account_row"
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    LocalBackupSection(showHeading = false)
                                }
                                SettingsPage.Appearance -> {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ThemeMode.entries.forEach { themeMode ->
                                            FilterChip(
                                                selected = ThemePreference.mode == themeMode,
                                                onClick = { ThemePreference.set(context, themeMode) },
                                                label = { Text(themeMode.label) },
                                                modifier = Modifier.testTag("theme_chip_${themeMode.name}")
                                            )
                                        }
                                    }
                                }
                                SettingsPage.Notifications -> {
                                    SettingsRow(
                                        icon = Icons.Default.NotificationsActive,
                                        title = "Notification sound",
                                        subtitle = soundLabel,
                                        onClick = { showSoundDialog = true },
                                        testTag = "settings_sound_row"
                                    )
                                    SettingsRow(
                                        icon = Icons.Default.BatteryChargingFull,
                                        title = "Background alerts",
                                        subtitle = if (isBatteryIgnoring) "Battery restrictions are off. Tap to review." else "Tap to keep alerts reliable",
                                        onClick = { showBackgroundSetupDialog = true },
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
                                    SettingsRow(
                                        icon = Icons.Default.WbSunny,
                                        title = "Daily summary",
                                        subtitle = if (summaryEnabled) {
                                            "Every day at ${TimeFormatter.format12Hour(summaryHour, summaryMinute)}"
                                        } else {
                                            "Off. Get one alert with today's classes and tasks"
                                        },
                                        onClick = { setSummaryEnabled(!summaryEnabled) },
                                        trailing = {
                                            Switch(
                                                checked = summaryEnabled,
                                                onCheckedChange = { setSummaryEnabled(it) }
                                            )
                                        },
                                        testTag = "settings_daily_summary_row"
                                    )
                                    if (summaryEnabled) {
                                        SettingsRow(
                                            icon = Icons.Default.Schedule,
                                            title = "Summary time",
                                            subtitle = TimeFormatter.format12Hour(summaryHour, summaryMinute),
                                            onClick = { showSummaryTimePicker = true },
                                            testTag = "settings_daily_summary_time_row"
                                        )
                                    }
                                    SettingsRow(
                                        icon = Icons.Default.Alarm,
                                        title = "Alarm for high priority tasks",
                                        subtitle = if (highAlarmEnabled) {
                                            "High priority reminders ring like an alarm until you open or dismiss them (1 minute at most)"
                                        } else {
                                            "Off. High priority reminders use the normal notification sound"
                                        },
                                        onClick = { setHighAlarmEnabled(!highAlarmEnabled) },
                                        trailing = {
                                            Switch(
                                                checked = highAlarmEnabled,
                                                onCheckedChange = { setHighAlarmEnabled(it) }
                                            )
                                        },
                                        testTag = "settings_high_priority_alarm_row"
                                    )
                                }
                                SettingsPage.Timetable -> {
                                    SettingsRow(
                                        icon = Icons.Default.Schedule,
                                        title = "This week is",
                                        subtitle = when (weekParityType) {
                                            com.example.util.WeekType.A -> "Week A"
                                            com.example.util.WeekType.B -> "Week B"
                                            else -> "Not set (Tap to choose Week A or Week B)"
                                        },
                                        onClick = { showWeekParityDialog = true },
                                        testTag = "settings_week_parity_row"
                                    )
                                    if (showWeekParityDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showWeekParityDialog = false },
                                            title = { Text("This week is") },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = "Choose which schedule week applies to the current week.",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    listOf(
                                                        Pair(com.example.util.WeekType.A, "Week A"),
                                                        Pair(com.example.util.WeekType.B, "Week B"),
                                                        Pair(null, "Not set (Show all classes every week)")
                                                    ).forEach { (type, label) ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    com.example.util.WeekParity.setThisWeekType(context, type)
                                                                    weekParityType = com.example.util.WeekParity.currentWeekType(context)
                                                                    showWeekParityDialog = false
                                                                    onRescheduleAllReminders()
                                                                    scope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            ClassReminderScheduler(context).rescheduleAll(AppDatabase.getDatabase(context))
                                                                        } catch (_: Exception) {}
                                                                    }
                                                                }
                                                                .padding(vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RadioButton(
                                                                selected = weekParityType == type,
                                                                onClick = {
                                                                    com.example.util.WeekParity.setThisWeekType(context, type)
                                                                    weekParityType = com.example.util.WeekParity.currentWeekType(context)
                                                                    showWeekParityDialog = false
                                                                    onRescheduleAllReminders()
                                                                    scope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            ClassReminderScheduler(context).rescheduleAll(AppDatabase.getDatabase(context))
                                                                        } catch (_: Exception) {}
                                                                    }
                                                                }
                                                            )
                                                            Text(
                                                                text = label,
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                modifier = Modifier.padding(start = 8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { showWeekParityDialog = false }) {
                                                    Text("Close")
                                                }
                                            }
                                        )
                                    }
                                }
                                SettingsPage.About -> {
                                    SettingsRow(
                                        icon = Icons.Default.Info,
                                        title = "Learn to use the app",
                                        subtitle = "Step by step help for every feature",
                                        onClick = { showFeatureGuide = true },
                                        testTag = "settings_features_row"
                                    )
                                    if (showFeatureGuide) FeatureGuideDialog(onDismiss = { showFeatureGuide = false })
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
                                    SettingsRow(
                                        icon = Icons.Default.BugReport,
                                        title = "Report a problem",
                                        subtitle = "Opens a short form. No GitHub account needed",
                                        onClick = {
                                            try {
                                                val info = "UniTask $currentVersion | " +
                                                    "Android ${Build.VERSION.RELEASE} | " +
                                                    "${Build.MANUFACTURER} ${Build.MODEL}"
                                                val url = REPORT_FORM_URL + Uri.encode(info)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
                                        testTag = "settings_report_problem_row"
                                    )
                                    SettingsRow(
                                        icon = Icons.Default.Lightbulb,
                                        title = "Request a feature",
                                        subtitle = "Tell us what you would like to see. Opens a short form",
                                        onClick = {
                                            try {
                                                val info = "UniTask $currentVersion | " +
                                                    "Android ${Build.VERSION.RELEASE} | " +
                                                    "${Build.MANUFACTURER} ${Build.MODEL}"
                                                val url = FEATURE_FORM_URL + Uri.encode(info)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
                                        testTag = "settings_request_feature_row"
                                    )
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
                                                } catch (_: Exception) {}
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
private fun SettingsSectionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    testTag: String,
    showBadge: Boolean = false
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = summary,
        onClick = onClick,
        testTag = testTag,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showBadge) {
                    Badge()
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

@Composable
private fun SoundPickerDialog(onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(NotificationSound.choice(context)) }
    val player = remember { arrayOfNulls<Ringtone>(1) }
    DisposableEffect(Unit) { onDispose { try { player[0]?.stop() } catch (_: Exception) {} } }

    fun preview(uri: Uri?) {
        try {
            player[0]?.stop()
            val target = uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player[0] = RingtoneManager.getRingtone(context, target)?.also { it.play() }
        } catch (_: Exception) {}
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (picked != null) {
                NotificationSound.setPhoneSound(context, picked)
                selected = NotificationSound.choice(context)
                onChanged()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification sound") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                NotificationSound.availableBuiltIn(context).forEach { sound ->
                    SoundOptionRow(
                        label = sound.label,
                        selected = selected == sound.key,
                        onSelect = {
                            NotificationSound.setChoice(context, sound.key)
                            selected = sound.key
                            onChanged()
                        },
                        onPlay = {
                            val id = context.resources.getIdentifier(sound.rawName, "raw", context.packageName)
                            preview(if (id == 0) null else Uri.parse("android.resource://${context.packageName}/$id"))
                        },
                        testTag = "sound_option_${sound.key}"
                    )
                }
                SoundOptionRow(
                    label = "Phone default",
                    selected = selected == NotificationSound.CHOICE_SYSTEM,
                    onSelect = {
                        NotificationSound.setChoice(context, NotificationSound.CHOICE_SYSTEM)
                        selected = NotificationSound.CHOICE_SYSTEM
                        onChanged()
                    },
                    onPlay = { preview(null) },
                    testTag = "sound_option_system"
                )
                SoundOptionRow(
                    label = if (selected == NotificationSound.CHOICE_PHONE) NotificationSound.label(context) else "Choose from phone…",
                    selected = selected == NotificationSound.CHOICE_PHONE,
                    onSelect = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Notification sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            NotificationSound.phoneUri(context)?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                            }
                        }
                        try { pickLauncher.launch(intent) } catch (_: Exception) {}
                    },
                    onPlay = if (selected == NotificationSound.CHOICE_PHONE) {
                        { preview(NotificationSound.phoneUri(context)) }
                    } else null,
                    testTag = "sound_option_phone"
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SoundOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: (() -> Unit)?,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (onPlay != null) {
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play $label")
            }
        }
    }
}
