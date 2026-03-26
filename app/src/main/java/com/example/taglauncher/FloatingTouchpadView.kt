package com.example.taglauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class FloatingTouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onTouchpadMove(normalizedX: Float, normalizedY: Float, inCancelZone: Boolean)
        fun onTouchpadRelease(normalizedX: Float, normalizedY: Float, inCancelZone: Boolean)
        fun onTouchpadCancel()
    }

    var listener: Listener? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D91A1A1A")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.FILL
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pointerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }
    private val cancelZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44D32F2F")
        style = Paint.Style.FILL
    }
    private val cancelZoneActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9D32F2F")
        style = Paint.Style.FILL
    }
    private val cancelZoneBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }
    private val cancelIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val panelRect = RectF()
    private val cancelZoneRect = RectF()
    private val cornerRadius = dpToPx(20f)
    private val cancelZoneSize = dpToPx(52f)
    private val cancelZoneGap = dpToPx(8f)
    private val pointerRadius = dpToPx(10f)
    private val centerDotRadius = dpToPx(3f)
    private var pointerX = 0.5f
    private var pointerY = 0.5f
    private var isTracking = false
    private var isPointerInCancelZone = false

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        updateLayoutRects()
        canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, borderPaint)
        canvas.drawRoundRect(
            cancelZoneRect,
            cancelZoneRect.width() / 2f,
            cancelZoneRect.height() / 2f,
            if (isPointerInCancelZone) cancelZoneActivePaint else cancelZonePaint
        )
        canvas.drawRoundRect(
            cancelZoneRect,
            cancelZoneRect.width() / 2f,
            cancelZoneRect.height() / 2f,
            cancelZoneBorderPaint
        )
        val iconInset = cancelZoneRect.width() * 0.32f
        canvas.drawLine(
            cancelZoneRect.left + iconInset,
            cancelZoneRect.top + iconInset,
            cancelZoneRect.right - iconInset,
            cancelZoneRect.bottom - iconInset,
            cancelIconPaint
        )
        canvas.drawLine(
            cancelZoneRect.right - iconInset,
            cancelZoneRect.top + iconInset,
            cancelZoneRect.left + iconInset,
            cancelZoneRect.bottom - iconInset,
            cancelIconPaint
        )

        val thirdWidth = panelRect.width() / 3f
        val thirdHeight = panelRect.height() / 3f
        for (index in 1..2) {
            val verticalX = panelRect.left + (thirdWidth * index)
            val horizontalY = panelRect.top + (thirdHeight * index)
            canvas.drawLine(verticalX, panelRect.top, verticalX, panelRect.bottom, guidePaint)
            canvas.drawLine(panelRect.left, horizontalY, panelRect.right, horizontalY, guidePaint)
        }

        canvas.drawCircle(panelRect.centerX(), panelRect.centerY(), centerDotRadius, centerDotPaint)

        if (isTracking) {
            val cx = panelRect.left + (pointerX * panelRect.width())
            val cy = panelRect.top + (pointerY * panelRect.height())
            canvas.drawCircle(cx, cy, pointerRadius, pointerPaint)
            canvas.drawCircle(cx, cy, pointerRadius, pointerRingPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        updateLayoutRects()
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !isPointInInteractiveArea(event.x, event.y)) {
            return false
        }
        if (!isTracking && event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        val normalizedX = normalizeAxis(event.x - panelRect.left, panelRect.width())
        val normalizedY = normalizeAxis(event.y - panelRect.top, panelRect.height())
        val inCancelZone = isPointInCancelZone(event.x, event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isTracking = true
                updatePointer(normalizedX, normalizedY, inCancelZone)
                listener?.onTouchpadMove(normalizedX, normalizedY, inCancelZone)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updatePointer(normalizedX, normalizedY, inCancelZone)
                listener?.onTouchpadMove(normalizedX, normalizedY, inCancelZone)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                updatePointer(normalizedX, normalizedY, inCancelZone)
                listener?.onTouchpadRelease(normalizedX, normalizedY, inCancelZone)
                isTracking = false
                isPointerInCancelZone = false
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTracking()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    fun cancelTracking() {
        val wasTracking = isTracking
        isTracking = false
        isPointerInCancelZone = false
        if (wasTracking) {
            listener?.onTouchpadCancel()
        }
        invalidate()
    }

    private fun updatePointer(normalizedX: Float, normalizedY: Float, inCancelZone: Boolean) {
        pointerX = normalizedX
        pointerY = normalizedY
        isPointerInCancelZone = inCancelZone
    }

    private fun normalizeAxis(value: Float, size: Float): Float {
        if (size <= 0f) return 0.5f
        return (value / size).coerceIn(0f, 1f)
    }

    fun getTouchpadPanelWidth(): Float {
        updateLayoutRects()
        return panelRect.width()
    }

    fun getTouchpadPanelHeight(): Float {
        updateLayoutRects()
        return panelRect.height()
    }

    fun getTouchpadPanelLeftOffset(): Float {
        updateLayoutRects()
        return panelRect.left
    }

    fun getTouchpadPanelTopOffset(): Float {
        updateLayoutRects()
        return panelRect.top
    }

    fun getTouchpadPanelRightEdge(): Float {
        updateLayoutRects()
        return panelRect.right
    }

    fun getTouchpadPanelBottomEdge(): Float {
        updateLayoutRects()
        return panelRect.bottom
    }

    fun getTouchpadPanelCenterX(): Float {
        updateLayoutRects()
        return panelRect.centerX()
    }

    fun getTouchpadPanelCenterY(): Float {
        updateLayoutRects()
        return panelRect.centerY()
    }

    fun isScreenPointInInteractiveArea(rawX: Float, rawY: Float): Boolean {
        if (visibility != View.VISIBLE || width <= 0 || height <= 0) return false
        val location = IntArray(2)
        getLocationOnScreen(location)
        val localX = rawX - location[0]
        val localY = rawY - location[1]
        return isPointInInteractiveArea(localX, localY)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun isPointInCancelZone(x: Float, y: Float): Boolean {
        updateLayoutRects()
        return cancelZoneRect.contains(x, y)
    }

    private fun isPointInInteractiveArea(x: Float, y: Float): Boolean {
        updateLayoutRects()
        return panelRect.contains(x, y) || cancelZoneRect.contains(x, y)
    }

    private fun updateLayoutRects() {
        val panelLeft = pointerRadius
        val panelTop = pointerRadius
        val panelRight = (width.toFloat() - cancelZoneSize - cancelZoneGap).coerceAtLeast(panelLeft)
        val panelBottom = (height.toFloat() - cancelZoneSize - cancelZoneGap).coerceAtLeast(panelTop)
        panelRect.set(panelLeft, panelTop, panelRight, panelBottom)
        cancelZoneRect.set(
            panelRect.right + cancelZoneGap,
            panelRect.bottom + cancelZoneGap,
            panelRect.right + cancelZoneGap + cancelZoneSize,
            panelRect.bottom + cancelZoneGap + cancelZoneSize
        )
    }
}
