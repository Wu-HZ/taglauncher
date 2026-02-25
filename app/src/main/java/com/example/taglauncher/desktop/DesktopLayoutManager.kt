package com.example.taglauncher.desktop

import android.content.Context
import com.example.taglauncher.PreferencesManager
import com.example.taglauncher.component.BaseDesktopComponent
import com.example.taglauncher.component.ComponentFactory
import com.example.taglauncher.component.ComponentListener
import com.example.taglauncher.component.ComponentType
import com.example.taglauncher.component.DesktopComponent
import com.example.taglauncher.persistence.ComponentData
import com.example.taglauncher.persistence.DesktopLayoutData

/**
 * Manages the desktop layout, including loading, saving, and manipulating components.
 */
class DesktopLayoutManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val canvasView: DesktopCanvasView
) {
    private val componentFactory = ComponentFactory(context, preferencesManager)
    private var layoutData: DesktopLayoutData = DesktopLayoutData.empty()

    /**
     * Listener for layout events.
     */
    interface LayoutListener {
        fun onLayoutLoaded(hasAppGrid: Boolean)
        fun onComponentAdded(component: DesktopComponent)
        fun onComponentRemoved(componentId: String)
        fun onComponentsChanged()
    }

    var layoutListener: LayoutListener? = null

    private val componentListener = object : ComponentListener {
        override fun onComponentBoundsChangeRequested(component: DesktopComponent, newBounds: ComponentBounds) {
            updateComponentBounds(component.componentId, newBounds)
        }

        override fun onComponentSettingsChanged(component: DesktopComponent) {
            updateComponentSettings(component.componentId)
        }

        override fun onComponentBringToFront(component: DesktopComponent) {
            bringToFront(component.componentId)
        }
    }

    /**
     * Load the desktop layout from persistence.
     * If no layout exists, creates the default layout.
     */
    fun loadLayout() {
        layoutData = preferencesManager.getDesktopLayout() ?: createDefaultLayout()
        layoutData = layoutData.copy(
            pageCount = layoutData.pageCount.coerceAtLeast(1),
            homePage = layoutData.homePage.coerceIn(0, layoutData.pageCount.coerceAtLeast(1) - 1)
        )

        // Clear existing components
        canvasView.clearAll()

        // Configure paging
        canvasView.setPageCount(layoutData.pageCount)
        canvasView.scrollToPage(layoutData.homePage, animate = false)

        // Create and add components
        layoutData.components.forEach { componentData ->
            createAndAddComponent(componentData)
        }

        layoutListener?.onLayoutLoaded(layoutData.hasAppDrawer())
    }

    /**
     * Save the current layout to persistence.
     */
    fun saveLayout() {
        // Update layout data with current component states
        val updatedComponents = canvasView.getAllComponents().mapIndexed { index, component ->
            component.toComponentData().withZIndex(index)
        }
        layoutData = layoutData.copy(
            components = updatedComponents,
            lastModified = System.currentTimeMillis()
        )
        preferencesManager.saveDesktopLayout(layoutData)
    }

    /**
     * Create a new component and add it to the canvas.
     */
    fun addComponent(type: ComponentType, bounds: ComponentBounds? = null): DesktopComponent {
        val screenWidthDp = getPageWidthDp()
        val screenHeightDp = pxToDp(canvasView.height)

        val componentBounds = bounds ?: componentFactory.getDefaultBounds(type, screenWidthDp, screenHeightDp)
        val offsetX = getCurrentPageOffsetX(screenWidthDp)
        val adjustedBounds = componentBounds.copy(x = componentBounds.x + offsetX)
        val component = componentFactory.createNew(type, adjustedBounds)
        attachComponentListener(component)

        canvasView.addComponent(component)

        // Update layout data
        layoutData = layoutData.withComponent(component.toComponentData().withZIndex(layoutData.nextZIndex()))
        saveLayout()

        layoutListener?.onComponentAdded(component)
        layoutListener?.onComponentsChanged()

        return component
    }

    fun addPage(): Int {
        val newCount = (layoutData.pageCount + 1).coerceAtLeast(1)
        layoutData = layoutData.copy(pageCount = newCount, lastModified = System.currentTimeMillis())
        preferencesManager.saveDesktopLayout(layoutData)
        canvasView.setPageCount(newCount)
        canvasView.scrollToPage(newCount - 1, animate = true)
        return newCount
    }

    fun getPageCount(): Int = layoutData.pageCount

    fun getLayoutSnapshot(): DesktopLayoutData = layoutData

    fun getHomePage(): Int = layoutData.homePage

    fun setHomePage(pageIndex: Int) {
        val clamped = pageIndex.coerceIn(0, layoutData.pageCount - 1)
        if (clamped != layoutData.homePage) {
            layoutData = layoutData.copy(homePage = clamped, lastModified = System.currentTimeMillis())
            preferencesManager.saveDesktopLayout(layoutData)
        }
        canvasView.scrollToPage(clamped, animate = true)
    }

    fun deletePage(pageIndex: Int): Boolean {
        if (layoutData.pageCount <= 1) return false

        val target = pageIndex.coerceIn(0, layoutData.pageCount - 1)
        val pageWidthDp = getPageWidthDp()
        val startX = target * pageWidthDp
        val endX = startX + pageWidthDp

        val removedIds = mutableListOf<String>()
        val updatedComponents = layoutData.components.mapNotNull { data ->
            val x = data.bounds.x
            val page = (x / pageWidthDp).toInt().coerceIn(0, layoutData.pageCount - 1)
            when {
                page == target -> {
                    removedIds.add(data.instanceId)
                    null
                }
                page > target -> data.copy(bounds = data.bounds.copy(x = x - pageWidthDp))
                else -> data
            }
        }

        removedIds.forEach { canvasView.removeComponent(it) }
        updatedComponents.forEach { canvasView.updateComponentBounds(it.instanceId, it.bounds) }

        val newCount = layoutData.pageCount - 1
        var newHome = layoutData.homePage
        newHome = when {
            target < newHome -> newHome - 1
            target == newHome -> (newHome - 1).coerceAtLeast(0)
            else -> newHome
        }.coerceIn(0, newCount - 1)

        layoutData = layoutData.copy(
            components = updatedComponents,
            pageCount = newCount,
            homePage = newHome,
            lastModified = System.currentTimeMillis()
        )
        preferencesManager.saveDesktopLayout(layoutData)

        canvasView.setPageCount(newCount)
        val current = canvasView.getCurrentPage()
        val newCurrent = when {
            target < current -> current - 1
            target == current -> current.coerceAtMost(newCount - 1)
            else -> current
        }.coerceIn(0, newCount - 1)
        canvasView.scrollToPage(newCurrent, animate = true)
        return true
    }

    fun movePage(fromIndex: Int, toIndex: Int): Boolean {
        val count = layoutData.pageCount
        if (count <= 1) return false

        val from = fromIndex.coerceIn(0, count - 1)
        val to = toIndex.coerceIn(0, count - 1)
        if (from == to) return false

        val pageWidthDp = getPageWidthDp()
        val updatedComponents = layoutData.components.map { data ->
            val x = data.bounds.x
            val page = (x / pageWidthDp).toInt().coerceIn(0, count - 1)
            val newX = when {
                from < to && page == from -> x + (to - from) * pageWidthDp
                from < to && page in (from + 1)..to -> x - pageWidthDp
                from > to && page == from -> x - (from - to) * pageWidthDp
                from > to && page in to until from -> x + pageWidthDp
                else -> x
            }
            if (newX == x) data else data.copy(bounds = data.bounds.copy(x = newX))
        }

        updatedComponents.forEach { canvasView.updateComponentBounds(it.instanceId, it.bounds) }

        var newHome = layoutData.homePage
        newHome = when {
            newHome == from -> to
            from < to && newHome in (from + 1)..to -> newHome - 1
            from > to && newHome in to until from -> newHome + 1
            else -> newHome
        }.coerceIn(0, count - 1)

        layoutData = layoutData.copy(
            components = updatedComponents,
            homePage = newHome,
            lastModified = System.currentTimeMillis()
        )
        preferencesManager.saveDesktopLayout(layoutData)

        val current = canvasView.getCurrentPage()
        val newCurrent = when {
            current == from -> to
            from < to && current in (from + 1)..to -> current - 1
            from > to && current in to until from -> current + 1
            else -> current
        }.coerceIn(0, count - 1)
        canvasView.scrollToPage(newCurrent, animate = true)
        return true
    }

    /**
     * Add a component from ComponentData (e.g., from paste operation).
     */
    fun addComponentFromData(data: ComponentData): DesktopComponent? {
        val component = componentFactory.createFromData(data) ?: return null
        attachComponentListener(component)
        canvasView.addComponent(component)

        layoutData = layoutData.withComponent(data.withZIndex(layoutData.nextZIndex()))
        saveLayout()

        layoutListener?.onComponentAdded(component)
        layoutListener?.onComponentsChanged()

        return component
    }

    /**
     * Remove a component from the canvas.
     */
    fun removeComponent(componentId: String) {
        canvasView.removeComponent(componentId)
        layoutData = layoutData.withoutComponent(componentId)
        saveLayout()

        layoutListener?.onComponentRemoved(componentId)
        layoutListener?.onComponentsChanged()
    }

    /**
     * Update a component's bounds.
     */
    fun updateComponentBounds(componentId: String, newBounds: ComponentBounds) {
        canvasView.updateComponentBounds(componentId, newBounds)

        val component = canvasView.getComponent(componentId) ?: return
        layoutData = layoutData.withUpdatedComponent(component.toComponentData())
        saveLayout()

        layoutListener?.onComponentsChanged()
    }

    /**
     * Update a component's settings.
     */
    fun updateComponentSettings(componentId: String) {
        val component = canvasView.getComponent(componentId) ?: return
        layoutData = layoutData.withUpdatedComponent(component.toComponentData())
        saveLayout()

        layoutListener?.onComponentsChanged()
    }

    /**
     * Get a component by ID.
     */
    fun getComponent(componentId: String): DesktopComponent? {
        return canvasView.getComponent(componentId)
    }

    /**
     * Get all components.
     */
    fun getAllComponents(): List<DesktopComponent> {
        return canvasView.getAllComponents()
    }

    /**
     * Find components by type.
     */
    fun getComponentsByType(type: ComponentType): List<DesktopComponent> {
        return canvasView.getAllComponents().filter { it.componentType == type }
    }

    /**
     * Check if any AppDrawer component exists.
     */
    fun hasAppDrawerComponent(): Boolean {
        return layoutData.hasAppDrawer()
    }

    /**
     * Enter edit mode.
     */
    fun enterEditMode() {
        canvasView.enterEditMode()
    }

    /**
     * Exit edit mode.
     */
    fun exitEditMode() {
        canvasView.exitEditMode()
        saveLayout()
    }

    /**
     * Check if in edit mode.
     */
    fun isEditMode(): Boolean {
        return canvasView.isEditModeActive()
    }

    /**
     * Select a component.
     */
    fun selectComponent(componentId: String?) {
        canvasView.selectComponent(componentId)
    }

    /**
     * Get the selected component.
     */
    fun getSelectedComponent(): DesktopComponent? {
        return canvasView.getSelectedComponent()
    }

    /**
     * Bring a component to the front.
     */
    fun bringToFront(componentId: String) {
        canvasView.bringToFront(componentId)
        saveLayout()
    }

    /**
     * Create the default layout with SearchBar and AppDrawer.
     */
    private fun createDefaultLayout(): DesktopLayoutData {
        val screenWidthDp = pxToDp(context.resources.displayMetrics.widthPixels)
        val screenHeightDp = pxToDp(context.resources.displayMetrics.heightPixels)

        val components = mutableListOf<ComponentData>()

        // Search bar at top
        if (preferencesManager.isSearchBarEnabled()) {
            components.add(
                ComponentData.create(
                    type = ComponentType.SEARCH_BAR,
                    bounds = componentFactory.getDefaultBounds(ComponentType.SEARCH_BAR, screenWidthDp, screenHeightDp),
                    zIndex = 1
                )
            )
        }

        // App drawer in center (shows all installed apps)
        val drawerBounds = componentFactory.getDefaultBounds(ComponentType.APP_DRAWER, screenWidthDp, screenHeightDp)
        components.add(
            ComponentData.create(
                type = ComponentType.APP_DRAWER,
                bounds = drawerBounds,
                zIndex = 0
            ).also { data ->
                // Migrate existing settings
                data.settings.set("columns", preferencesManager.getGridColumns())
                data.settings.set("showLabels", preferencesManager.getShowAppLabels())
                data.settings.set("iconSize", preferencesManager.getIconSize())
            }
        )

        return DesktopLayoutData(components = components, pageCount = 1, homePage = 0)
    }

    /**
     * Create a component from ComponentData and add it to the canvas.
     */
    private fun createAndAddComponent(data: ComponentData) {
        val component = componentFactory.createFromData(data) ?: return
        attachComponentListener(component)
        canvasView.addComponent(component)
    }

    private fun attachComponentListener(component: DesktopComponent) {
        (component as? BaseDesktopComponent)?.componentListener = componentListener
    }

    /**
     * Convert pixels to dp.
     */
    private fun pxToDp(px: Int): Float {
        return px / context.resources.displayMetrics.density
    }

    private fun getPageWidthDp(): Float {
        val widthPx = if (canvasView.width > 0) canvasView.width else context.resources.displayMetrics.widthPixels
        return pxToDp(widthPx)
    }

    private fun getCurrentPageOffsetX(pageWidthDp: Float): Float {
        return canvasView.getCurrentPage() * pageWidthDp
    }

    /**
     * Reset to default layout.
     */
    fun resetToDefaultLayout() {
        preferencesManager.clearDesktopLayout()
        loadLayout()
    }

    /**
     * Duplicate a component (for copy/paste).
     */
    fun duplicateComponent(componentId: String): DesktopComponent? {
        val original = canvasView.getComponent(componentId) ?: return null
        val originalData = original.toComponentData()
        val duplicatedData = originalData.duplicate()
        return addComponentFromData(duplicatedData)
    }
}
