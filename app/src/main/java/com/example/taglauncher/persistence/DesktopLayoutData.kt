package com.example.taglauncher.persistence

import com.example.taglauncher.component.ComponentType
import com.example.taglauncher.desktop.ComponentBounds
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents the complete desktop layout configuration.
 */
data class DesktopLayoutData(
    val version: Int = CURRENT_VERSION,
    val components: List<ComponentData>,
    val pageCount: Int = 1,
    val homePage: Int = 0,
    val lastModified: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("lastModified", lastModified)
            put("pageCount", pageCount)
            put("homePage", homePage)
            val componentsArray = JSONArray()
            components.forEach { component ->
                componentsArray.put(component.toJson())
            }
            put("components", componentsArray)
        }
    }

    /**
     * Find a component by its instance ID.
     */
    fun findComponent(instanceId: String): ComponentData? {
        return components.find { it.instanceId == instanceId }
    }

    /**
     * Find all components of a specific type.
     */
    fun findComponentsByType(type: ComponentType): List<ComponentData> {
        return components.filter { it.componentType == type.typeName }
    }

    /**
     * Check if any AppDrawer components exist (for Tag FAB visibility).
     */
    fun hasAppDrawer(): Boolean {
        return components.any { it.componentType == ComponentType.APP_DRAWER.typeName }
    }

    /**
     * Get the next available z-index.
     */
    fun nextZIndex(): Int {
        return (components.maxOfOrNull { it.zIndex } ?: -1) + 1
    }

    /**
     * Create a copy with an added component.
     */
    fun withComponent(component: ComponentData): DesktopLayoutData {
        return copy(
            components = components + component,
            lastModified = System.currentTimeMillis()
        )
    }

    /**
     * Create a copy with a removed component.
     */
    fun withoutComponent(instanceId: String): DesktopLayoutData {
        return copy(
            components = components.filter { it.instanceId != instanceId },
            lastModified = System.currentTimeMillis()
        )
    }

    /**
     * Create a copy with an updated component.
     */
    fun withUpdatedComponent(updatedComponent: ComponentData): DesktopLayoutData {
        return copy(
            components = components.map {
                if (it.instanceId == updatedComponent.instanceId) updatedComponent else it
            },
            lastModified = System.currentTimeMillis()
        )
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun fromJson(json: JSONObject): DesktopLayoutData {
            val version = json.optInt("version", CURRENT_VERSION)
            val lastModified = json.optLong("lastModified", System.currentTimeMillis())
            val pageCount = json.optInt("pageCount", 1).coerceAtLeast(1)
            val homePage = json.optInt("homePage", 0).coerceAtLeast(0)
            val componentsArray = json.optJSONArray("components") ?: JSONArray()

            val components = mutableListOf<ComponentData>()
            for (i in 0 until componentsArray.length()) {
                try {
                    components.add(ComponentData.fromJson(componentsArray.getJSONObject(i)))
                } catch (e: Exception) {
                    // Skip invalid components
                }
            }

            return DesktopLayoutData(
                version = version,
                components = components,
                pageCount = pageCount,
                homePage = homePage.coerceAtMost(pageCount - 1),
                lastModified = lastModified
            )
        }

        /**
         * Create an empty layout.
         */
        fun empty(): DesktopLayoutData {
            return DesktopLayoutData(components = emptyList(), pageCount = 1, homePage = 0)
        }
    }
}

/**
 * Represents a single component's data for persistence.
 */
data class ComponentData(
    val instanceId: String,
    val componentType: String,
    val bounds: ComponentBounds,
    val settings: ComponentSettings,
    val zIndex: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("instanceId", instanceId)
            put("componentType", componentType)
            put("bounds", bounds.toJson())
            put("settings", settings.toJson())
            put("zIndex", zIndex)
        }
    }

    /**
     * Create a copy with updated bounds.
     */
    fun withBounds(newBounds: ComponentBounds): ComponentData {
        return copy(bounds = newBounds)
    }

    /**
     * Create a copy with updated settings.
     */
    fun withSettings(newSettings: ComponentSettings): ComponentData {
        return copy(settings = newSettings)
    }

    /**
     * Create a copy with updated z-index.
     */
    fun withZIndex(newZIndex: Int): ComponentData {
        return copy(zIndex = newZIndex)
    }

    /**
     * Create a deep copy with a new instance ID (for paste operations).
     */
    fun duplicate(): ComponentData {
        val newId = "component_${System.currentTimeMillis()}"
        return copy(
            instanceId = newId,
            settings = settings.copyWithNewId(newId),
            bounds = bounds.copy(
                x = bounds.x + 20f,  // Offset to show it's a copy
                y = bounds.y + 20f
            )
        )
    }

    companion object {
        fun fromJson(json: JSONObject): ComponentData {
            return ComponentData(
                instanceId = json.getString("instanceId"),
                componentType = json.getString("componentType"),
                bounds = ComponentBounds.fromJson(json.getJSONObject("bounds")),
                settings = ComponentSettings.fromJson(json.getJSONObject("settings")),
                zIndex = json.optInt("zIndex", 0)
            )
        }

        /**
         * Create a new component data instance.
         */
        fun create(
            type: ComponentType,
            bounds: ComponentBounds,
            zIndex: Int = 0
        ): ComponentData {
            val instanceId = "${type.typeName}_${System.currentTimeMillis()}"
            return ComponentData(
                instanceId = instanceId,
                componentType = type.typeName,
                bounds = bounds,
                settings = ComponentSettings(instanceId),
                zIndex = zIndex
            )
        }
    }
}
