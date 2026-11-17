package com.example.ui.screens

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
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
import com.example.util.DateFormats
import java.util.Calendar

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
    val consentRequest: PendingIntent? = null,
    val showRestoreConfirm: Boolean = false,
    val showNoBackupDialog: Boolean = false,
    /** The second, older backup on Drive, shown as a choice in the restore dialog. Null if none. */
    val olderBackupInfo: DriveBackupInfo? = null,
    /** True when the person picked the older backup in the restore dialog. */
    val useOlderBackup: Boolean = false
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

    /** Text from strings.xml, so every message can be translated. */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private var lastToken: String? = null
    private var pendingRestoreAfterConsent: Boolean = false
    private var pendingBackupAfterConsent: Boolean = false

    private val tokenProvider: suspend (forceRefresh: Boolean) -> String = { forceRefresh ->
        val staleToken = lastToken
        if (forceRefresh && staleToken != null) {
            auth.clearToken(staleToken)
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
                throw DriveException(DriveException.Kind.AUTH, str(R.string.drive_backup_google_drive_access_was_lost))
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
            _uiState.update { it.copy(busy = DriveBusyState(str(R.string.drive_busy_connecting)), lastResultMessage = null, isError = false) }
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
                    // Automatic backup is on by default: start watching for changes now.
                    DriveAutoBackup.onAppOpen(getApplication())
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
            _uiState.update { it.copy(busy = DriveBusyState(str(R.string.drive_busy_connecting)), consentRequest = null) }
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
                    if (pendingRestoreAfterConsent) {
                        pendingRestoreAfterConsent = false
                        checkLatestAndPromptRestore()
                    } else if (pendingBackupAfterConsent) {
                        pendingBackupAfterConsent = false
                        DriveAutoBackup.onAppOpen(getApplication())
                        backUpNow(allowEmpty = false)
                    } else {
                        DriveAutoBackup.onAppOpen(getApplication())
                        refreshInfoInternal()
                    }
                }
                is DriveAuthResult.NeedsConsent -> {
                    _uiState.update { it.copy(busy = null, consentRequest = res.pendingIntent) }
                }
                is DriveAuthResult.Failed -> {
                    pendingRestoreAfterConsent = false
                    pendingBackupAfterConsent = false
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
            _uiState.update { it.copy(busy = DriveBusyState(str(R.string.drive_busy_backing_up)), lastResultMessage = null, isError = false) }
            try {
                val store = storeFactory(tokenProvider)
                val result = DriveBackupManager.backUp(
                    context = getApplication(),
                    store = store,
                    allowEmpty = allowEmpty,
                    force = true,
                    onProgress = { done, total ->
                        _uiState.update {
                            it.copy(
                        busy = DriveBusyState(
                            str(R.string.drive_busy_backing_up),
                            str(R.string.drive_busy_backing_up_files, done, total)
                        )
                    )
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
                                lastResultMessage = str(R.string.drive_result_backup_complete, countsText),
                                isError = false
                            )
                        }
                    }
                    is DriveBackupResult.Unchanged -> {
                        _uiState.update {
                            it.copy(
                                busy = null,
                                latestBackupInfo = result.info,
                                lastResultMessage = str(R.string.drive_result_backup_up_to_date),
                                isError = false
                            )
                        }
                    }
                    is DriveBackupResult.EmptyBlocked -> {
                        _uiState.update {
                            it.copy(
                                busy = null,
                                lastResultMessage = str(R.string.drive_result_app_has_no_data),
                                isError = true
                            )
                        }
                    }
                    is DriveBackupResult.Failure -> {
                        val isAuth = result.kind == DriveException.Kind.AUTH
                        val conn = if (isAuth) DriveConnectionState.NeedsReconnect else _uiState.value.connection
                        val msg = if (result.kind == DriveException.Kind.QUOTA) {
                            str(R.string.drive_error_drive_full)
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
                    isQuota -> str(R.string.drive_error_drive_full)
                    isAuth -> str(R.string.drive_backup_google_drive_access_was_lost)
                    else -> e.message ?: str(R.string.drive_error_backup_failed)
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

    /** @param slotFileId restores this one backup only (the older one); null restores the newest. */
    fun restoreFromDrive(slotFileId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busy = DriveBusyState(str(R.string.drive_busy_restoring)), lastResultMessage = null, isError = false) }
            try {
                val store = storeFactory(tokenProvider)
                val result = DriveBackupManager.restore(
                    context = getApplication(),
                    store = store,
                    slotFileId = slotFileId
                )
                when (result) {
                    is BackupResult.Success -> {
                        val desc = describe(result.counts)
                        _uiState.update {
                            it.copy(
                                busy = null,
                                lastResultMessage = str(R.string.drive_result_restored, desc),
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
                    isQuota -> str(R.string.drive_error_drive_full)
                    isAuth -> str(R.string.drive_backup_google_drive_access_was_lost)
                    else -> e.message ?: str(R.string.drive_error_restore_failed)
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

    /**
     * Initiates the full restore flow: authorizes silently or requests consent if not connected,
     * inspects Drive for existing backup slots, and prompts confirmation or reports absence.
     */
    fun requestRestore() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busy = DriveBusyState(str(R.string.drive_busy_checking_for_backup)), lastResultMessage = null, isError = false) }
            val isConn = prefs.isConnected
            if (!isConn) {
                when (val res = auth.authorize()) {
                    is DriveAuthResult.Granted -> {
                        lastToken = res.accessToken
                        val email = res.accountEmail ?: "Connected"
                        prefs.connectedEmail = email
                        _uiState.update { it.copy(connection = DriveConnectionState.Connected(email)) }
                        checkLatestAndPromptRestore()
                    }
                    is DriveAuthResult.NeedsConsent -> {
                        pendingRestoreAfterConsent = true
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
            } else {
                checkLatestAndPromptRestore()
            }
        }
    }

    /**
     * Connects to Google Drive (prompting for consent if needed) and runs an initial
     * backup for users migrating to Google Drive.
     */
    fun startMigrationBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busy = DriveBusyState(str(R.string.drive_busy_backing_up)), lastResultMessage = null, isError = false) }
            val isConn = prefs.isConnected
            if (!isConn) {
                when (val res = auth.authorize()) {
                    is DriveAuthResult.Granted -> {
                        lastToken = res.accessToken
                        val email = res.accountEmail ?: "Connected"
                        prefs.connectedEmail = email
                        _uiState.update { it.copy(connection = DriveConnectionState.Connected(email)) }
                        DriveAutoBackup.onAppOpen(getApplication())
                        backUpNow(allowEmpty = false)
                    }
                    is DriveAuthResult.NeedsConsent -> {
                        pendingBackupAfterConsent = true
                        _uiState.update { it.copy(busy = null, consentRequest = res.pendingIntent) }
                    }
                    is DriveAuthResult.Failed -> {
                        pendingBackupAfterConsent = false
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
            } else {
                backUpNow(allowEmpty = false)
            }
        }
    }

    private suspend fun checkLatestAndPromptRestore() {
        _uiState.update { it.copy(busy = DriveBusyState(str(R.string.drive_busy_checking_for_backup))) }
        try {
            val store = storeFactory(tokenProvider)
            val backups = DriveBackupManager.listBackups(store)
            val info = backups.firstOrNull()
            if (info != null) {
                val countsText = buildCountsText(info)
                val offlineText = "${formatBackupTime(info.exportedAt)}, $countsText"
                _uiState.update {
                    it.copy(
                        latestBackupInfo = info,
                        olderBackupInfo = backups.getOrNull(1),
                        useOlderBackup = false,
                        offlineBackupText = offlineText,
                        busy = null,
                        showRestoreConfirm = true,
                        showNoBackupDialog = false
                    )
                }
            } else if (!uiState.value.offlineBackupText.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        busy = null,
                        showRestoreConfirm = true,
                        showNoBackupDialog = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        busy = null,
                        showRestoreConfirm = false,
                        showNoBackupDialog = true,
                        lastResultMessage = str(R.string.drive_restore_no_backup_found_in_this),
                        isError = false
                    )
                }
            }
        } catch (e: Exception) {
            val isAuth = e is DriveException && e.kind == DriveException.Kind.AUTH
            val isQuota = e is DriveException && e.kind == DriveException.Kind.QUOTA
            val conn = if (isAuth) DriveConnectionState.NeedsReconnect else _uiState.value.connection
            val msg = when {
                isQuota -> str(R.string.drive_error_drive_full)
                isAuth -> str(R.string.drive_backup_google_drive_access_was_lost)
                else -> e.message ?: str(R.string.drive_error_check_backup_failed)
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

    fun dismissRestoreConfirm() {
        _uiState.update { it.copy(showRestoreConfirm = false, useOlderBackup = false) }
    }

    /** Switches the restore dialog between the newest and the older backup. */
    fun setUseOlderBackup(use: Boolean) {
        _uiState.update { it.copy(useOlderBackup = use && it.olderBackupInfo != null) }
    }

    fun dismissNoBackupDialog() {
        _uiState.update { it.copy(showNoBackupDialog = false) }
    }

    fun confirmRestore() {
        val older = _uiState.value.olderBackupInfo
        val slotFileId = if (_uiState.value.useOlderBackup) older?.slotFileId else null
        dismissRestoreConfirm()
        restoreFromDrive(slotFileId)
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
                    // Disconnecting resets the saved choice, which is on by default.
                    autoEnabled = true,
                    olderBackupInfo = null,
                    useOlderBackup = false,
                    showRestoreConfirm = false,
                    showNoBackupDialog = false
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
        fun buildCountsText(info: DriveBackupInfo): String =
            DriveBackupManager.buildCountsText(info)

        fun formatBackupTime(millis: Long): String {
            if (millis <= 0L) return ""
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = millis }
            val timeStr = DateFormats.clock24(millis)
            return when {
                now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "Today $timeStr"
                now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday $timeStr"
                else -> DateFormats.monthDayClock24(millis)
            }
        }
    }
}
