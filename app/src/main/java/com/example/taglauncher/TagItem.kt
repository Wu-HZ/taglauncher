package com.example.taglauncher

import android.graphics.Color

data class TagItem(
    val id: String,
    val label: String,
    val color: Int = Color.WHITE
)
