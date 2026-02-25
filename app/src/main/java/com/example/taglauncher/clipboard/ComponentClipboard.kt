package com.example.taglauncher.clipboard

import com.example.taglauncher.persistence.ComponentData

/**
 * Singleton clipboard for copy/cut/paste operations on components.
 */
object ComponentClipboard {

    private var clipboardData: ComponentData? = null
    private var isCut: Boolean = false

    /**
     * Copy a component to the clipboard.
     */
    fun copy(componentData: ComponentData) {
        clipboardData = componentData.duplicate()
        isCut = false
    }

    /**
     * Cut a component to the clipboard.
     */
    fun cut(componentData: ComponentData) {
        clipboardData = componentData.duplicate()
        isCut = true
    }

    /**
     * Check if clipboard has data.
     */
    fun hasData(): Boolean = clipboardData != null

    /**
     * Check if the clipboard operation was a cut.
     */
    fun isCutOperation(): Boolean = isCut

    /**
     * Get the clipboard data (for paste operation).
     * Returns a new duplicate to allow multiple pastes.
     */
    fun getData(): ComponentData? {
        return clipboardData?.duplicate()
    }

    /**
     * Get the clipboard data with position offset for paste.
     */
    fun getDataWithOffset(offsetDp: Float = 20f): ComponentData? {
        val data = clipboardData?.duplicate() ?: return null

        // Offset the position so pasted component doesn't overlap exactly
        val newBounds = data.bounds.copy(
            x = data.bounds.x + offsetDp,
            y = data.bounds.y + offsetDp
        )

        return data.copy(bounds = newBounds)
    }

    /**
     * Clear the clipboard.
     */
    fun clear() {
        clipboardData = null
        isCut = false
    }

    /**
     * Get the component type in clipboard (for UI display).
     */
    fun getComponentType(): String? {
        return clipboardData?.componentType
    }
}
