package com.example.taglauncher.edit

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.example.taglauncher.ColorSettingUtils
import com.example.taglauncher.component.DesktopComponent
import com.example.taglauncher.persistence.SettingDefinition
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog for editing component settings based on its settings schema.
 */
class ComponentSettingsDialog(
    private val context: Context,
    private val component: DesktopComponent,
    private val onSettingsChanged: () -> Unit
) {
    private val settingsSchema = component.getSettingsSchema()
    private val pendingChanges = mutableMapOf<String, Any>()

    fun show() {
        if (settingsSchema.isEmpty()) {
            return
        }

        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8))
        }

        settingsSchema.forEach { definition ->
            container.addView(createSettingView(definition))
        }

        scrollView.addView(container)

        MaterialAlertDialogBuilder(context)
            .setTitle("${component.componentType.displayName} Settings")
            .setView(scrollView)
            .setPositiveButton("Apply") { _, _ ->
                applyChanges()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createSettingView(definition: SettingDefinition): View {
        return when (definition) {
            is SettingDefinition.IntRange -> createIntRangeView(definition)
            is SettingDefinition.Toggle -> createToggleView(definition)
            is SettingDefinition.Choice -> createChoiceView(definition)
            is SettingDefinition.Color -> createColorView(definition)
            is SettingDefinition.Text -> createTextView(definition)
        }
    }

    private fun createIntRangeView(definition: SettingDefinition.IntRange): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        val currentValue = component.settings.get(definition.key, definition.default)

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val label = TextView(context).apply {
            text = definition.label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueText = TextView(context).apply {
            text = currentValue.toString()
            textSize = 14f
            setTextColor(Color.GRAY)
        }

        headerLayout.addView(label)
        headerLayout.addView(valueText)

        val description = TextView(context).apply {
            text = definition.description
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(2), 0, dpToPx(8))
        }

        val seekBar = SeekBar(context).apply {
            max = (definition.max - definition.min) / definition.step
            progress = (currentValue - definition.min) / definition.step

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = definition.min + (progress * definition.step)
                    valueText.text = value.toString()
                    pendingChanges[definition.key] = value
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        container.addView(headerLayout)
        container.addView(description)
        container.addView(seekBar)

        return container
    }

    private fun createToggleView(definition: SettingDefinition.Toggle): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        val currentValue = component.settings.get(definition.key, definition.default)

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val label = TextView(context).apply {
            text = definition.label
            textSize = 16f
        }

        val description = TextView(context).apply {
            text = definition.description
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        textContainer.addView(label)
        textContainer.addView(description)

        val switch = SwitchCompat(context).apply {
            isChecked = currentValue
            setOnCheckedChangeListener { _, isChecked ->
                pendingChanges[definition.key] = isChecked
            }
        }

        container.addView(textContainer)
        container.addView(switch)

        return container
    }

    private fun createChoiceView(definition: SettingDefinition.Choice): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        val currentValue = component.settings.get(definition.key, definition.default)

        val label = TextView(context).apply {
            text = definition.label
            textSize = 16f
        }

        val description = TextView(context).apply {
            text = definition.description
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(2), 0, dpToPx(8))
        }

        val displayNames = definition.options.map { it.second }
        val values = definition.options.map { it.first }
        val currentIndex = values.indexOf(currentValue).coerceAtLeast(0)

        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, displayNames)
            setSelection(currentIndex)

            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    pendingChanges[definition.key] = values[position]
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        container.addView(label)
        container.addView(description)
        container.addView(spinner)

        return container
    }

    private fun createColorView(definition: SettingDefinition.Color): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        val currentValue = component.settings.get(definition.key, definition.default)
        val allowSystemColor = definition.key == "iconFrameBackgroundColor"

        val label = TextView(context).apply {
            text = definition.label
            textSize = 16f
        }

        val description = TextView(context).apply {
            text = definition.description
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(2), 0, dpToPx(8))
        }

        // Color presets
        val presetColors = listOf(
            Color.TRANSPARENT,
            Color.parseColor("#80000000"),
            Color.parseColor("#CC000000"),
            Color.BLACK,
            Color.WHITE,
            Color.parseColor("#E57373"),
            Color.parseColor("#64B5F6"),
            Color.parseColor("#81C784"),
            Color.parseColor("#FFB74D"),
            Color.parseColor("#BA68C8")
        )

        val colorContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val colorViews = mutableListOf<View>()

        fun updateSelection(selected: View) {
            colorViews.forEach { view ->
                view.alpha = if (view == selected) 1.0f else 0.4f
            }
        }

        if (allowSystemColor) {
            val systemView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
                text = "SYS"
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(6).toFloat()
                    setStroke(dpToPx(1), Color.GRAY)
                    setColor(ColorSettingUtils.resolveColor(context, ColorSettingUtils.COLOR_FOLLOW_SYSTEM))
                }
                alpha = if (currentValue == ColorSettingUtils.COLOR_FOLLOW_SYSTEM) 1.0f else 0.4f
                setOnClickListener {
                    pendingChanges[definition.key] = ColorSettingUtils.COLOR_FOLLOW_SYSTEM
                    updateSelection(this)
                }
            }
            colorViews.add(systemView)
            colorContainer.addView(systemView)
        }

        presetColors.forEach { color ->
            val colorView = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
                setBackgroundColor(color)

                // Add border for transparent colors
                if (color == Color.TRANSPARENT) {
                    setBackgroundResource(android.R.drawable.btn_default)
                }

                alpha = if (color == currentValue) 1.0f else 0.4f

                setOnClickListener {
                    pendingChanges[definition.key] = color
                    updateSelection(this)
                }
            }
            colorViews.add(colorView)
            colorContainer.addView(colorView)
        }

        container.addView(label)
        container.addView(description)
        container.addView(colorContainer)

        return container
    }

    private fun createTextView(definition: SettingDefinition.Text): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        val currentValue = component.settings.get(definition.key, definition.default)

        val label = TextView(context).apply {
            text = definition.label
            textSize = 16f
        }

        val description = TextView(context).apply {
            text = definition.description
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(2), 0, dpToPx(8))
        }

        val editText = EditText(context).apply {
            hint = definition.hint
            setText(currentValue)
            isSingleLine = true

            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    pendingChanges[definition.key] = s?.toString() ?: ""
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        container.addView(label)
        container.addView(description)
        container.addView(editText)

        return container
    }

    private fun applyChanges() {
        pendingChanges.forEach { (key, value) ->
            component.settings.set(key, value)
            component.onSettingsChanged(key, value)
        }
        onSettingsChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
