package com.notificationsaver.app.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.notificationsaver.app.service.ForwardNotificationListener
import com.notificationsaver.app.service.OemAutostart

object DeviceStatus {
    fun hasNotificationAccess(context: Context): Boolean {
        val component = ForwardNotificationListener.component(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component)
        } else {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openNotificationAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    @SuppressLint("BatteryLife")
    fun openBatterySettings(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            openAppBatteryOrInfo(context)
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { openAppBatteryOrInfo(context) }
    }

    fun openAppInfo(context: Context) {
        OemAutostart.openAppInfo(context)
    }

    private fun openAppBatteryOrInfo(context: Context) {
        val battery = Intent("android.settings.APP_BATTERY_SETTINGS").apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            if (battery.resolveActivity(context.packageManager) != null) {
                context.startActivity(battery)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!launched) {
            openAppInfo(context)
        }
    }
}
