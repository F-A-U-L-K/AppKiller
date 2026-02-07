package com.faulk.appkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val killButton: Button = findViewById(R.id.btnKill)
        val statusText: TextView = findViewById(R.id.tvStatus) // Ensure you have a TextView with this ID

        killButton.setOnClickListener {
            // Disable button to prevent double-taps
            killButton.isEnabled = false
            killButton.text = "Scanning..."
            statusText.text = "Initializing..."

            // Use Coroutines for safer, cleaner background threading
            lifecycleScope.launch {
                optimizeSystem(killButton, statusText)
            }
        }
    }

    private suspend fun optimizeSystem(button: Button, status: TextView) {
        // Switch to Background Thread (IO)
        withContext(Dispatchers.IO) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = packageManager
            
            // Find Launcher (Home Screen) to avoid killing it
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homePkg = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName

            val runningApps = am.runningAppProcesses ?: return@withContext

            var killedCount = 0
            val total = runningApps.size

            for ((index, app) in runningApps.withIndex()) {
                val pkg = app.processName

                // Update Progress on Main Thread
                withContext(Dispatchers.Main) {
                    status.text = "Scanning: ${index + 1}/$total\n$pkg"
                }

                // SAFETY CHECKS
                if (pkg == packageName) continue // Don't kill self
                if (pkg == homePkg) continue // Don't kill Home Screen
                if (pkg.contains("inputmethod")) continue // Don't kill Keyboard
                if (pkg.contains("google.android.gms")) continue // Don't kill Google Play Services (prevents crashes)

                // KILL LOGIC
                // Only kill background apps (IMPORTANCE_SERVICE or lower)
                if (app.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    try {
                        am.killBackgroundProcesses(pkg)
                        killedCount++
                        // Tiny delay to prevent CPU freeze
                        delay(40) 
                    } catch (e: Exception) {
                        // Ignore errors
                    }
                }
            }

            // Completion on Main Thread
            withContext(Dispatchers.Main) {
                status.text = "Optimized $killedCount apps."
                button.text = "KILL APPS"
                button.isEnabled = true
                Toast.makeText(this@MainActivity, "Phone Optimized!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
