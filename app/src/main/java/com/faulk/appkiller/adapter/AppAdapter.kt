package com.example.appkiller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppAdapter : ListAdapter<AppInfo, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.app_icon)
        val nameView: TextView = itemView.findViewById(R.id.app_name)
        val packageView: TextView = itemView.findViewById(R.id.app_package)
        val memoryView: TextView = itemView.findViewById(R.id.app_memory)
        val lastUsedView: TextView = itemView.findViewById(R.id.app_last_used)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = getItem(position)

        holder.nameView.text = app.appName
        holder.packageView.text = app.packageName

        if (app.icon != null) {
            holder.iconView.setImageDrawable(app.icon)
        } else {
            holder.iconView.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // Display memory usage
        if (app.memoryUsedKB > 0) {
            val memoryMB = app.memoryUsedKB / 1024.0
            holder.memoryView.text = String.format("%.1f MB", memoryMB)
            holder.memoryView.visibility = View.VISIBLE
        } else {
            holder.memoryView.visibility = View.GONE
        }

        // Display last used time
        if (app.lastUsedTime > 0) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            holder.lastUsedView.text = "Last used: ${sdf.format(Date(app.lastUsedTime))}"
            holder.lastUsedView.visibility = View.VISIBLE
        } else {
            holder.lastUsedView.visibility = View.GONE
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}
