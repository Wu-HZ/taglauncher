package com.example.taglauncher.persistence

import org.json.JSONObject

/**
 * Represents per-instance settings for a component.
 * Each component instance has its own independent settings.
 */
data class ComponentSettings(
    val instanceId: String,
    private val settings: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Get a setting value with a default fallback.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T): T {
        return settings[key] as? T ?: default
    }

    /**
     * Set a setting value.
     */
    fun set(key: String, value: Any) {
        settings[key] = value
    }

    /**
     * Check if a setting exists.
     */
    fun has(key: String): Boolean = settings.containsKey(key)

    /**
     * Remove a setting.
     */
    fun remove(key: String) {
        settings.remove(key)
    }

    /**
     * Get all setting keys.
     */
    fun keys(): Set<String> = settings.keys.toSet()

    /**
     * Get a copy of all settings.
     */
    fun getAll(): Map<String, Any> = settings.toMap()

    /**
     * Create a deep copy with a new instance ID.
     */
    fun copyWithNewId(newInstanceId: String): ComponentSettings {
        return ComponentSettings(
            instanceId = newInstanceId,
            settings = settings.toMutableMap()
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("instanceId", instanceId)
            val settingsJson = JSONObject()
            settings.forEach { (key, value) ->
                when (value) {
                    is Boolean -> settingsJson.put(key, value)
                    is Int -> settingsJson.put(key, value)
                    is Long -> settingsJson.put(key, value)
                    is Float -> settingsJson.put(key, value.toDouble())
                    is Double -> settingsJson.put(key, value)
                    is String -> settingsJson.put(key, value)
                    else -> settingsJson.put(key, value.toString())
                }
            }
            put("settings", settingsJson)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ComponentSettings {
            val instanceId = json.getString("instanceId")
            val settingsJson = json.optJSONObject("settings") ?: JSONObject()
            val settings = mutableMapOf<String, Any>()

            settingsJson.keys().forEach { key ->
                val value = settingsJson.get(key)
                settings[key] = value
            }

            return ComponentSettings(instanceId, settings)
        }

        /**
         * Create new settings with a generated instance ID.
         */
        fun create(): ComponentSettings {
            return ComponentSettings(
                instanceId = "component_${System.currentTimeMillis()}"
            )
        }
    }
}

/**
 * Defines a setting that can be configured for a component.
 */
sealed class SettingDefinition(
    val key: String,
    val label: String,
    val description: String = ""
) {
    /**
     * Integer range setting (displayed as slider).
     */
    class IntRange(
        key: String,
        label: String,
        description: String = "",
        val default: Int,
        val min: Int,
        val max: Int,
        val step: Int = 1
    ) : SettingDefinition(key, label, description)

    /**
     * Boolean toggle setting (displayed as switch).
     */
    class Toggle(
        key: String,
        label: String,
        description: String = "",
        val default: Boolean
    ) : SettingDefinition(key, label, description)

    /**
     * Single choice setting (displayed as dropdown).
     */
    class Choice(
        key: String,
        label: String,
        description: String = "",
        val default: String,
        val options: List<Pair<String, String>> // value to display label
    ) : SettingDefinition(key, label, description)

    /**
     * Color setting (displayed as color picker).
     */
    class Color(
        key: String,
        label: String,
        description: String = "",
        val default: Int
    ) : SettingDefinition(key, label, description)

    /**
     * Text setting (displayed as text input).
     */
    class Text(
        key: String,
        label: String,
        description: String = "",
        val default: String,
        val hint: String = ""
    ) : SettingDefinition(key, label, description)
}
