package com.faulk.appkiller.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KillService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // We only act when the window changes (e.g., Settings page or Dialog appears)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return

            // Step 1: Find and click "Force stop" (Internal name: force_stop_button)
            // We search by text first as a fallback, but IDs are more reliable.
            val forceStopButtons = rootNode.findAccessibilityNodeInfosByText("Force stop")
            for (node in forceStopButtons) {
                if (node.isEnabled) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }

            // Step 2: Find and click "OK" on the confirmation dialog
            val okButtons = rootNode.findAccessibilityNodeInfosByText("OK")
            for (node in okButtons) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {}
}
