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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.util.BackgroundAccess
import com.example.util.OnboardingLogic
import com.example.util.OnboardingSetting
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3
private const val SETUP_PAGE = 1
private const val SETTINGS_PREFS = "unitask_settings"

/**
 * The full-screen welcome shown once on a brand new install.
 *
 * Page 1 says what UniTask does. Page 2 turns on the phone settings that reminders need
 * (notifications and battery) and tells people that auto-start is worth trying if reminders still
 * arrive late. Each setting ticks itself when the person comes back from the system screen. Page 3 shows the first steps of the "Get started" card
 * and ends with a button that opens the Add course screen.
 *
 * Skipping is always fine: the app asks again later, when a reminder is saved, and the "Get started"
 * card on Deadlines keeps the same steps.
 *
 * [onFinished] is called with true when the person chose to add their first course right away.
 */
@Composable
fun OnboardingScreen(
    onFinished: (startFirstCourse: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
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

    val on: Set<OnboardingSetting> = remember(refresh) {
        buildSet {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) add(OnboardingSetting.NOTIFICATIONS)
            if (BackgroundAccess.isIgnoringBatteryOptimizations(context)) add(OnboardingSetting.BATTERY)
        }
    }
    val allOn = OnboardingLogic.allOn(on)

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
                    settings.edit { putBoolean("notification_permission_prompted", true) }
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
                        resources.getString(R.string.toast_could_not_open_that_screen_2),
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
                    onClick = { onFinished(false) },
                    modifier = Modifier.testTag("onboarding_skip")
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                SETUP_PAGE -> SetupPage(
                    on = on,
                    onClick = onSettingClick
                )
                else -> FirstStepsPage()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentPage == SETUP_PAGE && !allOn) {
                Text(
                    text = stringResource(R.string.onboarding_finish_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            PageDots(count = PAGE_COUNT, current = currentPage)
            Spacer(modifier = Modifier.height(16.dp))
            if (currentPage < lastPage) {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(currentPage + 1) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_next")
                ) {
                    Text(stringResource(if (currentPage == SETUP_PAGE && !allOn) R.string.onboarding_continue else R.string.onboarding_next))
                }
            } else {
                Button(
                    onClick = { onFinished(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_get_started")
                ) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
                TextButton(
                    onClick = { onFinished(false) },
                    modifier = Modifier.testTag("onboarding_look_around")
                ) {
                    Text(stringResource(R.string.onboarding_look_around))
                }
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
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        FeatureRow(
            icon = Icons.Default.Alarm,
            title = stringResource(R.string.onboarding_feature_deadline_title),
            text = stringResource(R.string.onboarding_feature_deadline_body)
        )
        Spacer(modifier = Modifier.height(20.dp))
        FeatureRow(
            icon = Icons.Default.CalendarMonth,
            title = stringResource(R.string.onboarding_feature_week_title),
            text = stringResource(R.string.onboarding_feature_week_body)
        )
        Spacer(modifier = Modifier.height(20.dp))
        FeatureRow(
            icon = Icons.Default.Groups,
            title = stringResource(R.string.onboarding_feature_groups_title),
            text = stringResource(R.string.onboarding_feature_groups_body)
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
            text = stringResource(R.string.onboarding_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_setup_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OnboardingSetting.entries.forEach { setting ->
            val isOn = setting in on
            when (setting) {
                OnboardingSetting.NOTIFICATIONS -> SettingCard(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.onboarding_notifications_title),
                    text = stringResource(R.string.onboarding_notifications_body),
                    isOn = isOn,
                    buttonLabel = stringResource(R.string.onboarding_allow),
                    tag = "onboarding_notifications",
                    onClick = { onClick(setting) }
                )

                OnboardingSetting.BATTERY -> SettingCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(R.string.onboarding_battery_title),
                    text = stringResource(R.string.onboarding_battery_body),
                    isOn = isOn,
                    buttonLabel = stringResource(R.string.onboarding_allow),
                    tag = "onboarding_battery",
                    onClick = { onClick(setting) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = stringResource(R.string.reminders_autostart_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun FirstStepsPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_steps_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_steps_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        StepRow(
            number = 1,
            title = stringResource(R.string.onboarding_step_course_title),
            text = stringResource(R.string.onboarding_step_course_body)
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepRow(
            number = 2,
            title = stringResource(R.string.onboarding_step_deadline_title),
            text = stringResource(R.string.onboarding_step_deadline_body)
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepRow(
            number = 3,
            title = stringResource(R.string.onboarding_step_reminder_title),
            text = stringResource(R.string.onboarding_step_reminder_body)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_steps_card_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StepRow(number: Int, title: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
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
        }
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
    onClick: () -> Unit
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
                            text = stringResource(R.string.onboarding_turned_on),
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