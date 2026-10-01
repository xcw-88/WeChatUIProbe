package dev.wechat.uiprobe.parser

import dev.wechat.uiprobe.accessibility.NodeSnapshot
import dev.wechat.uiprobe.util.RectData
import org.junit.Assert.*
import org.junit.Test

class WeChatChatParserTest {
    private val parser = WeChatChatParser()
    private fun node(index: Int, text: String? = "消息", bounds: RectData = RectData(50, 400, 350, 470),
        depth: Int = 1, parent: Int? = null, description: String? = null,
        pkg: String = "com.tencent.mm", editable: Boolean = false, visible: Boolean = true,
        scrollable: Boolean = false, className: String = "android.widget.TextView") =
        NodeSnapshot(index, depth, parent, className, pkg, text, description, null,
            false, false, false, false, false, true, scrollable, editable, visible, bounds, 0)
    private fun parse(vararg nodes: NodeSnapshot) = parser.parse(nodes.toList(), 1000, "com.tencent.mm")

    @Test fun leftRightCenterAndUnknown() {
        assertEquals(MessageSide.OTHER, parser.classifySide(RectData(50, 0, 350, 30), 1000))
        assertEquals(MessageSide.SELF, parser.classifySide(RectData(650, 0, 950, 30), 1000))
        assertEquals(MessageSide.CENTER, parser.classifySide(RectData(400, 0, 600, 30), 1000))
        assertEquals(MessageSide.UNKNOWN, parser.classifySide(RectData(10, 0, 20, 30), 0))
    }
    @Test fun bufferBoundariesStayCenter() {
        assertEquals(MessageSide.CENTER, parser.classifySide(RectData(400, 0, 500, 30), 1000))
        assertEquals(MessageSide.CENTER, parser.classifySide(RectData(500, 0, 600, 30), 1000))
    }
    @Test fun sortsByYAndNumbersFromOne() {
        val result = parse(node(1, "下", RectData(50, 900, 350, 970)), node(2, "上"))
        assertEquals(listOf("上", "下"), result.map { it.text })
        assertEquals(listOf(1, 2), result.map { it.order })
    }
    @Test fun nearbyTopsUseTreeOrder() {
        val result = parse(node(8, "A", RectData(50, 400, 350, 470)), node(2, "B", RectData(50, 404, 350, 474)))
        assertEquals(listOf(2, 8), result.map { it.nodeIndex })
    }
    @Test fun rowGroupingIsDeterministicWithoutChaining() {
        val result = parse(node(9, "A", RectData(50, 400, 350, 430)), node(8, "B", RectData(50, 404, 350, 434)),
            node(1, "C", RectData(50, 408, 350, 438)))
        assertEquals(listOf(8, 9, 1), result.map { it.nodeIndex })
    }
    @Test fun deduplicatesParentAndKeepsSpecificChild() {
        assertEquals(listOf(2), parse(node(1), node(2, depth = 2, parent = 1)).map { it.nodeIndex })
    }
    @Test fun distinctRepeatedMessagesAreKept() {
        assertEquals(2, parse(node(1), node(2, bounds = RectData(50, 700, 350, 770))).size)
    }
    @Test fun identicalSiblingBoundsAreNotDeduplicated() {
        assertEquals(2, parse(node(1, parent = 0), node(2, parent = 0)).size)
    }
    @Test fun filtersEmptyTextAndDescriptions() {
        assertTrue(parse(node(1, null, description = "发送消息"), node(2, ""), node(3, " \n ")).isEmpty())
    }
    @Test fun preservesOriginalTextAndNeverUsesDescriptionAsText() {
        val result = parse(node(1, " 原文\n第二行 ", description = "按钮"))
        assertEquals(" 原文\n第二行 ", result.single().text)
    }
    @Test fun rejectsOtherPackagesAndForeignChildren() {
        assertTrue(parser.parse(listOf(node(1)), 1000, "other.app").isEmpty())
        assertTrue(parse(node(1, pkg = "other.app")).isEmpty())
    }
    @Test fun filtersEditableHiddenAndEmptyBounds() {
        assertTrue(parse(node(1, editable = true), node(2, visible = false),
            node(3, bounds = RectData(0, 0, 0, 0))).isEmpty())
    }
    @Test fun scrollerStructureExcludesToolbarAndInputDescendants() {
        val result = parse(node(0, null, scrollable = true), node(1, "标题"), node(2, "聊天", parent = 0),
            node(3, null, editable = true, parent = 0), node(4, "草稿", depth = 2, parent = 3))
        assertEquals(listOf("聊天"), result.map { it.text })
    }
    @Test fun malformedAncestorCycleTerminates() {
        assertEquals(2, parse(node(1, parent = 2), node(2, parent = 1)).size)
    }
    @Test fun invalidBoundsOverlapIsZero() {
        assertEquals(0.0, RectData(1, 1, 0, 0).overlapRatio(RectData(1, 1, 0, 0)), 0.0)
    }
}
