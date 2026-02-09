package com.faulk.appkiller.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faulk.appkiller.adapter.ViewPagerAdapter
import com.faulk.appkiller.databinding.ActivityMainBinding
import com.faulk.appkiller.service.AppKillerAccessibilityService
import com.faulk.appkiller.viewmodel.AppKillerViewModel
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AppKillerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupObservers()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndLoadApps()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "User Apps" else "System Apps"
        }.attach()
    }

    private fun checkPermissionsAndLoadApps() {
        when {
            !hasUsageStatsPermission() -> showPermissionDialog("Usage Access Required", "App Killer needs 'Usage Access' to find recently used apps and estimate their battery drain.", Settings.ACTION_USAGE_ACCESS_SETTINGS)
            !isAccessibilityServiceEnabled() -> showPermissionDialog("Accessibility Service Required", "App Killer needs this core permission to automate the hibernation process.", Settings.ACTION_ACCESSIBILITY_SETTINGS)
            else -> viewModel.loadApps()
        }
    }

    private fun setupObservers() {
        viewModel.categorizedApps.observe(this) { categorized ->
            val userCount = categorized.userApps.count { it.isSelected }
            val systemCount = categorized.systemApps.count { it.isSelected }
            val total = userCount + systemCount

            binding.textAppCount.text = "$userCount User & $systemCount System Apps Selected"
            binding.btnKillSelected.isEnabled = total > 0
            
            val hasApps = categorized.userApps.isNotEmpty() || categorized.systemApps.isNotEmpty()
            binding.emptyView.visibility = if (hasApps) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnKillSelected.setOnClickListener {
            val categorized = viewModel.categorizedApps.value ?: return@setOnClickListener
            val allSelected = categorized.userApps.filter { it.isSelected } + categorized.systemApps.filter { it.isSelected }

            if (allSelected.isEmpty()) {
                Snackbar.make(binding.root, "No apps selected.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val packageNames = ArrayList(allSelected.map { it.packageName })
            val intent = Intent(this, KillingProgressActivity::class.java).apply {
                putStringArrayListExtra("KILL_LIST", packageNames)
            }
            startActivity(intent)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${AppKillerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }

    private fun showPermissionDialog(title: String, message: String, action: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ -> startActivity(Intent(action)) }
            .setCancelable(false)
            .show()
    }
}
