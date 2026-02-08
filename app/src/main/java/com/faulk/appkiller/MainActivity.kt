package com.faulk.appkiller

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.faulk.appkiller.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val appsList = mutableListOf<AppItem>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView
        adapter = AppAdapter(appsList)
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        // Kill / Optimize
        binding.btnKill.setOnClickListener {
            if (!checkUsagePermission()) return@setOnClickListener

            binding.btnKill.isEnabled = false
            binding.tvStatus.text = "Starting optimization...\n"

            thread {
                loadRunningApps()
                optimizeMemory()
                runOnUiThread {
                    binding.btnKill.isEnabled = true
                    Toast.makeText(this, "Optimization Complete", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Clear cache
        binding.btnClearCache.setOnClickListener {
            binding.btnClearCache.isEnabled = false
            binding.tvStatus.append("Clearing cache...\n")
            thread {
                clearAppCache()
                runOnUiThread {
                    binding.btnClearCache.isEnabled = true
                    Toast.makeText(this, "Cache Cleared", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkUsagePermission(): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 1000 * 60
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        if (stats.isNullOrEmpty()) {
            Toast.makeText(this, "Please grant Usage Access", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return false
        }
        return true
    }

    private fun loadRunningApps() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager
        val processes = am.runningAppProcesses ?: return
        appsList.clear()
        for (proc in processes) {
            try {
                val info = pm.getApplicationInfo(proc.processName, 0)
                val name = pm.getApplicationLabel(info).toString()
                appsList.add(AppItem(proc.processName, name, pm.getApplicationIcon(info)))
            } catch (_: PackageManager.NameNotFoundException) { }
        }
        runOnUiThread { adapter.notifyDataSetChanged() }
    }

    private fun optimizeMemory() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager
        val homePkg = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName

        for (app in appsList) {
            if (app.packageName == packageName || app.packageName == homePkg) continue
            try {
                am.killBackgroundProcesses(app.packageName)
                runOnUiThread {
                    binding.tvStatus.append("Closed: ${app.appName}\n")
                }
                Thread.sleep(50)
            } catch (_: Exception) { }
        }
    }

    private fun clearAppCache() {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            try {
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                app.cacheDir?.deleteRecursively()
            } catch (_: Exception) { }
        }
        deleteCacheDir(this)
    }

    private fun deleteCacheDir(context: Context) {
        try { context.cacheDir?.deleteRecursively() } catch (_: Exception) { }
    }
}
