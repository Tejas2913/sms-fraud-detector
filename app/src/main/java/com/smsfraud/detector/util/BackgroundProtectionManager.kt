package com.smsfraud.detector.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

enum class BackgroundProtectionStatus {
    /** Verified programmatically: battery optimization is disabled / background execution active (e.g. stock Android). */
    VERIFIED_CONFIGURED,

    /** Verified programmatically: background restrictions are active (e.g. stock Android). */
    VERIFIED_RESTRICTED,

    /** OEM device (Xiaomi, Huawei, Oppo, Vivo) where Autostart cannot be read programmatically by 3rd party apps. */
    UNVERIFIABLE,

    /** User opened system Settings and returned on an unverifiable OEM device. */
    SETTINGS_OPENED_UNVERIFIABLE
}

/**
 * Utility for managing OEM background restrictions (Xiaomi, Huawei, Oppo, Vivo, Samsung, OnePlus)
 * and providing guided settings navigation for background SMS detection.
 *
 * Guarantees:
 *   1. Diagnostic logging for OEM state & battery optimization.
 *   2. Accurate status detection without false "Action Required" reporting on unverifiable devices.
 *   3. Safe intent execution (never crashes).
 *   4. Clear explanation of Android Force Stop behavior.
 */
object BackgroundProtectionManager {

    private const val TAG = "BackgroundProtection"

    const val FORCE_STOP_WARNING =
        "Note: If SMS Guard is manually 'Force Stopped' in Android Settings, " +
                "Android prevents all background broadcast delivery until the app is opened again."

    const val UNVERIFIABLE_NOTE =
        "Status cannot be verified automatically on this device."

    /** Returns the lowercase device manufacturer/brand name. */
    fun getManufacturer(): String =
        (Build.MANUFACTURER + " " + Build.BRAND).lowercase()

    fun isXiaomi(): Boolean {
        val m = getManufacturer()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") || m.contains("blackshark")
    }

    fun isHuawei(): Boolean {
        val m = getManufacturer()
        return m.contains("huawei") || m.contains("honor")
    }

    fun isOppo(): Boolean {
        val m = getManufacturer()
        return m.contains("oppo") || m.contains("realme")
    }

    fun isVivo(): Boolean {
        val m = getManufacturer()
        return m.contains("vivo") || m.contains("iqoo")
    }

    fun isSamsung(): Boolean =
        getManufacturer().contains("samsung")

    fun isOnePlus(): Boolean =
        getManufacturer().contains("oneplus")

    /**
     * Determines whether background autostart/launch permission can be reliably read programmatically.
     * OEM platforms (Xiaomi/MIUI/HyperOS, Huawei, Oppo, Vivo) manage Autostart in proprietary security apps
     * that do NOT expose public APIs to third-party applications.
     */
    fun canVerifyProgrammatically(): Boolean {
        return !(isXiaomi() || isHuawei() || isOppo() || isVivo())
    }

    /** Returns whether battery optimization is ignored for SMS Guard on standard Android. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Computes the background protection status with full diagnostic logging.
     */
    fun getStatus(context: Context, hasOpenedSettings: Boolean): BackgroundProtectionStatus {
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        val batteryOptIgnored = isIgnoringBatteryOptimizations(context)
        val canVerify = canVerifyProgrammatically()

        val status = when {
            !canVerify -> {
                if (hasOpenedSettings) BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE
                else BackgroundProtectionStatus.UNVERIFIABLE
            }
            batteryOptIgnored -> BackgroundProtectionStatus.VERIFIED_CONFIGURED
            else -> BackgroundProtectionStatus.VERIFIED_RESTRICTED
        }

        Log.d(
            TAG,
            "BackgroundProtection Diagnostics: " +
                    "manufacturer=$manufacturer, " +
                    "brand=$brand, " +
                    "batteryOptIgnored=$batteryOptIgnored, " +
                    "canVerifyAutostart=$canVerify, " +
                    "hasOpenedSettings=$hasOpenedSettings, " +
                    "finalStatus=$status"
        )

        return status
    }

    /**
     * Returns tailored guidance text explaining how to enable reliable background
     * SMS scanning on the current device.
     */
    fun getGuidanceText(context: Context): String {
        return when {
            isXiaomi() ->
                "Allow SMS Guard to run automatically so incoming SMS can be scanned when the app is closed. On Xiaomi/MIUI/HyperOS, enable Autostart in Security settings and set Battery Saver to No restrictions."
            isHuawei() ->
                "Allow SMS Guard to run automatically so incoming SMS can be scanned when the app is closed. On Huawei devices, set App Launch to Manage automatically in Optimizer settings."
            isOppo() ->
                "Allow SMS Guard to run automatically so incoming SMS can be scanned when the app is closed. On Oppo/RealMe devices, enable Allow Auto Launch in Security settings."
            isVivo() ->
                "Allow SMS Guard to run automatically so incoming SMS can be scanned when the app is closed. On Vivo devices, enable Autostart and High background power consumption in iManager."
            isSamsung() ->
                "On Samsung devices, remove SMS Guard from Sleeping apps and set Battery usage to Unrestricted."
            isOnePlus() ->
                "On OnePlus devices, set Battery Optimization to Don't optimize."
            else ->
                "To scan incoming SMS when SMS Guard is closed, allow SMS Guard to run automatically in the background without battery restrictions."
        }
    }

    /**
     * Safely attempts to open the OEM autostart / background protection settings screen.
     * Falls back to standard Battery Optimization or App Info settings if OEM intent fails.
     *
     * @return true if a settings activity was opened successfully, false otherwise.
     */
    fun openBackgroundSettings(context: Context): Boolean {
        val candidates = buildCandidateIntents(context)

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Opened background settings intent: $intent")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Could not launch intent ${intent.component ?: intent.action}: ${e.javaClass.simpleName}")
            }
        }
        return false
    }

    private fun buildCandidateIntents(context: Context): List<Intent> {
        val list = mutableListOf<Intent>()

        // 1. OEM-specific candidates
        when {
            isXiaomi() -> {
                list.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                list.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.Main")))
                list.add(Intent("miui.intent.action.OP_AUTO_START_SETTINGS"))
            }
            isHuawei() -> {
                list.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
            }
            isOppo() -> {
                list.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
            }
            isVivo() -> {
                list.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                list.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
            }
            isSamsung() -> {
                list.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                list.add(Intent().setComponent(ComponentName("com.samsung.android.looper", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
            isOnePlus() -> {
                list.add(Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")))
            }
        }

        // 2. Standard Android battery optimization prompt (API 23+)
        list.add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        })

        // 3. System Battery Optimization List
        list.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

        // 4. Ultimate Fallback: System App Info screen
        list.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })

        return list
    }
}
