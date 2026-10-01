package dev.wechat.uiprobe.export

import dev.wechat.uiprobe.accessibility.NodeSnapshot
import dev.wechat.uiprobe.parser.ChatMessageCandidate

data class DeviceData(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val density: Float,
    val rotation: Int,
)

data class ScanReport(
    val capturedAt: String,
    val timestamp: String,
    val packageName: String,
    val device: DeviceData,
    val nodes: List<NodeSnapshot>,
    val candidates: List<ChatMessageCandidate>,
    val durationMs: Long,
    val warnings: List<String>,
) {
    val textNodeCount: Int get() = nodes.count { !it.text.isNullOrBlank() }
}

// Long-lived state contains counts and filenames only, never node text.
data class ExportSummary(
    val capturedAt: String,
    val nodeCount: Int,
    val textNodeCount: Int,
    val candidateCount: Int,
    val durationMs: Long,
    val warnings: List<String>,
    val txtFileName: String,
    val jsonFileName: String,
)
