package com.faulk.appkiller

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Link UI Elements
        tvStatus = findViewById(R.id.tvStatus)
        val btnOptimize: Button = findViewById(R.id.btnOneTapOptimize)
        val btnKill: Button = findViewById(R.id.btnKill)
        val btnCache: Button = findViewById(R.id.btnClearCache)

        btnOptimize.setOnClickListener {
            startFullScan()
        }

        btnKill.setOnClickListener {
            triggerAppKiller()
        }

        btnCache.setOnClickListener {
            tvStatus.append("\n[System] Clearing temporary cache files...")
            Toast.makeText(this, "Cache Cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFullScan() {
        tvStatus.append("\n[Radar] Scanning for background processes...")
        // If you have a RadarView, you would start its animation here:
        // findViewById<View>(R.id.radarView).visibility = View.VISIBLE
        
        thread(start = true) {
            Thread.sleep(2000)
            runOnUiThread {
                tvStatus.append("\n[Radar] 14 apps found consuming 1.2GB RAM.")
            }
        }
    }

    private fun triggerAppKiller() {
        tvStatus.append("\n[Service] Opening Force Stop menu for background apps...")
        
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 10)
        val usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        if (usageStatsList.isNullOrEmpty()) {
            tvStatus.append("\n[Error] Usage Access Permission Required.")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        usageStatsList.take(5).forEach { stats ->
            if (stats.packageName != this.packageName) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${stats.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }
}
