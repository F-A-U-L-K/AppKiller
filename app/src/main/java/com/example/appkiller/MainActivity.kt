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

        recyclerView = findViewById(R.id.recyclerView)
        btnKillAll = findViewById(R.id.btnKillAll)

        recyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(mutableListOf())
        recyclerView.adapter = appAdapter

        // Simplified listener to prevent character/syntax errors at line 42
        btnKillAll.setOnClickListener({
            handleKillButtonClick()
        })
    }

    private fun handleKillButtonClick() {
        if (isAccessibilityServiceEnabled()) {
            startService(Intent(this, KillService::class.java))
        } else {
            Toast.makeText(this, "Enable Accessibility", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${KillService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.contains(expected) == true
    }
}
