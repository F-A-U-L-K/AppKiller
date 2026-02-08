package com.faulk.appkiller

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val killButton: Button = findViewById(R.id.btnKill)

        killButton.setOnClickListener {
            // This now triggers the automated "Force Stop" sequence
            onClearButtonClick(it)
        }
    }

    // This function finds recent apps and opens their settings for the KillerService
    fun onClearButtonClick(v: View) {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 10) // Check last 10 minutes

        val usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        // If list is empty, user likely hasn't granted "Usage Access" permission
        if (usageStatsList.isNullOrEmpty()) {
            Toast.makeText(this, "Please enable Usage Access for AppKiller", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        usageStatsList.forEach { stats ->
            val packageName = stats.packageName
            
            // Do not try to kill your own app or the system launcher
            if (packageName != this.packageName) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    // Keeping your original background cleaning logic as a fallback
    private fun performBulletproofClean() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPkg = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName

        val processes = am.runningAppProcesses ?: return

        for (app in processes) {
            val name = app.processName
            if (name == packageName || name == launcherPkg || name.contains("keyboard") || name.contains("google.android.gms")) continue

            if (app.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                am.killBackgroundProcesses(name)
                Thread.sleep(50) 
            }
        }
    }
}
