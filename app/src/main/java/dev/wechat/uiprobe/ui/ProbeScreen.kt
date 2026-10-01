package dev.wechat.uiprobe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wechat.uiprobe.accessibility.AccessibilityStateRepository
import dev.wechat.uiprobe.accessibility.ProbeState

@Composable
fun ProbeApp(
    accessibilityEnabled: Boolean,
    overlayAllowed: Boolean,
    onOpenAccessibility: () -> Unit,
    onScan: () -> Unit,
    onToggleOverlay: () -> Unit,
    onShare: (Boolean) -> Unit,
    onClear: () -> Unit,
    readResult: suspend (String) -> String,
) {
    val state by AccessibilityStateRepository.state.collectAsStateWithLifecycle()
    var showResult by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        if (showResult) {
            BackHandler { showResult = false }
            ProbeResultScreen(state.lastScan, readResult, onBack = { showResult = false })
        } else {
            ProbeScreen(state, accessibilityEnabled, overlayAllowed, onOpenAccessibility, onScan,
                onToggleOverlay, onViewResult = { showResult = true }, onShare = onShare, onClear = onClear)
        }
    }
}

@Composable
private fun ProbeScreen(
    state: ProbeState,
    accessibilityEnabled: Boolean,
    overlayAllowed: Boolean,
    onOpenAccessibility: () -> Unit,
    onScan: () -> Unit,
    onToggleOverlay: () -> Unit,
    onViewResult: () -> Unit,
    onShare: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("WeChat UI Probe", style = MaterialTheme.typography.headlineMedium)
            Text("v0.1 · 手动扫描 / 只读验证", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        item {
            ProbeCard {
                Text("服务状态", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    state.connected -> "Accessibility 已开启 · 已连接"
                    accessibilityEnabled -> "Accessibility 已开启 · 等待系统连接"
                    else -> "Accessibility 未开启"
                })
                Text("当前前台应用：${when (state.foregroundPackage) {
                    null -> "未知"
                    "com.tencent.mm" -> "微信"
                    else -> "非微信"
                }}")
                state.foregroundPackage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("应用状态来自窗口事件；扫描时会重新检查活动窗口包名。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onOpenAccessibility, Modifier.fillMaxWidth()) { Text("打开无障碍设置") }
            }
        }
        item {
            ProbeCard {
                Text("主动扫描", style = MaterialTheme.typography.titleMedium)
                Text("请先开启悬浮按钮，再手动打开微信聊天页，点击悬浮「扫描」。每次点击只读取一次当前窗口。")
                Button(onScan, Modifier.fillMaxWidth(), enabled = state.connected && !state.busy) {
                    Text(if (state.busy) "正在扫描…" else "获取当前微信 UI")
                }
                Text("本 App 在前台时通常无法读取微信；使用悬浮按钮可保持微信前台。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onToggleOverlay, Modifier.fillMaxWidth(), enabled = state.connected && !state.busy) {
                    Text(when {
                        state.overlayVisible -> "隐藏扫描悬浮按钮"
                        !overlayAllowed -> "开启悬浮窗权限"
                        else -> "显示扫描悬浮按钮"
                    })
                }
                Text("悬浮按钮可按住拖动，点击 × 关闭。授权后返回本页，再点击显示按钮。", style = MaterialTheme.typography.bodySmall)
                Text(state.status, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            ProbeCard {
                Text("最近扫描", style = MaterialTheme.typography.titleMedium)
                val summary = state.lastScan
                if (summary == null) Text("尚无扫描结果") else {
                    Text(summary.capturedAt)
                    Text("节点 ${summary.nodeCount} · 文本节点 ${summary.textNodeCount} · 候选 ${summary.candidateCount}")
                    Text("扫描耗时 ${summary.durationMs} ms")
                    summary.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                OutlinedButton(onViewResult, Modifier.fillMaxWidth(), enabled = summary != null && !state.busy) {
                    Text("查看最近一次结果")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ onShare(false) }, Modifier.weight(1f), enabled = summary != null && !state.busy) { Text("分享 TXT") }
                    OutlinedButton({ onShare(true) }, Modifier.weight(1f), enabled = summary != null && !state.busy) { Text("分享 JSON") }
                }
                OutlinedButton(onClear, Modifier.fillMaxWidth(), enabled = summary != null && !state.busy) { Text("清除最近一次结果") }
            }
        }
        item {
            ProbeCard {
                Text("本机验证说明", style = MaterialTheme.typography.titleMedium)
                Text("不自动读取、不操作微信、不联网。原文仅写入你主动生成的私有导出文件；仅保留最近一次结果。")
                Text("分享由系统分享面板完成；你选择的接收应用将获得该文件。")
                Text("SELF / OTHER / CENTER 仅依据位置和结构推测，不能确认发送者或聊天页面。完整节点树用于人工核对。")
            }
        }
    }
}

@Composable
internal fun ProbeCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
