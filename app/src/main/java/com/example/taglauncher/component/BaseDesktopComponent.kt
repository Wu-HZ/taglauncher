package com.example.taglauncher.component

import android.content.Context
import android.view.View
import com.example.taglauncher.desktop.ComponentBounds
import com.example.taglauncher.persistence.ComponentData
import com.example.taglauncher.persistence.ComponentSettings
import com.example.taglauncher.persistence.SettingDefinition

/**
 * Abstract base class for desktop components providing common functionality.
 */
abstract class BaseDesktopComponent(
    protected val context: Context,
    override val componentId: String,
    override val componentType: ComponentType,
    initialBounds: ComponentBounds,
    initialSettings: ComponentSettings
) : DesktopComponent {

    override var bounds: ComponentBounds = initialBounds
        set(value) {
            val oldBounds = field
            field = value
            if (oldBounds != value) {
                onBoundsChanged(value)
            }
        }

    override var settings: ComponentSettings = initialSettings

    override var isInEditMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    onEnterEditMode()
                } else {
                    onExitEditMode()
                }
            }
        }

    private var cachedView: View? = null

    /**
     * Listener for component events.
     */
    var componentListener: ComponentListener? = null

    /**
     * Create the view for this component.
     * Subclasses must implement this to create their specific view.
     */
    protected abstract fun createView(): View

    /**
     * Called after the view is created for additional setup.
     */
    protected open fun onViewCreated(view: View) {
        // Default: no-op, subclasses can override
    }

    override fun getView(): View {
        return cachedView ?: createView().also {
            cachedView = it
            onViewCreated(it)
            applySettingsToView()
        }
    }

    /**
     * Get a setting value with type safety.
     */
    protected inline fun <reified T> getSetting(key: String, default: T): T {
        return settings.get(key, default)
    }

    /**
     * Set a setting value and notify listeners.
     */
    protected fun setSetting(key: String, value: Any) {
        settings.set(key, value)
        onSettingsChanged(key, value)
        componentListener?.onComponentSettingsChanged(this)
    }

    /**
     * Apply all settings to the view.
     * Subclasses should override to apply their specific settings.
     */
    protected open fun applySettingsToView() {
        // Default: apply each setting from schema
        getSettingsSchema().forEach { definition ->
            val value = when (definition) {
                is SettingDefinition.IntRange -> settings.get(definition.key, definition.default)
                is SettingDefinition.Toggle -> settings.get(definition.key, definition.default)
                is SettingDefinition.Choice -> settings.get(definition.key, definition.default)
                is SettingDefinition.Color -> settings.get(definition.key, definition.default)
                is SettingDefinition.Text -> settings.get(definition.key, definition.default)
            }
            onSettingsChanged(definition.key, value)
        }
    }

    override fun applyDefaultSettings() {
        getSettingsSchema().forEach { definition ->
            val defaultValue = when (definition) {
                is SettingDefinition.IntRange -> definition.default
                is SettingDefinition.Toggle -> definition.default
                is SettingDefinition.Choice -> definition.default
                is SettingDefinition.Color -> definition.default
                is SettingDefinition.Text -> definition.default
            }
            if (!settings.has(definition.key)) {
                settings.set(definition.key, defaultValue)
            }
        }
    }

    override fun onBoundsChanged(newBounds: ComponentBounds) {
        // Subclasses can override for specific behavior
        // The view's layout params will be updated by DesktopCanvasView
    }

    override fun onSettingsChanged(key: String, value: Any) {
        // Subclasses should override to apply specific settings
    }

    override fun onEnterEditMode() {
        // Default: disable clickable on the view
        cachedView?.let { view ->
            disableInteraction(view)
        }
    }

    override fun onExitEditMode() {
        // Default: re-enable clickable on the view
        cachedView?.let { view ->
            enableInteraction(view)
        }
    }

    /**
     * Disable user interaction for edit mode.
     * Subclasses should override to handle specific views (e.g., RecyclerView scrolling).
     */
    protected open fun disableInteraction(view: View) {
        // Default implementation - subclasses should override for specifics
    }

    /**
     * Enable user interaction after edit mode.
     */
    protected open fun enableInteraction(view: View) {
        // Default implementation - subclasses should override for specifics
    }

    override fun toComponentData(): ComponentData {
        return ComponentData(
            instanceId = componentId,
            componentType = componentType.typeName,
            bounds = bounds,
            settings = settings,
            zIndex = 0  // Will be updated by DesktopLayoutManager
        )
    }

    override fun destroy() {
        cachedView = null
        componentListener = null
    }

    /**
     * Convert dp to pixels.
     */
    protected fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Convert pixels to dp.
     */
    protected fun pxToDp(px: Int): Float {
        return px / context.resources.displayMetrics.density
    }

    companion object {
        /**
         * Generate a new unique component ID.
         */
        fun generateId(type: ComponentType): String {
            return "${type.typeName}_${System.currentTimeMillis()}"
        }
    }
}
