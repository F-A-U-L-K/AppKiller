package com.example.appkiller

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
        killButton.setOnClickListener { showKillConfirmationDialog() }
        refreshButton.setOnClickListener { loadRunningApps() }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showUsageStatsPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs \"Usage Access\" permission to detect running apps.")
            .setPositiveButton("Grant Permission") { _, _ -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .setNegativeButton("Use Basic Mode") { _, _ -> loadRunningApps() }
            .setCancelable(false)
            .show()
    }

    private fun loadRunningApps() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Scanning..."
        coroutineScope.launch {
            val apps = withContext(Dispatchers.IO) { getRunningApps() }
            progressBar.visibility = View.GONE
            emptyText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            killButton.isEnabled = apps.isNotEmpty()
            statusText.text = if (apps.isEmpty()) "No apps found" else "Apps detected"
            countText.text = "${apps.size} apps"
            adapter.submitList(apps)
            updateMemoryInfo()
        }
    }

    private fun getRunningApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val pm = packageManager
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        apps.addAll(getAppsFromUsageStats(pm, am))
        if (apps.isEmpty()) apps.addAll(getAppsFromRunningProcesses(pm, am))
        return apps.sortedByDescending { it.memoryUsedKB }
    }

    private fun getAppsFromUsageStats(pm: PackageManager, am: ActivityManager): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 2 * 60 * 60 * 1000
        val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val seenPackages = mutableSetOf<String>()

        for (usageStats in usageStatsList) {
            val pkgName = usageStats.packageName
            if (pkgName == packageName || seenPackages.contains(pkgName) || isSystemUIPackage(pkgName)) continue
            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                if (isSystemApp(appInfo) && !isUserInstalledSystemApp(appInfo)) continue
                val icon = try { pm.getApplicationIcon(pkgName) } catch (e: Exception) { null }
                seenPackages.add(pkgName)
                apps.add(AppInfo(pm.getApplicationLabel(appInfo).toString(), pkgName, icon, usageStats.lastTimeUsed, getAppMemoryUsage(am, pkgName)))
            } catch (e: Exception) {}
        }
        return apps
    }

    private fun getAppsFromRunningProcesses(pm: PackageManager, am: ActivityManager): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        @Suppress("DEPRECATION")
        val processes = am.runningAppProcesses ?: return apps
        for (process in processes) {
            for (pkgName in process.pkgList) {
                if (pkgName == packageName || isSystemUIPackage(pkgName)) continue
                try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    apps.add(AppInfo(pm.getApplicationLabel(appInfo).toString(), pkgName, null, 0, getAppMemoryUsage(am, pkgName)))
                } catch (e: Exception) {}
            }
        }
        return apps
    }

    private fun showKillConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Kill All Apps")
            .setMessage("Are you sure?")
            .setPositiveButton("Kill All") { _, _ -> killAllApps() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun killAllApps() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val apps = adapter.currentList.toList()
        progressBar.visibility = View.VISIBLE
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                for (app in apps) {
                    try { am.killBackgroundProcesses(app.packageName) } catch (e: Exception) {}
                }
            }
            delay(1000)
            loadRunningApps()
            updateMemoryInfo()
            Toast.makeText(this@MainActivity, "Tasks cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppMemoryUsage(am: ActivityManager, packageName: String): Long {
        val processes = am.runningAppProcesses ?: return 0L
        val pid = processes.find { it.pkgList.contains(packageName) }?.pid ?: return 0L
        return am.getProcessMemoryInfo(intArrayOf(pid))[0].totalPss.toLong()
    }

    private fun updateMemoryInfo() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val usedMB = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        val totalMB = memInfo.totalMem / (1024 * 1024)
        memoryInfoText.text = "RAM: ${usedMB}MB / ${totalMB}MB"
    }

    private fun isSystemApp(info: ApplicationInfo) = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    private fun isUserInstalledSystemApp(info: ApplicationInfo) = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    private fun isSystemUIPackage(pkg: String) = pkg == "com.android.systemui" || pkg == "android" || pkg == "com.android.settings"

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
