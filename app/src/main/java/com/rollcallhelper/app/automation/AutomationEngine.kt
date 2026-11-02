package com.rollcallhelper.app.automation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import com.rollcallhelper.app.accessibility.SignInAccessibilityService
import com.rollcallhelper.app.notify.RunNotifier
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RunState {
    data object Idle : RunState
    data class Running(val step: String, val detail: String = "") : RunState
    data class Done(val message: String) : RunState
    data class Unknown(val message: String) : RunState
    data class Failed(val message: String) : RunState
}

object AutomationEngine {
    private const val TAG = "RollCall"
    const val TARGET_PACKAGE = "cn.edu.fzu.fdxypa"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var service: SignInAccessibilityService? = null
    private var appContext: Context? = null
    private var job: Job? = null

    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _serviceReady = MutableStateFlow(false)
    val serviceReady: StateFlow<Boolean> = _serviceReady.asStateFlow()

    private val _closeRequest = MutableStateFlow(false)
    val closeRequest: StateFlow<Boolean> = _closeRequest.asStateFlow()

    val isServiceReady: Boolean get() = service != null
    val isRunning: Boolean get() = job?.isActive == true

    fun attach(service: SignInAccessibilityService) {
        this.service = service
        _serviceReady.value = true
        log("无障碍服务已连接")
    }

    fun detach(service: SignInAccessibilityService) {
        if (this.service === service) {
            this.service = null
            _serviceReady.value = false
            log("无障碍服务已断开")
            if (job?.isActive == true) {
                job?.cancel()
                _state.value = RunState.Failed("无障碍服务中途断开，本次签到未完成")
                notifyResult("签到中断", "无障碍服务中途断开；请重新开启无障碍后再试")
            }
        }
    }

    fun startDryRun(context: Context) = start(context, dryRun = true)

    fun startRealRun(context: Context) = start(context, dryRun = false)

