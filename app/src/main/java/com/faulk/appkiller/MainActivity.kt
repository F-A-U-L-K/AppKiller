package com.faulk.appkiller
import android.app.usage.UsageStatsManager
import android.net.Uri
import android.provider.Settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
            // Lock the UI immediately to prevent freezing/crashing
            killButton.isEnabled = false
            killButton.text = "Cleaning..."
            
            Toast.makeText(this, "Cleaning Background Memory...", Toast.LENGTH_SHORT).show()

            thread(start = true) {
                performBulletproofClean()
                
                // Return to main screen thread to unlock
                runOnUiThread {
                    killButton.isEnabled = true
                    killButton.text = "KILL APPS"
                    Toast.makeText(this, "Optimization Complete", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performBulletproofClean() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager

        // Identify the Home Screen so we don't break the phone's UI
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPkg = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName

        val processes = am.runningAppProcesses ?: return

        for (app in processes) {
            val name = app.processName

            // SECURITY & STABILITY RULES:
            if (name == packageName) continue             // Don't kill this app
            if (name == launcherPkg) continue            // Don't kill the home screen
            if (name.contains("keyboard")) continue      // Don't kill the keyboard
            if (name.contains("google.android.gms")) continue // Don't kill system services

            // Only kill apps that are actually in the background
            if (app.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                am.killBackgroundProcesses(name)
                // Small pause to let the CPU breathe
                Thread.sleep(50) 
            }
        }
    }
}
