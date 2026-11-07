package com.luoh.music.lrc

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luoh.music.lrc.ui.agora.SettingsItem

private const val ANALYTICS_SUMMARY = "帮助改进歌词匹配、翻译与稳定性。开启后向维护者的 Umami 服务（umi.luoh.org）发送随机安装标识、应用与 Android 版本、设备品牌型号、播放器类别、使用时长、功能点击与翻译模式、权限状态、首次使用步骤及功能结果。Java/Kotlin 崩溃只记录异常类型和部分代码位置，下次启动时补传，不包含异常消息原文。"
private const val ANALYTICS_BOUNDARY = "使用情况统计默认开启，不发送歌名、歌词、听歌历史、通知正文或 API 密钥。可随时在「设置 → 关于应用」关闭；关闭会清除待发送记录并重置本机统计标识。"

@Composable
internal fun UsageAnalyticsSettings() {
    val context = LocalContext.current
    val preferences by UsageAnalytics.preferences.collectAsStateWithLifecycle()
    SettingsItem(
        headlineContent = { Text("使用情况统计") },
        supportingContent = { Text(if (preferences.enabled) "已开启 · 帮助改进歌词匹配与翻译" else "已关闭 · 可随时重新开启") },
        trailingContent = { Switch(checked = preferences.enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(preferences.enabled, role = Role.Switch) { value ->
            if (!UsageAnalytics.setEnabled(value)) Toast.makeText(context, "无法保存设置，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
internal fun UsageAnalyticsExplanation() {
    Text("$ANALYTICS_SUMMARY\n\n$ANALYTICS_BOUNDARY", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
}
