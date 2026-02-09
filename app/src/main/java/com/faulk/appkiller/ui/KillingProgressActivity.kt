package com.faulk.appkiller.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.faulk.appkiller.databinding.ActivityKillingProgressBinding
import com.faulk.appkiller.service.AppKillerAccessibilityService

class KillingProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKillingProgressBinding

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AppKillerAccessibilityService.ACTION_PROGRESS_UPDATE -> {
                    val currentApp = intent.getStringExtra("current_app") ?: "Finishing..."
                    val current = intent.getIntExtra("current_count", 0)
                    val total = intent.getIntExtra("total_count", 0)
                    binding.textStatus.text = "Hibernating: $currentApp"
                    binding.textProgress.text = "$current / $total"
                }
                AppKillerAccessibilityService.ACTION_KILL_PROCESS_FINISHED -> {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKillingProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val killList = intent.getStringArrayListExtra("KILL_LIST")
        if (killList == null || killList.isEmpty()) {
            finish()
            return
        }

        binding.btnCancel.setOnClickListener {
            val stopIntent = Intent(this, AppKillerAccessibilityService::class.java).apply {
                action = AppKillerAccessibilityService.ACTION_ABORT_KILL_PROCESS
            }
            startService(stopIntent)
            finish()
        }

        val serviceIntent = Intent(this, AppKillerAccessibilityService::class.java).apply {
            putStringArrayListExtra("KILL_LIST", killList)
        }
        startService(serviceIntent)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(AppKillerAccessibilityService.ACTION_PROGRESS_UPDATE)
            addAction(AppKillerAccessibilityService.ACTION_KILL_PROCESS_FINISHED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(progressReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver)
    }

    override fun onBackPressed() {
        // Do nothing to prevent user from accidentally exiting. Use the cancel button.
    }
}
