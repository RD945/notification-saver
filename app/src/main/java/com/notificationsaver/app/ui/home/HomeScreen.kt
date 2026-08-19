package com.notificationsaver.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notificationsaver.app.ui.DeviceStatus
import com.notificationsaver.app.ui.components.AppleAlert
import com.notificationsaver.app.ui.components.GroupedDivider
import com.notificationsaver.app.ui.components.GroupedList
import com.notificationsaver.app.ui.components.GroupedRow
import com.notificationsaver.app.ui.components.LargeTitle
import com.notificationsaver.app.ui.components.SectionHeader
import com.notificationsaver.app.ui.theme.AppleGreen
import com.notificationsaver.app.ui.theme.AppleLabel
import com.notificationsaver.app.ui.theme.AppleRed
import com.notificationsaver.app.ui.theme.appButtonColors
import com.notificationsaver.app.ui.theme.appOutlinedButtonColors

@Composable
fun HomeScreen(
    onOpenApps: () -> Unit = {},
    onOpenNpoint: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            LargeTitle("Home")

            SectionHeader("Forwarding")
            GroupedList {
                GroupedRow(
                    title = "Forward to Telegram",
                    subtitle = when {
                        state.settings.telegramActive -> "On"
                        !state.settings.telegramConfigured -> "Save bot token and chat ID first"
                        else -> "Off"
                    },
                    trailing = {
                        Switch(
                            checked = state.settings.forwardingEnabled && state.settings.telegramConfigured,
                            onCheckedChange = vm::setTelegramEnabled,
                        )
                    },
                )
                GroupedDivider()
                GroupedRow(
                    title = "Forward to npoint",
                    subtitle = when {
                        state.settings.npointActive -> "On"
                        !state.settings.npointConfigured -> "Set the bin URL first"
                        else -> "Off"
                    },
                    trailing = {
                        Switch(
                            checked = state.settings.npointEnabled && state.settings.npointConfigured,
                            onCheckedChange = vm::setNpointEnabled,
                        )
                    },
                )
                GroupedDivider()
                GroupedRow(
                    title = "npoint bin",
                    subtitle = if (state.settings.npointUrl.isBlank()) {
                        "Tap to enter API URL and keys"
                    } else {
                        "URL, keys, test, clear"
                    },
                    onClick = onOpenNpoint,
                )
                GroupedDivider()
                GroupedRow(
                    title = "OTP only",
                    subtitle = if (state.settings.otpOnly) {
                        "Only one-time codes are forwarded"
                    } else {
                        "Off — every allowlisted notification is sent"
                    },
                    trailing = {
                        Switch(
                            checked = state.settings.otpOnly,
                            onCheckedChange = vm::setOtpOnly,
                        )
                    },
                )
            }

            SectionHeader("Required")
            GroupedList {
                GroupedRow(
                    title = "Notification access",
                    subtitle = if (state.hasAccess) "Allowed" else "Tap to open system settings",
                    onClick = { DeviceStatus.openNotificationAccess(context) },
                    trailing = { StatusDot(state.hasAccess) },
                )
                GroupedDivider()
                GroupedRow(
                    title = "Listener",
                    subtitle = when {
                        state.listenerConnected -> "Connected — tap to reconnect"
                        state.settings.listenerShouldRun -> "Waiting — tap to reconnect"
                        else -> "Idle — tap to reconnect"
                    },
                    onClick = vm::onListenerTap,
                    trailing = { StatusDot(state.listenerConnected) },
                )
                GroupedDivider()
                GroupedRow(
                    title = "Activity",
                    subtitle = state.activitySubtitle,
                )
                GroupedDivider()
                GroupedRow(
                    title = "Target apps",
                    subtitle = if (state.settings.allowlist.isEmpty()) {
                        "None selected — tap to choose"
                    } else {
                        "${state.settings.allowlist.size} selected"
                    },
                    onClick = onOpenApps,
                    trailing = { StatusDot(state.settings.allowlist.isNotEmpty()) },
                )
            }

            SectionHeader("Optional")
            GroupedList {
                GroupedRow(
                    title = "Battery",
                    subtitle = if (state.batteryExempt) {
                        "Unrestricted — phone may still show 0% (normal)"
                    } else {
                        "Tap to allow unrestricted"
                    },
                    onClick = { DeviceStatus.openBatterySettings(context) },
                    trailing = { StatusDot(state.batteryExempt) },
                )
                GroupedDivider()
                GroupedRow(
                    title = "Hourly Telegram ping",
                    subtitle = if (state.settings.hourlyPingEnabled) "On" else "Off",
                    trailing = {
                        Switch(
                            checked = state.settings.hourlyPingEnabled,
                            onCheckedChange = vm::setHourlyPing,
                        )
                    },
                )
            }

            Button(
                onClick = vm::sendTest,
                enabled = !state.busy && state.settings.telegramConfigured,
                colors = appButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                Text(
                    if (state.busy) "Sending…" else "Send test to Telegram",
                    color = AppleLabel,
                )
            }
            OutlinedButton(
                onClick = vm::requestReset,
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                Text("Reset all", color = AppleLabel)
            }
        }

        state.notice?.let { notice ->
            AppleAlert(
                notice = notice,
                onDismiss = vm::consumeNotice,
                onAction = vm::performNoticeAction,
            )
        }
    }
}

@Composable
private fun StatusDot(ok: Boolean) {
    Text(
        if (ok) "On" else "Off",
        color = if (ok) AppleGreen else AppleRed,
        style = MaterialTheme.typography.bodyMedium,
    )
}
