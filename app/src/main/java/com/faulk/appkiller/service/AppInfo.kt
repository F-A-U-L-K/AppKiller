package com.example.appkiller
import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val lastUsedTime: Long = 0L,
    val memoryUsedKB: Long = 0L
)
