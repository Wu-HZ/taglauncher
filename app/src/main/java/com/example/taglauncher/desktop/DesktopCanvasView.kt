package com.example.taglauncher.desktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.example.taglauncher.component.DesktopComponent
import kotlin.math.abs
import kotlin.math.min

/**
 * Custom ViewGroup that hosts desktop components.
 * Positions children based on their ComponentBounds (in dp).
 */
class DesktopCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val components = mutableMapOf<String, DesktopComponent>()
    private val viewToComponent = mutableMapOf<View, DesktopComponent>()

    private var isInEditMode = false
    private var selectedComponentId: String? = null

    // Touch tracking
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var isResizing = false
    private var activeResizeHandle = -1
    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null
    private var hasTriggeredLongPress = false

    // Paging
    private var pageCount = 1
    private var currentPage = 0
    private var isPaging = false
    private var pagingStartX = 0f
    private var pagingStartY = 0f
    private var pagingLastX = 0f
    private var pagingStartPage = 0
    private val pageFlipThresholdRatio = 0.80f
    private val pagingTouchSlop = (ViewConfiguration.get(context).scaledTouchSlop * 0.6f).toInt()
    private val scroller = OverScroller(context)

    // Original bounds before drag/resize
    private var originalBounds: ComponentBounds? = null

    // Long press duration
    private val longPressDuration = 500L
    private val dragThreshold = 10f * resources.displayMetrics.density

    // Listener for canvas events
    interface CanvasEventListener {
        fun onComponentSelected(component: DesktopComponent?)
        fun onComponentMoved(component: DesktopComponent, newBounds: ComponentBounds)
        fun onComponentResized(component: DesktopComponent, newBounds: ComponentBounds)
    }

    var canvasEventListener: CanvasEventListener? = null

    // Edit mode visual indicators
    private val editModeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(100, 255, 255, 255)
    }

    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(200, 0, 0, 0)
    }

    private val density = context.resources.displayMetrics.density
    private val handleRadius = 12f * density
    private val tempRect = RectF()

    /**
     * Set the number of horizontal pages.
     */
    fun setPageCount(count: Int) {
        pageCount = count.coerceAtLeast(1)
        if (currentPage >= pageCount) {
            currentPage = pageCount - 1
        }
        scrollToPage(currentPage, animate = false)
    }

    fun getPageCount(): Int = pageCount

    fun getCurrentPage(): Int = currentPage

    fun getPageWidthDp(): Float {
        val widthPx = if (width > 0) width else context.resources.displayMetrics.widthPixels
        return widthPx / density
    }

    fun scrollToPage(page: Int, animate: Boolean = true, durationOverrideMs: Int? = null) {
        val targetPage = page.coerceIn(0, pageCount - 1)
        currentPage = targetPage
        val pageWidth = if (width > 0) width else context.resources.displayMetrics.widthPixels
        if (pageWidth == 0) {
            return
        }
        val targetX = targetPage * pageWidth
        if (animate) {
            val distance = abs(targetX - scrollX)
            if (distance == 0) {
                return
            }
            if (!scroller.isFinished) {
                scroller.forceFinished(true)
            }
            val pageDelta = distance.toFloat() / pageWidth
            val duration = durationOverrideMs
                ?: (220 + 120 * pageDelta).toInt().coerceIn(220, 520)
            scroller.startScroll(scrollX, 0, targetX - scrollX, 0, duration)
            postInvalidateOnAnimation()
        } else {
            if (!scroller.isFinished) {
                scroller.forceFinished(true)
            }
            scrollTo(targetX, 0)
        }
    }

    fun renderPagePreview(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (targetWidth <= 0 || targetHeight <= 0) return null
        val pageWidth = if (width > 0) width else context.resources.displayMetrics.widthPixels
        val pageHeight = if (height > 0) height else context.resources.displayMetrics.heightPixels
        if (pageWidth == 0 || pageHeight == 0) return null

        val clampedIndex = pageIndex.coerceIn(0, pageCount - 1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val scale = min(
            targetWidth.toFloat() / pageWidth.toFloat(),
            targetHeight.toFloat() / pageHeight.toFloat()
        )
        val scaledWidth = pageWidth * scale
        val scaledHeight = pageHeight * scale
        val offsetX = (targetWidth - scaledWidth) / 2f
        val offsetY = (targetHeight - scaledHeight) / 2f

        val pageLeft = clampedIndex * pageWidth

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childLeft = child.left - pageLeft
            val childTop = child.top
            val childRight = childLeft + child.width
            val childBottom = childTop + child.height
            if (childRight < 0 || childLeft > pageWidth ||
                childBottom < 0 || childTop > pageHeight
            ) {
                continue
            }
            canvas.save()
            canvas.translate(childLeft.toFloat(), childTop.toFloat())
            child.draw(canvas)
            canvas.restore()
        }

        canvas.restore()

        return bitmap
    }

    /**
     * Add a component to the canvas.
     */
    fun addComponent(component: DesktopComponent) {
        if (components.containsKey(component.componentId)) {
            return  // Already added
        }

        components[component.componentId] = component
        val view = component.getView()
        viewToComponent[view] = component

        // Add view with layout params
        addView(view)

        // Apply edit mode state
        component.isInEditMode = isInEditMode

        requestLayout()
    }

    /**
     * Remove a component from the canvas.
     */
    fun removeComponent(componentId: String) {
        val component = components.remove(componentId) ?: return
        val view = component.getView()
        viewToComponent.remove(view)
        removeView(view)
        component.destroy()

        if (selectedComponentId == componentId) {
            selectedComponentId = null
        }

        invalidate()
    }

    /**
     * Update a component's bounds.
     */
    fun updateComponentBounds(componentId: String, newBounds: ComponentBounds) {
        val component = components[componentId] ?: return
        component.bounds = newBounds
        requestLayout()
    }

    /**
     * Get a component by ID.
     */
    fun getComponent(componentId: String): DesktopComponent? {
        return components[componentId]
    }

    /**
     * Get all components.
     */
    fun getAllComponents(): List<DesktopComponent> {
        return components.values.toList()
    }

    /**
     * Find the component at a given point (in pixels).
     */
    fun getComponentAt(x: Float, y: Float): DesktopComponent? {
        val adjustedX = x + scrollX
        val adjustedY = y + scrollY
        // Check in reverse order (top-most first based on z-index)
        val sortedComponents = components.values.sortedByDescending {
            it.toComponentData().zIndex
        }

        for (component in sortedComponents) {
            val bounds = component.bounds
            val left = bounds.x * density
            val top = bounds.y * density
            val right = left + bounds.width * density
            val bottom = top + bounds.height * density

            if (adjustedX >= left && adjustedX <= right && adjustedY >= top && adjustedY <= bottom) {
                return component
            }
        }
        return null
    }

    /**
     * Enter desktop edit mode.
     */
    fun enterEditMode() {
        isInEditMode = true
        components.values.forEach { it.isInEditMode = true }
        invalidate()
    }

    /**
     * Exit desktop edit mode.
     */
    fun exitEditMode() {
        isInEditMode = false
        selectedComponentId = null
        components.values.forEach { it.isInEditMode = false }
        invalidate()
    }

    /**
     * Select a component for editing.
     */
    fun selectComponent(componentId: String?) {
        selectedComponentId = componentId
        invalidate()
    }

    /**
     * Get the currently selected component.
     */
    fun getSelectedComponent(): DesktopComponent? {
        return selectedComponentId?.let { components[it] }
    }

    /**
     * Check if a point hits a resize handle of the selected component.
     * Returns the handle index (0-7) or -1 if no handle hit.
     * Handle indices: 0=TL, 1=T, 2=TR, 3=R, 4=BR, 5=B, 6=BL, 7=L
     */
    fun getResizeHandleAt(x: Float, y: Float): Int {
        val component = getSelectedComponent() ?: return -1
        val adjustedX = x + scrollX
        val adjustedY = y + scrollY
        val bounds = component.bounds

        val left = bounds.x * density
        val top = bounds.y * density
        val right = left + bounds.width * density
        val bottom = top + bounds.height * density
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2

        val handles = listOf(
            left to top,        // 0: Top-left
            centerX to top,     // 1: Top
            right to top,       // 2: Top-right
            right to centerY,   // 3: Right
            right to bottom,    // 4: Bottom-right
            centerX to bottom,  // 5: Bottom
            left to bottom,     // 6: Bottom-left
            left to centerY     // 7: Left
        )

        val hitRadius = handleRadius * 1.5f
        handles.forEachIndexed { index, (hx, hy) ->
            val dx = adjustedX - hx
            val dy = adjustedY - hy
            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                return index
            }
        }

        return -1
    }

    /**
     * Check if currently in edit mode.
     */
    fun isEditModeActive(): Boolean = isInEditMode

    /**
     * Bring a component to the front (highest z-index).
     */
    fun bringToFront(componentId: String) {
        val view = components[componentId]?.getView() ?: return
        view.bringToFront()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        // Measure each child based on its component bounds
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val component = viewToComponent[child] ?: continue
            val bounds = component.bounds

            val childWidth = (bounds.width * density).toInt()
            val childHeight = (bounds.height * density).toInt()

            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
            )
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) {
            scrollToPage(currentPage, animate = false)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val component = viewToComponent[child] ?: continue
            val bounds = component.bounds

            val left = (bounds.x * density).toInt()
            val top = (bounds.y * density).toInt()
            val right = left + (bounds.width * density).toInt()
            val bottom = top + (bounds.height * density).toInt()

            child.layout(left, top, right, bottom)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        // Draw edit mode overlays
        if (isInEditMode) {
            drawEditModeOverlays(canvas)
        }
    }

    private fun drawEditModeOverlays(canvas: Canvas) {
        // Draw border around each component in edit mode
        components.values.forEach { component ->
            val bounds = component.bounds
            tempRect.set(
                bounds.x * density,
                bounds.y * density,
                (bounds.x + bounds.width) * density,
                (bounds.y + bounds.height) * density
            )

            if (component.componentId == selectedComponentId) {
                // Draw selection border and handles
                canvas.drawRect(tempRect, selectionBorderPaint)
                drawResizeHandles(canvas, tempRect)
            } else {
                // Draw subtle edit mode border
                canvas.drawRect(tempRect, editModeBorderPaint)
            }
        }
    }

    private fun drawResizeHandles(canvas: Canvas, rect: RectF) {
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2

        val handles = listOf(
            rect.left to rect.top,        // Top-left
            centerX to rect.top,          // Top
            rect.right to rect.top,       // Top-right
            rect.right to centerY,        // Right
            rect.right to rect.bottom,    // Bottom-right
            centerX to rect.bottom,       // Bottom
            rect.left to rect.bottom,     // Bottom-left
            rect.left to centerY          // Left
        )

        handles.forEach { (x, y) ->
            canvas.drawCircle(x, y, handleRadius, handlePaint)
            canvas.drawCircle(x, y, handleRadius, handleStrokePaint)
        }
    }

    /**
     * Clear all components.
     */
    fun clearAll() {
        val componentIds = components.keys.toList()
        componentIds.forEach { removeComponent(it) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isInEditMode) {
            if (pageCount <= 1) {
                return false
            }
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    pagingStartX = ev.x
                    pagingStartY = ev.y
                    pagingLastX = ev.x
                    pagingStartPage = currentPage
                    isPaging = false
                    if (!scroller.isFinished) {
                        scroller.forceFinished(true)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(ev.x - pagingStartX)
                    val dy = abs(ev.y - pagingStartY)
                    if (dx > pagingTouchSlop && dx > dy) {
                        isPaging = true
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPaging = false
                }
            }
            return false
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.x
                touchDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                hasTriggeredLongPress = false

                // Check if we're touching a resize handle of selected component
                if (selectedComponentId != null) {
                    val handleIndex = getResizeHandleAt(ev.x, ev.y)
                    if (handleIndex >= 0) {
                        activeResizeHandle = handleIndex
                        isResizing = true
                        originalBounds = getSelectedComponent()?.bounds?.copy()
                        return true
                    }
                }

                // Check if touching a component
                val component = getComponentAt(ev.x, ev.y)
                if (component != null) {
                    // Start long press detection
                    cancelPendingLongPress()
                    longPressHandler = Handler(Looper.getMainLooper())
                    longPressRunnable = Runnable {
                        hasTriggeredLongPress = true
                        selectComponent(component.componentId)
                        canvasEventListener?.onComponentSelected(component)
                        originalBounds = component.bounds.copy()
                        isDragging = true
                    }
                    longPressHandler?.postDelayed(longPressRunnable!!, longPressDuration)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - touchDownX)
                val dy = abs(ev.y - touchDownY)
                if (dx > dragThreshold || dy > dragThreshold) {
                    cancelPendingLongPress()
                    // If already selected, start dragging
                    if (selectedComponentId != null && !isDragging && !isResizing) {
                        val selectedComponent = getSelectedComponent()
                        if (selectedComponent != null) {
                            val bounds = selectedComponent.bounds
                            val left = bounds.x * density
                            val top = bounds.y * density
                            val right = left + bounds.width * density
                            val bottom = top + bounds.height * density
                            val adjustedDownX = touchDownX + scrollX
                            val adjustedDownY = touchDownY + scrollY

                            if (adjustedDownX >= left && adjustedDownX <= right &&
                                adjustedDownY >= top && adjustedDownY <= bottom
                            ) {
                                isDragging = true
                                originalBounds = bounds.copy()
                                return true
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
            }
        }

        return isDragging || isResizing
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInEditMode) {
            if (pageCount <= 1) {
                return false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pagingLastX = event.x
                    pagingStartPage = currentPage
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = pagingLastX - event.x
                    val target = (scrollX + dx).toInt()
                        .coerceIn(0, (pageCount - 1) * width)
                    scrollTo(target, 0)
                    pagingLastX = event.x
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val targetPage = if (width > 0) {
                        val startPage = pagingStartPage.coerceIn(0, pageCount - 1)
                        val delta = scrollX - startPage * width
                        val trigger = width * (1f - pageFlipThresholdRatio)
                        when {
                            delta > trigger -> (startPage + 1).coerceAtMost(pageCount - 1)
                            delta < -trigger -> (startPage - 1).coerceAtLeast(0)
                            else -> startPage
                        }
                    } else {
                        0
                    }
                    scrollToPage(targetPage, animate = true)
                    isPaging = false
                    return true
                }
            }
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                if (isDragging) {
                    handleDrag(dx, dy)
                } else if (isResizing) {
                    handleResize(event.x, event.y)
                }

                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging || isResizing) {
                    finishDragOrResize()
                } else if (!hasTriggeredLongPress) {
                    // Check if we tapped on a component to select it
                    val component = getComponentAt(event.x, event.y)
                    if (component != null) {
                        selectComponent(component.componentId)
                        canvasEventListener?.onComponentSelected(component)
                    } else {
                        // Tapped on empty area - deselect
                        selectComponent(null)
                        canvasEventListener?.onComponentSelected(null)
                    }
                }
                cancelPendingLongPress()
                isDragging = false
                isResizing = false
                activeResizeHandle = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                isDragging = false
                isResizing = false
                activeResizeHandle = -1
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun handleDrag(dx: Float, dy: Float) {
        val component = getSelectedComponent() ?: return
        val original = originalBounds ?: return

        val newX = component.bounds.x + dx / density
        val newY = component.bounds.y + dy / density

        // Clamp to screen bounds
        val pageWidthDp = getPageWidthDp()
        val pageIndex = (original.x / pageWidthDp).toInt().coerceIn(0, pageCount - 1)
        val minX = pageIndex * pageWidthDp
        val maxX = minX + pageWidthDp - component.bounds.width
        val maxY = (height / density) - component.bounds.height
        val clampedX = newX.coerceIn(minX, maxX.coerceAtLeast(minX))
        val clampedY = newY.coerceIn(0f, maxY.coerceAtLeast(0f))

        component.bounds = component.bounds.copy(x = clampedX, y = clampedY)
        requestLayout()
        invalidate()
    }

    private fun handleResize(x: Float, y: Float) {
        val component = getSelectedComponent() ?: return
        val original = originalBounds ?: return

        val deltaX = (x - touchDownX) / density
        val deltaY = (y - touchDownY) / density

        var newX = original.x
        var newY = original.y
        var newWidth = original.width
        var newHeight = original.height

        when (activeResizeHandle) {
            0 -> { // Top-left
                newX = (original.x + deltaX).coerceAtMost(original.x + original.width - original.minWidth)
                newY = (original.y + deltaY).coerceAtMost(original.y + original.height - original.minHeight)
                newWidth = original.width - (newX - original.x)
                newHeight = original.height - (newY - original.y)
            }
            1 -> { // Top
                newY = (original.y + deltaY).coerceAtMost(original.y + original.height - original.minHeight)
                newHeight = original.height - (newY - original.y)
            }
            2 -> { // Top-right
                newY = (original.y + deltaY).coerceAtMost(original.y + original.height - original.minHeight)
                newWidth = (original.width + deltaX).coerceAtLeast(original.minWidth)
                newHeight = original.height - (newY - original.y)
            }
            3 -> { // Right
                newWidth = (original.width + deltaX).coerceAtLeast(original.minWidth)
            }
            4 -> { // Bottom-right
                newWidth = (original.width + deltaX).coerceAtLeast(original.minWidth)
                newHeight = (original.height + deltaY).coerceAtLeast(original.minHeight)
            }
            5 -> { // Bottom
                newHeight = (original.height + deltaY).coerceAtLeast(original.minHeight)
            }
            6 -> { // Bottom-left
                newX = (original.x + deltaX).coerceAtMost(original.x + original.width - original.minWidth)
                newWidth = original.width - (newX - original.x)
                newHeight = (original.height + deltaY).coerceAtLeast(original.minHeight)
            }
            7 -> { // Left
                newX = (original.x + deltaX).coerceAtMost(original.x + original.width - original.minWidth)
                newWidth = original.width - (newX - original.x)
            }
        }

        // Apply max constraints
        if (original.maxWidth < Float.MAX_VALUE) {
            newWidth = newWidth.coerceAtMost(original.maxWidth)
        }
        if (original.maxHeight < Float.MAX_VALUE) {
            newHeight = newHeight.coerceAtMost(original.maxHeight)
        }

        // Clamp position to screen
        newX = newX.coerceAtLeast(0f)
        newY = newY.coerceAtLeast(0f)

        component.bounds = component.bounds.copy(
            x = newX,
            y = newY,
            width = newWidth,
            height = newHeight
        )
        requestLayout()
        invalidate()
    }

    private fun finishDragOrResize() {
        val component = getSelectedComponent() ?: return

        if (isDragging) {
            canvasEventListener?.onComponentMoved(component, component.bounds)
        } else if (isResizing) {
            canvasEventListener?.onComponentResized(component, component.bounds)
        }

        originalBounds = null
    }

    private fun cancelPendingLongPress() {
        longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
        longPressHandler = null
        longPressRunnable = null
    }
}
