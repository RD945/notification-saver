package com.notificationsaver.app.ui.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notificationsaver.app.data.db.DeliveryLog
import com.notificationsaver.app.data.db.DeliveryStatus
import com.notificationsaver.app.ui.components.GroupedList
import com.notificationsaver.app.ui.components.LargeTitle
import com.notificationsaver.app.ui.theme.AppleGreen
import com.notificationsaver.app.ui.theme.AppleOrange
import com.notificationsaver.app.ui.theme.AppleRed
import java.text.DateFormat
import java.util.Date

@Composable
fun LogsScreen(vm: LogsViewModel = viewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                LargeTitle("Logs", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = vm::clear,
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.padding(end = 8.dp, top = 16.dp),
                ) { Text("Clear") }
            }
            if (logs.isEmpty()) {
                Text(
                    "No notifications captured yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs, key = { it.id }) { log ->
                        LogCard(log, onRetry = { vm.retry(log.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: DeliveryLog, onRetry: () -> Unit) {
    val statusColor = when (log.status) {
        DeliveryStatus.SENT.name -> AppleGreen
        DeliveryStatus.FAILED.name -> AppleRed
        else -> AppleOrange
    }
    GroupedList(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(log.appName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(log.status, color = statusColor, style = MaterialTheme.typography.labelLarge)
            }
            if (log.otp != null) {
                Text("OTP ${log.otp}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
            }
            if (log.title.isNotBlank()) {
                Text(log.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
            }
            if (log.text.isNotBlank()) {
                Text(log.text, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(log.queuedAt)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (!log.error.isNullOrBlank()) {
                Text(log.error, color = AppleRed, style = MaterialTheme.typography.bodySmall)
            }
            if (log.status != DeliveryStatus.SENT.name) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
