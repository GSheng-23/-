package com.rollcallhelper.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rollcallhelper.app.automation.AutomationEngine
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class SignInAccessibilityService : AccessibilityService() {

    private val recentEvents = ArrayDeque<Pair<Long, String>>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationEngine.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val text = buildString {
            event.text?.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it) }
        }.trim()
        if (text.isEmpty()) return
        synchronized(recentEvents) {
            recentEvents.addLast(SystemClock.elapsedRealtime() to text)
            while (recentEvents.size > 50) recentEvents.removeFirst()
        }
    }

    fun recentTexts(maxAgeMs: Long = 5_000): List<String> {
        val cutoff = SystemClock.elapsedRealtime() - maxAgeMs
        synchronized(recentEvents) {
            while (recentEvents.isNotEmpty() && recentEvents.first().first < cutoff) {
                recentEvents.removeFirst()
            }
            return recentEvents.map { it.second }
        }
    }

    override fun onInterrupt() {
        // 系统要求实现；无需处理。
    }

    override fun onDestroy() {
        AutomationEngine.detach(this)
        super.onDestroy()
    }

    fun windowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try {
            windows.forEach { window ->
                window.root?.let { root ->
                    if (roots.none { it == root }) roots.add(root)
                }
            }
        } catch (_: Exception) {
            // 服务正在断开时 windows 可能不可用。
        }
        return roots
    }

    suspend fun tap(x: Float, y: Float): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
        val dispatched = dispatchGesture(gesture, callback, null)
        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }
}
