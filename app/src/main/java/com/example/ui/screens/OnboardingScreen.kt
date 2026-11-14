package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.BackgroundAccess
import com.example.util.OnboardingLogic
import com.example.util.OnboardingSetting
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 2
private const val SETTINGS_PREFS = "unitask_settings"

/**
 * The full-screen welcome shown once on a brand new install.
 *
 * Page 1 says what UniTask does. Page 2 turns on the phone settings that reminders need
 * (notifications, battery, and auto-start on phones that have it). Each setting ticks itself when the
 * person comes back from the system screen. Skipping is always fine: the app asks again later, when a
 * reminder is saved, and the "Get started" card on Deadlines keeps the same steps.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val lastPage = PAGE_COUNT - 1
    val currentPage = pagerState.currentPage

    // Phone settings are read again every time the person comes back from a system screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val needsAutostart = remember { BackgroundAccess.needsAutostartStep() }
    val shownSettings = remember(needsAutostart) { OnboardingLogic.settingsFor(needsAutostart) }
    val on: Set<OnboardingSetting> = remember(refresh) {
        buildSet {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) add(OnboardingSetting.NOTIFICATIONS)
            if (BackgroundAccess.isIgnoringBatteryOptimizations(context)) add(OnboardingSetting.BATTERY)
            if (BackgroundAccess.isAutostartStepDone(context)) add(OnboardingSetting.AUTOSTART)
        }
    }
    val allOn = OnboardingLogic.allOn(on, shownSettings)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    val onSettingClick: (OnboardingSetting) -> Unit = { setting ->
        when (setting) {
            OnboardingSetting.NOTIFICATIONS -> {
                val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                val activity = context as? Activity
                val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED &&
                    (activity?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                    } == true || !settings.getBoolean("notification_permission_prompted", false))
                if (canAsk) {
                    // Remembered so the app does not ask a second time when the first reminder is saved.
                    settings.edit().putBoolean("notification_permission_prompted", true).apply()
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Android will not show its dialog again, or notifications are switched off in the
                    // phone's settings: open the app's notification settings instead.
                    openNotificationSettings(context)
                }
            }

            OnboardingSetting.BATTERY -> {
                val started = BackgroundAccess.requestIgnoreBatteryOptimizations(context)
                if (!started) {
                    Toast.makeText(
                        context,
                        "Could not open that screen. Find UniTask in your phone's battery settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            OnboardingSetting.AUTOSTART -> {
                val started = BackgroundAccess.openAutostartSettings(context)
                // Android cannot tell us if auto-start is on, so it counts as done once the settings
                // screen was opened. This is the same rule the Get started card uses.
                BackgroundAccess.setAutostartStepDone(context, true)
                refresh++
                if (!started) {
                    Toast.makeText(
                        context,
                        "Could not open that screen. Find UniTask in your phone's app settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Back goes to the welcome page first. On the first page it leaves the app as usual.
    BackHandler(enabled = currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("onboarding_screen")
    ) {
        // The row keeps its height on the last page so the content does not jump.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage < lastPage) {
                TextButton(
                    onClick = onFinished,
                    modifier = Modifier.testTag("onboarding_skip")
                ) {
                    Text("Skip")
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            if (page == 0) {
                WelcomePage()
            } else {
                SetupPage(
                    shown = shownSettings,
                    on = on,
                    onClick = onSettingClick
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentPage == lastPage && !allOn) {
                Text(
                    text = "You can finish these later from Get started on the home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            PageDots(count = PAGE_COUNT, current = currentPage)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (currentPage < lastPage) {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_next")
            ) {
                Text(
                    when {
                        currentPage < lastPage -> "Get started"
                        allOn -> "Start using UniTask"
                        else -> "Continue"
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to UniTask",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your classes, deadlines and group work in one place, with reminders that arrive on time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        FeatureRow(
            icon = Icons.Default.Alarm,
            title = "Never miss a deadline",
            text = "Add courses and deadlines, then get reminded before they are due."
        )
        Spacer(modifier = Modifier.height(20.dp))
        FeatureRow(
            icon = Icons.Default.CalendarMonth,
            title = "Plan your week",
            text = "Keep your class timetable and a focus timer in one app."
        )
        Spacer(modifier = Modifier.height(20.dp))
        FeatureRow(
            icon = Icons.Default.Groups,
            title = "Work in groups",
            text = "Share tasks with classmates using a join code."
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SetupPage(
    shown: List<OnboardingSetting>,
    on: Set<OnboardingSetting>,
    onClick: (OnboardingSetting) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Keep reminders on time",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Some phones quiet or stop apps in the background, so reminders can arrive late or not at all. " +
                "These settings prevent that. You can change them any time in your phone's settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        shown.forEach { setting ->
            val isOn = setting in on
            when (setting) {
                OnboardingSetting.NOTIFICATIONS -> SettingCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Notifications",
                    text = "So reminders and group alerts can reach you.",
                    isOn = isOn,
                    buttonLabel = "Allow",
                    tag = "onboarding_notifications",
                    onClick = { onClick(setting) }
                )

                OnboardingSetting.BATTERY -> SettingCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Battery optimization",
                    text = "Stops your phone from putting UniTask to sleep, so reminders ring on time.",
                    isOn = isOn,
                    buttonLabel = "Allow",
                    tag = "onboarding_battery",
                    onClick = { onClick(setting) }
                )

                OnboardingSetting.AUTOSTART -> SettingCard(
                    icon = Icons.Default.Settings,
                    title = "Auto-start",
                    text = "Keeps reminders working after your phone restarts.",
                    extraHint = BackgroundAccess.brandHint(),
                    isOn = isOn,
                    buttonLabel = "Open settings",
                    tag = "onboarding_autostart",
                    onClick = { onClick(setting) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    text: String,
    isOn: Boolean,
    buttonLabel: String,
    tag: String,
    onClick: () -> Unit,
    extraHint: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isOn && extraHint != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = extraHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (isOn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Turned on",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onClick,
                        modifier = Modifier.testTag("${tag}_button")
                    ) {
                        Text(buttonLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == current
            val color by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "onboarding_dot"
            )
            Box(
                modifier = Modifier
                    .size(if (selected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Log.w("UniTask", "Could not open the notification settings", e)
    }
}