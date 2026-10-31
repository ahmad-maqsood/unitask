package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.example.ui.screens.MainScreen
import com.example.ui.screens.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
import com.example.util.AppVisibility

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
        // Moves repeating tasks that were ticked on an earlier day to their next date, and makes
        // sure the midnight alarm is set. Covers a missed alarm.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.example.reminder.RepeatRolloverReceiver.rolloverAndReschedule(applicationContext)
            } catch (_: Exception) {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppVisibility.isForeground = true
        com.example.data.groups.GroupRepository.localChanges.tryEmit(Unit)
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.isForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val groupId = intent.getStringExtra("open_group_id")
        val openChat = intent.getBooleanExtra("open_group_chat", false)
        val openedTaskId = intent.getLongExtra("opened_task_id", -1L)
        val isMyTask = intent.getBooleanExtra("extra_is_my_task", false)
        if (!groupId.isNullOrBlank()) {
            mainViewModel.openGroup(groupId, openChat = openChat)
            intent.removeExtra("open_group_id")
            intent.removeExtra("open_group_chat")
        } else if (openedTaskId > 0L || intent.hasExtra("opened_task_id")) {
            if (isMyTask) {
                mainViewModel.setTab(com.example.ui.screens.MainTab.MYTASKS)
            } else {
                mainViewModel.setTab(com.example.ui.screens.MainTab.DEADLINES)
            }
            intent.removeExtra("opened_task_id")
            intent.removeExtra("extra_is_my_task")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Leave the splash theme before the first frame is drawn.
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        ThemePreference.load(applicationContext)

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.example.data.local.AppDatabase.getDatabase(applicationContext)
                val known = db.taskAttachmentDao().getAllRelativePaths().toSet()
                com.example.util.AttachmentManager.deleteOrphanedFiles(applicationContext, known)
            } catch (_: Exception) {}
        }

        if (savedInstanceState == null) {
            handleNotificationIntent(intent)
        }

        setContent {
            val darkTheme = when (ThemePreference.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keeps the status bar and navigation bar icons readable in the chosen theme.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        detectDarkMode = { darkTheme }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                        detectDarkMode = { darkTheme }
                    )
                )
                onDispose {}
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = mainViewModel)
                }
            }
        }
    }
}
