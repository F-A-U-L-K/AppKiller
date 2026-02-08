package com.faulk.appkiller

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.faulk.appkiller.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnKill.setOnClickListener {
            // Immediately open App Info for recent apps
            onClearButtonClick()

            // Disable button and show progress
            binding.btnKill.isEnabled = false
            binding.btnKill.text = "Cleaning..."
            Toast.makeText(this, "Optimizing Memory...", Toast.LENGTH_SHORT).show()

            // Run background memory optimization safely
            thread {
                performSafeClean()
                runOnUiThread {
                    binding.btnKill.isEnabled = true
                    binding.btnKill.text = "KILL APPS"
                    Toast.makeText(this, "Optimization Complete", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Opens "App Info" for apps used in the last 10 minutes.
     */
    private fun onClearButtonClick() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 10 // last 10 minutes

        val usageStatsList = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            Toast.makeText(this, "Enable 'Usage Access' for AppKiller", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        usageStatsList.forEach { stats ->
            val pkg = stats.packageName
            if (pkg != this.packageName) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    /**
     * Safely frees memory without killing system-critical apps.
     */
    private fun performSafeClean() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPkg = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

        val processes = am.runningAppProcesses ?: return

        for (app in processes) {
            val name = app.processName

            // Skip critical apps
            if (name == packageName || name == launcherPkg ||
                name.contains("keyboard") || name.contains("google.android.gms")
            ) continue

            // Only target apps that are visible but not foreground
            if (app.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                try {
                    // Modern Android: killBackgroundProcesses may not always work,
                    // but this is safe and won’t crash
                    am.killBackgroundProcesses(name)
                    Thread.sleep(50)
                } catch (e: Exception) {
                    // Ignore failures (some system apps cannot be killed)
                }
            }
        }
    }
}
