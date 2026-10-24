package dev.sajancodes.hardblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dev.sajancodes.hardblock.usage.UsageTracker
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import dev.sajancodes.hardblock.MainActivity
import dev.sajancodes.hardblock.R
import dev.sajancodes.hardblock.enforcement.*
import kotlinx.coroutines.*
import javax.inject.Inject

/** Receives window package changes only. Never retrieves window content or typed text. */
@AndroidEntryPoint
class ProtectionService : AccessibilityService() {
    @Inject lateinit var coordinator: EnforcementCoordinator
    @Inject lateinit var usage: UsageTracker
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var foreground: String? = null
    private var overlay: View? = null
    private var displayed: String? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) { job?.cancel(); removeOverlay() }
            else evaluate()
        }
    }
    override fun onServiceConnected() {
        ProtectionBridge.service = this
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { if (usage.hasAccess()) foreground = runCatching { usage.snapshot().foregroundPackage }.getOrNull(); evaluate() }
        evaluate()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName && overlay != null) return // Our overlay must not erase the underlying foreground target.
        if (pkg != foreground) { foreground = pkg; removeOverlay(); evaluate() }
    }
    fun evaluate() {
        job?.cancel()
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        job = scope.launch {
            while (isActive) {
                val status = coordinator.reconcile(foreground)
                val reason = foreground?.let { ProtectionBridge.blocks[it] }
                if (reason != null) showOverlay(foreground!!, reason.message, reason.availableAt?.toString()) else removeOverlay()
                delay(status.nextCheckMillis)
            }
        }
    }
    private fun showOverlay(pkg: String, reason: String, available: String?) {
        val key = "$pkg|$reason|$available"
        if (displayed == key) return
        removeOverlay()
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 60, 48, 60); setBackgroundColor(Color.rgb(11, 14, 17))
        }
        fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(color); setPadding(0, 20, 0, 20)
        }
        layout.addView(text("HARDBLOCK", 14f, Color.rgb(197, 237, 171)))
        layout.addView(text(label, 32f))
        layout.addView(text(reason, 22f))
        layout.addView(text(available?.let { "Available again\n${java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm z"))}" }
            ?: "Your commitment is active", 17f))
        layout.addView(Button(this).apply { setText(R.string.close_app); setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); removeOverlay() } })
        layout.addView(Button(this).apply { setText(R.string.make_stricter); setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_HOME); removeOverlay()
            startActivity(Intent(this@ProtectionService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } })
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE)
        getSystemService(WindowManager::class.java).addView(layout, params)
        overlay = layout; displayed = key
    }
    private fun removeOverlay() { overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }; overlay = null; displayed = null }
    override fun onInterrupt() { removeOverlay() }
    override fun onDestroy() {
        if (ProtectionBridge.service === this) ProtectionBridge.service = null
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel(); removeOverlay(); super.onDestroy()
    }
}
