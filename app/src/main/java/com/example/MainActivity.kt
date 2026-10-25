package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
        if (!groupId.isNullOrBlank()) {
            mainViewModel.openGroup(groupId, openChat = openChat)
            intent.removeExtra("open_group_id")
            intent.removeExtra("open_group_chat")
        } else if (openedTaskId > 0L || intent.hasExtra("opened_task_id")) {
            mainViewModel.setTab(com.example.ui.screens.MainTab.DEADLINES)
            intent.removeExtra("opened_task_id")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Leave the splash theme before the first frame is drawn.
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        ThemePreference.load(applicationContext)

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
