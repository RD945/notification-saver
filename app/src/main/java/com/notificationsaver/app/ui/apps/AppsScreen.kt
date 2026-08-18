package com.notificationsaver.app.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notificationsaver.app.ui.components.LargeTitle
import com.notificationsaver.app.ui.components.SectionHeader
import com.notificationsaver.app.ui.theme.AppleLabel
import com.notificationsaver.app.ui.theme.appTextFieldColors

@Composable
fun AppsScreen(vm: AppsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LargeTitle("Apps")
            Text(
                "${state.allowlist.size} selected. Only checked apps are sent to Telegram.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search", color = AppleLabel) },
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
            )
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.selected.isNotEmpty()) {
                    item { SectionHeader("Selected") }
                    items(state.selected, key = { "sel-${it.packageName}" }) { app ->
                        AppRow(app, checked = true, onChecked = { vm.toggle(app.packageName, it) })
                    }
                }
                item { SectionHeader("All apps") }
                items(state.available, key = { it.packageName }) { app ->
                    AppRow(app, checked = false, onChecked = { vm.toggle(app.packageName, it) })
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(96, 96)
                .asImageBitmap()
        }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}
