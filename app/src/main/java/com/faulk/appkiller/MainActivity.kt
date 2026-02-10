package com.faulk.appkiller

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
Execution failed for task ':app:compileDebugKotlin'.
import android.R
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var killButton: Button
    private lateinit var refreshButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var memoryInfoText: TextView
    private lateinit var emptyText: TextView

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupButtons()
        updateMemoryInfo()

        // Check and request usage stats permission
        if (!hasUsageStatsPermission()) {
            showUsageStatsPermissionDialog()
        } else {
            loadRunningApps()
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_view)
        killButton = findViewById(R.id.btn_kill_all)
        refreshButton = findViewById(R.id.btn_refresh)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        countText = findViewById(R.id.count_text)
        memoryInfoText = findViewById(R.id.memory_info_text)
        emptyText = findViewById(R.id.empty_text)
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        killButton.setOnClickListener {
            showKillConfirmationDialog()
        }

        refreshButton.setOnClickListener {
            loadRunningApps()
        }
    }

    // ─────────────────────────────────────────────────────────
    //  USAGE STATS PERMISSION
    // ─────────────────────────────────────────────────────────

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showUsageStatsPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "This app needs \"Usage Access\" permission to detect running apps.\n\n" +
                "Please find \"${getString(R.string.app_name)}\" in the list and enable access.\n\n" +
                "Without this permission, the app will use a basic method to detect background processes."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Use Basic Mode") { _, _ ->
                loadRunningApps()
            }
            .setCancelable(false)
            .show()
    }

    // ─────────────────────────────────────────────────────────
    //  LOAD RUNNING APPS
    // ─────────────────────────────────────────────────────────

    private fun loadRunningApps() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Scanning running apps..."
        emptyText.visibility = View.GONE
        killButton.isEnabled = false

        coroutineScope.launch {
            val apps = withContext(Dispatchers.IO) {
                getRunningApps()
            }

            progressBar.visibility = View.GONE

            if (apps.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                statusText.text = "No killable apps found"
                countText.text = "0 apps"
                killButton.isEnabled = false
            } else {
                emptyText.visibility = View.GONE
                statusText.text = "Running apps detected"
                countText.text = "${apps.size} apps"
                killButton.isEnabled = true
            }

            adapter.submitList(apps)
            updateMemoryInfo()
        }
    }

    private fun getRunningApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val pm = packageManager
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        if (hasUsageStatsPermission()) {
            // METHOD 1: UsageStatsManager (more accurate)
            apps.addAll(getAppsFromUsageStats(pm, am))
        }

        if (apps.isEmpty()) {
            // METHOD 2: Fallback - Running processes via ActivityManager
            apps.addAll(getAppsFromRunningProcesses(pm, am))
        }

        // Sort by memory usage (descending)
        return apps.sortedByDescending { it.memoryUsedKB }
    }

    private fun getAppsFromUsageStats(
        pm: PackageManager,
        am: ActivityManager
    ): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Query apps used in last 2 hours
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 2 * 60 * 60 * 1000 // 2 hours ago

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        val seenPackages = mutableSetOf<String>()

        for (usageStats in usageStatsList) {
            val pkgName = usageStats.packageName

            // Skip our own app and already-seen packages
            if (pkgName == packageName || seenPackages.contains(pkgName)) continue

            // Skip system UI packages
            if (isSystemUIPackage(pkgName)) continue

            // Only include if recently used
            if (usageStats.lastTimeUsed < startTime) continue

            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)

                // Skip system apps (optional — remove this check to include them)
                if (isSystemApp(appInfo) && !isUserInstalledSystemApp(appInfo)) continue

                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = try {
                    pm.getApplicationIcon(pkgName)
                } catch (e: Exception) {
                    null
                }

                // Get memory info
                val memoryKB = getAppMemoryUsage(am, pkgName)

                seenPackages.add(pkgName)
                apps.add(
                    AppInfo(
                        appName = appName,
                        packageName = pkgName,
                        icon = icon,
                        lastUsedTime = usageStats.lastTimeUsed,
                        memoryUsedKB = memoryKB
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // App not found, skip
            }
        }

        return apps
    }

    private fun getAppsFromRunningProcesses(
        pm: PackageManager,
        am: ActivityManager
    ): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        @Suppress("DEPRECATION")
        val runningProcesses = am.runningAppProcesses ?: return apps

        for (process in runningProcesses) {
            for (pkgName in process.pkgList) {
                if (pkgName == packageName || seenPackages.contains(pkgName)) continue
                if (isSystemUIPackage(pkgName)) continue

                try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    if (isSystemApp(appInfo) && !isUserInstalledSystemApp(appInfo)) continue

                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        pm.getApplicationIcon(pkgName)
                    } catch (e: Exception) {
                        null
                    }

                    val memoryKB = getAppMemoryUsage(am, pkgName)

                    seenPackages.add(pkgName)
                    apps.add(
                        AppInfo(
                            appName = appName,
                            packageName = pkgName,
                            icon = icon,
                            memoryUsedKB = memoryKB
                        )
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    // skip
                }
            }
        }

        return apps
    }

    // ─────────────────────────────────────────────────────────
    //  KILL ALL APPS
    // ─────────────────────────────────────────────────────────

    private fun showKillConfirmationDialog() {
        val count = adapter.currentList.size
        AlertDialog.Builder(this)
            .setTitle("⚠️ Kill All Apps")
            .setMessage("Are you sure you want to close $count running app(s)?\n\nThis will force-stop background processes.")
            .setPositiveButton("Kill All") { _, _ ->
                killAllApps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun killAllApps() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val apps = adapter.currentList.toList()
        var killedCount = 0

        progressBar.visibility = View.VISIBLE
        statusText.text = "Killing apps..."

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                for (app in apps) {
                    try {
                        am.killBackgroundProcesses(app.packageName)
                        killedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            progressBar.visibility = View.GONE

            Toast.makeText(
                this@MainActivity,
                "✅ Closed $killedCount of ${apps.size} apps",
                Toast.LENGTH_LONG
            ).show()

            // Refresh the list after a short delay
            delay(1000)
            loadRunningApps()
            updateMemoryInfo()
        }
    }

    // ─────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────

    private fun getAppMemoryUsage(am: ActivityManager, packageName: String): Long {
        return try {
            @Suppress("DEPRECATION")
            val processes = am.runningAppProcesses ?: return 0L
            val pid = processes.find { it.pkgList.contains(packageName) }?.pid ?: return 0L
            val memInfo = am.getProcessMemoryInfo(intArrayOf(pid))
            memInfo[0].totalPss.toLong() // PSS in KB
        } catch (e: Exception) {
            0L
        }
    }

    private fun updateMemoryInfo() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMB = memInfo.totalMem / (1024 * 1024)
        val availMB = memInfo.availMem / (1024 * 1024)
        val usedMB = totalMB - availMB
        val usedPercent = (usedMB * 100) / totalMB

        memoryInfoText.text = "RAM: ${usedMB}MB / ${totalMB}MB (${usedPercent}% used) • Free: ${availMB}MB"
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun isUserInstalledSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    private fun isSystemUIPackage(packageName: String): Boolean {
        val systemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.settings",
            "com.android.phone",
            "com.android.inputmethod.latin",
            "com.android.providers.contacts",
            "com.android.providers.telephony",
            "com.android.providers.media",
            "com.android.providers.calendar",
            "com.android.deskclock",
            "com.google.android.inputmethod.latin",
            "com.google.android.gms",             // Google Play Services
            "com.google.android.gsf",             // Google Services Framework
            "com.android.vending",                // Play Store
            "com.google.android.ext.services"
        )
        return systemPackages.contains(packageName) ||
                packageName.startsWith("com.android.providers.") ||
                packageName.startsWith("com.android.internal.")
    }

    override fun onResume() {
        super.onResume()
        // Reload when returning from settings
        if (hasUsageStatsPermission()) {
            loadRunningApps()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
