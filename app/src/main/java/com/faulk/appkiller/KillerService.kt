package app.faulk.apkiller // Change this to your actual package name

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KillerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val nodeInfo = event.source ?: return

        // Search for "Force Stop" button (for English systems)
        // Note: You can add more strings here for other languages
        val stopButtons = nodeInfo.findAccessibilityNodeInfosByText("FORCE STOP")
        for (node in stopButtons) {
            if (node.isEnabled) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }

        // Search for the "OK" button in the confirmation popup
        val okButtons = nodeInfo.findAccessibilityNodeInfosByText("OK")
        for (node in okButtons) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() {}
}
