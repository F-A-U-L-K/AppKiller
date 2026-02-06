package com.faulk.appkiller.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class KillService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // We only care about window state changes (when a new app settings page opens)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            
            // Search for the "Force stop" button
            // Note: Names can vary by language, but "force_stop" is the common resource ID
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button")
            
            if (nodes.isNullOrEmpty()) {
                // Fallback: search by common text if ID fails
                val textNodes = rootNode.findAccessibilityNodeInfosByText("Force stop")
                performClickOnNodes(textNodes)
            } else {
                performClickOnNodes(nodes)
            }
            
            // After clicking "Force stop", look for the confirmation "OK" button
            val confirmNodes = rootNode.findAccessibilityNodeInfosByText("OK")
            performClickOnNodes(confirmNodes)
        }
    }

    private fun performClickOnNodes(nodes: List<AccessibilityNodeInfo>?) {
        nodes?.forEach { node ->
            if (node.isEnabled && node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("KillService", "Clicked: ${node.text ?: "button"}")
            }
        }
    }

    override fun onInterrupt() {
        Log.e("KillService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("KillService", "Service Connected Successfully")
    }
}
