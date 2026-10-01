package dev.wechat.uiprobe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wechat.uiprobe.export.ExportSummary
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@Composable
fun ProbeResultScreen(summary: ExportSummary?, readResult: suspend (String) -> String, onBack: () -> Unit) {
    var report by remember(summary?.jsonFileName) { mutableStateOf<JSONObject?>(null) }
    var error by remember(summary?.jsonFileName) { mutableStateOf<String?>(null) }
    var showNodes by remember { mutableStateOf(false) }
    LaunchedEffect(summary?.jsonFileName) {
        val filename = summary?.jsonFileName ?: return@LaunchedEffect
        try { report = JSONObject(readResult(filename)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "无法读取导出文件，可能已被清除或替换。" }
    }
    val objects = remember(report, showNodes) {
        val array = report?.optJSONArray(if (showNodes) "nodes" else "candidates")
        if (array == null) emptyList() else List(array.length()) { array.getJSONObject(it) }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedButton(onBack) { Text("返回") } }
        item {
            Text("最近一次结果", style = MaterialTheme.typography.headlineSmall)
            Text(summary?.capturedAt ?: "没有扫描结果")
            Text("候选与发送方向只是启发式推测。父子关系可通过 parentIndex 核对。")
            summary?.warnings?.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(!showNodes, { showNodes = false }, label = { Text("聊天候选") })
                FilterChip(showNodes, { showNodes = true }, label = { Text("完整节点") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (summary != null && report == null && error == null) Text("正在读取私有导出文件…")
            if (report != null && objects.isEmpty()) Text("没有候选消息；可切换完整节点查看 text 与 contentDescription。")
        }
        items(objects, key = { if (showNodes) it.getInt("index") else it.getInt("order") }) { entry ->
            ProbeCard {
                SelectionContainer {
                    Text(if (showNodes) entry.toString(2) else buildString {
                        appendLine("${entry.getInt("order")}  ${entry.getString("side")}")
                        appendLine(entry.getString("text"))
                        val bounds = entry.getJSONObject("bounds")
                        appendLine("[${bounds.getInt("left")},${bounds.getInt("top")}][${bounds.getInt("right")},${bounds.getInt("bottom")}]")
                        append("nodeIndex=${entry.getInt("nodeIndex")}")
                    }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
