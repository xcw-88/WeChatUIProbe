package dev.wechat.uiprobe.accessibility

import dev.wechat.uiprobe.util.RectData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeReadWarningsTest {
    private fun node(className: String? = null, visible: Boolean = false, text: String? = null,
        description: String? = null) = NodeSnapshot(0, 0, null, className, "com.tencent.mm", text,
        description, null, false, false, false, false, false, true, false, false, visible,
        RectData(0, 0, 1000, 2000), 0)

    @Test fun invisiblePlaceholderIsReportedAsUnreadableNotEmptyChat() {
        val warnings = treeReadWarnings(listOf(node()))
        assertTrue(warnings.single().contains("未暴露微信 UI 树"))
        assertTrue(warnings.single().contains("不代表当前聊天没有消息"))
    }

    @Test fun descriptionOnlyNodesDoNotMeanMessagesAreReadable() {
        val warnings = treeReadWarnings(listOf(node("android.widget.ImageView", true, description = "test")))
        assertTrue(warnings.single().contains("没有 text 文本"))
        assertFalse(warnings.single().contains("test"))
    }

    @Test fun ordinaryVisibleTextHasNoReadabilityWarning() {
        assertTrue(treeReadWarnings(listOf(node("android.widget.TextView", true, "test"))).isEmpty())
    }
}
