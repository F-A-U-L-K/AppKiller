package com.faulk.appkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
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

        // Find the button (Make sure your ID in activity_main.xml is 'btnKill')
        val killButton: Button = findViewById(R.id.btnKill)

        killButton.setOnClickListener {
            // 1. Disable button immediately to prevent double-clicks
            killButton.isEnabled = false
            killButton.text = "Optimizing..."
            
            Toast.makeText(this, "Starting Safe Clean...", Toast.LENGTH_SHORT).show()

            // 2. Run the heavy work in the background
            runSafeOptimization(this) {
                // 3. This code runs when finished
                killButton.isEnabled = true
                killButton.text = "KILL APPS"
                Toast.makeText(this, "Phone is optimized!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun runSafeOptimization(context: Context, onComplete: () -> Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager
        val mainHandler = Handler(Looper.getMainLooper())

        Thread {
            // A. Find the Home Screen (Launcher) so we don't kill it
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val launcherPkg = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName

            // B. Get only running processes
            val runningProcesses = am.runningAppProcesses ?: return@Thread

            for (process in runningProcesses) {
                try {
                    val pkgName = process.processName

                    // SAFETY CHECK 1: Don't kill THIS app
                    if (pkgName == context.packageName) continue

                    // SAFETY CHECK 2: Don't kill the Home Screen
                    if (pkgName == launcherPkg) continue

                    // SAFETY CHECK 3: Don't kill Keyboards (Input Methods)
                    // If you kill the keyboard, you can't type!
                    if (pkgName.contains("inputmethod") || pkgName.contains("keyboard")) continue

                    // SAFETY CHECK 4: Only kill "unimportant" background apps
                    // IMPORTANCE_VISIBLE (200) or higher means the user can see it. Don't kill it.
                    if (process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        
                        am.killBackgroundProcesses(pkgName)
                        
                        // C. Vital CPU Pause (Prevents freezing)
                        Thread.sleep(30) 
                    }
                } catch (e: Exception) {
                    // Ignore errors for individual apps and keep going
                }
            }

            // D. Notify the Main Thread that we are done
            mainHandler.post {
                onComplete()
            }
        }.start()
    }
}
