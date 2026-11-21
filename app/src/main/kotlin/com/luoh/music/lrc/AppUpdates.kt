package com.luoh.music.lrc

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Request and dialog state survive rotation; cooldowns survive app restarts. */
class AppUpdateModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    val currentVersion: String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
    val stable = UpdateChecker.stableVersion(currentVersion) != null
    var checking by mutableStateOf(false)
        private set
    var release by mutableStateOf<UpdateChecker.Release?>(null)
        private set
    private var request: Deferred<Result<UpdateChecker.Release>>? = null

    fun checkAutomatically() {
        if (!stable || release != null || request?.isActive == true) return
        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong("last_check_at", 0L)
        val due = preferences.getString("checked_version", null) != currentVersion ||
            lastCheck == 0L || now < lastCheck || now - lastCheck >= DAY_MS
        if (!due) {
            offerCached()
            return
        }
        val pending = requestLatest()
        viewModelScope.launch {
            pending.await()
            offerCached()
        }
    }

    suspend fun checkManually(): Result<UpdateChecker.Release> {
        val pending = requestLatest()
        // Leaving the settings page must not discard a completed update check.
        return viewModelScope.async {
            pending.await().onSuccess { offer(it, force = true) }
        }.await()
    }

    private fun requestLatest(): Deferred<Result<UpdateChecker.Release>> {
        request?.takeIf { it.isActive }?.let { return it }
        checking = true
        // Record attempts too, so offline launches do not repeatedly request GitHub.
        preferences.edit()
            .putLong("last_check_at", System.currentTimeMillis())
            .putString("checked_version", currentVersion)
            .apply()
        return viewModelScope.async {
            try {
                UpdateChecker.fetchLatest(currentVersion).onSuccess { latest ->
                    val cached = JSONObject()
                        .put("tag", latest.tag)
                        .put("version", latest.version)
                        .put("page", latest.pageUrl)
                        .put("summary", latest.summary)
                    preferences.edit().putString("latest_release", cached.toString()).apply()
                }
            } finally {
                checking = false
            }
        }.also { request = it }
    }

    private fun offerCached() {
        val latest = runCatching {
            val cached = JSONObject(preferences.getString("latest_release", null) ?: return)
            UpdateChecker.Release(
                cached.getString("tag"), cached.getString("version"),
                cached.getString("page"), cached.getString("summary")
            )
        }.getOrNull() ?: return
        offer(latest)
    }

    private fun offer(latest: UpdateChecker.Release, force: Boolean = false) {
        if (!UpdateChecker.isNewer(latest.version, currentVersion)) return
        val remaining = preferences.getLong("remind_at", 0L) - System.currentTimeMillis()
        // A clock correction must not suppress reminders indefinitely.
        if (!force && remaining in 1..DAY_MS) return
        release = latest
    }

    fun markShown() {
        preferences.edit().putLong("remind_at", System.currentTimeMillis() + DAY_MS).apply()
    }

    fun later() {
        markShown()
        release = null
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

internal val LocalAppUpdates = staticCompositionLocalOf<AppUpdateModel?> { null }

@Composable
internal fun rememberAppUpdateModel(): AppUpdateModel {
    val activity = LocalContext.current as ComponentActivity
    return remember(activity) { ViewModelProvider(activity)[AppUpdateModel::class.java] }
}

@Composable
internal fun AppUpdateHost(content: @Composable () -> Unit) {
    val model = rememberAppUpdateModel()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model, resumed) {
        if (resumed) model.checkAutomatically()
    }
    CompositionLocalProvider(LocalAppUpdates provides model) {
        content()
        if (resumed) AppUpdateDialog(model)
    }
}

@Composable
internal fun AppUpdateDialog(model: AppUpdateModel) {
    val update = model.release ?: return
    val context = LocalContext.current
    LaunchedEffect(model, update.version) { model.markShown() }
    AlertDialog(
        onDismissRequest = model::later,
        title = { Text("发现新版本 ${update.version}") },
        text = { Text(update.summary) },
        confirmButton = {
            TextButton(onClick = {
                model.later()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl))) }
                    .onFailure { Toast.makeText(context, "无法打开下载页面", Toast.LENGTH_SHORT).show() }
            }) { Text("前往下载") }
        },
        dismissButton = { TextButton(onClick = model::later) { Text("稍后") } },
    )
}