    private fun start(context: Context, dryRun: Boolean) {
        if (isRunning) return
        val accessibility = service
        if (accessibility == null) {
            _state.value = RunState.Failed("无障碍服务未连接，请先开启")
            return
        }
        appContext = context.applicationContext
        job = scope.launch {
            _logs.value = emptyList()
            _closeRequest.value = false
            _state.value = RunState.Running(
                "准备",
                if (dryRun) "演练：启动智汇福大" else "真实签到：启动智汇福大"
            )
            try {
                val result = runFlow(accessibility, dryRun)
                if (result.unknown) {
                    _state.value = RunState.Unknown(result.message)
                    log("结果不确定：${result.message}")
                    notifyResult("结果不确定", result.message)
                } else {
                    _state.value = RunState.Done(result.message)
                    log((if (dryRun) "演练完成：" else "签到完成：") + result.message)
                    notifyResult(if (dryRun) "演练完成" else "签到完成", result.message)
                    if (!dryRun && result.closeAfter) {
                        // 让用户看到成功页 1 秒，再回桌面并结束助手。
                        delay(1_000)
                        closeTargetApp(accessibility)
                        _closeRequest.value = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "run failed", e)
                val message = e.message ?: "未知错误"
                _state.value = RunState.Failed(message)
                log("失败：$message")
                notifyResult(if (dryRun) "演练失败" else "签到失败", message)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = RunState.Idle
        log("已取消")
    }

    fun resetCloseRequest() {
        _closeRequest.value = false
    }

    /**
     * 新一次点图标启动时调用：上一次已经结束（成功/失败/不确定）时，把状态恢复为 Idle，
     * 让本次启动可以重新自动签到；正在跑的任务不受影响。
     */
    fun prepareNewLaunch() {
        if (!isRunning) {
            _state.value = RunState.Idle
            _closeRequest.value = false
        }
    }

    private data class FlowResult(
        val message: String,
        val unknown: Boolean = false,
        val closeAfter: Boolean = false
    )

    private suspend fun runFlow(
        accessibility: SignInAccessibilityService,
        dryRun: Boolean
    ): FlowResult {
        log("启动智汇福大")
        launchTarget(accessibility)
        waitFor(20_000, 300) {
            if (accessibility.rootInActiveWindow?.packageName?.toString() == TARGET_PACKAGE) true else null
        } ?: fail("智汇福大没有进入前台")

        log("已在前台，查找首页入口或签到页")
        val page = waitFor(25_000, 500) {
            when {
                findSignInPage(accessibility) != null -> Page.SIGN_IN
                findHomeEntry(accessibility) != null -> Page.HOME
                else -> null
            }
        }
        if (page == null) {
            log("当前可见文本：${debugVisibleText(accessibility)}")
            fail("既没找到首页入口，也没找到签到页")
        }

        if (page == Page.HOME) {
            val entry = findHomeEntry(accessibility) ?: fail("首页入口消失")
            log("点击首页入口：${entry.textOrDesc()}")
            clickNode(accessibility, entry) ?: fail("首页入口点击失败")
            val entered = waitFor(25_000, 500) {
                if (findSignInPage(accessibility) != null) true else null
            }
            if (entered == null) {
                log("当前可见文本：${debugVisibleText(accessibility)}")
                fail("点击入口后没有进入签到页")
            }
        } else {
            log("已经在签到页")
        }

        val marker = findSignInPage(accessibility) ?: fail("签到页标志消失")
        log("签到页已就绪：${marker.textOrDesc()}")

        val button = waitFor(25_000, 500) { detectSignInButton(accessibility) }
            ?: fail("25 秒内没读到签到按钮")
        log("按钮状态：${button.describe()}")

        if (button.alreadySigned) {
            return FlowResult("今日已签到，无需重复", closeAfter = true)
        }

        if (dryRun) {
            val message = if (button.ready) {
                "已进入签到页，按钮可签到（${button.text}）；演练模式未点击"
            } else {
                "已进入签到页，当前按钮不可签到（${button.text}）；演练模式未点击"
            }
            return FlowResult(message)
        }

        if (!button.ready) {
            fail("当前按钮不可签到（${button.text}），未点击")
        }
        if (!isInSignInWindow()) {
            fail("当前不在签到时段（21:00–23:59），未点击")
        }

        log("点击签到按钮：${button.text}")
        val clicked = clickNode(accessibility, button.node) ?: fail("签到按钮点击失败")
        if (!clicked) fail("签到按钮手势未被系统接受")

        log("已点击签到，等待结果")
        val success = waitFor(15_000, 300) { findSuccess(accessibility) }
        return if (success != null) {
            FlowResult("签到成功：$success", closeAfter = true)
        } else {
            FlowResult(
                "已点击，但 15 秒内未检测到“签到成功/已签到”，请打开智汇福大人工确认",
                unknown = true
            )
        }
    }

    private suspend fun closeTargetApp(accessibility: SignInAccessibilityService) {
        runCatching { accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        delay(300)
        val context = appContext ?: return
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        runCatching { manager?.killBackgroundProcesses(TARGET_PACKAGE) }
        log("已返回桌面，并尝试关闭智汇福大")
    }

    private fun isInSignInWindow(): Boolean {
        val now = LocalTime.now()
        return !now.isBefore(LocalTime.of(21, 0)) && now.isBefore(LocalTime.of(23, 55))
    }

    private fun findSuccess(accessibility: SignInAccessibilityService): String? {
        val texts = accessibility.windowRoots()
            .flatMap { root -> root.walk().map { it.textOrDesc() } } +
            accessibility.recentTexts()
        return texts.firstOrNull { it.contains("签到成功") }
            ?: texts.firstOrNull { it.contains("已签到") }
    }

    private fun launchTarget(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
            ?: throw IllegalStateException("未安装智汇福大")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }

    private fun findHomeEntry(accessibility: SignInAccessibilityService): AccessibilityNodeInfo? {
        for (root in accessibility.windowRoots()) {
            val hit = root.walk()
                .firstOrNull {
                    it.isVisibleToUser &&
                        it.bounds().width() > 0 &&
                        it.textOrDesc().contains("晚点名签到")
                }
            if (hit != null) return hit
        }
        return null
    }

    private fun findSignInPage(accessibility: SignInAccessibilityService): AccessibilityNodeInfo? {
        for (root in accessibility.windowRoots()) {
            val nodes = root.walk().filter { it.isVisibleToUser }
            val campus = nodes.firstOrNull { it.textOrDesc().contains("可签到校区") }
            val timeRange = nodes.firstOrNull { it.textOrDesc().contains("签到时段") }
            if (campus != null || timeRange != null) return campus ?: timeRange
        }
        return null
    }

    private data class ButtonState(
        val text: String,
        val ready: Boolean,
        val node: AccessibilityNodeInfo,
        val alreadySigned: Boolean = false
    ) {
        fun describe(): String = when {
            alreadySigned -> "已签到"
            ready -> "可签到（$text）"
            else -> "不可签到（$text）"
        }
    }

    private fun detectSignInButton(accessibility: SignInAccessibilityService): ButtonState? {
        val readyTexts = setOf("签到", "点击签到", "立即签到")
        val disabledTexts = setOf("无法签到", "未到签到时间", "定位中", "正在定位", "定位失败")
        for (root in accessibility.windowRoots()) {
            val visibleNodes = root.walk().filter { it.isVisibleToUser }
            // 页面已经显示“签到成功/已签到”时，不再寻找“签到”按钮，避免重复点击。
            val successNode = visibleNodes.firstOrNull {
                val text = it.textOrDesc()
                text.contains("签到成功") || text.contains("已签到")
            }
            if (successNode != null) {
                return ButtonState("已签到", false, successNode, alreadySigned = true)
            }
            val candidates = visibleNodes.mapNotNull { node ->
                val text = exactText(node)
                if (text in readyTexts || text in disabledTexts) node to text else null
            }
            val disabled = candidates.firstOrNull { it.second in disabledTexts }
            if (disabled != null) {
                return ButtonState(disabled.second, false, disabled.first)
            }
            val ready = candidates.firstOrNull { (node, text) ->
                text in readyTexts && node.bounds().height() > 60
            }
            if (ready != null) {
                return ButtonState(ready.second, true, ready.first)
            }
        }
        return null
    }

    private fun debugVisibleText(accessibility: SignInAccessibilityService): String {
        return accessibility.windowRoots().joinToString(" | ") { root ->
            root.walk()
                .filter { it.isVisibleToUser }
                .map { it.textOrDesc() }
                .filter { it.isNotBlank() }
                .take(30)
                .joinToString(" / ")
        }
    }

    private suspend fun clickNode(
        accessibility: SignInAccessibilityService,
        node: AccessibilityNodeInfo
    ): Boolean? {
        val target = node.closestClickable(3) ?: node
        if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val bounds = target.bounds()
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        return accessibility.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun exactText(node: AccessibilityNodeInfo): String =
        (node.text?.toString() ?: node.contentDescription?.toString() ?: "")
            .replace(Regex("\\s+"), "")

    private fun AccessibilityNodeInfo.textOrDesc(): String =
        (text?.toString() ?: contentDescription?.toString() ?: "")
            .replace(Regex("\\s+"), "")

    private fun AccessibilityNodeInfo.bounds(): Rect =
        Rect().also { getBoundsInScreen(it) }

    private fun AccessibilityNodeInfo.closestClickable(maxDepth: Int): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = this
        var depth = 0
        while (current != null && depth <= maxDepth) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun AccessibilityNodeInfo.walk(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@walk)
        val count = runCatching { childCount }.getOrDefault(0)
        for (index in 0 until count) {
            val child = runCatching { getChild(index) }.getOrNull() ?: continue
            yieldAll(child.walk())
        }
    }

    private fun notifyResult(title: String, message: String) {
        val context = appContext ?: return
        RunNotifier.notify(context, title, message)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.value = (_logs.value + "$stamp  $message").takeLast(200)
    }

    private fun fail(message: String): Nothing = throw IllegalStateException(message)

    private suspend fun <T> waitFor(
        timeoutMs: Long,
        intervalMs: Long,
        block: () -> T?
    ): T? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            block()?.let { return it }
            delay(intervalMs)
        }
        return null
    }

    private enum class Page { HOME, SIGN_IN }
}
