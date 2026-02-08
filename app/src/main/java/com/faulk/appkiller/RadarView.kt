package com.faulk.appkiller

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class RadarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sweepAngle = 0f

    private val sweepAnimator = object : Runnable {
        override fun run() {
            sweepAngle += 5f
            if (sweepAngle >= 360f) sweepAngle = 0f
            invalidate()
            postDelayed(this, 16)
        }
    }

    private val beamHistory = mutableListOf<Pair<Float, Float>>()
    private val maxBeamTrail = 6

    fun setBeamTarget(x: Float, y: Float) {
        beamHistory.add(Pair(x, y))
        if (beamHistory.size > maxBeamTrail) beamHistory.removeAt(0)
        invalidate()
    }

    init { paint.style = Paint.Style.FILL }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(sweepAnimator)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(sweepAnimator)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = (width.coerceAtMost(height) / 2 * 0.9f)
        val centerX = width / 2f
        val centerY = height / 2f

        // Radar circles
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.parseColor("#33FF00")
        for (i in 1..4) canvas.drawCircle(centerX, centerY, radius * i / 4, paint)

        // Sweep
        val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(centerX, centerY, Color.TRANSPARENT, Color.parseColor("#66FF66"))
            alpha = 160
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(sweepAngle, centerX, centerY)
        canvas.drawArc(centerX - radius, centerY - radius, centerX + radius, centerY + radius, 0f, 60f, true, sweepPaint)
        canvas.restore()

        // Laser beam trail
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        for ((i, point) in beamHistory.withIndex()) {
            val alpha = ((i + 1).toFloat() / beamHistory.size * 255).toInt()
            paint.color = Color.argb(alpha, 0, 255, 0)
            canvas.drawLine(centerX, centerY, point.first, point.second, paint)
        }
    }
}
