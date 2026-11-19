package com.example.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.net.toUri
import com.example.R
import com.example.util.runLogged
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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.data.local.AppDatabase
import com.example.reminder.ClassReminderScheduler
import com.example.reminder.DailySummaryScheduler
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import com.example.util.AppUser
import com.example.util.BackgroundAccess
import com.example.util.NotificationSound
import com.example.util.TimeFormatter
import com.example.util.UpdateChecker
import com.example.util.UpdateResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.ui.theme.AccentTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val SOURCE_CODE_URL = "https://github.com/CondaLabs/unitask"
private const val LATEST_RELEASE_URL = "https://github.com/CondaLabs/unitask/releases/latest"
internal const val REPORT_FORM_URL =
    "https://docs.google.com/forms/d/e/1FAIpQLSdJpx67ioTDiYOvbntGTh-5AGFb213DFMJNitEl12MYYZSYNw/viewform?usp=pp_url&entry.1106186054="
private const val FEATURE_FORM_URL =
    "https://docs.google.com/forms/d/e/1FAIpQLSc1G4_iMtG3hcoowIswyG9VNq21P11Gqy5MKMRaXI0OXn6RZA/viewform?usp=pp_url&entry.335656724="

enum class SettingsPage {
    General,
    Notifications,
    Backup,
    About
}

