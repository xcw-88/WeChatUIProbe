package dev.wechat.uiprobe.accessibility

import dev.wechat.uiprobe.export.ExportSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProbeState(
    val connected: Boolean = false,
    val foregroundPackage: String? = null,
    val busy: Boolean = false,
    val status: String = "等待用户主动扫描。",
    val lastScan: ExportSummary? = null,
    val overlayVisible: Boolean = false,
)

object AccessibilityStateRepository {
    private val mutableState = MutableStateFlow(ProbeState())
    val state = mutableState.asStateFlow()
    fun connected(value: Boolean) = mutableState.update {
        it.copy(connected = value, foregroundPackage = null, busy = false, overlayVisible = false)
    }
    fun foreground(packageName: String?) = mutableState.update { it.copy(foregroundPackage = packageName) }
    fun status(message: String, busy: Boolean = false) = mutableState.update { it.copy(status = message, busy = busy) }
    fun latest(summary: ExportSummary?) = mutableState.update { it.copy(lastScan = summary) }
    fun overlay(visible: Boolean) = mutableState.update { it.copy(overlayVisible = visible) }
}
