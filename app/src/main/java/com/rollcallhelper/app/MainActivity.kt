package com.rollcallhelper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rollcallhelper.app.automation.AutomationEngine
import com.rollcallhelper.app.automation.RunState
import com.rollcallhelper.app.databinding.ActivityMainBinding
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var autoStarted = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AutomationEngine.prepareNewLaunch()

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnDryRun.setOnClickListener {
            if (!AutomationEngine.isServiceReady) {
                Toast.makeText(this, R.string.accessibility_status_disconnected, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            ensureNotificationPermission()
            AutomationEngine.startDryRun(applicationContext)
        }

        binding.btnOpenTarget.setOnClickListener { openTargetApp() }

        binding.btnRealRun.setOnClickListener { confirmRealRun() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AutomationEngine.serviceReady.collect { ready ->
                        binding.serviceStatus.text = getString(
                            if (ready) R.string.accessibility_status_connected
                            else R.string.accessibility_status_disconnected
                        )
                        maybeAutoStart(ready)
                    }
                }
                launch {
                    AutomationEngine.state.collect { renderState(it) }
                }
                launch {
                    AutomationEngine.logs.collect { logs ->
                        binding.log.text = if (logs.isEmpty()) {
                            getString(R.string.status_placeholder)
                        } else {
                            logs.joinToString("\n")
                        }
                    }
                }
            }
        }

        // 签到成功后，引擎会先停留 1 秒、回桌面并尝试关闭智汇福大，然后通知这里退出。
        lifecycleScope.launch {
            AutomationEngine.closeRequest.collect { close ->
                if (close) {
                    AutomationEngine.resetCloseRequest()
                    finishAndRemoveTask()
                }
            }
        }
    }

    private fun maybeAutoStart(serviceReady: Boolean) {
        if (!serviceReady || autoStarted) return
        if (AutomationEngine.state.value !is RunState.Idle) return

        val now = LocalTime.now()
        val windowOpen = !now.isBefore(LocalTime.of(21, 0)) && now.isBefore(LocalTime.of(23, 55))
        if (!windowOpen) {
            binding.runStatus.text = getString(R.string.outside_window)
            return
        }

        autoStarted = true
        lifecycleScope.launch {
            delay(500)
            if (!AutomationEngine.isRunning) {
                AutomationEngine.startRealRun(applicationContext)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.serviceStatus.text = getString(
            if (AutomationEngine.isServiceReady) R.string.accessibility_status_connected
            else R.string.accessibility_status_disconnected
        )
    }

    private fun renderState(state: RunState) {
        binding.runStatus.text = when (state) {
            RunState.Idle -> getString(R.string.idle)
            is RunState.Running -> "${getString(R.string.running)}：${state.step}\n${state.detail}"
            is RunState.Done -> "${getString(R.string.done)}：${state.message}"
            is RunState.Unknown -> "${getString(R.string.unknown)}：${state.message}"
            is RunState.Failed -> "${getString(R.string.failed)}：${state.message}"
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun confirmRealRun() {
        if (!AutomationEngine.isServiceReady) {
            Toast.makeText(this, R.string.accessibility_status_disconnected, Toast.LENGTH_LONG).show()
            return
        }
        val now = LocalTime.now()
        if (now.isBefore(LocalTime.of(21, 0)) || !now.isBefore(LocalTime.of(23, 55))) {
            Toast.makeText(this, R.string.outside_window, Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.real_run_title)
            .setMessage(R.string.real_run_message)
            .setPositiveButton(R.string.real_run_confirm) { _, _ ->
                ensureNotificationPermission()
                AutomationEngine.startRealRun(applicationContext)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openTargetApp() {
        val intent = packageManager.getLaunchIntentForPackage(AutomationEngine.TARGET_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, "未安装智汇福大", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(intent)
    }
}
