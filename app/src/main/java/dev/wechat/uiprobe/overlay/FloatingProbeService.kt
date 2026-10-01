package dev.wechat.uiprobe.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import dev.wechat.uiprobe.accessibility.AccessibilityStateRepository
import kotlin.math.abs
import kotlin.math.roundToInt

// A window controller owned by the accessibility service, not a separately running background service.
// No notifications, persistent worker or automatic scan is needed.
class FloatingProbeService(private val context: Context, private val onScan: () -> Unit) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var window: LinearLayout? = null
    private var scanButton: Button? = null

    fun show() {
        if (window != null) return
        check(Settings.canDrawOverlays(context))
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply { setColor(Color.rgb(22, 107, 82)); cornerRadius = dp(20).toFloat() }
        }
        val scan = Button(context).apply {
            text = "扫描"
            contentDescription = "点击读取一次当前微信 UI，按住拖动悬浮按钮"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            minWidth = dp(64)
            minimumHeight = dp(48)
            setOnClickListener { onScan(); refresh() }
        }
        val close = Button(context).apply {
            text = "×"
            contentDescription = "关闭扫描悬浮按钮"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            minWidth = dp(48)
            minimumHeight = dp(48)
            setOnClickListener { hide() }
        }
        panel.addView(scan)
        panel.addView(close)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        var dragged = false
        scan.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    initialX = params.x; initialY = params.y; dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragged = true
                    if (dragged && window != null) {
                        val display = context.resources.displayMetrics
                        params.x = (initialX + dx.roundToInt()).coerceIn(0, (display.widthPixels - panel.width).coerceAtLeast(0))
                        params.y = (initialY + dy.roundToInt()).coerceIn(0, (display.heightPixels - panel.height - dp(32)).coerceAtLeast(0))
                        manager.updateViewLayout(panel, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { dragged = false; true }
                else -> false
            }
        }
        manager.addView(panel, params)
        window = panel
        scanButton = scan
        AccessibilityStateRepository.overlay(true)
        refresh()
    }

    fun refresh() {
        scanButton?.isEnabled = !AccessibilityStateRepository.state.value.busy
        scanButton?.text = if (AccessibilityStateRepository.state.value.busy) "读取中" else "扫描"
    }

    fun hide() {
        window?.let { manager.removeView(it) }
        window = null
        scanButton = null
        AccessibilityStateRepository.overlay(false)
    }
}
