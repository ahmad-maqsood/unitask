package com.example.ui.screens

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.util.BackupCounts
import com.example.util.BackupResult
import com.example.util.drive.DriveAuthApi
import com.example.util.drive.DriveAuthResult
import com.example.util.drive.DriveAutoBackup
import com.example.util.drive.DriveBackupInfo
import com.example.util.drive.DriveBackupManager
import com.example.util.drive.DriveBackupResult
import com.example.util.drive.DriveException
import com.example.util.drive.DrivePrefs
import com.example.util.drive.DriveRestClient
import com.example.util.drive.DriveStore
import com.example.util.drive.GoogleDriveAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class DriveConnectionState {
    object NotConnected : DriveConnectionState()
    data class Connected(val email: String) : DriveConnectionState()
    object NeedsReconnect : DriveConnectionState()
}

data class DriveBusyState(
    val label: String,
    val progressText: String? = null
)

data class DriveUiState(
    val connection: DriveConnectionState = DriveConnectionState.NotConnected,
    val latestBackupInfo: DriveBackupInfo? = null,
    val offlineBackupText: String? = null,
    val busy: DriveBusyState? = null,
    val lastResultMessage: String? = null,
    val isError: Boolean = false,
    val autoEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val includeAttachments: Boolean = true,
    val consentRequest: PendingIntent? = null
)

