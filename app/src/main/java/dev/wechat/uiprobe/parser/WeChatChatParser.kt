package dev.wechat.uiprobe.parser

import dev.wechat.uiprobe.accessibility.NodeSnapshot
import dev.wechat.uiprobe.util.RectData

class WeChatChatParser {
    fun classifySide(bounds: RectData, screenWidth: Int): MessageSide {
        if (screenWidth <= 0 || bounds.width == 0) return MessageSide.UNKNOWN
        return when {
            bounds.centerX < screenWidth * 0.45 -> MessageSide.OTHER
            bounds.centerX > screenWidth * 0.55 -> MessageSide.SELF
            else -> MessageSide.CENTER
        }
    }

    fun parse(nodes: List<NodeSnapshot>, screenWidth: Int, packageName: String): List<ChatMessageCandidate> {
        if (packageName != WECHAT_PACKAGE) return emptyList()
        val byIndex = nodes.associateBy { it.index }
        fun ancestors(node: NodeSnapshot): Sequence<NodeSnapshot> = sequence {
            var parent = node.parentIndex
            val visited = mutableSetOf<Int>()
            while (parent != null && visited.add(parent)) {
                val value = byIndex[parent] ?: break
                yield(value)
                parent = value.parentIndex
            }
        }
        val hasVisibleScroller = nodes.any { it.scrollable && it.visibleToUser && it.packageName == WECHAT_PACKAGE }
        val eligible = nodes.filter { node ->
            node.packageName == WECHAT_PACKAGE && !node.text.isNullOrBlank() && node.visibleToUser &&
                node.boundsInScreen.width > 0 && node.boundsInScreen.height > 0 &&
                !isInputOrButton(node) && ancestors(node).none(::isInputOrButton) &&
                (!hasVisibleScroller || ancestors(node).any { it.scrollable } || node.scrollable)
        }
        // Only identical, overlapping direct parent/child pairs are duplicates. Repeated messages remain.
        val duplicateParents = mutableSetOf<Int>()
        val eligibleByIndex = eligible.associateBy { it.index }
        eligible.forEach { child ->
            val parent = eligibleByIndex[child.parentIndex]
            if (parent != null && child.depth == parent.depth + 1 && child.text == parent.text &&
                child.boundsInScreen.overlapRatio(parent.boundsInScreen) >= 0.85) {
                duplicateParents += parent.index
            }
        }
        val sorted = eligible.filterNot { it.index in duplicateParents }
            .sortedWith(compareBy<NodeSnapshot> { it.boundsInScreen.top }.thenBy { it.index })
        // Group against a fixed row anchor: a fuzzy comparator would violate comparator transitivity.
        val visualOrder = mutableListOf<NodeSnapshot>()
        var cursor = 0
        val rowTolerance = (screenWidth * 0.005).toInt().coerceIn(2, 12)
        while (cursor < sorted.size) {
            val anchor = sorted[cursor].boundsInScreen.top
            val row = mutableListOf<NodeSnapshot>()
            while (cursor < sorted.size && sorted[cursor].boundsInScreen.top - anchor <= rowTolerance) {
                row += sorted[cursor++]
            }
            visualOrder += row.sortedBy { it.index }
        }
        return visualOrder.mapIndexed { order, node ->
            ChatMessageCandidate(order + 1, classifySide(node.boundsInScreen, screenWidth),
                requireNotNull(node.text), node.boundsInScreen, node.index)
        }
    }

    private fun isInputOrButton(node: NodeSnapshot): Boolean = node.editable ||
        node.className?.let { it.endsWith("EditText") || it.endsWith("Button") } == true

    companion object { const val WECHAT_PACKAGE = "com.tencent.mm" }
}
