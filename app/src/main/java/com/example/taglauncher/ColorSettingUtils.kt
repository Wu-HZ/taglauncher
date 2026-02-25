package com.example.taglauncher

import android.content.Context
import android.graphics.Color
import android.os.Build
import com.google.android.material.color.MaterialColors

object ColorSettingUtils {
    const val COLOR_FOLLOW_SYSTEM = 0x7F010101

    fun resolveColor(context: Context, value: Int): Int {
        return if (value == COLOR_FOLLOW_SYSTEM) {
            getMonetColor(context)
                ?: MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorPrimary,
                    Color.GRAY
                )
        } else {
            value
        }
    }

    private fun getMonetColor(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }
        val resId = context.resources.getIdentifier("system_accent1_200", "color", "android")
        if (resId == 0) {
            return null
        }
        return context.getColor(resId)
    }
}
