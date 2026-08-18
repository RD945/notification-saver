package com.notificationsaver.app.ui.apps

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notificationsaver.app.NotificationSaverApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
)

data class AppsUiState(
    val query: String = "",
    val selected: List<InstalledApp> = emptyList(),
    val available: List<InstalledApp> = emptyList(),
    val allowlist: Set<String> = emptySet(),
    val loading: Boolean = true,
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val query = MutableStateFlow("")
    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)

    val state: StateFlow<AppsUiState> = combine(
        query,
        installed,
        app.container.settings.snapshot,
        loading,
    ) { q, apps, settings, isLoading ->
        val needle = q.trim().lowercase()
        val filtered = if (needle.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
            }
        }
        AppsUiState(
            query = q,
            selected = filtered.filter { it.packageName in settings.allowlist },
            available = filtered.filter { it.packageName !in settings.allowlist },
            allowlist = settings.allowlist,
            loading = isLoading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        refresh()
    }

    fun onQuery(value: String) {
        query.value = value
    }

    fun toggle(packageName: String, allowed: Boolean) {
        viewModelScope.launch {
            app.container.settings.setPackageAllowed(packageName, allowed)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            installed.value = withContext(Dispatchers.IO) { loadInstalled() }
            loading.value = false
        }
    }

    private fun loadInstalled(): List<InstalledApp> {
        val pm = app.packageManager
        val seen = LinkedHashMap<String, InstalledApp>()

        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        for (info in infos) {
            if (info.packageName == app.packageName) continue
            seen[info.packageName] = InstalledApp(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
            )
        }

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }
        for (resolve in resolved) {
            val packageName = resolve.activityInfo.packageName
            if (packageName == app.packageName || packageName in seen) continue
            seen[packageName] = InstalledApp(
                packageName = packageName,
                label = resolve.loadLabel(pm).toString(),
            )
        }

        return seen.values.sortedBy { it.label.lowercase() }
    }
}
