package com.faulk.appkiller.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.faulk.appkiller.data.AppInfo
import com.faulk.appkiller.data.AppType
import com.faulk.appkiller.data.CategorizedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val ourPackageName: String = context.packageName

    suspend fun getCategorizedApps(): CategorizedApps = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val timeWindow = 2 * 60 * 60 * 1000L // 2 hours
        val endTime = System.currentTimeMillis()
        val startTime = endTime - timeWindow

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        // --- Battery Drain Estimation Logic ---
        // 1. Calculate a "Usage Score" for all apps combined.
        val totalUsageScore = usageStatsList.sumOf {
            // Give more weight to foreground time
            (it.totalTimeInForeground * 1.5) + it.totalTimeVisible
        }

        // 2. Estimate total battery drain in the time window (e.g., 5% over 2 hours). This is a placeholder.
        val estimatedTotalDrainPercentage = 5.0

        val userApps = mutableListOf<AppInfo>()
        val systemApps = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        val criticalSystemPackages = setOf(
            "com.android.systemui", "com.samsung.android.ui.homemode",
            "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard", "android"
        )

        usageStatsList
            .filter { (it.totalTimeInForeground > 0 || it.totalTimeVisible > 0) && it.packageName != ourPackageName }
            .forEach { usageStats ->
                try {
                    val app = packageManager.getApplicationInfo(usageStats.packageName, 0)
                    if (seenPackages.contains(app.packageName)) return@forEach

                    // Calculate this app's individual contribution to the total usage
                    val appUsageScore = (usageStats.totalTimeInForeground * 1.5) + usageStats.totalTimeVisible
                    val appUsageRatio = if (totalUsageScore > 0) appUsageScore / totalUsageScore else 0.0
                    val estimatedAppDrain = appUsageRatio * estimatedTotalDrainPercentage

                    val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val appType = if (isSystemApp) AppType.SYSTEM else AppType.USER

                    val newAppInfo = AppInfo(
                        appName = packageManager.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        icon = packageManager.getApplicationIcon(app),
                        lastUsedTimestamp = usageStats.lastTimeUsed,
                        estimatedDrain = estimatedAppDrain,
                        type = appType,
                        isSelected = !criticalSystemPackages.contains(app.packageName)
                    )

                    if (appType == AppType.USER) {
                        userApps.add(newAppInfo)
                    } else {
                        systemApps.add(newAppInfo)
                    }
                    seenPackages.add(app.packageName)

                } catch (e: PackageManager.NameNotFoundException) {
                    // Ignore
                }
            }

        CategorizedApps(
            userApps = userApps.sortedByDescending { it.estimatedDrain },
            systemApps = systemApps.sortedByDescending { it.estimatedDrain }
        )
    }
}