/**
 * The Settings sheet. Its gear button is optional: the top bar opens it from the More sheet.
 * It has an Account row (sign in for groups / online account), and sections for General (theme, week parity),
 * Notifications & reminders, Backup & data, and About & help.
 * It also checks GitHub once when the app opens and shows a badge on the More button if a newer
 * release exists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsButton(
    currentUser: AppUser?,
    onOpenCloudSheet: () -> Unit,
    onRescheduleAllReminders: () -> Unit = {},
    openRequest: SettingsOpenRequest? = null,
    onOpenRequestHandled: () -> Unit = {},
    showButton: Boolean = true,
    onUpdateAvailableChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    // Lets global search jump straight to a Settings page.
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            page = openRequest.page
            showSheet = true
            onOpenRequestHandled()
        }
    }
    var showFeatureGuide by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var soundLabel by remember { mutableStateOf(NotificationSound.label(context)) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var isBatteryIgnoring by remember { mutableStateOf(BackgroundAccess.isIgnoringBatteryOptimizations(context)) }
    var showReminderCheck by remember { mutableStateOf(false) }
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
    val setClassStartEnabled: (Boolean) -> Unit = { enabled ->
        classStartEnabled = enabled
        ClassReminderScheduler.setEnabled(context, enabled)
        // Turning it on sets the alarms, turning it off removes them.
        scope.launch(Dispatchers.IO) {
            runLogged("Class reminder reschedule") {
                ClassReminderScheduler(context).rescheduleAll(AppDatabase.getDatabase(context))
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        isBatteryIgnoring = BackgroundAccess.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    // Silent check when the app opens: only a found update is remembered.
    LaunchedEffect(Unit) {
        val result = UpdateChecker.checkIfDue(context)
        if (result != null) updateResult = result
    }

    val available = updateResult as? UpdateResult.Available
    val currentVersion = remember(context) { UpdateChecker.currentVersion(context) }
    // Lets the More button show the update dot when this button itself is hidden.
    LaunchedEffect(available != null) { onUpdateAvailableChanged(available != null) }

    if (showButton) {
        IconButton(
            onClick = { showSheet = true },
            modifier = Modifier.testTag("top_bar_settings_button")
        ) {
            BadgedBox(badge = { if (available != null) Badge() }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_settings))
            }
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
            title = stringResource(R.string.settings_daily_summary_time),
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
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            BackHandler(enabled = page != null) {
                page = null
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Crossfade(
                    targetState = page,
                    animationSpec = tween(durationMillis = 150),
                    label = "settings_page_crossfade"
                ) { currentPage ->
                    if (currentPage == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_settings),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            SettingsRow(
                                icon = Icons.Default.AccountCircle,
                                title = stringResource(if (currentUser == null) R.string.settings_sign_in_for_groups else R.string.cloud_backup_online_account),
                                subtitle = currentUser?.email ?: stringResource(R.string.settings_account_needed_for_study_groups),
                                onClick = {
                                    showSheet = false
                                    page = null
                                    onOpenCloudSheet()
                                },
                                testTag = "settings_account_row",
                                subtitleMaxLines = 1
                            )
                            val weekSummary = when (weekParityType) {
                                com.example.util.WeekType.A -> stringResource(R.string.timetable_week_a)
                                com.example.util.WeekType.B -> stringResource(R.string.timetable_week_b)
                                else -> stringResource(R.string.settings_week_not_set)
                            }
                            SettingsSectionRow(
                                icon = Icons.Default.Tune,
                                title = stringResource(R.string.settings_general),
                                summary = stringResource(R.string.settings_general_summary, stringResource(ThemePreference.mode.labelRes), weekSummary),
                                onClick = { page = SettingsPage.General },
                                testTag = "settings_section_general"
                            )
                            val soundSummary = stringResource(R.string.settings_summary_sound, soundLabel)
                            val dailySummaryOn = stringResource(R.string.settings_summary_daily_summary_on)
                            val classAlertsOn = stringResource(R.string.settings_summary_class_alerts_on)
                            val notificationsSummary = buildList {
                                add(soundSummary)
                                if (summaryEnabled) add(dailySummaryOn)
                                if (classStartEnabled) add(classAlertsOn)
                            }.joinToString(", ")
                            SettingsSectionRow(
                                icon = Icons.Default.NotificationsActive,
                                title = stringResource(R.string.settings_notifications_reminders),
                                summary = notificationsSummary,
                                onClick = { page = SettingsPage.Notifications },
                                testTag = "settings_section_notifications"
                            )
                            SettingsSectionRow(
                                icon = Icons.Default.Backup,
                                title = stringResource(R.string.settings_backup_data),
                                summary = stringResource(R.string.settings_google_drive_backup_and_backup),
                                onClick = { page = SettingsPage.Backup },
                                testTag = "settings_section_backup"
                            )
                            SettingsSectionRow(
                                icon = Icons.Default.Info,
                                title = stringResource(R.string.settings_about_help),
                                summary = if (available != null) stringResource(R.string.settings_update_available) else stringResource(R.string.settings_version_format, currentVersion),
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
                                        contentDescription = stringResource(R.string.common_back)
                                    )
                                }
                                Text(
                                    text = when (currentPage) {
                                        SettingsPage.General -> stringResource(R.string.settings_general)
                                        SettingsPage.Notifications -> stringResource(R.string.settings_notifications_reminders)
                                        SettingsPage.Backup -> stringResource(R.string.settings_backup_data)
                                        SettingsPage.About -> stringResource(R.string.settings_about_help)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            when (currentPage) {
                                SettingsPage.General -> {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ThemeMode.entries.forEach { themeMode ->
                                            FilterChip(
                                                selected = ThemePreference.mode == themeMode,
                                                onClick = { ThemePreference.set(context, themeMode) },
                                                label = { Text(stringResource(themeMode.labelRes)) },
                                                modifier = Modifier.testTag("theme_chip_${themeMode.name}")
                                            )
                                        }
                                    }
                                    if (com.example.util.AppLanguage.isSupported) {
                                        val currentLanguage = remember { com.example.util.AppLanguage.current(context) }
                                        Text(
                                            stringResource(R.string.settings_language),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            com.example.util.AppLanguage.entries.forEach { language ->
                                                FilterChip(
                                                    selected = currentLanguage == language,
                                                    onClick = {
                                                        if (currentLanguage != language) {
                                                            com.example.util.AppLanguage.apply(context, language)
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            stringResource(
                                                                when (language) {
                                                                    com.example.util.AppLanguage.SYSTEM -> R.string.settings_language_system
                                                                    com.example.util.AppLanguage.ENGLISH -> R.string.settings_language_english
                                                                    com.example.util.AppLanguage.URDU -> R.string.settings_language_urdu
                                                                }
                                                            )
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("language_chip_${language.name}")
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.settings_accent_colour),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        AccentTheme.entries.forEach { accent ->
                                            val selected = ThemePreference.accent == accent
                                            val accentName = stringResource(accent.labelRes)
                                            val accentDesc = if (selected) stringResource(R.string.accent_selected_desc, accentName) else accentName
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(accent.swatch)
                                                    .clickable { ThemePreference.setAccent(context, accent) }
                                                    .semantics {
                                                        contentDescription = accentDesc
                                                    }
                                                    .testTag("accent_chip_${accent.name}")
                                            ) {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = androidx.compose.ui.graphics.Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    SettingsRow(
                                        icon = Icons.Default.Schedule,
                                        title = stringResource(R.string.settings_this_week_is),
                                        subtitle = when (weekParityType) {
                                            com.example.util.WeekType.A -> stringResource(R.string.timetable_week_a)
                                            com.example.util.WeekType.B -> stringResource(R.string.timetable_week_b)
                                            else -> stringResource(R.string.week_not_set_tap_to_choose)
                                        },
                                        onClick = { showWeekParityDialog = true },
                                        testTag = "settings_week_parity_row"
                                    )
                                    if (showWeekParityDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showWeekParityDialog = false },
                                            title = { Text(stringResource(R.string.settings_this_week_is)) },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = stringResource(R.string.settings_choose_which_schedule_week_applies),
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
                                                                        runLogged("Class reminder reschedule") {
                                                                            ClassReminderScheduler(context).rescheduleAll(AppDatabase.getDatabase(context))
                                                                        }
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
                                                                        runLogged("Class reminder reschedule") {
                                                                            ClassReminderScheduler(context).rescheduleAll(AppDatabase.getDatabase(context))
                                                                        }
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
                                                    Text(stringResource(R.string.common_close))
                                                }
                                            }
                                        )
                                    }
                                }
                                SettingsPage.Notifications -> {
                                    SettingsRow(
                                        icon = Icons.Default.NotificationsActive,
                                        title = stringResource(R.string.settings_notification_sound),
                                        subtitle = soundLabel,
                                        onClick = { showSoundDialog = true },
                                        testTag = "settings_sound_row"
                                    )
                                    SettingsRow(
                                        icon = Icons.Default.BatteryChargingFull,
                                        title = stringResource(R.string.settings_background_alerts),
                                        subtitle = stringResource(if (isBatteryIgnoring) R.string.settings_battery_restrictions_are_off_tap else R.string.settings_tap_to_keep_alerts_reliable),
                                        onClick = { showBackgroundSetupDialog = true },
                                        testTag = "settings_background_row"
                                    )
                                    SettingsRow(
                                        icon = Icons.Default.NotificationsActive,
                                        title = stringResource(R.string.reminder_check_check_reminders),
                                        subtitle = stringResource(R.string.settings_make_sure_reminders_reach_this),
                                        onClick = { showReminderCheck = true },
                                        testTag = "settings_reminder_check_row"
                                    )
                                    if (showReminderCheck) ReminderCheckDialog(onDismiss = { showReminderCheck = false })
                                    SettingsRow(
                                        icon = Icons.Default.School,
                                        title = stringResource(R.string.settings_class_start_notifications),
                                        subtitle = stringResource(if (classStartEnabled) R.string.settings_you_get_an_alert_10 else R.string.settings_off),
                                        onClick = { setClassStartEnabled(!classStartEnabled) },
                                        switchChecked = classStartEnabled,
                                        testTag = "settings_class_start_row"
                                    )
                                    SettingsRow(
                                        icon = Icons.Default.WbSunny,
                                        title = stringResource(R.string.settings_daily_summary),
                                        subtitle = if (summaryEnabled) {
                                            "Every day at ${TimeFormatter.format(summaryHour, summaryMinute)}"
                                        } else {
                                            "Off. Get one alert with today's classes and tasks"
                                        },
                                        onClick = { setSummaryEnabled(!summaryEnabled) },
                                        switchChecked = summaryEnabled,
                                        testTag = "settings_daily_summary_row"
                                    )
                                    if (summaryEnabled) {
                                        SettingsRow(
                                            icon = Icons.Default.Schedule,
                                            title = stringResource(R.string.settings_summary_time),
                                            subtitle = TimeFormatter.format(summaryHour, summaryMinute),
                                            onClick = { showSummaryTimePicker = true },
                                            testTag = "settings_daily_summary_time_row"
                                        )
                                    }
                                }
                                SettingsPage.Backup -> {
                                    DriveBackupSection()
                                    Spacer(Modifier.height(16.dp))
                                    LocalBackupSection(showHeading = true)
                                }
                                SettingsPage.About -> {
                                    SettingsRow(
                                        icon = Icons.Default.Info,
                                        title = stringResource(R.string.search_learn_to_use_the_app),
                                        subtitle = stringResource(R.string.settings_step_by_step_help_for),
                                        onClick = { showFeatureGuide = true },
                                        testTag = "settings_features_row"
                                    )
                                    if (showFeatureGuide) FeatureGuideDialog(onDismiss = { showFeatureGuide = false })
                                    SettingsRow(
                                        icon = Icons.Default.Code,
                                        title = stringResource(R.string.settings_source_code),
                                        subtitle = "github.com/CondaLabs/unitask",
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_CODE_URL.toUri()))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, resources.getString(R.string.toast_no_app_can_open_this), Toast.LENGTH_SHORT).show()
                                            }
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
                                        icon = Icons.Default.NewReleases,
                                        title = stringResource(R.string.settings_latest_release),
                                        subtitle = stringResource(R.string.settings_open_the_newest_version_on),
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, LATEST_RELEASE_URL.toUri()))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, resources.getString(R.string.toast_no_app_can_open_this), Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        trailing = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        testTag = "settings_latest_release_row"
                                    )
                                    SettingsRow(
                                        icon = Icons.Default.BugReport,
                                        title = stringResource(R.string.crash_report_report_a_problem),
                                        subtitle = stringResource(R.string.settings_opens_a_short_form_no),
                                        onClick = {
                                            try {
                                                val info = "UniTask $currentVersion | " +
                                                    "Android ${Build.VERSION.RELEASE} | " +
                                                    "${Build.MANUFACTURER} ${Build.MODEL}"
                                                val url = REPORT_FORM_URL + Uri.encode(info)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, resources.getString(R.string.toast_no_app_can_open_this), Toast.LENGTH_SHORT).show()
                                            }
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
                                        title = stringResource(R.string.settings_request_a_feature),
                                        subtitle = stringResource(R.string.settings_tell_us_what_you_would),
                                        onClick = {
                                            try {
                                                val info = "UniTask $currentVersion | " +
                                                    "Android ${Build.VERSION.RELEASE} | " +
                                                    "${Build.MANUFACTURER} ${Build.MODEL}"
                                                val url = FEATURE_FORM_URL + Uri.encode(info)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, resources.getString(R.string.toast_no_app_can_open_this), Toast.LENGTH_SHORT).show()
                                            }
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
                                        title = stringResource(R.string.settings_check_for_updates),
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
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, available.pageUrl.toUri()))
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, resources.getString(R.string.toast_no_app_can_open_this), Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("settings_open_github_button")
                                        ) {
                                            Text(stringResource(R.string.settings_open_github_to_download))
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
        subtitleMaxLines = 1,
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
    trailing: (@Composable () -> Unit)? = null,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    // Set for an on/off row: the whole row is the switch and onClick flips it.
    switchChecked: Boolean? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (switchChecked != null) {
                    Modifier.toggleable(value = switchChecked, role = Role.Switch, onValueChange = { onClick() })
                } else {
                    Modifier.clickable(onClick = onClick, role = Role.Button)
                }
            )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = if (subtitleMaxLines != Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip
            )
        }
        if (switchChecked != null) Switch(checked = switchChecked, onCheckedChange = null)
        if (trailing != null) trailing()
    }
}

@Composable
private fun SoundPickerDialog(onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(NotificationSound.choice(context)) }
    val player = remember { arrayOfNulls<Ringtone>(1) }
    DisposableEffect(Unit) { onDispose { try { player[0]?.stop() } catch (e: Exception) { Log.w("UniTask", "Could not stop the sound preview", e) } } }

    fun preview(uri: Uri?) {
        try {
            player[0]?.stop()
            val target = uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player[0] = RingtoneManager.getRingtone(context, target)?.also { it.play() }
        } catch (e: Exception) { Log.w("UniTask", "Could not play the sound preview", e) }
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
        title = { Text(stringResource(R.string.settings_notification_sound)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SoundOptionRow(
                    label = stringResource(R.string.settings_phone_default),
                    selected = selected == NotificationSound.CHOICE_SYSTEM,
                    onSelect = {
                        NotificationSound.setChoice(context, NotificationSound.CHOICE_SYSTEM)
                        selected = NotificationSound.CHOICE_SYSTEM
                        onChanged()
                    },
                    onPlay = { preview(null) },
                    testTag = "sound_option_system"
                )
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
                            preview(if (id == 0) null else "android.resource://${context.packageName}/$id".toUri())
                        },
                        testTag = "sound_option_${sound.key}"
                    )
                }

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
                        try { pickLauncher.launch(intent) } catch (e: Exception) { Log.w("UniTask", "Could not open the sound picker", e) }
                    },
                    onPlay = if (selected == NotificationSound.CHOICE_PHONE) {
                        { preview(NotificationSound.phoneUri(context)) }
                    } else null,
                    testTag = "sound_option_phone"
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } }
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
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (onPlay != null) {
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.settings_play, label))
            }
        }
    }
}
