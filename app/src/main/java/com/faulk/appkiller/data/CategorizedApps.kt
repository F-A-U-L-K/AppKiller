package com.faulk.appkiller.data

data class CategorizedApps(
    val userApps: List<AppInfo>,
    val systemApps: List<AppInfo>
)
