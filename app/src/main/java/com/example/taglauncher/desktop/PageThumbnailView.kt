package com.example.taglauncher.desktop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.taglauncher.persistence.ComponentData
import kotlin.math.max

class PageThumbnailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pageIndex: Int = 0
    private var pageWidthDp: Float = 1f
    private var pageHeightDp: Float = 1f
    private var components: List<ComponentData> = emptyList()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val componentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val componentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    private val tempRect = RectF()

    fun setData(
        pageIndex: Int,
        pageWidthDp: Float,
        pageHeightDp: Float,
        components: List<ComponentData>
    ) {
        this.pageIndex = pageIndex
        this.pageWidthDp = max(pageWidthDp, 1f)
        this.pageHeightDp = max(pageHeightDp, 1f)
        this.components = components
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (width == 0 || height == 0) return

        val scaleX = width / pageWidthDp
        val scaleY = height / pageHeightDp
        val pageStartX = pageIndex * pageWidthDp

        components.forEach { data ->
            val bounds = data.bounds
            val page = (bounds.x / pageWidthDp).toInt().coerceAtLeast(0)
            if (page != pageIndex) return@forEach

            val localX = bounds.x - pageStartX
            val left = (localX * scaleX).coerceAtLeast(0f)
            val top = (bounds.y * scaleY).coerceAtLeast(0f)
            val right = ((localX + bounds.width) * scaleX).coerceAtMost(width.toFloat())
            val bottom = ((bounds.y + bounds.height) * scaleY).coerceAtMost(height.toFloat())

            if (right <= left || bottom <= top) return@forEach

            tempRect.set(left, top, right, bottom)
            canvas.drawRoundRect(tempRect, 6f, 6f, componentFillPaint)
            canvas.drawRoundRect(tempRect, 6f, 6f, componentStrokePaint)
        }
    }
}
