package dev.wechat.uiprobe.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import dev.wechat.uiprobe.util.RectData

class NodeTreeScanner {
    fun scan(root: AccessibilityNodeInfo): TreeScan {
        val nodes = mutableListOf<NodeSnapshot>()
        val warnings = linkedSetOf<String>()
        val deadline = SystemClock.elapsedRealtime() + 4_000L
        fun visit(node: AccessibilityNodeInfo, depth: Int, parentIndex: Int?) {
            if (nodes.size >= 15_000 || depth > 128 || SystemClock.elapsedRealtime() > deadline) {
                warnings += "扫描达到节点、深度或时间保护上限，导出树不完整。"
                return
            }
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val index = nodes.size
            val childCount = node.childCount
            nodes += NodeSnapshot(index, depth, parentIndex, node.className?.toString(),
                node.packageName?.toString(), node.text?.toString(), node.contentDescription?.toString(),
                node.viewIdResourceName, node.isClickable, node.isLongClickable, node.isFocusable,
                node.isFocused, node.isSelected, node.isEnabled, node.isScrollable, node.isEditable,
                node.isVisibleToUser, RectData(bounds.left, bounds.top, bounds.right, bounds.bottom), childCount)
            for (childIndex in 0 until childCount) {
                if (nodes.size >= 15_000 || SystemClock.elapsedRealtime() > deadline) {
                    warnings += "扫描达到节点或时间保护上限，导出树不完整。"
                    break
                }
                val child = try { node.getChild(childIndex) } catch (_: IllegalStateException) { null }
                if (child == null) {
                    warnings += "部分子节点不可读取；界面可能在扫描时发生变化。"
                } else {
                    try { visit(child, depth + 1, index) } finally { releaseNode(child) }
                }
            }
        }
        visit(root, 0, null)
        return TreeScan(nodes, warnings.toList())
    }
}

@Suppress("DEPRECATION")
internal fun releaseNode(node: AccessibilityNodeInfo) {
    // Pooling was removed in API 33; recycle is still necessary on older devices.
    if (android.os.Build.VERSION.SDK_INT < 33) node.recycle()
}
