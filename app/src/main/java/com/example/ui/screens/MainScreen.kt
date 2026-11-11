package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings


import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.local.TaskItem
import com.example.data.local.TaskStatus
import com.example.data.local.isMyTasksCourse
import com.example.ui.theme.StatusDoneFgDark
import com.example.ui.theme.StatusDoneFgLight
import com.example.ui.theme.isDarkTheme
import com.example.util.BackgroundAccess
import com.example.util.drive.DriveMigrationPrompt
import com.example.util.drive.DrivePrefs
import com.example.util.timer.TimerController
import com.example.util.timer.TimerLogic
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.Crossfade
/** Builds the list of tiles shown in the More sheet. Search has its own top bar icon. */
fun buildToolsList(
    onOpenNotes: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit,
    onOpenSettings: () -> Unit,
    updateAvailable: Boolean = false,
    onOpenHelp: () -> Unit = {}
): List<ToolEntry> = listOf(
    ToolEntry(
        label = "Notes",
        description = "Lecture notes and summaries",
        icon = Icons.Default.EditNote,
        testTag = "tools_tile_notes",
        onClick = onOpenNotes
    ),
    ToolEntry(
        label = "Timer",
        description = "Stopwatch and countdown",
        icon = Icons.Default.Timer,
        testTag = "tools_tile_timer",
        onClick = onOpenTimer
    ),
    ToolEntry(
        label = "Recently deleted",
        description = "Restore tasks and notes",
        icon = Icons.Default.DeleteSweep,
        testTag = "tools_tile_recently_deleted",
        onClick = onOpenRecentlyDeleted
    ),
    ToolEntry(
        label = "Help",
        description = "Learn how to use UniTask",
        icon = Icons.AutoMirrored.Filled.HelpOutline,
        testTag = "tools_tile_help",
        onClick = onOpenHelp
    ),
    ToolEntry(
        label = "Settings",
        description = "Reminders, backup and account",
        icon = Icons.Default.Settings,
        testTag = "tools_tile_settings",
        showBadge = updateAvailable,
        onClick = onOpenSettings
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Request notification permission for Android 13+
    var showNotificationBanner by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            } else false
        )
    }

    var showBackgroundSetupDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.refreshMessage.collectLatest { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val job = launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
            if (message == "Up to date" || message == "Syncing...") {
                delay(2000L)
                job.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    val checkAndShowBackgroundSetup: () -> Unit = {
        if (!BackgroundAccess.isBackgroundSetupPrompted(context)) {
            val needsBattery = !BackgroundAccess.isIgnoringBatteryOptimizations(context)
            val needsAutostart = BackgroundAccess.needsAutostartStep() && !BackgroundAccess.isAutostartStepDone(context)
            if (needsBattery || needsAutostart) {
                BackgroundAccess.setBackgroundSetupPrompted(context, true)
                showBackgroundSetupDialog = true
            } else {
                BackgroundAccess.setBackgroundSetupPrompted(context, true)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showNotificationBanner = !isGranted
        checkAndShowBackgroundSetup()
    }

    // Request notification permission once on first launch.
    // If permission is denied, keep the existing banner available for retry.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            val prefs = context.getSharedPreferences(
                "unitask_settings",
                android.content.Context.MODE_PRIVATE
            )
            val alreadyPrompted = prefs.getBoolean(
                "notification_permission_prompted",
                false
            )

            if (!permissionGranted && !alreadyPrompted) {
                prefs.edit()
                    .putBoolean("notification_permission_prompted", true)
                    .apply()

                permissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                checkAndShowBackgroundSetup()
            }
        } else {
            checkAndShowBackgroundSetup()
        }
    }
        // Android 12+ can refuse exact alarms. Without them, reminders can arrive minutes late.
    val alarmManager = remember {
        context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
    }
    var needsExactAlarmAccess by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        // Immediate re-read of memberships and open group on resume
        com.example.data.groups.GroupRepository.localChanges.tryEmit(Unit)
        val blocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager?.canScheduleExactAlarms() == false
        if (needsExactAlarmAccess && !blocked) {
            // The user just allowed it: set every reminder again as an exact alarm.
            viewModel.rescheduleAllReminders()
        }
        needsExactAlarmAccess = blocked
        onPauseOrDispose { }
    }

    // ViewModel states
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val allCourses by viewModel.allCourses.collectAsStateWithLifecycle()
    val coursesWithCounts by viewModel.coursesWithCounts.collectAsStateWithLifecycle()
    val archivedSemesters by viewModel.archivedSemesters.collectAsStateWithLifecycle()
    val activeCoursesForArchive by viewModel.activeCoursesForArchive.collectAsStateWithLifecycle()
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val allTasksWithCourse by viewModel.allTasksWithCourse.collectAsStateWithLifecycle()
    val allLectureSlots by viewModel.allLectureSlots.collectAsStateWithLifecycle()
    val highlightedClassInfo by viewModel.highlightedClassInfo.collectAsStateWithLifecycle()
    val groupEntryByItemId by viewModel.groupEntryByItemId.collectAsStateWithLifecycle()
    val groupToOpen by viewModel.groupToOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()
    val courseFilter by viewModel.courseFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val overdueCount by viewModel.overdueCount.collectAsStateWithLifecycle()
    val myTasksOverdueCount by viewModel.myTasksOverdueCount.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedCount.collectAsStateWithLifecycle()
    val dueTodayCount by viewModel.dueTodayCount.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
     val tasksLoaded by viewModel.tasksLoaded.collectAsStateWithLifecycle()
    val isAddEditTaskDialogOpen by viewModel.isAddEditTaskDialogOpen.collectAsStateWithLifecycle()
    val taskToEdit by viewModel.taskToEdit.collectAsStateWithLifecycle()
    val taskAttachmentsToEdit by viewModel.taskAttachmentsToEdit.collectAsStateWithLifecycle()
    val preselectedCourseId by viewModel.preselectedCourseId.collectAsStateWithLifecycle()

    val isAddEditCourseDialogOpen by viewModel.isAddEditCourseDialogOpen.collectAsStateWithLifecycle()
    val courseToEdit by viewModel.courseToEdit.collectAsStateWithLifecycle()
    val courseSaveError by viewModel.courseSaveError.collectAsStateWithLifecycle()
    val courseArchivedConflict by viewModel.courseArchivedConflict.collectAsStateWithLifecycle()


    val isAddEditSlotDialogOpen by viewModel.isAddEditSlotDialogOpen.collectAsStateWithLifecycle()
    val slotToEdit by viewModel.slotToEdit.collectAsStateWithLifecycle()
    val preselectedCourseIdForSlot by viewModel.preselectedCourseIdForSlot.collectAsStateWithLifecycle()
    val preselectedDayForSlot by viewModel.preselectedDayForSlot.collectAsStateWithLifecycle()

    val selectedCourseDetailId by viewModel.selectedCourseDetailId.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val announcements = rememberAnnouncements(currentUser?.uid)
    AnnouncementHost(announcements)
    UpdatePrompt()
    val isCloudBackupSheetOpen by viewModel.isCloudBackupSheetOpen.collectAsStateWithLifecycle()
    val totalUnreadMessages by viewModel.totalUnreadMessages.collectAsStateWithLifecycle()
    val groupChatInfo by viewModel.groupChatInfo.collectAsStateWithLifecycle()
    val groupChatToOpen by viewModel.groupChatToOpen.collectAsStateWithLifecycle()
    val pendingGroupInvite by viewModel.pendingGroupInvite.collectAsStateWithLifecycle()
    val openBellRequest by viewModel.openBellRequest.collectAsStateWithLifecycle()
    val openTimerRequest by viewModel.openTimerRequest.collectAsStateWithLifecycle()
    val myTasks by viewModel.myTasks.collectAsStateWithLifecycle()
    val isMyTaskDialogOpen by viewModel.isMyTaskDialogOpen.collectAsStateWithLifecycle()
    val myTaskToEdit by viewModel.myTaskToEdit.collectAsStateWithLifecycle()

    val pullRefreshState = rememberPullToRefreshState()
    val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val markdownViewerState by viewModel.markdownViewerState.collectAsStateWithLifecycle()
    var openedNoteIdFromViewer by rememberSaveable { mutableStateOf<Long?>(null) }
    // A new number every time the editor opens from the viewer, so each open gets a fresh editor ViewModel
    var viewerEditorSession by rememberSaveable { mutableIntStateOf(0) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    var showRecentlyDeleted by rememberSaveable { mutableStateOf(false) }
    var showArchiveCoursesSheet by rememberSaveable { mutableStateOf(false) }
    var showArchivedSemesters by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    // The timer notification asks for the Timer screen through the ViewModel. Other full-screen
    // panels are closed first, because some are drawn above the Timer and would hide it.
    LaunchedEffect(openTimerRequest) {
        if (openTimerRequest) {
            showTools = false
            showNotes = false
            showRecentlyDeleted = false
            showArchivedSemesters = false
            showArchiveCoursesSheet = false
            showSearch = false
            showTimer = true
            viewModel.onTimerOpened()
        }
    }
    var settingsOpenRequest by remember { mutableStateOf<SettingsOpenRequest?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }

    val db = remember { AppDatabase.getDatabase(context) }
    val activeNotes by remember(db) { db.noteDao().observeNotes() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var hasShownMigrationDialogThisSession by rememberSaveable { mutableStateOf(false) }
    val drivePrefs = remember { DrivePrefs(context) }
    val usedCloudSync = drivePrefs.legacyCloudSyncUser == true
    val hasMigrationData = allCourses.any { !it.isDeleted && !it.isMyTasksCourse() } ||
        allTasksWithCourse.any { !it.task.isDeleted } ||
        activeNotes.any { !it.note.isDeleted }
    val shouldShowDriveMigration = !hasShownMigrationDialogThisSession && DriveMigrationPrompt.shouldPrompt(
        hasData = hasMigrationData,
        usedCloudSync = usedCloudSync,
        driveConnected = drivePrefs.isConnected,
        promptCount = drivePrefs.migrationPromptCount,
        nextPromptAt = drivePrefs.migrationNextPromptAt,
        now = System.currentTimeMillis()
    )

    // Observe timer state to show an active indicator dot on the More button.
    // The state flow only changes on start, pause, resume, reset, and finish,
    // so the top bar does NOT recompose every second. No ticking value is observed here.
    val timerState by TimerController.get(context).state.collectAsStateWithLifecycle()
    val timerActive = !TimerLogic.isIdle(timerState)

    val tools = buildToolsList(
        onOpenNotes = { showNotes = true },
        onOpenTimer = { showTimer = true },
        onOpenRecentlyDeleted = { showRecentlyDeleted = true },
        onOpenSettings = { settingsOpenRequest = SettingsOpenRequest(null) },
        updateAvailable = updateAvailable,
        onOpenHelp = { showHelp = true }
    )
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Back closes search and archived semesters first, then returns to Deadlines, and only then asks to exit.
    BackHandler {
        when {
            showArchivedSemesters -> showArchivedSemesters = false
            showSearch -> showSearch = false
            currentTab != MainTab.DEADLINES -> viewModel.setTab(MainTab.DEADLINES)
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    (context as? ComponentActivity)?.finish() ?: activity?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

        // The course detail screen is drawn over the tabs further down. Remember the last course
    // so it can finish its slide-out animation after it is closed or deleted.
    val detailCourse = remember(coursesWithCounts, selectedCourseDetailId) {
        coursesWithCounts.find { it.course.id == selectedCourseDetailId }
    }
    val lastDetailCourse = remember { mutableStateOf<com.example.data.local.CourseWithTaskCount?>(null) }
    if (detailCourse != null) lastDetailCourse.value = detailCourse
    // While the keyboard is open on the Groups tab, hide the app title bar and the tab row so the
    // chat gets the space. They come back when the keyboard closes.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val hideMainChrome = imeVisible && currentTab == MainTab.GROUPS
    Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!hideMainChrome) TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_stat_unitask),
                                    contentDescription = "UniTask logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Text(
                            text = "UniTask",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSearch = true },
                        modifier = Modifier.testTag("top_bar_search_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    AnnouncementBell(
                        announcements,
                        openRequest = openBellRequest,
                        onOpenRequestHandled = { viewModel.onBellOpened() }
                    ) { target ->
                        when {
                            target.startsWith("group:") -> viewModel.openGroup(target.removePrefix("group:"))
                            target.startsWith("chat:") -> viewModel.openGroup(target.removePrefix("chat:"), openChat = true)
                            target == "tab:DEADLINES" -> viewModel.setTab(MainTab.DEADLINES)
                            target == "tab:TIMETABLE" -> viewModel.setTab(MainTab.TIMETABLE)
                            target == "tab:MYTASKS" -> viewModel.setTab(MainTab.MYTASKS)
                        }
                    }
                    ToolsButton(
                        timerActive = timerActive,
                        updateAvailable = updateAvailable,
                        onClick = { showTools = true }
                    )
                    SettingsButton(
                        currentUser = currentUser,
                        onOpenCloudSheet = { viewModel.openCloudBackupSheet() },
                        onRescheduleAllReminders = { viewModel.rescheduleAllReminders() },
                        openRequest = settingsOpenRequest,
                        onOpenRequestHandled = { settingsOpenRequest = null },
                        showButton = false,
                        onUpdateAvailableChanged = { updateAvailable = it }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!hideMainChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    MainNavBarItem(
                        selected = currentTab == MainTab.DEADLINES,
                        onClick = { viewModel.setTab(MainTab.DEADLINES) },
                        label = "Deadlines",
                        icon = Icons.Default.Flag,
                        // Only overdue work is flagged, because that is what needs attention.
                        // A glowing dot is enough; the numbers are on the tab itself.
                        showDot = overdueCount > 0,
                        testTag = "tab_deadlines"
                    )
                    MainNavBarItem(
                        selected = currentTab == MainTab.TIMETABLE,
                        onClick = { viewModel.setTab(MainTab.TIMETABLE) },
                        label = "Timetable",
                        icon = Icons.Default.Schedule,
                        testTag = "tab_timetable"
                    )
                    MainNavBarItem(
                        selected = currentTab == MainTab.COURSES,
                        onClick = { viewModel.setTab(MainTab.COURSES) },
                        label = "Courses",
                        icon = Icons.Default.School,
                        testTag = "tab_courses"
                    )
                    MainNavBarItem(
                        selected = currentTab == MainTab.GROUPS,
                        onClick = { viewModel.setTab(MainTab.GROUPS) },
                        label = "Groups",
                        icon = Icons.Default.Groups,
                        count = totalUnreadMessages,
                        testTag = "tab_groups",
                        badgeContainerColor = MaterialTheme.colorScheme.error,
                        badgeContentColor = MaterialTheme.colorScheme.onError
                    )
                    MainNavBarItem(
                        selected = currentTab == MainTab.MYTASKS,
                        onClick = { viewModel.setTab(MainTab.MYTASKS) },
                        label = "My tasks",
                        icon = Icons.Default.TaskAlt,
                        showDot = myTasksOverdueCount > 0,
                        testTag = "tab_mytasks"
                    )
                }
            }
        },
        floatingActionButton = {
            // One plain button per tab that adds what the tab lists (Groups has its own buttons).
            if (currentTab != MainTab.GROUPS) {
                // A class needs a course and the class dialog cannot create one, so with no
                // real courses yet the Timetable button starts with the course instead.
                val hasRealCourses = allCourses.any { !it.isDeleted && !it.isMyTasksCourse() }
                val fabDescription = when (currentTab) {
                    MainTab.DEADLINES -> "Add assignment"
                    MainTab.TIMETABLE -> if (hasRealCourses) "Add class slot" else "Add course"
                    MainTab.COURSES -> "Add course"
                    else -> "Add task"
                }
                FloatingActionButton(
                    onClick = {
                        when (currentTab) {
                            MainTab.DEADLINES -> viewModel.openAddTaskDialog()
                            MainTab.TIMETABLE ->
                                if (hasRealCourses) viewModel.openAddSlotDialog() else viewModel.openAddCourseDialog()
                            MainTab.COURSES -> viewModel.openAddCourseDialog()
                            else -> viewModel.openMyTaskDialog(null)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier.testTag("main_fab_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = fabDescription
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showNotificationBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        .testTag("notification_permission_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Deadline Reminders",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap here to receive deadline alerts even when the app is closed.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(
                            onClick = { showNotificationBanner = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
                        if (needsExactAlarmAccess) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        }
                        .testTag("exact_alarm_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow exact reminders",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap to open the setting. Without it, deadline alerts can arrive late.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }



            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshData() },
                state = pullRefreshState
            ) {
                Crossfade(
                    targetState = currentTab,
                    animationSpec = tween(180),
                    label = "main_tabs"
                ) { tab ->
                    // Each tab keeps its own scroll position while you look at another one.
                    tabStateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                    MainTab.DEADLINES -> {
                        DeadlinesTab(
                            tasks = filteredTasks,
                            courses = allCourses,
                            searchQuery = searchQuery,
                            onSearchChange = { viewModel.onSearchQueryChange(it) },
                            statusFilter = statusFilter,
                            onStatusFilterSelect = { viewModel.onStatusFilterSelect(it) },
                            sourceFilter = sourceFilter,
                            onSourceFilterSelect = { viewModel.onSourceFilterSelect(it) },
                            courseFilter = courseFilter,
                            onCourseFilterSelect = { viewModel.onCourseFilterSelect(it) },
                            sortOption = sortOption,
                            onSortOptionSelect = { viewModel.onSortOptionSelect(it) },
                            pendingCount = pendingCount,
                            completedCount = completedCount,
                            dueTodayCount = dueTodayCount,
                            onStatusCycle = { viewModel.cycleTaskStatus(it) },
                            onSetStatus = { task, status -> viewModel.setTaskStatus(task, status) },
                            onEditTask = { viewModel.openEditTaskDialog(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onUndoDeleteTask = { viewModel.undoDeleteTask(it) },
                            onAddTaskClick = { viewModel.openAddTaskDialog() },
                            highlightedClassInfo = highlightedClassInfo,
                            onHighlightedClassTaskClick = { viewModel.openEditTaskDialog(it.task) },
                            groupEntries = groupEntryByItemId,
                            isLoading = !tasksLoaded,
                            snackbarHostState = snackbarHostState,
                            onChecklistToggle = { t, i -> viewModel.toggleChecklistItem(t, i) }
                        )
                    }

                    MainTab.TIMETABLE -> {
                        TimetableTab(
                            allSlots = allLectureSlots,
                            allTasks = allTasksWithCourse,
                            courses = allCourses,
                            onAddSlotClick = { viewModel.openAddSlotDialog() },
                            onSelectedDayChange = { viewModel.setTimetableSelectedDay(it) },
                            onEditSlot = { viewModel.openEditSlotDialog(it) },
                            onDeleteSlot = { slot ->
                                viewModel.deleteLectureSlot(slot)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Class moved to Recently deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDeleteLectureSlot(slot)
                                    }
                                }
                            },
                            onTaskClick = { viewModel.openEditTaskDialog(it.task) },
                            onRescheduleAllReminders = { viewModel.rescheduleAllReminders() }
                        )
                    }

                    MainTab.COURSES -> {
                        CoursesTab(
                            coursesWithCounts = coursesWithCounts,
                            onCourseClick = { courseId -> viewModel.openCourseDetail(courseId) },
                            onEditCourse = { viewModel.openEditCourseDialog(it) },
                            onDeleteCourse = { viewModel.deleteCourse(it) },
                            onAddCourseClick = { viewModel.openAddCourseDialog() },
                            archivedSemestersCount = archivedSemesters.size,
                            onArchiveCoursesClick = { showArchiveCoursesSheet = true },
                            onArchivedSemestersClick = { showArchivedSemesters = true }
                        )
                    }

                    MainTab.GROUPS -> {
                        GroupsTab(
                            onOpenCloudSheet = { viewModel.openCloudBackupSheet() },
                            groupToOpen = groupToOpen,
                            onGroupOpened = { viewModel.onGroupOpened() },
                            chatInfo = groupChatInfo,
                            onChatRead = { groupId, upToMillis -> viewModel.markGroupChatRead(groupId, upToMillis) },
                            openChatGroupId = groupChatToOpen,
                            pendingInvite = pendingGroupInvite,
                            onInviteConsumed = { viewModel.onGroupInviteConsumed() }
                        )
                    }

                    MainTab.MYTASKS -> {
                        MyTasksTab(
                            tasks = myTasks,
                            onStatusCycle = { viewModel.cycleTaskStatus(it) },
                            onUndoTick = { viewModel.restoreTaskSnapshot(it) },
                            onEditTask = { viewModel.openMyTaskDialog(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onUndoDeleteTask = { viewModel.undoDeleteTask(it) },
                            onQuickAdd = { title ->
                                val cal = java.util.Calendar.getInstance()
                                viewModel.saveMyTask(
                                    title = title,
                                    description = "",
                                    dueDateEpochMillis = com.example.data.local.startOfLocalDay(cal.timeInMillis),
                                    hour = 12,
                                    minute = 0,
                                    priority = com.example.data.local.Priority.MEDIUM,
                                    reminders = setOf(com.example.data.local.ReminderOption.AT_DEADLINE),
                                    repeat = com.example.data.local.RepeatRule.NONE,
                                    existing = null
                                )
                            },
                            onAddTaskClick = { viewModel.openMyTaskDialog(null) },
                            snackbarHostState = snackbarHostState,
                            onChecklistToggle = { t, i -> viewModel.toggleChecklistItem(t, i) }
                        )
                    }
                }
                }
                }
            }
        }
    }
        // Course detail slides in over the tabs, so the tabs underneath keep their scroll position.
    AnimatedVisibility(
        visible = detailCourse != null,
        enter = slideInHorizontally(animationSpec = tween(220), initialOffsetX = { it / 5 }) +
            fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(180), targetOffsetX = { it / 5 }) +
            fadeOut(animationSpec = tween(180))
    ) {
        lastDetailCourse.value?.let { detail ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                CourseDetailSheet(
                    courseWithCount = detail,
                    tasks = allTasksWithCourse.filter { it.task.courseId == detail.course.id },
                    slots = allLectureSlots.filter { it.slot.courseId == detail.course.id },
                    onBack = { viewModel.closeCourseDetail() },
                    onEditCourse = { viewModel.openEditCourseDialog(it) },
                    onDeleteCourse = { viewModel.deleteCourse(it) },
                    onAddTask = { courseId -> viewModel.openAddTaskDialog(courseId) },
                    onAddSlot = { courseId -> viewModel.openAddSlotDialog(courseId) },
                    onEditSlot = { viewModel.openEditSlotDialog(it) },
                    onDeleteSlot = { viewModel.deleteLectureSlot(it) },
                    onUndoDeleteSlot = { viewModel.undoDeleteLectureSlot(it) },
                    onStatusCycle = { viewModel.cycleTaskStatus(it) },
                    onEditTask = { viewModel.openEditTaskDialog(it) },
                    onDeleteTask = { viewModel.deleteTask(it) },
                    onUndoDeleteTask = { viewModel.undoDeleteTask(it) },
                    onChecklistToggle = { t, i -> viewModel.toggleChecklistItem(t, i) }
                )
            }
        }
    }
    }
    if (showBackgroundSetupDialog) {
        BackgroundSetupDialog(
            onDismiss = { showBackgroundSetupDialog = false }
        )
    }

    if (shouldShowDriveMigration &&
        announcements.popup.isEmpty() &&
        !showBackgroundSetupDialog &&
        !isCloudBackupSheetOpen &&
        !isAddEditTaskDialogOpen &&
        !isAddEditCourseDialogOpen &&
        !isAddEditSlotDialogOpen &&
        !isMyTaskDialogOpen &&
        !showNotes &&
        !showTools &&
        !showTimer &&
        !showRecentlyDeleted &&
        !showArchivedSemesters &&
        !showSearch
    ) {
        DriveMigrationDialog(
            onDismiss = { hasShownMigrationDialogThisSession = true }
        )
    }

    if (isAddEditTaskDialogOpen) {
        AddEditTaskDialog(
            courses = allCourses,
            taskToEdit = taskToEdit,
            existingAttachments = taskAttachmentsToEdit,
            initialCourseId = preselectedCourseId,
            onDismiss = { viewModel.closeTaskDialog() },
            onDeleteExistingAttachment = { viewModel.deleteTaskAttachment(it) },
            onSave = { title, desc, courseId, dueDate, hour, min, priority, status, reminder, newAtt ->
                viewModel.saveTask(title, desc, courseId, dueDate, hour, min, priority, status, reminder, newAtt)
            },
            onNeedCreateCourse = {
                viewModel.closeTaskDialog()
                viewModel.openAddCourseDialog()
            }
        )
    }

    if (isAddEditCourseDialogOpen) {
        AddEditCourseDialog(
            courseToEdit = courseToEdit,
            onDismiss = { viewModel.closeCourseDialog() },
            onSave = { name, code, instructor, color, location ->
                viewModel.saveCourse(name, code, instructor, color, location)
            },
            duplicateError = courseSaveError,
            onClearDuplicateError = { viewModel.clearCourseSaveError() },
            archivedConflict = courseArchivedConflict,
            onCheckArchivedCode = { code ->
                viewModel.findArchivedCourseByCode(code)?.archivedSemester
            },
            onRestoreSemester = { semesterName ->
                viewModel.restoreSemester(semesterName) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Restored $semesterName",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                viewModel.closeCourseDialog()
            }
        )
    }

    if (isAddEditSlotDialogOpen) {
        AddEditLectureSlotDialog(
            courses = allCourses,
            slotToEdit = slotToEdit,
            existingSlots = allLectureSlots,
            preselectedCourseId = preselectedCourseIdForSlot,
            preselectedDay = preselectedDayForSlot,
            onDismiss = { viewModel.closeSlotDialog() },
            onSave = { courseId, dayOfWeek, startHour, startMinute, endHour, endMinute, classroom, instructor ->
                viewModel.saveLectureSlot(courseId, dayOfWeek, startHour, startMinute, endHour, endMinute, classroom, instructor)
            }
        )
    }

    if (isCloudBackupSheetOpen) {
        CloudBackupSheet(
            currentUser = currentUser,
            onDismiss = { viewModel.closeCloudBackupSheet() },
            onAuthChanged = { viewModel.refreshAuth() }
        )
    }

    if (isMyTaskDialogOpen) {
        MyTaskDialog(
            taskToEdit = myTaskToEdit,
            onDismiss = { viewModel.closeMyTaskDialog() },
            onSave = { title, desc, dueEpoch, hour, min, priority, reminders, repeat, autoDelete ->
                viewModel.saveMyTask(title, desc, dueEpoch, hour, min, priority, reminders, repeat, myTaskToEdit, autoDelete)
            }
        )
    }

    if (showTools) {
        ToolsSheet(
            tools = tools,
            onDismiss = { showTools = false }
        )
    }

    if (showHelp) {
        FeatureGuideDialog(onDismiss = { showHelp = false })
    }

    if (showNotes) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NotesHost(onClose = { showNotes = false })
        }
    }

    if (showTimer) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            TimerScreen(onClose = { showTimer = false })
        }
    }

    if (showRecentlyDeleted) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            RecentlyDeletedScreen(onClose = { showRecentlyDeleted = false })
        }
    }

    if (showArchivedSemesters) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ArchivedSemestersScreen(
                semesters = archivedSemesters,
                onRestoreSemester = { semesterName ->
                    viewModel.restoreSemester(semesterName) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Restored $semesterName",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onDeleteSemesterForever = { semesterName ->
                    viewModel.deleteSemesterForever(semesterName) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Deleted $semesterName",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onClose = { showArchivedSemesters = false }
            )
        }
    }

    if (showSearch) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SearchScreen(
                onClose = { showSearch = false },
                onNavigateToPersonalTask = { taskWithCourse, isMyTask ->
                    showSearch = false
                    if (isMyTask) {
                        viewModel.setTab(MainTab.MYTASKS)
                        viewModel.openMyTaskDialog(taskWithCourse.task)
                    } else {
                        viewModel.setTab(MainTab.DEADLINES)
                        viewModel.openEditTaskDialog(taskWithCourse.task)
                    }
                },
                onNavigateToGroupTask = { taskTitle ->
                    showSearch = false
                    viewModel.setTab(MainTab.DEADLINES)
                    viewModel.onSearchQueryChange(taskTitle)
                },
                onNavigateToCourse = { courseId ->
                    showSearch = false
                    viewModel.setTab(MainTab.COURSES)
                    viewModel.openCourseDetail(courseId)
                },
                onRestoreSemester = { semesterName ->
                    viewModel.restoreSemester(semesterName) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Restored $semesterName",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onNavigateToSettings = { page, opensAccount ->
                    showSearch = false
                    if (opensAccount) {
                        viewModel.openCloudBackupSheet()
                    } else {
                        settingsOpenRequest = SettingsOpenRequest(page)
                    }
                },
                groupTasks = groupEntryByItemId.values.map { it.toTaskWithCourse() }
            )
        }
    }

    if (showArchiveCoursesSheet) {
        ArchiveCoursesSheet(
            courses = activeCoursesForArchive,
            existingSemesterNames = archivedSemesters.map { it.name },
            onConfirmArchive = { courseIds, semesterName ->
                showArchiveCoursesSheet = false
                viewModel.archiveCourses(courseIds, semesterName) {
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val courseLabel = if (courseIds.size == 1) "1 course" else "${courseIds.size} courses"
                        val result = snackbarHostState.showSnackbar(
                            message = "Archived $courseLabel to $semesterName",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoArchive(courseIds)
                        }
                    }
                }
            },
            onDismiss = { showArchiveCoursesSheet = false }
        )
    }

    markdownViewerState?.let { mState ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MarkdownFileViewer(
                state = mState,
                onClose = { viewModel.closeMarkdownViewer() },
                onSaveToNotes = { displayName, content ->
                    viewModel.saveMarkdownToNotes(displayName, content)
                },
                onOpenSavedNote = { noteId ->
                    viewModel.closeMarkdownViewer()
                    viewerEditorSession = NoteEditorSessions.next()
                    openedNoteIdFromViewer = noteId
                }
            )
        }
    }

    openedNoteIdFromViewer?.let { targetId ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NoteEditorScreen(
                noteId = targetId,
                viewModelSession = viewerEditorSession,
                onClose = { openedNoteIdFromViewer = null },
                onDeleted = { openedNoteIdFromViewer = null }
            )
        }
    }
    }
}

/**
 * One item of the main navigation bar at the bottom.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MainNavBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    testTag: String,
    // A number badge, kept for unread group messages.
    count: Int = 0,
    badgeContainerColor: Color = MaterialTheme.colorScheme.primary,
    badgeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    // A glowing dot with no number, for tabs that only need to say "look here" (overdue tasks).
    showDot: Boolean = false
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        icon = {
            if (showDot) {
                Box {
                    Icon(
                        imageVector = icon,
                        contentDescription = "$label, overdue tasks",
                        modifier = Modifier.size(24.dp)
                    )
                    OverdueGlowDot(
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                    )
                }
            } else {
                BadgedBox(
                    badge = {
                        if (count > 0) {
                            Badge(
                                containerColor = badgeContainerColor,
                                contentColor = badgeContentColor
                            ) {
                                Text(if (count > 99) "99+" else "$count")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        label = {
            ShrinkToFitText(
                text = label,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                minFontSize = 9.sp
            )
        }
    )
}
