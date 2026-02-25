package com.example.taglauncher.component

import android.content.Context
import com.example.taglauncher.PreferencesManager
import com.example.taglauncher.component.impl.AppDrawerComponent
import com.example.taglauncher.component.impl.AppGridComponent
import com.example.taglauncher.component.impl.SearchBarComponent
import com.example.taglauncher.desktop.ComponentBounds
import com.example.taglauncher.persistence.ComponentData
import com.example.taglauncher.persistence.ComponentSettings

/**
 * Factory for creating component instances.
 * This allows decoupling component creation from the rest of the system.
 */
class ComponentFactory(
    private val context: Context,
    private val preferencesManager: PreferencesManager? = null
) {

    /**
     * Create a component from persisted ComponentData.
     */
    fun createFromData(data: ComponentData): DesktopComponent? {
        val type = ComponentType.fromTypeName(data.componentType) ?: return null
        return createComponent(
            type = type,
            componentId = data.instanceId,
            bounds = data.bounds,
            settings = data.settings
        )
    }

    /**
     * Create a new component with default settings.
     */
    fun createNew(
        type: ComponentType,
        bounds: ComponentBounds
    ): DesktopComponent {
        val componentId = BaseDesktopComponent.generateId(type)
        val settings = ComponentSettings(componentId)

        val component = createComponent(type, componentId, bounds, settings)
        component.applyDefaultSettings()

        return component
    }

    /**
     * Create a component with specific parameters.
     */
    private fun createComponent(
        type: ComponentType,
        componentId: String,
        bounds: ComponentBounds,
        settings: ComponentSettings
    ): DesktopComponent {
        return when (type) {
            ComponentType.APP_DRAWER -> createAppDrawerComponent(componentId, bounds, settings)
            ComponentType.APP_GRID -> createAppGridComponent(componentId, bounds, settings)
            ComponentType.SEARCH_BAR -> createSearchBarComponent(componentId, bounds, settings)
        }
    }

    /**
     * Create an AppDrawerComponent (shows all installed apps).
     */
    private fun createAppDrawerComponent(
        componentId: String,
        bounds: ComponentBounds,
        settings: ComponentSettings
    ): DesktopComponent {
        val prefs = preferencesManager ?: PreferencesManager(context)
        return AppDrawerComponent(context, componentId, bounds, settings, prefs)
    }

    /**
     * Create an AppGridComponent (customized app grid).
     */
    private fun createAppGridComponent(
        componentId: String,
        bounds: ComponentBounds,
        settings: ComponentSettings
    ): DesktopComponent {
        val prefs = preferencesManager ?: PreferencesManager(context)
        return AppGridComponent(context, componentId, bounds, settings, prefs)
    }

    /**
     * Create a SearchBarComponent.
     */
    private fun createSearchBarComponent(
        componentId: String,
        bounds: ComponentBounds,
        settings: ComponentSettings
    ): DesktopComponent {
        return SearchBarComponent(context, componentId, bounds, settings)
    }

    /**
     * Get default bounds for a component type based on screen dimensions.
     */
    fun getDefaultBounds(type: ComponentType, screenWidthDp: Float, screenHeightDp: Float): ComponentBounds {
        return when (type) {
            ComponentType.SEARCH_BAR -> ComponentBounds(
                x = 16f,
                y = 8f,
                width = screenWidthDp - 32f,
                height = 48f,
                minWidth = 120f,
                minHeight = 40f,
                maxHeight = 64f
            )
            ComponentType.APP_DRAWER -> ComponentBounds(
                x = 0f,
                y = 64f,
                width = screenWidthDp,
                height = screenHeightDp - 100f,
                minWidth = 150f,
                minHeight = 150f
            )
            ComponentType.APP_GRID -> ComponentBounds(
                x = 16f,
                y = screenHeightDp - 200f,
                width = screenWidthDp - 32f,
                height = 150f,
                minWidth = 100f,
                minHeight = 80f
            )
        }
    }
}
