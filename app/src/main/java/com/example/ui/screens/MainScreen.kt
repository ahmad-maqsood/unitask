package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.StatusDoneFgDark
import com.example.ui.theme.StatusDoneFgLight
import com.example.ui.theme.isDarkTheme
import com.example.util.BackgroundAccess
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.Crossfade
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

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showBackgroundSetupDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.refreshMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
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
    val completedCount by viewModel.completedCount.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
     val tasksLoaded by viewModel.tasksLoaded.collectAsStateWithLifecycle()
    val isAddEditTaskDialogOpen by viewModel.isAddEditTaskDialogOpen.collectAsStateWithLifecycle()
    val taskToEdit by viewModel.taskToEdit.collectAsStateWithLifecycle()
    val taskAttachmentsToEdit by viewModel.taskAttachmentsToEdit.collectAsStateWithLifecycle()
    val preselectedCourseId by viewModel.preselectedCourseId.collectAsStateWithLifecycle()

    val isAddEditCourseDialogOpen by viewModel.isAddEditCourseDialogOpen.collectAsStateWithLifecycle()
    val courseToEdit by viewModel.courseToEdit.collectAsStateWithLifecycle()
    val courseSaveError by viewModel.courseSaveError.collectAsStateWithLifecycle()


    val isAddEditSlotDialogOpen by viewModel.isAddEditSlotDialogOpen.collectAsStateWithLifecycle()
    val slotToEdit by viewModel.slotToEdit.collectAsStateWithLifecycle()
    val preselectedCourseIdForSlot by viewModel.preselectedCourseIdForSlot.collectAsStateWithLifecycle()
    val preselectedDayForSlot by viewModel.preselectedDayForSlot.collectAsStateWithLifecycle()

    val selectedCourseDetailId by viewModel.selectedCourseDetailId.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val announcements = rememberAnnouncements(currentUser?.uid)
    AnnouncementHost(announcements)
    val isCloudBackupSheetOpen by viewModel.isCloudBackupSheetOpen.collectAsStateWithLifecycle()
    val totalUnreadMessages by viewModel.totalUnreadMessages.collectAsStateWithLifecycle()
    val groupChatInfo by viewModel.groupChatInfo.collectAsStateWithLifecycle()
    val groupChatToOpen by viewModel.groupChatToOpen.collectAsStateWithLifecycle()
    val myTasks by viewModel.myTasks.collectAsStateWithLifecycle()
    val myTasksPendingCount by viewModel.myTasksPendingCount.collectAsStateWithLifecycle()
    val isMyTaskDialogOpen by viewModel.isMyTaskDialogOpen.collectAsStateWithLifecycle()
    val myTaskToEdit by viewModel.myTaskToEdit.collectAsStateWithLifecycle()

    val pullRefreshState = rememberPullToRefreshState()
    val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    // FAB Speed-dial menu expanded state
    var isFabMenuExpanded by remember { mutableStateOf(false) }

        // Back closes the speed-dial menu first, then returns to Deadlines, and only then asks to exit.
    BackHandler {
        when {
            isFabMenuExpanded -> isFabMenuExpanded = false
            currentTab != MainTab.DEADLINES -> viewModel.setTab(MainTab.DEADLINES)
            else -> showExitConfirmDialog = true
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
                                    imageVector = Icons.Default.School,
                                    contentDescription = "UniTask logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "UniTask",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "University Task Manager",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    AnnouncementBell(announcements)
                    SettingsButton(
                        currentUser = currentUser,
                        onOpenCloudSheet = { viewModel.openCloudBackupSheet() }
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
                        icon = Icons.Default.AccessTime,
                        count = pendingCount,
                        testTag = "tab_deadlines"
                    )
                    MainNavBarItem(
                        selected = currentTab == MainTab.TIMETABLE,
                        onClick = { viewModel.setTab(MainTab.TIMETABLE) },
                        label = "Timetable",
                        icon = Icons.Default.Schedule,
                        count = allLectureSlots.size,
                        testTag = "tab_timetable"
                    )
                    MainNavBarItem(
                        selected = currentTab == MainTab.COURSES,
                        onClick = { viewModel.setTab(MainTab.COURSES) },
                        label = "Courses",
                        icon = Icons.Default.School,
                        count = coursesWithCounts.size,
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
                        count = myTasksPendingCount,
                        testTag = "tab_mytasks"
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab != MainTab.GROUPS) {
                if (currentTab == MainTab.MYTASKS) {
                    FloatingActionButton(
                        onClick = { viewModel.openMyTaskDialog(null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                        modifier = Modifier.testTag("main_fab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add task"
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isFabMenuExpanded,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 3.dp
                                    ) {
                                        Text(
                                            text = "Add Course",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    SmallFloatingActionButton(
                                        onClick = {
                                            isFabMenuExpanded = false
                                            viewModel.openAddCourseDialog()
                                        },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.testTag("fab_add_course_option")
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = "Add Course")
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 3.dp
                                    ) {
                                        Text(
                                            text = "Add Class Slot",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    SmallFloatingActionButton(
                                        onClick = {
                                            isFabMenuExpanded = false
                                            viewModel.openAddSlotDialog()
                                        },
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.testTag("fab_add_slot_option")
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = "Add Class Slot")
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 3.dp
                                    ) {
                                        Text(
                                            text = "Add Assignment",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    SmallFloatingActionButton(
                                        onClick = {
                                            isFabMenuExpanded = false
                                            viewModel.openAddTaskDialog()
                                        },
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.testTag("fab_add_task_option")
                                    ) {
                                        Icon(Icons.Default.Assignment, contentDescription = "Add Assignment")
                                    }
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = !isFabMenuExpanded
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                            modifier = Modifier.testTag("main_fab_button")
                        ) {
                            Icon(
                                imageVector = if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = if (isFabMenuExpanded) "Close add menu" else "Add task or course"
                            )
                        }
                    }
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
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { showNotificationBanner = false }
                        )
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
                            onStatusCycle = { viewModel.cycleTaskStatus(it) },
                            onEditTask = { viewModel.openEditTaskDialog(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onAddTaskClick = { viewModel.openAddTaskDialog() },
                            highlightedClassInfo = highlightedClassInfo,
                            onHighlightedClassTaskClick = { viewModel.openEditTaskDialog(it.task) },
                            groupEntries = groupEntryByItemId,
                            isLoading = !tasksLoaded
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
                            onDeleteSlot = { viewModel.deleteLectureSlot(it) },
                            onTaskClick = { viewModel.openEditTaskDialog(it.task) }
                        )
                    }

                    MainTab.COURSES -> {
                        CoursesTab(
                            coursesWithCounts = coursesWithCounts,
                            onCourseClick = { courseId -> viewModel.openCourseDetail(courseId) },
                            onEditCourse = { viewModel.openEditCourseDialog(it) },
                            onDeleteCourse = { viewModel.deleteCourse(it) },
                            onAddCourseClick = { viewModel.openAddCourseDialog() }
                        )
                    }

                    MainTab.GROUPS -> {
                        GroupsTab(
                            onOpenCloudSheet = { viewModel.openCloudBackupSheet() },
                            groupToOpen = groupToOpen,
                            onGroupOpened = { viewModel.onGroupOpened() },
                            chatInfo = groupChatInfo,
                            onChatRead = { groupId, upToMillis -> viewModel.markGroupChatRead(groupId, upToMillis) },
                            openChatGroupId = groupChatToOpen
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
                                    dueDateEpochMillis = cal.timeInMillis,
                                    hour = 12,
                                    minute = 0,
                                    priority = com.example.data.local.Priority.MEDIUM,
                                    reminders = setOf(com.example.data.local.ReminderOption.AT_DEADLINE),
                                    repeat = com.example.data.local.RepeatRule.NONE,
                                    existing = null
                                )
                            },
                            onAddTaskClick = { viewModel.openMyTaskDialog(null) },
                            snackbarHostState = snackbarHostState
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
                    onStatusCycle = { viewModel.cycleTaskStatus(it) },
                    onEditTask = { viewModel.openEditTaskDialog(it) },
                    onDeleteTask = { viewModel.deleteTask(it) }
                )
            }
        }
    }
    }
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Exit UniTask?") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        activity?.finishAffinity()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBackgroundSetupDialog && !showExitConfirmDialog) {
        BackgroundSetupDialog(
            onDismiss = { showBackgroundSetupDialog = false }
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
            onClearDuplicateError = { viewModel.clearCourseSaveError() }

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
            onSave = { title, desc, dueEpoch, hour, min, priority, reminders, repeat ->
                viewModel.saveMyTask(title, desc, dueEpoch, hour, min, priority, reminders, repeat, myTaskToEdit)
            }
        )
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
    count: Int,
    testTag: String,
    badgeContainerColor: Color = MaterialTheme.colorScheme.primary,
    badgeContentColor: Color = MaterialTheme.colorScheme.onPrimary
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
        },
        label = {
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    )
}
