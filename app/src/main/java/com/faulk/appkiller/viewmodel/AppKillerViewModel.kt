package com.faulk.appkiller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.faulk.appkiller.data.AppInfo
import com.faulk.appkiller.data.AppType
import com.faulk.appkiller.data.CategorizedApps
import com.faulk.appkiller.repository.AppRepository
import kotlinx.coroutines.launch

class AppKillerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _categorizedApps = MutableLiveData<CategorizedApps>()
    val categorizedApps: LiveData<CategorizedApps> = _categorizedApps

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            _categorizedApps.postValue(repository.getCategorizedApps())
            _isLoading.postValue(false)
        }
    }

    fun updateAppSelection(appInfo: AppInfo, isSelected: Boolean) {
        val currentCategorized = _categorizedApps.value ?: return

        fun updateList(list: List<AppInfo>): List<AppInfo> {
            return list.map { if (it.packageName == appInfo.packageName) it.copy(isSelected = isSelected) else it }
        }

        val updatedCategorized = if (appInfo.type == AppType.USER) {
            currentCategorized.copy(userApps = updateList(currentCategorized.userApps))
        } else {
            currentCategorized.copy(systemApps = updateList(currentCategorized.systemApps))
        }
        _categorizedApps.value = updatedCategorized
    }

    fun toggleSelectAll(appType: AppType, select: Boolean) {
        val currentCategorized = _categorizedApps.value ?: return

        val updatedCategorized = if (appType == AppType.USER) {
            val updatedUserApps = currentCategorized.userApps.map { it.copy(isSelected = select) }
            currentCategorized.copy(userApps = updatedUserApps)
        } else { // SYSTEM
            // When selecting all system apps, still skip the critical ones.
            val criticalSystemPackages = setOf("com.android.systemui", "android")
            val updatedSystemApps = currentCategorized.systemApps.map {
                if (criticalSystemPackages.contains(it.packageName)) {
                    it.copy(isSelected = false) // Always keep critical apps deselected
                } else {
                    it.copy(isSelected = select)
                }
            }
            currentCategorized.copy(systemApps = updatedSystemApps)
        }
        _categorizedApps.value = updatedCategorized
    }
}
