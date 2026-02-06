package com.faulk.appkiller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.faulk.appkiller.adapter.AppAdapter
import com.faulk.appkiller.model.AppInfo
import com.faulk.appkiller.service.KillService

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private lateinit var btnKillAll: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        recyclerView = findViewById(R.id.recyclerView)
        btnKillAll = findViewById(R.id.btnKillAll)

        setupRecyclerView()
        checkAccessibilityPermission()

        btnKillAll.setOnClickListener {
            startKillingProcess()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(mutableListOf())
        recyclerView.adapter = appAdapter
        
        // Load installed apps (Logic should be in a Background Task)
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        // This is a placeholder for the app scanning logic
        // Ensure you are filtering system apps correctly
    }

    private fun startKillingProcess() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable Accessibility Service", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }
        
        // Trigger the background service to start closing apps
        val intent = Intent(this, KillService::class.java)
        startService(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${KillService::class.java.canonicalName}"
        val enabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
        if (enabled == 1) {
            val settingValue = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return settingValue?.contains(service) == true
        }
        return false
    }

    private fun checkAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Accessibility permission required", Toast.LENGTH_LONG).show()
        }
    }
}
