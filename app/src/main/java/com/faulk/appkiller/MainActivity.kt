package com.faulk.appkiller

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val killButton: Button = findViewById(R.id.btnKill) // Ensure this ID matches your XML
        
        killButton.setOnClickListener {
            Toast.makeText(this, "Cleaning started...", Toast.LENGTH_SHORT).show()
            runOptimization(this)
        }
    }

    private fun runOptimization(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager

        // FIXED: Using a Thread so the UI does not freeze
        Thread {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            for (packageInfo in packages) {
                // SAFETY: Skip system apps and your own app to prevent crashing the OS
                val isSystemApp = (packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!isSystemApp && packageInfo.packageName != context.packageName) {
                    
                    am.killBackgroundProcesses(packageInfo.packageName)
                    
                    // Tiny pause to prevent CPU spikes
                    Thread.sleep(20) 
                }
            }

            // Return to Main Thread to show the "Finished" message
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Optimization Complete!", Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}
