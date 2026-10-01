package dev.wechat.uiprobe.parser

import dev.wechat.uiprobe.util.RectData

enum class MessageSide { SELF, OTHER, CENTER, UNKNOWN }

data class ChatMessageCandidate(
    val order: Int,
    val side: MessageSide,
    val text: String,
    val bounds: RectData,
    val nodeIndex: Int,
)
