package com.example.taglauncher.component

/**
 * Enum defining all available component types in the launcher.
 */
enum class ComponentType(val typeName: String, val displayName: String) {
    APP_DRAWER("app_drawer", "App Drawer"),
    APP_GRID("app_grid", "App Grid"),
    SEARCH_BAR("search_bar", "Search Bar");

    companion object {
        fun fromTypeName(typeName: String): ComponentType? {
            return entries.find { it.typeName == typeName }
        }
    }
}
