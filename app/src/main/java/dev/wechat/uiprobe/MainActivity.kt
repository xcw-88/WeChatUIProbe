package dev.wechat.uiprobe

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dev.wechat.uiprobe.accessibility.AccessibilityStateRepository
import dev.wechat.uiprobe.accessibility.WeChatAccessibilityService
import dev.wechat.uiprobe.export.ProbeStorage
import dev.wechat.uiprobe.ui.ProbeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var accessibilityEnabled by mutableStateOf(false)
    private var overlayAllowed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF80D8B8))
                else lightColorScheme(primary = Color(0xFF166B52), background = Color(0xFFF5F8F6))) {
                ProbeApp(accessibilityEnabled, overlayAllowed,
                    onOpenAccessibility = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onScan = {
                        val service = WeChatAccessibilityService.instance
                        if (service == null) AccessibilityStateRepository.status("请先开启并连接无障碍服务。")
                        else service.scanOnce()
                    },
                    onToggleOverlay = {
                        val service = WeChatAccessibilityService.instance
                        when {
                            service == null -> AccessibilityStateRepository.status("请先开启并连接无障碍服务。")
                            !Settings.canDrawOverlays(this) -> openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")))
                            else -> service.setFloatingProbeVisible(!AccessibilityStateRepository.state.value.overlayVisible)
                        }
                    },
                    onShare = ::share,
                    onClear = {
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { ProbeStorage.exporter(this@MainActivity).clear() }
                                AccessibilityStateRepository.latest(null)
                                AccessibilityStateRepository.status("最近一次结果已清除。")
                            } catch (_: Exception) { AccessibilityStateRepository.status("清除失败，请重试。") }
                        }
                    },
                    readResult = { name -> withContext(Dispatchers.IO) {
                        ProbeStorage.exporter(this@MainActivity).file(name).readText(Charsets.UTF_8)
                    } },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(AccessibilityManager::class.java)
        val expected = ComponentName(this, WeChatAccessibilityService::class.java)
        accessibilityEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == expected }
        overlayAllowed = Settings.canDrawOverlays(this)
        lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) { ProbeStorage.exporter(this@MainActivity).latest() }
                AccessibilityStateRepository.latest(summary)
            } catch (_: Exception) { AccessibilityStateRepository.status("读取最近一次结果失败，请清除后重试。") }
        }
    }

    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: Exception) { AccessibilityStateRepository.status("系统未提供此设置页面，请从手机设置手动打开。") }
    }

    private fun share(json: Boolean) {
        val summary = AccessibilityStateRepository.state.value.lastScan ?: return
        try {
            val file = ProbeStorage.exporter(this).file(if (json) summary.jsonFileName else summary.txtFileName)
            check(file.isFile)
            val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (json) "application/json" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "WeChat UI Probe export", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享最近一次扫描结果"))
        } catch (_: Exception) { AccessibilityStateRepository.status("分享失败，导出文件可能已清除或替换。") }
    }
}
