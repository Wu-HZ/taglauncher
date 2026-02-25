package com.example.taglauncher.desktop

import org.json.JSONObject

/**
 * Represents the position and size of a component on the desktop canvas.
 * All values are in dp (density-independent pixels).
 */
data class ComponentBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val minWidth: Float = 100f,
    val minHeight: Float = 80f,
    val maxWidth: Float = Float.MAX_VALUE,
    val maxHeight: Float = Float.MAX_VALUE
) {
    /**
     * Create a copy with updated position.
     */
    fun withPosition(newX: Float, newY: Float): ComponentBounds {
        return copy(x = newX, y = newY)
    }

    /**
     * Create a copy with updated size, respecting min/max constraints.
     */
    fun withSize(newWidth: Float, newHeight: Float): ComponentBounds {
        return copy(
            width = newWidth.coerceIn(minWidth, maxWidth),
            height = newHeight.coerceIn(minHeight, maxHeight)
        )
    }

    /**
     * Check if a point (in dp) is within this bounds.
     */
    fun contains(pointX: Float, pointY: Float): Boolean {
        return pointX >= x && pointX <= x + width &&
               pointY >= y && pointY <= y + height
    }

    /**
     * Get the center X coordinate.
     */
    val centerX: Float get() = x + width / 2

    /**
     * Get the center Y coordinate.
     */
    val centerY: Float get() = y + height / 2

    /**
     * Get the right edge X coordinate.
     */
    val right: Float get() = x + width

    /**
     * Get the bottom edge Y coordinate.
     */
    val bottom: Float get() = y + height

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("width", width.toDouble())
            put("height", height.toDouble())
            put("minWidth", minWidth.toDouble())
            put("minHeight", minHeight.toDouble())
            put("maxWidth", if (maxWidth == Float.MAX_VALUE) -1.0 else maxWidth.toDouble())
            put("maxHeight", if (maxHeight == Float.MAX_VALUE) -1.0 else maxHeight.toDouble())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ComponentBounds {
            val maxW = json.optDouble("maxWidth", -1.0)
            val maxH = json.optDouble("maxHeight", -1.0)
            return ComponentBounds(
                x = json.getDouble("x").toFloat(),
                y = json.getDouble("y").toFloat(),
                width = json.getDouble("width").toFloat(),
                height = json.getDouble("height").toFloat(),
                minWidth = json.optDouble("minWidth", 100.0).toFloat(),
                minHeight = json.optDouble("minHeight", 80.0).toFloat(),
                maxWidth = if (maxW < 0) Float.MAX_VALUE else maxW.toFloat(),
                maxHeight = if (maxH < 0) Float.MAX_VALUE else maxH.toFloat()
            )
        }

        /**
         * Create default bounds for a component type.
         */
        fun defaultFor(screenWidthDp: Float, screenHeightDp: Float): ComponentBounds {
            return ComponentBounds(
                x = 16f,
                y = 16f,
                width = screenWidthDp - 32f,
                height = 200f
            )
        }
    }
}
