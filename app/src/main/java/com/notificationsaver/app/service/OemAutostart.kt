package com.notificationsaver.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

object OemAutostart {
    private const val SAMSUNG_NEVER_SLEEP = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"

    fun isSamsung(): Boolean = Build.BRAND.lowercase(Locale.ROOT) == "samsung"

    fun isAvailable(context: Context): Boolean =
        isSamsung() || intentsForBrand(context).any { isResolvable(context, it) }

    fun rowSubtitle(): String = if (isSamsung()) {
        "Open App info → Battery. Samsung + search hides new apps"
    } else {
        "Allow this app to run after reboot on this phone"
    }

    fun open(context: Context): Boolean {
        if (isSamsung()) {
            return openAppInfo(context)
        }
        for (intent in intentsForBrand(context)) {
            val samsungAction = intent.action == SAMSUNG_NEVER_SLEEP
            if (!samsungAction && !isResolvable(context, intent)) continue
            val launched = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return openAppInfo(context)
    }

    fun openAppInfo(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrDefault(false)

    private fun isResolvable(context: Context, intent: Intent): Boolean {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return list.isNotEmpty()
    }

    private fun component(pkg: String, cls: String) = Intent().apply {
        this.component = ComponentName(pkg, cls)
    }

    private fun samsungNeverSleeping(deviceCare: String, appPackage: String) =
        Intent(SAMSUNG_NEVER_SLEEP).apply {
            setPackage(deviceCare)
            putExtra("activity_type", 2)
            putExtra("package", appPackage)
            putExtra("packageName", appPackage)
            putExtra("extra_package_name", appPackage)
        }

    private fun intentsForBrand(context: Context): List<Intent> {
        val appPackage = context.packageName
        return when (Build.BRAND.lowercase(Locale.ROOT)) {
            "xiaomi", "redmi", "poco" -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            "samsung" -> listOf(
                samsungNeverSleeping("com.samsung.android.lool", appPackage),
                samsungNeverSleeping("com.samsung.android.sm_cn", appPackage),
            )
            "oppo", "realme" -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            "vivo", "iqoo" -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            "huawei", "honor" -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            "oneplus" -> listOf(
                component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )
            "asus" -> listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                component("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
            )
            else -> emptyList()
        }
    }
}
