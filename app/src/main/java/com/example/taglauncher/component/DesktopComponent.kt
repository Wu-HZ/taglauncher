package com.example.taglauncher.component

import android.view.View
import com.example.taglauncher.desktop.ComponentBounds
import com.example.taglauncher.persistence.ComponentData
import com.example.taglauncher.persistence.ComponentSettings
import com.example.taglauncher.persistence.SettingDefinition

/**
 * Interface defining the contract for all desktop components.
 * Components are self-contained UI elements that can be placed,
 * sized, and configured independently on the desktop canvas.
 */
interface DesktopComponent {
    /**
     * Unique identifier for this component instance.
     */
    val componentId: String

    /**
     * The type of this component.
     */
    val componentType: ComponentType

    /**
     * Current bounds (position and size) of this component.
     */
    var bounds: ComponentBounds

    /**
     * Per-instance settings for this component.
     */
    var settings: ComponentSettings

    /**
     * Whether this component is currently in edit mode.
     */
    var isInEditMode: Boolean

    /**
     * Get the View that represents this component.
     * The view should be created lazily and cached.
     */
    fun getView(): View

    /**
     * Called when the component's bounds change (position or size).
     * The component should update its view layout accordingly.
     */
    fun onBoundsChanged(newBounds: ComponentBounds)

    /**
     * Called when a setting value changes.
     * The component should update its view to reflect the new setting.
     */
    fun onSettingsChanged(key: String, value: Any)

    /**
     * Get the schema of configurable settings for this component type.
     */
    fun getSettingsSchema(): List<SettingDefinition>

    /**
     * Apply default settings to this component.
     */
    fun applyDefaultSettings()

    /**
     * Called when entering edit mode.
     * Components should disable interactive elements like scrolling.
     */
    fun onEnterEditMode()

    /**
     * Called when exiting edit mode.
     * Components should re-enable interactive elements.
     */
    fun onExitEditMode()

    /**
     * Serialize this component to ComponentData for persistence.
     */
    fun toComponentData(): ComponentData

    /**
     * Release any resources held by this component.
     */
    fun destroy()
}

/**
 * Listener interface for component events.
 */
interface ComponentListener {
    /**
     * Called when the component requests a bounds update.
     */
    fun onComponentBoundsChangeRequested(component: DesktopComponent, newBounds: ComponentBounds)

    /**
     * Called when the component's settings have changed.
     */
    fun onComponentSettingsChanged(component: DesktopComponent)

    /**
     * Called when the component should be brought to front.
     */
    fun onComponentBringToFront(component: DesktopComponent)
}