class DriveBackupViewModel(
    application: Application,
    private val auth: DriveAuthApi,
    private val storeFactory: ((suspend (forceRefresh: Boolean) -> String) -> DriveStore),
    private val prefs: DrivePrefs
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        GoogleDriveAuth(application),
        { provider -> DriveRestClient(tokenProvider = provider) },
        DrivePrefs(application)
    )

    private var lastToken: String? = null

    private val tokenProvider: suspend (forceRefresh: Boolean) -> String = { forceRefresh ->
        if (forceRefresh && lastToken != null) {
            auth.clearToken(lastToken!!)
            lastToken = null
        }
        when (val res = auth.authorize()) {
            is DriveAuthResult.Granted -> {
                lastToken = res.accessToken
                if (res.accountEmail != null) {
                    prefs.connectedEmail = res.accountEmail
                }
                res.accessToken
            }
            is DriveAuthResult.NeedsConsent -> {
                // Never launch a consent screen from the background
                _uiState.update { it.copy(connection = DriveConnectionState.NeedsReconnect, busy = null) }
                throw DriveException(DriveException.Kind.AUTH, "Google Drive access was lost.")
            }
            is DriveAuthResult.Failed -> {
                throw DriveException(DriveException.Kind.AUTH, res.message)
            }
        }
    }

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private fun createInitialState(): DriveUiState {
        val email = prefs.connectedEmail
        val connection = if (email != null) {
            DriveConnectionState.Connected(email)
        } else {
            DriveConnectionState.NotConnected
        }
        val offlineText = if (prefs.lastSuccessAt > 0L) {
            val counts = prefs.lastCounts
            val time = formatBackupTime(prefs.lastSuccessAt)
            if (!counts.isNullOrBlank()) "$time, $counts" else time
        } else {
            null
        }
        return DriveUiState(
            connection = connection,
            offlineBackupText = offlineText,
            lastResultMessage = prefs.lastError,
            isError = prefs.lastError != null,
            autoEnabled = prefs.autoEnabled,
            wifiOnly = prefs.wifiOnly,
            includeAttachments = prefs.includeAttachments
        )
    }

    init {
        if (prefs.isConnected) {
            refreshInfo()
        }
    }

    fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busy = DriveBusyState("Connecting"), lastResultMessage = null, isError = false) }
            when (val res = auth.authorize()) {
                is DriveAuthResult.Granted -> {
                    lastToken = res.accessToken
                    val email = res.accountEmail ?: "Connected"
                    prefs.connectedEmail = email
                    _uiState.update {
                        it.copy(
                            connection = DriveConnectionState.Connected(email),
                            busy = null,
                            lastResultMessage = null,
                            isError = false
                        )
                    }
                    refreshInfoInternal()
                }
                is DriveAuthResult.NeedsConsent -> {
                    _uiState.update { it.copy(busy = null, consentRequest = res.pendingIntent) }
                }
                is DriveAuthResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            connection = DriveConnectionState.NotConnected,
                            busy = null,
                            lastResultMessage = res.message,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busy = DriveBusyState("Connecting"), consentRequest = null) }
            when (val res = auth.resultFromIntent(data)) {
                is DriveAuthResult.Granted -> {
                    lastToken = res.accessToken
                    val email = res.accountEmail ?: "Connected"
                    prefs.connectedEmail = email
                    _uiState.update {
                        it.copy(
                            connection = DriveConnectionState.Connected(email),
                            busy = null,
                            lastResultMessage = null,
                            isError = false
                        )
                    }
                    refreshInfoInternal()
                }
                is DriveAuthResult.NeedsConsent -> {
                    _uiState.update { it.copy(busy = null, consentRequest = res.pendingIntent) }
                }
                is DriveAuthResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            connection = DriveConnectionState.NotConnected,
                            busy = null,
                            lastResultMessage = res.message,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun onConsentLaunched() {
        _uiState.update { it.copy(consentRequest = null) }
    }

    fun backUpNow(allowEmpty: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busy = DriveBusyState("Backing up"), lastResultMessage = null, isError = false) }
            try {
                val store = storeFactory(tokenProvider)
                val result = DriveBackupManager.backUp(
                    context = getApplication(),
                    store = store,
                    allowEmpty = allowEmpty,
                    force = true,
                    onProgress = { done, total ->
                        _uiState.update {
                            it.copy(busy = DriveBusyState("Backing up", "Backing up ($done of $total files)"))
                        }
                    }
                )
                when (result) {
                    is DriveBackupResult.Success -> {
                        val info = result.info
                        val countsText = buildCountsText(info)
                        val offlineText = "${formatBackupTime(info.exportedAt)}, $countsText"
                        _uiState.update {
                            it.copy(
                                busy = null,
                                latestBackupInfo = info,
                                offlineBackupText = offlineText,
                                lastResultMessage = "Backup complete: $countsText.",
                                isError = false
                            )
                        }
                    }
                    is DriveBackupResult.Unchanged -> {
                        _uiState.update {
                            it.copy(
                                busy = null,
                                latestBackupInfo = result.info,
                                lastResultMessage = "Backup is already up to date.",
                                isError = false
                            )
                        }
                    }
                    is DriveBackupResult.EmptyBlocked -> {
                        _uiState.update {
                            it.copy(
                                busy = null,
                                lastResultMessage = "Your app has no data.",
                                isError = true
                            )
                        }
                    }
                    is DriveBackupResult.Failure -> {
                        val isAuth = result.kind == DriveException.Kind.AUTH
                        val conn = if (isAuth) DriveConnectionState.NeedsReconnect else _uiState.value.connection
                        val msg = if (result.kind == DriveException.Kind.QUOTA) {
                            "Your Google Drive is full."
                        } else {
                            result.message
                        }
                        _uiState.update {
                            it.copy(
                                connection = conn,
                                busy = null,
                                lastResultMessage = msg,
                                isError = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                val isAuth = e is DriveException && e.kind == DriveException.Kind.AUTH
                val isQuota = e is DriveException && e.kind == DriveException.Kind.QUOTA
                val conn = if (isAuth) DriveConnectionState.NeedsReconnect else _uiState.value.connection
                val msg = when {
                    isQuota -> "Your Google Drive is full."
                    isAuth -> "Google Drive access was lost."
                    else -> e.message ?: "Backup failed"
                }
                _uiState.update {
                    it.copy(
                        connection = conn,
                        busy = null,
                        lastResultMessage = msg,
                        isError = true
                    )
                }
            }
        }
    }

    fun restoreFromDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busy = DriveBusyState("Restoring"), lastResultMessage = null, isError = false) }
            try {
                val store = storeFactory(tokenProvider)
                val result = DriveBackupManager.restore(
                    context = getApplication(),
                    store = store
                )
                when (result) {
                    is BackupResult.Success -> {
                        val desc = describe(result.counts)
                        _uiState.update {
                            it.copy(
                                busy = null,
                                lastResultMessage = "Restored $desc.",
                                isError = false
                            )
                        }
                        refreshInfoInternal()
                    }
                    is BackupResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                busy = null,
                                lastResultMessage = result.message,
                                isError = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                val isAuth = e is DriveException && e.kind == DriveException.Kind.AUTH
                val isQuota = e is DriveException && e.kind == DriveException.Kind.QUOTA
                val conn = if (isAuth) DriveConnectionState.NeedsReconnect else _uiState.value.connection
                val msg = when {
                    isQuota -> "Your Google Drive is full."
                    isAuth -> "Google Drive access was lost."
                    else -> e.message ?: "Restore failed"
                }
                _uiState.update {
                    it.copy(
                        connection = conn,
                        busy = null,
                        lastResultMessage = msg,
                        isError = true
                    )
                }
            }
        }
    }

    fun setAuto(enabled: Boolean) {
        prefs.autoEnabled = enabled
        _uiState.update { it.copy(autoEnabled = enabled) }
        if (enabled) {
            DriveAutoBackup.onAppOpen(getApplication())
            if (prefs.lastSuccessAt == 0L && _uiState.value.latestBackupInfo == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    if (!DriveBackupManager.isDatabaseEmpty(getApplication())) {
                        backUpNow(allowEmpty = false)
                    }
                }
            }
        } else {
            DriveAutoBackup.cancel(getApplication())
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.wifiOnly = enabled
        _uiState.update { it.copy(wifiOnly = enabled) }
        DriveAutoBackup.onAppOpen(getApplication())
    }

    fun setIncludeAttachments(enabled: Boolean) {
        prefs.includeAttachments = enabled
        _uiState.update { it.copy(includeAttachments = enabled) }
        DriveAutoBackup.onAppOpen(getApplication())
    }

    fun disconnect() {
        prefs.forgetAccount()
        DriveAutoBackup.cancel(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            val token = lastToken
            if (!token.isNullOrBlank()) {
                auth.clearToken(token)
            }
            lastToken = null
            DriveAutoBackup.cancel(getApplication())
            _uiState.update {
                it.copy(
                    connection = DriveConnectionState.NotConnected,
                    latestBackupInfo = null,
                    offlineBackupText = null,
                    lastResultMessage = null,
                    isError = false,
                    autoEnabled = false
                )
            }
        }
    }

    fun refreshInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshInfoInternal()
        }
    }

    private suspend fun refreshInfoInternal() {
        if (!prefs.isConnected) return
        try {
            val store = storeFactory(tokenProvider)
            val info = DriveBackupManager.latestInfo(store)
            if (info != null) {
                val countsText = buildCountsText(info)
                val offlineText = "${formatBackupTime(info.exportedAt)}, $countsText"
                _uiState.update {
                    it.copy(
                        latestBackupInfo = info,
                        offlineBackupText = offlineText
                    )
                }
            }
        } catch (e: Exception) {
            if (e is DriveException && e.kind == DriveException.Kind.AUTH) {
                _uiState.update { it.copy(connection = DriveConnectionState.NeedsReconnect) }
            }
        }
    }

    suspend fun isAppEmpty(): Boolean = withContext(Dispatchers.IO) {
        DriveBackupManager.isDatabaseEmpty(getApplication())
    }

    companion object {
        fun buildCountsText(info: DriveBackupInfo): String {
            val parts = mutableListOf(
                "${info.courses} courses",
                "${info.tasks} tasks",
                "${info.notes} notes"
            )
            if (info.attachments > 0) {
                parts.add("${info.attachments} attachments")
            }
            return parts.joinToString(", ")
        }

        fun formatBackupTime(millis: Long): String {
            if (millis <= 0L) return ""
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = millis }
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
            return when {
                now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "Today $timeStr"
                now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday $timeStr"
                else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))
            }
        }

        fun provideFactory(
            application: Application,
            auth: DriveAuthApi = GoogleDriveAuth(application),
            storeFactory: ((suspend (forceRefresh: Boolean) -> String) -> DriveStore) = { DriveRestClient(tokenProvider = it) },
            prefs: DrivePrefs = DrivePrefs(application)
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DriveBackupViewModel(application, auth, storeFactory, prefs) as T
            }
        }
    }
}
