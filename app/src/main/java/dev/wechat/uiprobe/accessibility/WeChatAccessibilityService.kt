package dev.wechat.uiprobe.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.wechat.uiprobe.export.DeviceData
import dev.wechat.uiprobe.export.ProbeStorage
import dev.wechat.uiprobe.export.ScanReport
import dev.wechat.uiprobe.overlay.FloatingProbeService
import dev.wechat.uiprobe.parser.WeChatChatParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class WeChatAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var floatingProbe: FloatingProbeService? = null

    override fun onServiceConnected() {
        instance = this
        AccessibilityStateRepository.connected(true)
        Log.i(TAG, "Accessibility service connected")
        floatingProbe = FloatingProbeService(this, ::scanOnce)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Do not read event text, event.source or any tree here, including for WeChat.
        val packageName = event?.packageName?.toString() ?: return
        AccessibilityStateRepository.foreground(packageName)
        Log.d(TAG, "Window event package=$packageName")
    }

    override fun onInterrupt() {
        AccessibilityStateRepository.status("无障碍服务被系统中断，请重试或重新开启。")
        Log.i(TAG, "Accessibility service interrupted")
    }

    // Called only by an explicit UI click. Never called from onAccessibilityEvent.
    fun scanOnce() {
        if (AccessibilityStateRepository.state.value.busy) return
        AccessibilityStateRepository.status("正在读取用户请求的当前窗口…", busy = true)
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                val root = rootInActiveWindow
                Log.i(TAG, "User scan rootExists=${root != null}")
                if (root == null) {
                    AccessibilityStateRepository.foreground(null)
                    AccessibilityStateRepository.status("无法获取活动窗口 root，请打开微信聊天页后重试。")
                    return@launch
                }
                val packageName: String?
                val tree: TreeScan
                try {
                    packageName = root.packageName?.toString()
                    AccessibilityStateRepository.foreground(packageName)
                    Log.i(TAG, "User scan package=$packageName")
                    if (packageName != WeChatChatParser.WECHAT_PACKAGE) {
                        AccessibilityStateRepository.status("当前活动窗口不是微信。请在微信前台点击悬浮扫描按钮。")
                        return@launch
                    }
                    tree = withContext(Dispatchers.Default) { NodeTreeScanner().scan(root) }
                } finally { releaseNode(root) }
                val device = deviceData()
                val report = withContext(Dispatchers.Default) {
                    val now = ZonedDateTime.now()
                    val candidates = WeChatChatParser().parse(tree.nodes, device.screenWidth,
                        WeChatChatParser.WECHAT_PACKAGE)
                    ScanReport(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")),
                        WeChatChatParser.WECHAT_PACKAGE, device, tree.nodes, candidates,
                        SystemClock.elapsedRealtime() - started, tree.warnings)
                }
                val summary = withContext(Dispatchers.IO) { ProbeStorage.exporter(this@WeChatAccessibilityService).export(report) }
                AccessibilityStateRepository.latest(summary)
                AccessibilityStateRepository.status(if (summary.warnings.isEmpty()) "扫描完成，已生成最近一次 TXT / JSON。"
                    else "已导出部分结果，请查看扫描警告。")
                Log.i(TAG, "Scan nodes=${summary.nodeCount} textNodes=${summary.textNodeCount} " +
                    "candidates=${summary.candidateCount} durationMs=${summary.durationMs}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Do not log exception messages, node objects or stack traces that could include text.
                Log.e(TAG, "Scan failed type=${error.javaClass.simpleName} durationMs=${SystemClock.elapsedRealtime() - started}")
                AccessibilityStateRepository.status("扫描或导出失败，请重试；必要时清除结果。")
            } finally {
                floatingProbe?.refresh()
            }
        }
    }

    fun setFloatingProbeVisible(visible: Boolean) {
        try {
            if (visible) floatingProbe?.show() else floatingProbe?.hide()
        } catch (error: Exception) {
            Log.e(TAG, "Overlay failed type=${error.javaClass.simpleName}")
            AccessibilityStateRepository.status("无法显示悬浮按钮，请确认悬浮窗权限。")
        }
    }

    @Suppress("DEPRECATION")
    private fun deviceData(): DeviceData {
        val manager = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        manager.defaultDisplay.getRealMetrics(metrics)
        return DeviceData(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
            metrics.widthPixels, metrics.heightPixels, metrics.density, manager.defaultDisplay.rotation)
    }

    override fun onDestroy() {
        floatingProbe?.hide()
        floatingProbe = null
        scope.cancel()
        if (instance === this) instance = null
        AccessibilityStateRepository.connected(false)
        Log.i(TAG, "Accessibility service disconnected")
        super.onDestroy()
    }

    companion object {
        const val TAG = "WeChatUIProbe"
        internal var instance: WeChatAccessibilityService? = null
            private set
    }
}
