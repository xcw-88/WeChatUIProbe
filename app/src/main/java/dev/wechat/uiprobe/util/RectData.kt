package dev.wechat.uiprobe.util

import kotlin.math.max
import kotlin.math.min

data class RectData(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Double get() = (left.toDouble() + right) / 2.0
    fun overlapRatio(other: RectData): Double {
        val intersection = max(0, min(right, other.right) - max(left, other.left)).toDouble() *
            max(0, min(bottom, other.bottom) - max(top, other.top))
        val union = width.toDouble() * height + other.width.toDouble() * other.height - intersection
        return if (union > 0) intersection / union else 0.0
    }
    override fun toString(): String = "[$left,$top][$right,$bottom]"
}
