package com.example.taglauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TagRingMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class RingConfig(
        val tags: List<TagItem?>,  // Nullable to support empty slots
        val innerRadiusRatio: Float,
        val outerRadiusRatio: Float,
        val isRibbon: Boolean = false  // True for action ribbon ring
    )

    private var pages: List<List<RingConfig>> = listOf(emptyList())
    private var currentPage: Int = 0
    private val rings: List<RingConfig> get() = pages.getOrNull(currentPage) ?: emptyList()
    private var availableSwapZones: Set<Int> = emptySet()  // 0=upper-back, 1=lower-back
    private var centerX = 0f
    private var centerY = 0f
    private var maxRadius = 0f

    // Semi-circle configuration: start angle and sweep angle in degrees
    private var arcStartAngle = 90f   // Start from left (90°)
    private var arcSweepAngle = 180f  // Half circle (180°)

    private var selectedRingIndex = -1
    private var selectedSegmentIndex = -1

    private var animationProgress = 0f
    private var animator: ValueAnimator? = null

    private var showPinIndicators = false
    private var pinnedPositions: Set<Triple<Int, Int, Int>> = emptySet()
    private var allowEmptySelection = false

    var onTagSelected: ((TagItem?, Boolean, Int, Int) -> Unit)? = null  // tag, isRibbon, ringIndex, segmentIndex

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val segmentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }

    private val emptySlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(40, 255, 255, 255)
    }

    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ribbonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = 36f
        isFakeBoldText = true
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(100, 255, 255, 255)
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 0, 0, 0)
    }

    private val pinIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val pinIndicatorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(200, 0, 0, 0)
    }

    private val swapZoneArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()
    private val rectF = RectF()

    fun setPages(pageList: List<List<RingConfig>>) {
        pages = if (pageList.isEmpty()) listOf(emptyList()) else pageList
        if (currentPage >= pages.size) currentPage = 0
        invalidate()
    }

    fun setRings(ringConfigs: List<RingConfig>) {
        setPages(listOf(ringConfigs))
    }

    fun setCurrentPage(page: Int) {
        val clamped = page.coerceIn(0, pages.size - 1)
        if (clamped != currentPage) {
            currentPage = clamped
            selectedRingIndex = -1
            selectedSegmentIndex = -1
            invalidate()
        }
    }

    fun getCurrentPage(): Int = currentPage

    fun setAvailableSwapZones(zones: Set<Int>) {
        availableSwapZones = zones
        invalidate()
    }

    /**
     * Returns 0 for upper-back zone, 1 for lower-back zone, or -1 if not in any
     * available swap zone.
     */
    fun getSwapZoneAt(x: Float, y: Float): Int {
        if (animationProgress <= 0f) return -1
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx.pow(2) + dy.pow(2))
        // Require the touch to be at least near the rings (not directly on the FAB).
        val innerEnter = maxRadius * (rings.firstOrNull()?.innerRadiusRatio ?: 0.1f) * animationProgress
        if (distance < innerEnter) return -1

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f

        // Inside the front arc → not a swap zone.
        val endAngle = arcStartAngle + arcSweepAngle
        val normalizedAngle = if (angle < arcStartAngle) angle + 360 else angle
        if (normalizedAngle in arcStartAngle..endAngle) return -1

        // Upper half of circle: angle ∈ (180, 360); lower half: (0, 180).
        val zone = if (angle in 180f..360f) 0 else 1
        return if (availableSwapZones.contains(zone)) zone else -1
    }

    fun setCenter(x: Float, y: Float) {
        centerX = x
        centerY = y
        invalidate()
    }

    fun setMaxRadius(radius: Float) {
        maxRadius = radius
        textPaint.textSize = radius * 0.08f
        invalidate()
    }

    fun setArcAngles(startAngle: Float, sweepAngle: Float) {
        arcStartAngle = startAngle
        arcSweepAngle = sweepAngle
        invalidate()
    }

    fun setShowPinIndicators(show: Boolean) {
        showPinIndicators = show
        invalidate()
    }

    fun setAllowEmptySelection(allow: Boolean) {
        allowEmptySelection = allow
        invalidate()
    }

    fun setPinnedPositions(positions: Set<Triple<Int, Int, Int>>) {
        pinnedPositions = positions
        invalidate()
    }

    fun show() {
        visibility = VISIBLE
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun hide() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(animationProgress, 0f).apply {
            duration = 200
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                if (animationProgress == 0f) {
                    visibility = GONE
                }
                invalidate()
            }
            start()
        }
    }

    fun getSelectedTag(): TagItem? {
        if (selectedRingIndex in rings.indices) {
            val ring = rings[selectedRingIndex]
            if (selectedSegmentIndex in ring.tags.indices) {
                return ring.tags[selectedSegmentIndex]  // May return null for empty slots
            }
        }
        return null
    }

    fun isSelectedFromRibbon(): Boolean {
        if (selectedRingIndex in rings.indices) {
            return rings[selectedRingIndex].isRibbon
        }
        return false
    }

    fun getSelectedRingIndex(): Int = selectedRingIndex

    fun getSelectedSegmentIndex(): Int = selectedSegmentIndex

    fun updateSelectionAt(x: Float, y: Float) {
        updateSelection(x, y)
    }

    fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                updateSelection(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val selected = getSelectedTag()
                val isRibbon = isSelectedFromRibbon()
                onTagSelected?.invoke(selected, isRibbon, selectedRingIndex, selectedSegmentIndex)
                clearSelection()
                return true
            }
        }
        return false
    }

    private fun updateSelection(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val distance = sqrt(dx.pow(2) + dy.pow(2))

        // Calculate angle in degrees (0° = right, 90° = down, 180° = left, 270° = up)
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f

        selectedRingIndex = -1
        selectedSegmentIndex = -1

        // Check if angle is within our arc range
        val endAngle = arcStartAngle + arcSweepAngle
        val normalizedAngle = if (angle < arcStartAngle) angle + 360 else angle

        if (normalizedAngle < arcStartAngle || normalizedAngle > endAngle) {
            invalidate()
            return
        }

        for ((ringIndex, ring) in rings.withIndex()) {
            val innerRadius = maxRadius * ring.innerRadiusRatio * animationProgress
            val outerRadius = maxRadius * ring.outerRadiusRatio * animationProgress

            if (distance >= innerRadius && distance <= outerRadius) {
                val segmentCount = ring.tags.size
                if (segmentCount > 0) {
                    val segmentAngle = arcSweepAngle / segmentCount
                    val angleInArc = normalizedAngle - arcStartAngle
                    val potentialSegment = (angleInArc / segmentAngle).toInt().coerceIn(0, segmentCount - 1)

                    val isSelectable = ring.tags[potentialSegment] != null || (allowEmptySelection && !ring.isRibbon)
                    if (isSelectable) {
                        selectedRingIndex = ringIndex
                        selectedSegmentIndex = potentialSegment
                    }
                }
                break
            }
        }
        invalidate()
    }

    private fun clearSelection() {
        selectedRingIndex = -1
        selectedSegmentIndex = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (animationProgress <= 0f || rings.isEmpty()) return

        // Draw shadow/backdrop arc
        drawShadowArc(canvas)

        // Draw each ring from outer to inner
        for ((ringIndex, ring) in rings.withIndex().reversed()) {
            drawRing(canvas, ringIndex, ring)
        }

        drawSwapZoneIndicators(canvas)
    }

    private fun drawSwapZoneIndicators(canvas: Canvas) {
        if (availableSwapZones.isEmpty()) return
        val arrowRadius = maxRadius * 0.45f * animationProgress
        val arrowSize = maxRadius * 0.06f * animationProgress
        // Shift along x so the chevron isn't stacked directly above/below the FAB.
        val xShift = maxRadius * 0.08f * animationProgress * when {
            arcStartAngle > 0f && arcStartAngle < 180f -> 1f   // back on the right
            arcStartAngle < 0f || arcStartAngle > 180f -> -1f  // back on the left
            else -> 0f
        }
        for (zone in availableSwapZones) {
            // zone 0 = upper, zone 1 = lower.
            val cx = centerX + xShift
            val cy = if (zone == 0) centerY - arrowRadius else centerY + arrowRadius
            // Mapping: zone 0 → page 1, zone 1 → page 2.
            val isActivePage = currentPage == zone + 1
            val baseAlpha = if (isActivePage) 230 else 110
            swapZoneArrowPaint.alpha = (baseAlpha * animationProgress).toInt().coerceIn(0, 255)

            // Chevron points OUTWARD: zone 0 points up, zone 1 points down.
            val tipY = if (zone == 0) cy - arrowSize else cy + arrowSize
            val baseY = if (zone == 0) cy + arrowSize else cy - arrowSize
            canvas.drawLine(cx - arrowSize, baseY, cx, tipY, swapZoneArrowPaint)
            canvas.drawLine(cx + arrowSize, baseY, cx, tipY, swapZoneArrowPaint)
        }
    }

    private fun drawShadowArc(canvas: Canvas) {
        val outerRadius = maxRadius * rings.last().outerRadiusRatio * animationProgress
        val innerRadius = maxRadius * rings.first().innerRadiusRatio * animationProgress

        path.reset()
        rectF.set(
            centerX - outerRadius, centerY - outerRadius,
            centerX + outerRadius, centerY + outerRadius
        )
        path.arcTo(rectF, arcStartAngle, arcSweepAngle)
        rectF.set(
            centerX - innerRadius, centerY - innerRadius,
            centerX + innerRadius, centerY + innerRadius
        )
        path.arcTo(rectF, arcStartAngle + arcSweepAngle, -arcSweepAngle)
        path.close()

        canvas.drawPath(path, shadowPaint)
    }

    private fun drawRing(canvas: Canvas, ringIndex: Int, ring: RingConfig) {
        val segmentCount = ring.tags.size
        if (segmentCount == 0) return

        val innerRadius = maxRadius * ring.innerRadiusRatio * animationProgress
        val outerRadius = maxRadius * ring.outerRadiusRatio * animationProgress
        val segmentAngle = arcSweepAngle / segmentCount

        for ((segmentIndex, tag) in ring.tags.withIndex()) {
            val startAngle = arcStartAngle + segmentIndex * segmentAngle
            val isSelected = ringIndex == selectedRingIndex && segmentIndex == selectedSegmentIndex

            // Draw segment path
            path.reset()
            rectF.set(
                centerX - outerRadius, centerY - outerRadius,
                centerX + outerRadius, centerY + outerRadius
            )
            path.arcTo(rectF, startAngle, segmentAngle)
            rectF.set(
                centerX - innerRadius, centerY - innerRadius,
                centerX + innerRadius, centerY + innerRadius
            )
            path.arcTo(rectF, startAngle + segmentAngle, -segmentAngle)
            path.close()

            if (tag != null) {
                if (ring.isRibbon) {
                    // Draw ribbon segment with distinct style
                    ribbonPaint.color = if (isSelected) {
                        brightenColor(tag.color, 1.4f)
                    } else {
                        tag.color
                    }
                    ribbonPaint.alpha = (230 * animationProgress).toInt()

                    canvas.drawPath(path, ribbonPaint)
                    canvas.drawPath(path, ribbonStrokePaint.apply {
                        alpha = (255 * animationProgress).toInt()
                    })

                    // Draw highlight overlay if selected
                    if (isSelected) {
                        canvas.drawPath(path, highlightPaint.apply {
                            alpha = (120 * animationProgress).toInt()
                        })
                    }

                    // Draw label text (slightly larger for ribbon)
                    val midAngle = Math.toRadians((startAngle + segmentAngle / 2).toDouble())
                    val textRadius = (innerRadius + outerRadius) / 2
                    val textX = centerX + (textRadius * cos(midAngle)).toFloat()
                    val textY = centerY + (textRadius * sin(midAngle)).toFloat() + textPaint.textSize / 3

                    textPaint.alpha = (255 * animationProgress).toInt()
                    canvas.drawText(tag.label, textX, textY, textPaint)
                } else {
                    // Draw regular tag segment
                    segmentPaint.color = if (isSelected) {
                        brightenColor(tag.color, 1.3f)
                    } else {
                        tag.color
                    }
                    segmentPaint.alpha = (255 * animationProgress).toInt()

                    canvas.drawPath(path, segmentPaint)
                    canvas.drawPath(path, segmentStrokePaint.apply {
                        alpha = (255 * animationProgress).toInt()
                    })

                    // Draw highlight overlay if selected
                    if (isSelected) {
                        canvas.drawPath(path, highlightPaint.apply {
                            alpha = (100 * animationProgress).toInt()
                        })
                    }

                    // Draw label text
                    val midAngle = Math.toRadians((startAngle + segmentAngle / 2).toDouble())
                    val textRadius = (innerRadius + outerRadius) / 2
                    val textX = centerX + (textRadius * cos(midAngle)).toFloat()
                    val textY = centerY + (textRadius * sin(midAngle)).toFloat() + textPaint.textSize / 3

                    textPaint.alpha = (255 * animationProgress).toInt()
                    canvas.drawText(tag.label, textX, textY, textPaint)

                    // Draw pin indicator if in pin mode and this position is pinned
                    if (showPinIndicators && pinnedPositions.contains(Triple(currentPage, ringIndex, segmentIndex))) {
                        val pinRadius = maxRadius * 0.025f * animationProgress
                        val pinDistance = outerRadius - pinRadius * 1.5f
                        val pinX = centerX + (pinDistance * cos(midAngle)).toFloat()
                        val pinY = centerY + (pinDistance * sin(midAngle)).toFloat()

                        pinIndicatorPaint.alpha = (255 * animationProgress).toInt()
                        pinIndicatorStrokePaint.alpha = (200 * animationProgress).toInt()

                        canvas.drawCircle(pinX, pinY, pinRadius, pinIndicatorPaint)
                        canvas.drawCircle(pinX, pinY, pinRadius, pinIndicatorStrokePaint)
                    }
                }
            } else {
                // Draw empty slot - just a faint outline
                if (isSelected) {
                    canvas.drawPath(path, highlightPaint.apply {
                        alpha = (80 * animationProgress).toInt()
                    })
                }
                emptySlotPaint.alpha = (40 * animationProgress).toInt()
                canvas.drawPath(path, emptySlotPaint)
            }
        }
    }

    private fun brightenColor(color: Int, factor: Float): Int {
        val r = min(255, (Color.red(color) * factor).toInt())
        val g = min(255, (Color.green(color) * factor).toInt())
        val b = min(255, (Color.blue(color) * factor).toInt())
        return Color.rgb(r, g, b)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only handle direct touches when ring is visible
        if (visibility != VISIBLE || animationProgress <= 0f) {
            return false
        }

        // Convert view-local coordinates to window coordinates (to match setCenter coordinates)
        val location = IntArray(2)
        getLocationInWindow(location)
        val windowX = event.x + location[0]
        val windowY = event.y + location[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                updateSelection(windowX, windowY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSelection(windowX, windowY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateSelection(windowX, windowY)
                val selected = getSelectedTag()
                val isRibbon = isSelectedFromRibbon()
                onTagSelected?.invoke(selected, isRibbon, selectedRingIndex, selectedSegmentIndex)
                clearSelection()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearSelection()
                return true
            }
        }
        return false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
