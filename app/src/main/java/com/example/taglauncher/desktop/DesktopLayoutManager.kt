package com.example.taglauncher.desktop

import android.content.Context
import com.example.taglauncher.PreferencesManager
import com.example.taglauncher.component.ComponentFactory
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

    /**
     * Load the desktop layout from persistence.
     * If no layout exists, creates the default layout.
     */
    fun loadLayout() {
        layoutData = preferencesManager.getDesktopLayout() ?: createDefaultLayout()

        // Clear existing components
        canvasView.clearAll()

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
        val screenWidthDp = pxToDp(canvasView.width)
        val screenHeightDp = pxToDp(canvasView.height)

        val componentBounds = bounds ?: componentFactory.getDefaultBounds(type, screenWidthDp, screenHeightDp)
        val component = componentFactory.createNew(type, componentBounds)

        canvasView.addComponent(component)

        // Update layout data
        layoutData = layoutData.withComponent(component.toComponentData().withZIndex(layoutData.nextZIndex()))
        saveLayout()

        layoutListener?.onComponentAdded(component)
        layoutListener?.onComponentsChanged()

        return component
    }

    /**
     * Add a component from ComponentData (e.g., from paste operation).
     */
    fun addComponentFromData(data: ComponentData): DesktopComponent? {
        val component = componentFactory.createFromData(data) ?: return null
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

        return DesktopLayoutData(components = components)
    }

    /**
     * Create a component from ComponentData and add it to the canvas.
     */
    private fun createAndAddComponent(data: ComponentData) {
        val component = componentFactory.createFromData(data) ?: return
        canvasView.addComponent(component)
    }

    /**
     * Convert pixels to dp.
     */
    private fun pxToDp(px: Int): Float {
        return px / context.resources.displayMetrics.density
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
