package dev.wechat.uiprobe.accessibility

internal fun treeReadWarnings(nodes: List<NodeSnapshot>): List<String> {
    if (nodes.size == 1 && nodes.single().let { it.childCount == 0 && it.className == null && !it.visibleToUser }) {
        return listOf("系统只返回不可见的空窗口节点，未暴露微信 UI 树。可能受应用或系统的无障碍数据保护限制；这不代表当前聊天没有消息。")
    }
    if (nodes.none { !it.text.isNullOrBlank() }) {
        return listOf("当前可读取节点没有 text 文本，不能确认聊天消息可读；contentDescription 不会当作普通消息。")
    }
    return emptyList()
}
