package com.faulk.appkiller.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.faulk.appkiller.ui.MainActivity

class AppKillerAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AppKillerService"
        const val ACTION_PROGRESS_UPDATE = "com.faulk.appkiller.ACTION_PROGRESS_UPDATE"
        const val ACTION_KILL_PROCESS_FINISHED = "com.faulk.appkiller.ACTION_KILL_PROCESS_FINISHED"
        const val ACTION_ABORT_KILL_PROCESS = "com.faulk.appkiller.ABORT_KILL_PROCESS"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isKilling = false
    private var killQueue = mutableListOf<String>()
    private var packageNamesToAppNames = mutableMapOf<String, String>()
    private var totalAppsToKill = 0
    private var currentAppRetries = 0

    private val timeoutRunnable = Runnable { handleTimeout() }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isKilling || event.packageName != "com.android.settings") return

        val rootNode = rootInActiveWindow ?: return

        // Prefer finding by text first, then fallback to ID for OEM-specific UIs
        val forceStopButton = findNode(rootNode, "Force stop", "com.android.settings:id/button1")
        if (forceStopButton != null && forceStopButton.isEnabled) {
            handler.removeCallbacks(timeoutRunnable) // We've made progress, cancel timeout
            Log.d(TAG, "Clicking 'Force stop'")
            forceStopButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed(timeoutRunnable, 2000) // Set a new, shorter timeout for the "OK" dialog
            return
        }

        val okButton = findNode(rootNode, "OK", "android:id/button1")
        if (okButton != null && okButton.isEnabled) {
            Log.d(TAG, "Clicking 'OK'")
            okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({ proceedToNextApp() }, 300) // Short delay to allow settings to close
            return
        }
        rootNode.recycle()
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String, resourceId: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isClickable }?.let { return it }
        root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull { it.isClickable }?.let { return it }
        return null
    }

    private fun startKillingProcess(packages: ArrayList<String>) {
        if (isKilling) return
        isKilling = true
        killQueue.clear()
        killQueue.addAll(packages)
        totalAppsToKill = killQueue.size
        currentAppRetries = 0
        buildAppNameMap()
        Log.d(TAG, "Starting kill process for ${killQueue.size} apps.")
        openNextAppSettings()
    }

    private fun openNextAppSettings() {
        handler.removeCallbacks(timeoutRunnable)
        if (killQueue.isEmpty()) {
            finishKillingProcess()
            return
        }

        val packageName = killQueue.first()
        broadcastProgress()

        // Set a timeout. If nothing happens in 5 seconds, we're stuck.
        handler.postDelayed(timeoutRunnable, 5000)

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun proceedToNextApp() {
        handler.removeCallbacks(timeoutRunnable)
        currentAppRetries = 0
        if (killQueue.isNotEmpty()) {
            killQueue.removeAt(0)
        }
        openNextAppSettings()
    }

    private fun handleTimeout() {
        val stuckPackage = killQueue.firstOrNull() ?: "Unknown"
        Log.w(TAG, "Timeout while processing $stuckPackage. Retries: $currentAppRetries")
        if (currentAppRetries < 1) { // Retry once
            currentAppRetries++
            openNextAppSettings()
        } else {
            Log.e(TAG, "Max retries reached for $stuckPackage. Skipping.")
            proceedToNextApp()
        }
    }

    private fun finishKillingProcess() {
        if (!isKilling) return
        isKilling = false
        killQueue.clear()
        handler.removeCallbacksAndMessages(null)
        broadcastFinish()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ABORT_KILL_PROCESS) {
            finishKillingProcess()
            return START_NOT_STICKY
        }
        intent?.getStringArrayListExtra("KILL_LIST")?.let {
            startKillingProcess(it)
        }
        return START_STICKY
    }

    private fun buildAppNameMap() {
        packageNamesToAppNames.clear()
        killQueue.forEach { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageNamesToAppNames[pkg] = packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageNamesToAppNames[pkg] = pkg
            }
        }
    }

    private fun broadcastProgress() {
        val currentPackage = killQueue.firstOrNull() ?: return
        val appName = packageNamesToAppNames[currentPackage] ?: currentPackage
        val intent = Intent(ACTION_PROGRESS_UPDATE).apply {
            putExtra("current_app", appName)
            putExtra("current_count", (totalAppsToKill - killQueue.size) + 1)
            putExtra("total_count", totalAppsToKill)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun broadcastFinish() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_KILL_PROCESS_FINISHED))
    }

    override fun onInterrupt() {
        finishKillingProcess()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        finishKillingProcess()
        return super.onUnbind(intent)
    }
}
