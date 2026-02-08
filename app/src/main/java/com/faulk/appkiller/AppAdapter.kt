package com.faulk.appkiller

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color

class AppAdapter(private val apps: List<AppItem>) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.cbSelect)
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val pkg: TextView = view.findViewById(R.id.tvPackage)
        val container: View = view
        private val iconPosition = IntArray(2)
        fun getIconCenter(): Pair<Float, Float> {
            icon.getLocationOnScreen(iconPosition)
            val x = iconPosition[0] + icon.width / 2f
            val y = iconPosition[1] + icon.height / 2f
            return Pair(x, y)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.pkg.text = app.packageName
        holder.checkbox.isChecked = app.isSelected

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            app.isSelected = isChecked
        }
    }

    override fun getItemCount() = apps.size

    fun animateAppBeam(position: Int) {
        val holder = this@AppAdapter.recyclerView?.findViewHolderForAdapterPosition(position) as? AppViewHolder ?: return

        // Pulse background
        val colorFrom = Color.parseColor("#33FF00")
        val colorTo = Color.TRANSPARENT
        val anim = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        anim.duration = 500
        anim.addUpdateListener { holder.container.setBackgroundColor(it.animatedValue as Int) }
        anim.start()

        // Icon pulse
        holder.icon.animate().scaleX(1.3f).scaleY(1.3f).setDuration(250).withEndAction {
            holder.icon.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
        }.start()
    }

    var recyclerView: RecyclerView? = null
    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        recyclerView = rv
    }
    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        recyclerView = null
    }
    }
