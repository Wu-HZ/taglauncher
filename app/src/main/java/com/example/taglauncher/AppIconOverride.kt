package com.example.taglauncher

data class AppIconOverride(
    val iconUri: String? = null,
    val backgroundColor: Int? = null,
    val backgroundImageUri: String? = null,
    val scalePercent: Int = 100
) {
    fun isEmpty(): Boolean {
        return iconUri == null &&
            backgroundColor == null &&
            backgroundImageUri == null &&
            scalePercent == 100
    }
}
