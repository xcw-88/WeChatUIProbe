package dev.wechat.uiprobe.export

import dev.wechat.uiprobe.accessibility.NodeSnapshot
import dev.wechat.uiprobe.parser.ChatMessageCandidate
import dev.wechat.uiprobe.parser.MessageSide
import dev.wechat.uiprobe.util.RectData
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProbeExporterTest {
    @get:Rule val folder = TemporaryFolder()
    private fun report(timestamp: String = "20261001_120000_123", warnings: List<String> = emptyList()): ScanReport {
        val bounds = RectData(20, 300, 250, 350)
        val node = NodeSnapshot(0, 0, null, "android.widget.TextView", "com.tencent.mm", "原文\n\"引号\" 😀",
            null, null, false, false, false, false, false, true, false, false, true, bounds, 0)
        return ScanReport("2026-10-01T12:00:00+08:00", timestamp, "com.tencent.mm",
            DeviceData("test", "phone", "15", 35, 1080, 2400, 3f, 0), listOf(node),
            listOf(ChatMessageCandidate(1, MessageSide.OTHER, node.text!!, bounds, 0)), 15L, warnings)
    }
    @Test fun structuredExportPreservesTextNullsAndCoordinates() {
        val source = report()
        val json = JSONObject(ProbeExporter.renderJson(source).toString())
        val node = json.getJSONArray("nodes").getJSONObject(0)
        assertEquals(source.nodes.single().text, node.getString("text"))
        assertTrue(node.isNull("contentDescription"))
        assertTrue(node.isNull("parentIndex"))
        assertEquals(300, node.getJSONObject("boundsInScreen").getInt("top"))
        assertEquals(19, node.length())
        assertTrue(json.getBoolean("candidatesAreHeuristic"))
    }
    @Test fun txtIncludesCompleteNodeFlagsAndCandidates() {
        val txt = ProbeExporter.renderTxt(report())
        assertTrue(txt.contains("screen=1080x2400"))
        assertTrue(txt.contains("contentDescription=null"))
        assertTrue(txt.contains("longClickable=false"))
        assertTrue(txt.contains("01 OTHER"))
    }
    @Test fun onlyLatestPairIsRetainedAndClearRemovesContent() {
        val exporter = ProbeExporter(folder.newFolder())
        val first = exporter.export(report())
        val second = exporter.export(report("20261001_120100_123"))
        assertFalse(exporter.file(first.txtFileName).exists())
        assertFalse(exporter.file(first.jsonFileName).exists())
        assertEquals(second, exporter.latest())
        exporter.clear()
        assertNull(exporter.latest())
        assertFalse(exporter.file(second.jsonFileName).exists())
    }
    @Test fun partialScanIsMarkedIncomplete() {
        val json = ProbeExporter.renderJson(report(warnings = listOf("部分节点不可读取")))
        assertFalse(json.getBoolean("complete"))
        assertEquals(1, json.getJSONArray("warnings").length())
    }
    @Test fun rejectsPathTraversal() {
        val exporter = ProbeExporter(folder.newFolder())
        assertThrows(IllegalArgumentException::class.java) { exporter.file("../secret.txt") }
    }
    @Test fun startupRemovesInterruptedOrOrphanedExports() {
        val directory = folder.newFolder()
        val exporter = ProbeExporter(directory)
        File(directory, "wechat_probe_20261001_1.txt").writeText("临时数据")
        assertNull(exporter.latest())
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
