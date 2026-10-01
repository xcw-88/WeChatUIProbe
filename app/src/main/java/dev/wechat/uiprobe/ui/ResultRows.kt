package dev.wechat.uiprobe.ui

import org.json.JSONObject

internal data class ResultRow(val key: String, val text: String)

internal fun resultRows(report: JSONObject, showNodes: Boolean): List<ResultRow> {
    val array = report.optJSONArray(if (showNodes) "nodes" else "candidates") ?: return emptyList()
    return List(array.length()) { position ->
        val entry = array.getJSONObject(position)
        if (showNodes) {
            ResultRow("node:${entry.getInt("index")}", entry.toString(2))
        } else {
            val order = entry.getInt("order")
            ResultRow("candidate:$order", buildString {
                appendLine("$order  ${entry.getString("side")}")
                appendLine(entry.getString("text"))
                val bounds = entry.getJSONObject("bounds")
                appendLine("[${bounds.getInt("left")},${bounds.getInt("top")}][${bounds.getInt("right")},${bounds.getInt("bottom")}]")
                append("nodeIndex=${entry.getInt("nodeIndex")}")
            })
        }
    }
}
