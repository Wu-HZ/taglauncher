package com.example.taglauncher.persistence

import com.example.taglauncher.component.ComponentType
import com.example.taglauncher.desktop.ComponentBounds
import org.json.JSONObject

/**
 * Represents a saved component template that can be reused.
 * Templates store the settings and default bounds for creating new component instances.
 */
data class ComponentTemplate(
    val templateId: String,
    val name: String,
    val componentType: String,
    val settings: ComponentSettings,
    val defaultBounds: ComponentBounds,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("templateId", templateId)
            put("name", name)
            put("componentType", componentType)
            put("settings", settings.toJson())
            put("defaultBounds", defaultBounds.toJson())
            put("createdAt", createdAt)
        }
    }

    /**
     * Create ComponentData from this template with a new instance ID.
     */
    fun createComponentData(
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        zIndex: Int = 0
    ): ComponentData {
        val newId = "component_${System.currentTimeMillis()}"
        return ComponentData(
            instanceId = newId,
            componentType = componentType,
            bounds = defaultBounds.copy(
                x = defaultBounds.x + offsetX,
                y = defaultBounds.y + offsetY
            ),
            settings = settings.copyWithNewId(newId),
            zIndex = zIndex
        )
    }

    companion object {
        fun fromJson(json: JSONObject): ComponentTemplate {
            return ComponentTemplate(
                templateId = json.getString("templateId"),
                name = json.getString("name"),
                componentType = json.getString("componentType"),
                settings = ComponentSettings.fromJson(json.getJSONObject("settings")),
                defaultBounds = ComponentBounds.fromJson(json.getJSONObject("defaultBounds")),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }

        /**
         * Create a template from a component.
         */
        fun fromComponent(
            name: String,
            componentType: ComponentType,
            settings: ComponentSettings,
            bounds: ComponentBounds
        ): ComponentTemplate {
            val templateId = "template_${System.currentTimeMillis()}"
            return ComponentTemplate(
                templateId = templateId,
                name = name,
                componentType = componentType.typeName,
                settings = settings.copyWithNewId(templateId),
                defaultBounds = bounds
            )
        }
    }
}
