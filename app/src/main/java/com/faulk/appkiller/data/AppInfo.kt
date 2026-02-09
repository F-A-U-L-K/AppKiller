package com.faulk.appkiller.data

import android.graphics.drawable.Drawable

enum class AppType { USER, SYSTEM }

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val lastUsedTimestamp: Long,
    val estimatedDrain: Double, // The new 4-decimal drain value
    val type: AppType,
    var isSelected: Boolean = true
)
