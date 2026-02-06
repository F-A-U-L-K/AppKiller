package com.faulk.appkiller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.faulk.appkiller.adapter.AppAdapter
import com.faulk.appkiller.service.KillService

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnKillAll: Button
    private lateinit var appAdapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Setup
        recyclerView = findViewById(R.id.recyclerView)
        btnKillAll = findViewById(R.id.btnKillAll)

        // Adapter Setup
        recyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(mutableListOf())
        recyclerView.adapter = appAdapter

        // The problematic area (Line 42)
        btnKillAll.setOnClickListener {
            handleKillAction()
        }
    }

    private fun handleKillAction() {
        if (isAccessibilityServiceEnabled()) {
            val intent = Intent(this, KillService::class.java)
            startService(intent)
            Toast.makeText(this, "Starting cleanup...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Accessibility Permission Required", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "$packageName/${KillService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, 
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceId) == true
    }
}
