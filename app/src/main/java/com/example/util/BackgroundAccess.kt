package com.example.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

enum class PhoneBrand {
    XIAOMI,
    OPPO,
    REALME,
    ONEPLUS,
    VIVO,
    HUAWEI,
    HONOR,
    TRANSSION,
    SAMSUNG,
    OTHER
}

object BackgroundAccess {

    private const val PREFS_NAME = "unitask_settings"
    private const val KEY_SETUP_PROMPTED = "background_setup_prompted"
    private const val KEY_AUTOSTART_DONE = "autostart_step_done"

    private val autostartCandidates: Map<PhoneBrand, List<Pair<String, String>>> = mapOf(
        PhoneBrand.XIAOMI to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        PhoneBrand.OPPO to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        PhoneBrand.REALME to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        PhoneBrand.ONEPLUS to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        PhoneBrand.VIVO to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
        ),
        PhoneBrand.HUAWEI to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
        ),
        PhoneBrand.HONOR to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
        ),
        PhoneBrand.TRANSSION to listOf(
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity"
        ),
        PhoneBrand.SAMSUNG to emptyList(),
        PhoneBrand.OTHER to emptyList()
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val pkg = context.packageName
        val addFlags: (Intent) -> Intent = { intent ->
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            intent
        }

        // (1) Official one-tap dialog
        try {
            val intent = addFlags(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            })
            context.startActivity(intent)
            return
        } catch (_: Exception) {}

        // (2) All-apps battery optimization list
        try {
            val intent = addFlags(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            context.startActivity(intent)
            return
        } catch (_: Exception) {}

        // (3) App info details page fallback
        try {
            val intent = addFlags(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            })
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }

    fun brand(): PhoneBrand {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val b = Build.BRAND.lowercase(Locale.ROOT)
        fun matches(vararg keys: String) = keys.any { k -> m.contains(k) || b.contains(k) }

        return when {
            matches("xiaomi", "redmi", "poco", "blackshark") -> PhoneBrand.XIAOMI
            matches("oppo") -> PhoneBrand.OPPO
            matches("realme") -> PhoneBrand.REALME
            matches("oneplus") -> PhoneBrand.ONEPLUS
            matches("vivo", "iqoo") -> PhoneBrand.VIVO
            matches("huawei") -> PhoneBrand.HUAWEI
            matches("honor") -> PhoneBrand.HONOR
            matches("tecno", "infinix", "itel", "transsion") -> PhoneBrand.TRANSSION
            matches("samsung") -> PhoneBrand.SAMSUNG
            else -> PhoneBrand.OTHER
        }
    }

    fun needsAutostartStep(): Boolean =
        brand() != PhoneBrand.SAMSUNG && brand() != PhoneBrand.OTHER

    fun openAutostartSettings(context: Context): Boolean {
        val candidates = autostartCandidates[brand()].orEmpty()
        for ((pkg, cls) in candidates) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(pkg, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        // Fallback to app info page
        try {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
        return false
    }

    fun brandHint(): String = when (brand()) {
        PhoneBrand.XIAOMI ->
            "Turn UniTask ON in the list. Then open Settings > Apps > UniTask > Battery saver and choose No restrictions."
        PhoneBrand.OPPO, PhoneBrand.REALME, PhoneBrand.ONEPLUS ->
            "Allow UniTask to auto-start. Then open Settings > Battery > UniTask and allow background activity."
        PhoneBrand.VIVO ->
            "Allow UniTask to auto-start. Then open Settings > Battery > Background power consumption > UniTask and choose Allow."
        PhoneBrand.HUAWEI, PhoneBrand.HONOR ->
            "Open Settings > Apps > App launch > UniTask, turn Manage manually ON and allow Auto-launch, Secondary launch and Run in background."
        PhoneBrand.TRANSSION ->
            "Allow UniTask to auto-start. Then set UniTask to No restrictions in Phone Master or Settings > Battery."
        PhoneBrand.SAMSUNG ->
            "Open Settings > Battery > Background usage limits and make sure UniTask is not in Sleeping apps or Deep sleeping apps."
        PhoneBrand.OTHER ->
            "No extra step is needed on this phone."
    }

    fun guideUrl(): String {
        val slug = when (brand()) {
            PhoneBrand.XIAOMI -> "xiaomi"
            PhoneBrand.OPPO -> "oppo"
            PhoneBrand.REALME -> "realme"
            PhoneBrand.ONEPLUS -> "oneplus"
            PhoneBrand.VIVO -> "vivo"
            PhoneBrand.HUAWEI, PhoneBrand.HONOR -> "huawei"
            PhoneBrand.SAMSUNG -> "samsung"
            PhoneBrand.TRANSSION -> "tecno"
            PhoneBrand.OTHER -> "general"
        }
        return "https://dontkillmyapp.com/$slug"
    }

    fun isBackgroundSetupPrompted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_PROMPTED, false)

    fun setBackgroundSetupPrompted(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_PROMPTED, value)
            .apply()
    }

    fun isAutostartStepDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART_DONE, false)

    fun setAutostartStepDone(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTOSTART_DONE, value)
            .apply()
    }
}
