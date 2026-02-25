package com.example.taglauncher

sealed class SettingsItem {
    data class Header(
        val title: String
    ) : SettingsItem()

    data class Toggle(
        val id: String,
        val title: String,
        val summary: String,
        val isChecked: Boolean,
        val icon: Int? = null
    ) : SettingsItem()

    data class Choice(
        val id: String,
        val title: String,
        val summary: String,
        val icon: Int? = null
    ) : SettingsItem()

    data class Navigation(
        val id: String,
        val title: String,
        val summary: String,
        val icon: Int? = null
    ) : SettingsItem()

    data class Info(
        val id: String,
        val title: String,
        val summary: String,
        val icon: Int? = null
    ) : SettingsItem()
}
