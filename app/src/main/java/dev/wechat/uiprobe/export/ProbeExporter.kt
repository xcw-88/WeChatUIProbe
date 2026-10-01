package dev.wechat.uiprobe.export

import dev.wechat.uiprobe.accessibility.NodeSnapshot
import dev.wechat.uiprobe.parser.ChatMessageCandidate
import dev.wechat.uiprobe.util.RectData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ProbeExporter(private val directory: File) {
    @Synchronized
    fun export(report: ScanReport): ExportSummary {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create private export directory" }
        val base = "wechat_probe_${report.timestamp}"
        val summary = ExportSummary(report.capturedAt, report.nodes.size, report.textNodeCount,
            report.candidates.size, report.durationMs, report.warnings, "$base.txt", "$base.json")
        val txt = File(directory, summary.txtFileName)
        val json = File(directory, summary.jsonFileName)
        val meta = File(directory, "latest.meta")
        val pendingMeta = File(directory, "latest.meta.pending")
        try {
            txt.writeText(renderTxt(report), Charsets.UTF_8)
            json.writeText(renderJson(report).toString(2), Charsets.UTF_8)
            pendingMeta.writeText(summaryJson(summary).toString(), Charsets.UTF_8)
            Files.move(pendingMeta.toPath(), meta.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: Exception) {
            txt.delete()
            json.delete()
            pendingMeta.delete()
            throw error
        }
        // Keep a single user-generated snapshot. Older files are removed after the new pair is committed.
        directory.listFiles()?.filter {
            it.name.startsWith("wechat_probe_") && it.name != txt.name && it.name != json.name
        }?.forEach { check(it.delete()) { "Cannot remove previous export" } }
        return summary
    }

    @Synchronized
    fun latest(): ExportSummary? {
        val meta = File(directory, "latest.meta")
        if (!meta.isFile) {
            // Clean an interrupted first export; do not keep orphaned chat content.
            clear()
            return null
        }
        return try {
            val value = JSONObject(meta.readText(Charsets.UTF_8))
            val summary = ExportSummary(value.getString("capturedAt"), value.getInt("nodeCount"),
                value.getInt("textNodeCount"), value.getInt("candidateCount"), value.getLong("durationMs"),
                value.getJSONArray("warnings").let { array -> List(array.length()) { array.getString(it) } },
                value.getString("txtFileName"), value.getString("jsonFileName"))
            check(file(summary.txtFileName).isFile && file(summary.jsonFileName).isFile)
            directory.listFiles()?.filter {
                it.name != "latest.meta" && it.name != summary.txtFileName && it.name != summary.jsonFileName
            }?.forEach { check(it.delete()) }
            summary
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun file(name: String): File {
        require(name.matches(Regex("wechat_probe_[0-9_]+\\.(txt|json)"))) { "Invalid export filename" }
        return File(directory, name)
    }

    @Synchronized
    fun clear() {
        directory.listFiles()?.forEach { check(it.isFile && it.delete()) { "Cannot clear private exports" } }
    }

    companion object {
        fun renderTxt(report: ScanReport): String = buildString {
            appendLine("WeChat UI Probe v0.1.2 — read-only user-triggered snapshot")
            appendLine("Captured: ${report.capturedAt}")
            appendLine("Device:")
            appendLine("${report.device.manufacturer} ${report.device.model}")
            appendLine("Android=${report.device.androidRelease} SDK=${report.device.sdkInt}")
            appendLine("screen=${report.device.screenWidth}x${report.device.screenHeight}")
            appendLine("density=${report.device.density} rotation=${report.device.rotation}")
            appendLine("Package:\n${report.packageName}")
            appendLine("Node Count:\n${report.nodes.size}")
            appendLine("Text Nodes:\n${report.textNodeCount}")
            appendLine("Scan Duration Ms:\n${report.durationMs}")
            appendLine("Complete: ${report.warnings.isEmpty()}")
            report.warnings.forEach { appendLine("WARNING: $it") }
            report.nodes.forEach { node ->
                appendLine("\n====================\n[NODE ${node.index}]")
                appendLine("depth=${node.depth} parentIndex=${node.parentIndex}")
                appendLine("class=${node.className} package=${node.packageName}")
                appendLine("text=${node.text}")
                appendLine("contentDescription=${node.contentDescription}")
                appendLine("viewId=${node.viewIdResourceName}")
                appendLine("bounds=${node.boundsInScreen}")
                appendLine("clickable=${node.clickable} longClickable=${node.longClickable}")
                appendLine("focusable=${node.focusable} focused=${node.focused} selected=${node.selected}")
                appendLine("enabled=${node.enabled} scrollable=${node.scrollable} editable=${node.editable}")
                appendLine("visibleToUser=${node.visibleToUser} childCount=${node.childCount}")
            }
            appendLine("\n====================\nCHAT CANDIDATES")
            appendLine("Heuristics only: side and candidate text may be inaccurate; screen type is not verified.")
            report.candidates.forEach { candidate ->
                appendLine("\n${candidate.order.toString().padStart(2, '0')} ${candidate.side}")
                appendLine(candidate.text)
                appendLine("${candidate.bounds} nodeIndex=${candidate.nodeIndex}")
            }
        }

        fun renderJson(report: ScanReport): JSONObject = JSONObject().apply {
            put("schemaVersion", 1)
            put("appVersion", "0.1.2")
            put("capturedAt", report.capturedAt)
            put("packageName", report.packageName)
            put("durationMs", report.durationMs)
            put("complete", report.warnings.isEmpty())
            put("warnings", JSONArray(report.warnings))
            put("candidatesAreHeuristic", true)
            put("screenTypeVerified", false)
            put("device", JSONObject().apply {
                put("manufacturer", report.device.manufacturer); put("model", report.device.model)
                put("androidRelease", report.device.androidRelease); put("sdkInt", report.device.sdkInt)
                put("screenWidth", report.device.screenWidth); put("screenHeight", report.device.screenHeight)
                put("density", report.device.density.toDouble()); put("rotation", report.device.rotation)
            })
            put("nodeCount", report.nodes.size)
            put("textNodeCount", report.textNodeCount)
            put("candidateCount", report.candidates.size)
            put("nodes", JSONArray().apply { report.nodes.forEach { put(nodeJson(it)) } })
            put("candidates", JSONArray().apply { report.candidates.forEach { put(candidateJson(it)) } })
        }

        private fun boundsJson(rect: RectData) = JSONObject().apply {
            put("left", rect.left); put("top", rect.top); put("right", rect.right); put("bottom", rect.bottom)
        }

        private fun nodeJson(node: NodeSnapshot) = JSONObject().apply {
            put("index", node.index); put("depth", node.depth); put("parentIndex", node.parentIndex ?: JSONObject.NULL)
            put("className", node.className ?: JSONObject.NULL); put("packageName", node.packageName ?: JSONObject.NULL)
            put("text", node.text ?: JSONObject.NULL); put("contentDescription", node.contentDescription ?: JSONObject.NULL)
            put("viewIdResourceName", node.viewIdResourceName ?: JSONObject.NULL)
            put("clickable", node.clickable); put("longClickable", node.longClickable)
            put("focusable", node.focusable); put("focused", node.focused); put("selected", node.selected)
            put("enabled", node.enabled); put("scrollable", node.scrollable); put("editable", node.editable)
            put("visibleToUser", node.visibleToUser); put("boundsInScreen", boundsJson(node.boundsInScreen))
            put("childCount", node.childCount)
        }

        private fun candidateJson(candidate: ChatMessageCandidate) = JSONObject().apply {
            put("order", candidate.order); put("side", candidate.side.name); put("text", candidate.text)
            put("bounds", boundsJson(candidate.bounds)); put("nodeIndex", candidate.nodeIndex)
        }

        private fun summaryJson(summary: ExportSummary) = JSONObject().apply {
            put("capturedAt", summary.capturedAt); put("nodeCount", summary.nodeCount)
            put("textNodeCount", summary.textNodeCount); put("candidateCount", summary.candidateCount)
            put("durationMs", summary.durationMs); put("warnings", JSONArray(summary.warnings))
            put("txtFileName", summary.txtFileName); put("jsonFileName", summary.jsonFileName)
        }
    }
}
