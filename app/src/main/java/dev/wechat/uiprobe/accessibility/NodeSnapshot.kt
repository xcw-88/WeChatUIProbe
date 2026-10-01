package dev.wechat.uiprobe.accessibility

import dev.wechat.uiprobe.util.RectData

// Value objects only: no AccessibilityNodeInfo survives the scan.
data class NodeSnapshot(
    val index: Int,
    val depth: Int,
    val parentIndex: Int?,
    val className: String?,
    val packageName: String?,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val clickable: Boolean,
    val longClickable: Boolean,
    val focusable: Boolean,
    val focused: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val visibleToUser: Boolean,
    val boundsInScreen: RectData,
    val childCount: Int,
)

data class TreeScan(val nodes: List<NodeSnapshot>, val warnings: List<String>)
