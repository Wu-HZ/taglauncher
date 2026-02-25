package com.example.taglauncher.component.impl

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.taglauncher.component.BaseDesktopComponent
import com.example.taglauncher.component.ComponentType
import com.example.taglauncher.desktop.ComponentBounds
import com.example.taglauncher.persistence.ComponentSettings
import com.example.taglauncher.persistence.SettingDefinition

/**
 * Search Bar component for filtering apps.
 */
class SearchBarComponent(
    context: Context,
    componentId: String,
    bounds: ComponentBounds,
    settings: ComponentSettings
) : BaseDesktopComponent(context, componentId, ComponentType.SEARCH_BAR, bounds, settings) {

    private lateinit var container: LinearLayout
    private lateinit var searchEditText: EditText
    private lateinit var searchIcon: ImageView

    /**
     * Callback when search text changes.
     */
    var onSearchTextChanged: ((String) -> Unit)? = null

    /**
     * Callback when search is submitted (keyboard action).
     */
    var onSearchSubmitted: ((String) -> Unit)? = null

    override fun createView(): View {
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
        }

        // Search icon
        searchIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.parseColor("#AAAAAA"))
            contentDescription = "Search"
        }

        // Search EditText
        searchEditText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginStart = dpToPx(12f)
            }
            background = null
            hint = getSetting("hint", "Search apps...")
            setHintTextColor(Color.parseColor("#AAAAAA"))
            setTextColor(Color.WHITE)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            isSingleLine = true
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        // Setup text change listener
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onSearchTextChanged?.invoke(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup keyboard action
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                onSearchSubmitted?.invoke(searchEditText.text.toString())
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // Clear focus when focus lost
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideKeyboard()
            }
        }

        container.addView(searchIcon)
        container.addView(searchEditText)

        return container
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        applyVisualSettings()
    }

    /**
     * Clear the search text.
     */
    fun clearSearch() {
        searchEditText.text.clear()
    }

    /**
     * Get current search text.
     */
    fun getSearchText(): String {
        return searchEditText.text.toString()
    }

    /**
     * Request focus on the search input.
     */
    fun requestSearchFocus() {
        searchEditText.requestFocus()
        showKeyboard()
    }

    /**
     * Clear focus from search input.
     */
    fun clearSearchFocus() {
        searchEditText.clearFocus()
        hideKeyboard()
    }

    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        searchEditText.clearFocus()
    }

    override fun getSettingsSchema(): List<SettingDefinition> {
        return listOf(
            SettingDefinition.Text(
                key = "hint",
                label = "Hint Text",
                description = "Placeholder text when empty",
                default = "Search apps...",
                hint = "Enter hint text"
            ),
            SettingDefinition.Color(
                key = "backgroundColor",
                label = "Background Color",
                description = "Background color of the search bar",
                default = Color.parseColor("#80000000")
            ),
            SettingDefinition.IntRange(
                key = "cornerRadius",
                label = "Corner Radius",
                description = "Corner radius in dp",
                default = 24,
                min = 0,
                max = 32
            ),
            SettingDefinition.Color(
                key = "textColor",
                label = "Text Color",
                description = "Color of the search text",
                default = Color.WHITE
            ),
            SettingDefinition.Color(
                key = "hintColor",
                label = "Hint Color",
                description = "Color of the hint text",
                default = Color.parseColor("#AAAAAA")
            )
        )
    }

    override fun onSettingsChanged(key: String, value: Any) {
        when (key) {
            "hint" -> {
                val hint = (value as? String) ?: "Search apps..."
                searchEditText.hint = hint
            }
            "textColor" -> {
                val color = (value as? Int) ?: Color.WHITE
                searchEditText.setTextColor(color)
            }
            "hintColor" -> {
                val color = (value as? Int) ?: Color.parseColor("#AAAAAA")
                searchEditText.setHintTextColor(color)
                searchIcon.setColorFilter(color)
            }
            "backgroundColor", "cornerRadius" -> {
                applyVisualSettings()
            }
        }
    }

    private fun applyVisualSettings() {
        val backgroundColor = getSetting("backgroundColor", Color.parseColor("#80000000"))
        val cornerRadius = getSetting("cornerRadius", 24)

        val drawable = GradientDrawable().apply {
            setColor(backgroundColor)
            this.cornerRadius = dpToPx(cornerRadius.toFloat()).toFloat()
        }
        container.background = drawable
        container.clipToOutline = true
    }

    override fun disableInteraction(view: View) {
        searchEditText.isEnabled = false
        searchEditText.isFocusable = false
        hideKeyboard()
    }

    override fun enableInteraction(view: View) {
        searchEditText.isEnabled = true
        searchEditText.isFocusable = true
        searchEditText.isFocusableInTouchMode = true
    }
}
