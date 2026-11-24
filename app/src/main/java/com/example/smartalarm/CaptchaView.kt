package com.example.smartalarm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class CaptchaView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var text = ""
    private var level = 0
    private var seed = 0L
    private val bg = Paint().apply { color = Color.rgb(235, 235, 235) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
    private val density = ctx.resources.displayMetrics.density

    fun setCaptcha(t: String, difficulty: Int) {
        text = t
        level = difficulty
        seed = Random.nextLong()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (100 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        if (text.isEmpty()) return
        val r = Random(seed)
        repeat(3 + level * 2) {
            linePaint.color = Color.rgb(r.nextInt(80, 200), r.nextInt(80, 200), r.nextInt(80, 200))
            canvas.drawLine(r.nextFloat() * w, r.nextFloat() * h, r.nextFloat() * w, r.nextFloat() * h, linePaint)
        }
        val cell = w / text.length
        text.forEachIndexed { i, ch ->
            textPaint.textSize = (30 + r.nextInt(0, 10)) * density
            textPaint.color = Color.rgb(r.nextInt(0, 110), r.nextInt(0, 110), r.nextInt(0, 110))
            val cx = cell * i + cell / 2
            val cy = h / 2 + (r.nextFloat() - 0.5f) * level * 2f * density + textPaint.textSize / 3
            val angle = (r.nextFloat() - 0.5f) * 2f * (5 + level * 3)
            canvas.save()
            canvas.rotate(angle, cx, cy)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(ch.toString(), cx, cy, textPaint)
            canvas.restore()
        }
        repeat(level * 2) {
            linePaint.color = Color.argb(160, r.nextInt(0, 150), r.nextInt(0, 150), r.nextInt(0, 150))
            canvas.drawLine(0f, r.nextFloat() * h, w, r.nextFloat() * h, linePaint)
        }
        repeat(20 + level * 15) {
            linePaint.color = Color.argb(180, r.nextInt(0, 255), r.nextInt(0, 255), r.nextInt(0, 255))
            canvas.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 2f * density, linePaint)
        }
    }
}