package com.example.taglauncher.component.impl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taglauncher.ColorSettingUtils
import com.example.taglauncher.AppInfo
import com.example.taglauncher.AppIconOverride
import com.example.taglauncher.MainActivity
import com.example.taglauncher.PreferencesManager
import com.example.taglauncher.component.BaseDesktopComponent
import com.example.taglauncher.component.ComponentType
import com.example.taglauncher.desktop.ComponentBounds
import com.example.taglauncher.persistence.ComponentSettings
import com.example.taglauncher.persistence.SettingDefinition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Configuration for a single grid cell with gesture actions.
 */
data class GridCellConfig(
    var mainApp: String = "",        // Tap action
    var swipeUpApp: String = "",     // Swipe up action
    var swipeDownApp: String = "",   // Swipe down action
    var swipeLeftApp: String = "",   // Swipe left action
    var swipeRightApp: String = ""   // Swipe right action
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("main", mainApp)
            put("up", swipeUpApp)
            put("down", swipeDownApp)
            put("left", swipeLeftApp)
            put("right", swipeRightApp)
        }
    }

    fun hasAnyApp(): Boolean {
        return mainApp.isNotEmpty() || swipeUpApp.isNotEmpty() ||
               swipeDownApp.isNotEmpty() || swipeLeftApp.isNotEmpty() ||
               swipeRightApp.isNotEmpty()
    }

    fun hasGestureApps(): Boolean {
        return swipeUpApp.isNotEmpty() || swipeDownApp.isNotEmpty() ||
               swipeLeftApp.isNotEmpty() || swipeRightApp.isNotEmpty()
    }

    companion object {
        fun fromJson(json: JSONObject): GridCellConfig {
            return GridCellConfig(
                mainApp = json.optString("main", ""),
                swipeUpApp = json.optString("up", ""),
                swipeDownApp = json.optString("down", ""),
                swipeLeftApp = json.optString("left", ""),
                swipeRightApp = json.optString("right", "")
            )
        }
    }
}

/**
 * App Grid component that displays a customizable grid of user-selected apps.
 * Each cell supports gesture actions: tap, swipe up/down/left/right.
 * Empty cells show a "+" placeholder. In edit mode, clicking a cell allows
 * configuring apps for each gesture.
 */
class AppGridComponent(
    context: Context,
    componentId: String,
    bounds: ComponentBounds,
    settings: ComponentSettings,
    private val preferencesManager: PreferencesManager
) : BaseDesktopComponent(context, componentId, ComponentType.APP_GRID, bounds, settings) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var gridAdapter: CustomGridAdapter
    private var containerView: View? = null

    // Grid data: list of cell configurations
    private var gridCells: MutableList<GridCellConfig> = mutableListOf()
    private var allAppsProvider: (() -> List<AppInfo>)? = null

    // Callback for launching app
    var onAppClick: ((AppInfo) -> Unit)? = null

    /**
     * Set the provider for all installed apps (for the app picker dialog).
     */
    fun setAllAppsProvider(provider: () -> List<AppInfo>) {
        allAppsProvider = provider
    }

    override fun createView(): View {
        val container = FrameLayout(context)

        recyclerView = RecyclerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        container.addView(recyclerView)
        containerView = container

        return container
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        loadGridCells()
        setupRecyclerView()
        applyVisualSettings()
    }

    private fun loadGridCells() {
        val savedData = settings.get("gridCellsJson", "")
        val gridSize = getSetting("gridSize", 6)

        gridCells = if (savedData.isNotEmpty()) {
            try {
                val jsonArray = JSONArray(savedData)
                val cells = mutableListOf<GridCellConfig>()
                for (i in 0 until jsonArray.length()) {
                    cells.add(GridCellConfig.fromJson(jsonArray.getJSONObject(i)))
                }
                cells
            } catch (e: Exception) {
                // Migration from old format
                migrateFromOldFormat()
            }
        } else {
            // Try migration from old format
            val oldData = settings.get("gridApps", "")
            if (oldData.isNotEmpty()) {
                migrateFromOldFormat()
            } else {
                MutableList(gridSize) { GridCellConfig() }
            }
        }

        // Ensure grid size matches setting
        while (gridCells.size < gridSize) {
            gridCells.add(GridCellConfig())
        }
        while (gridCells.size > gridSize) {
            gridCells.removeAt(gridCells.lastIndex)
        }
    }

    private fun migrateFromOldFormat(): MutableList<GridCellConfig> {
        val oldData = settings.get("gridApps", "")
        return if (oldData.isNotEmpty()) {
            oldData.split(",").map { packageName ->
                GridCellConfig(mainApp = packageName)
            }.toMutableList()
        } else {
            val gridSize = getSetting("gridSize", 6)
            MutableList(gridSize) { GridCellConfig() }
        }
    }

    private fun saveGridCells() {
        val jsonArray = JSONArray()
        gridCells.forEach { cell ->
            jsonArray.put(cell.toJson())
        }
        settings.set("gridCellsJson", jsonArray.toString())
    }

    private fun setupRecyclerView() {
        val columns = getSetting("columns", 3)
        recyclerView.layoutManager = GridLayoutManager(context, columns)

        gridAdapter = CustomGridAdapter(
            context = context,
            gridCells = gridCells,
            iconSizeDp = getSetting("iconSize", 48),
            iconFrameBackgroundColor = getSetting("iconFrameBackgroundColor", Color.TRANSPARENT),
            showLabels = getSetting("showLabels", true),
            allAppsProvider = { allAppsProvider?.invoke() ?: emptyList() },
            isEditMode = { isInEditMode },
            onCellClick = { position ->
                if (isInEditMode) {
                    showCellConfigDialog(position)
                } else {
                    launchAppFromCell(position, GestureType.TAP)
                }
            },
            onCellGesture = { position, gestureType ->
                if (!isInEditMode) {
                    launchAppFromCell(position, gestureType)
                    true  // Gesture was handled
                } else {
                    false
                }
            },
            onCellLongClick = { position ->
                if (isInEditMode) {
                    showClearCellDialog(position)
                    true
                } else {
                    false
                }
            },
            iconOverrideProvider = { packageName ->
                preferencesManager.getEffectiveIconOverride(componentId, packageName)
            }
        )

        recyclerView.adapter = gridAdapter
    }

    private fun launchAppFromCell(position: Int, gestureType: GestureType) {
        val cell = gridCells.getOrNull(position) ?: return
        val packageName = when (gestureType) {
            GestureType.TAP -> cell.mainApp
            GestureType.SWIPE_UP -> cell.swipeUpApp
            GestureType.SWIPE_DOWN -> cell.swipeDownApp
            GestureType.SWIPE_LEFT -> cell.swipeLeftApp
            GestureType.SWIPE_RIGHT -> cell.swipeRightApp
        }

        if (packageName.isNotEmpty()) {
            val appInfo = findAppInfo(packageName)
            if (appInfo != null) {
                onAppClick?.invoke(appInfo)
            }
        }
    }

    private fun findAppInfo(packageName: String): AppInfo? {
        return allAppsProvider?.invoke()?.find { it.packageName == packageName }
    }

    private fun showCellConfigDialog(position: Int) {
        val cell = gridCells.getOrNull(position) ?: return
        val allApps = allAppsProvider?.invoke() ?: emptyList()

        if (allApps.isEmpty()) {
            Toast.makeText(context, "No apps available", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24f), dpToPx(16f), dpToPx(24f), dpToPx(8f))
        }

        // Create action rows for each gesture
        val gestureRows = listOf(
            Triple("Tap", cell.mainApp) { pkg: String -> cell.mainApp = pkg },
            Triple("Swipe Up", cell.swipeUpApp) { pkg: String -> cell.swipeUpApp = pkg },
            Triple("Swipe Down", cell.swipeDownApp) { pkg: String -> cell.swipeDownApp = pkg },
            Triple("Swipe Left", cell.swipeLeftApp) { pkg: String -> cell.swipeLeftApp = pkg },
            Triple("Swipe Right", cell.swipeRightApp) { pkg: String -> cell.swipeRightApp = pkg }
        )

        val rowViews = mutableListOf<GestureRowView>()

        gestureRows.forEach { (label, currentPkg, setter) ->
            val rowView = createGestureRow(label, currentPkg, allApps) { selectedPkg ->
                setter(selectedPkg)
            }
            rowViews.add(rowView)
            container.addView(rowView.view)
        }

        val scrollView = ScrollView(context).apply {
            addView(container)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Configure Cell ${position + 1}")
            .setView(scrollView)
            .setPositiveButton("Save") { _, _ ->
                saveGridCells()
                gridAdapter.notifyItemChanged(position)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear All") { _, _ ->
                gridCells[position] = GridCellConfig()
                saveGridCells()
                gridAdapter.notifyItemChanged(position)
            }
            .show()
    }

    private data class GestureRowView(val view: View, val updateSelection: (String) -> Unit)

    private fun createGestureRow(
        label: String,
        currentPackage: String,
        allApps: List<AppInfo>,
        onSelect: (String) -> Unit
    ): GestureRowView {
        val rowContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8f), 0, dpToPx(8f))
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(dpToPx(80f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(36f)).apply {
                marginStart = dpToPx(8f)
                marginEnd = dpToPx(8f)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val appNameView = TextView(context).apply {
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val clearButton = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setPadding(dpToPx(12f), dpToPx(4f), dpToPx(12f), dpToPx(4f))
            visibility = if (currentPackage.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Update display
        fun updateDisplay(packageName: String) {
            if (packageName.isEmpty()) {
                iconView.setImageResource(android.R.drawable.ic_menu_add)
                appNameView.text = "Not set"
                appNameView.setTextColor(Color.GRAY)
                clearButton.visibility = View.GONE
            } else {
                val appInfo = allApps.find { it.packageName == packageName }
                if (appInfo != null) {
                    iconView.setImageDrawable(appInfo.icon)
                    appNameView.text = appInfo.label
                    appNameView.setTextColor(Color.WHITE)
                } else {
                    iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                    appNameView.text = "Unknown app"
                    appNameView.setTextColor(Color.GRAY)
                }
                clearButton.visibility = View.VISIBLE
            }
        }

        updateDisplay(currentPackage)

        // Click to select app
        val clickableArea = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true

            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)

            addView(iconView)
            addView(appNameView)
        }

        clickableArea.setOnClickListener {
            showAppPickerForGesture(label, allApps) { selectedPkg ->
                onSelect(selectedPkg)
                updateDisplay(selectedPkg)
            }
        }

        clearButton.setOnClickListener {
            onSelect("")
            updateDisplay("")
        }

        rowContainer.addView(labelView)
        rowContainer.addView(clickableArea)
        rowContainer.addView(clearButton)

        return GestureRowView(rowContainer) { pkg ->
            updateDisplay(pkg)
        }
    }

    private fun showAppPickerForGesture(
        gestureLabel: String,
        allApps: List<AppInfo>,
        onSelect: (String) -> Unit
    ) {
        val sortedApps = allApps.sortedBy { it.label.lowercase() }
        val appNames = sortedApps.map { it.label }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("$gestureLabel Action")
            .setItems(appNames) { _, which ->
                val selectedApp = sortedApps[which]
                onSelect(selectedApp.packageName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearCellDialog(position: Int) {
        val cell = gridCells.getOrNull(position) ?: return

        if (!cell.hasAnyApp()) {
            Toast.makeText(context, "Cell is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Clear Cell")
            .setMessage("Clear all apps from this cell?")
            .setPositiveButton("Clear") { _, _ ->
                gridCells[position] = GridCellConfig()
                saveGridCells()
                gridAdapter.notifyItemChanged(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Refresh the grid display.
     */
    fun refresh() {
        if (::gridAdapter.isInitialized) {
            gridAdapter.notifyDataSetChanged()
        }
    }

    override fun getSettingsSchema(): List<SettingDefinition> {
        return listOf(
            SettingDefinition.IntRange(
                key = "columns",
                label = "Columns",
                description = "Number of columns in the grid",
                default = 3,
                min = 1,
                max = 8
            ),
            SettingDefinition.IntRange(
                key = "gridSize",
                label = "Grid Size",
                description = "Total number of cells",
                default = 6,
                min = 1,
                max = 24
            ),
            SettingDefinition.Toggle(
                key = "showLabels",
                label = "Show App Labels",
                description = "Display app names below icons",
                default = true
            ),
            SettingDefinition.Toggle(
                key = "showGestureIndicator",
                label = "Show Gesture Indicator",
                description = "Show dot when cell has gesture actions",
                default = true
            ),
            SettingDefinition.IntRange(
                key = "iconSize",
                label = "Icon Size",
                description = "Size of app icons in dp",
                default = 48,
                min = 32,
                max = 80,
                step = 4
            ),
            SettingDefinition.Color(
                key = "iconFrameBackgroundColor",
                label = "Icon Frame Background",
                description = "Background color for transparent icons",
                default = Color.TRANSPARENT
            ),
            SettingDefinition.Color(
                key = "backgroundColor",
                label = "Background Color",
                description = "Background color of the grid",
                default = Color.TRANSPARENT
            ),
            SettingDefinition.IntRange(
                key = "cornerRadius",
                label = "Corner Radius",
                description = "Corner radius in dp",
                default = 0,
                min = 0,
                max = 32
            )
        )
    }

    override fun onSettingsChanged(key: String, value: Any) {
        when (key) {
            "columns" -> {
                val columns = (value as? Int) ?: 3
                (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = columns
            }
            "gridSize" -> {
                val newSize = (value as? Int) ?: 6
                while (gridCells.size < newSize) {
                    gridCells.add(GridCellConfig())
                }
                while (gridCells.size > newSize) {
                    gridCells.removeAt(gridCells.lastIndex)
                }
                saveGridCells()
                if (::gridAdapter.isInitialized) {
                    gridAdapter.updateGridCells(gridCells)
                }
            }
            "showLabels" -> {
                val showLabels = (value as? Boolean) ?: true
                if (::gridAdapter.isInitialized) {
                    gridAdapter.setShowLabels(showLabels)
                }
            }
            "showGestureIndicator" -> {
                val show = (value as? Boolean) ?: true
                if (::gridAdapter.isInitialized) {
                    gridAdapter.setShowGestureIndicator(show)
                }
            }
            "iconSize" -> {
                val iconSize = (value as? Int) ?: 48
                if (::gridAdapter.isInitialized) {
                    gridAdapter.setIconSize(iconSize)
                }
            }
            "iconFrameBackgroundColor" -> {
                val color = (value as? Int) ?: Color.TRANSPARENT
                if (::gridAdapter.isInitialized) {
                    gridAdapter.setIconFrameBackgroundColor(color)
                }
            }
            "backgroundColor", "cornerRadius" -> {
                applyVisualSettings()
            }
        }
    }

    private fun applyVisualSettings() {
        val backgroundColor = getSetting("backgroundColor", Color.TRANSPARENT)
        val cornerRadius = getSetting("cornerRadius", 0)

        containerView?.let { container ->
            if (cornerRadius > 0 || backgroundColor != Color.TRANSPARENT) {
                val drawable = GradientDrawable().apply {
                    setColor(backgroundColor)
                    this.cornerRadius = dpToPx(cornerRadius.toFloat()).toFloat()
                }
                container.background = drawable
                container.clipToOutline = true
            } else {
                container.background = null
            }
        }
    }

    override fun disableInteraction(view: View) {
        recyclerView.suppressLayout(true)
        recyclerView.isNestedScrollingEnabled = false
    }

    override fun enableInteraction(view: View) {
        recyclerView.suppressLayout(false)
        recyclerView.isNestedScrollingEnabled = true
    }

    override fun onEnterEditMode() {
        super.onEnterEditMode()
        if (::gridAdapter.isInitialized) {
            gridAdapter.notifyDataSetChanged()
        }
    }

    override fun onExitEditMode() {
        super.onExitEditMode()
        if (::gridAdapter.isInitialized) {
            gridAdapter.notifyDataSetChanged()
        }
    }

    override fun onBoundsChanged(newBounds: ComponentBounds) {
        super.onBoundsChanged(newBounds)
    }

    /**
     * Gesture types for cell actions.
     */
    enum class GestureType {
        TAP, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT
    }

    /**
     * Adapter for the custom app grid with gesture support.
     */
    private class CustomGridAdapter(
        private val context: Context,
        private var gridCells: MutableList<GridCellConfig>,
        private var iconSizeDp: Int,
        private var iconFrameBackgroundColor: Int,
        private var showLabels: Boolean,
        private val allAppsProvider: () -> List<AppInfo>,
        private val isEditMode: () -> Boolean,
        private val onCellClick: (Int) -> Unit,
        private val onCellGesture: (Int, GestureType) -> Boolean,  // Returns true if gesture was handled
        private val onCellLongClick: (Int) -> Boolean,
        private val iconOverrideProvider: (String) -> AppIconOverride?
    ) : RecyclerView.Adapter<CustomGridAdapter.ViewHolder>() {

        private val density = context.resources.displayMetrics.density
        private var showGestureIndicator = true

        inner class ViewHolder(
            itemView: View,
            val iconFrame: FrameLayout,
            val iconBackground: ImageView,
            val iconView: ImageView,
            val labelView: TextView,
            val addIcon: TextView,
            val gestureIndicator: View
        ) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val container = FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }

            val innerContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(8.dp, 12.dp, 8.dp, 12.dp)
            }

            val iconFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSizeDp.dp, iconSizeDp.dp)
                clipToOutline = true
            }

            val iconBackground = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }

            val iconView = ImageView(context).apply {
                id = android.R.id.icon
                layoutParams = FrameLayout.LayoutParams(iconSizeDp.dp, iconSizeDp.dp, Gravity.CENTER)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val labelView = TextView(context).apply {
                id = android.R.id.text1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.dp
                }
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val addIcon = TextView(context).apply {
                id = android.R.id.text2
                layoutParams = LinearLayout.LayoutParams(iconSizeDp.dp, iconSizeDp.dp)
                text = "+"
                textSize = 32f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#80808080"))
                visibility = View.GONE
            }

            // Gesture indicator (small dot in corner)
            val gestureIndicator = View(context).apply {
                id = android.R.id.icon1
                layoutParams = FrameLayout.LayoutParams(8.dp, 8.dp).apply {
                    gravity = Gravity.TOP or Gravity.END
                    marginEnd = 4.dp
                    topMargin = 4.dp
                }
                val indicatorDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#4CAF50"))
                }
                background = indicatorDrawable
                visibility = View.GONE
            }

            iconFrame.addView(iconBackground)
            iconFrame.addView(iconView)
            innerContainer.addView(iconFrame)
            innerContainer.addView(addIcon)
            innerContainer.addView(labelView)

            container.addView(innerContainer)
            container.addView(gestureIndicator)

            return ViewHolder(container, iconFrame, iconBackground, iconView, labelView, addIcon, gestureIndicator)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cell = gridCells.getOrNull(position) ?: GridCellConfig()
            val hasMainApp = cell.mainApp.isNotEmpty()
            val hasGestures = cell.hasGestureApps()
            val inEditMode = isEditMode()

            if (!hasMainApp) {
                // Show "+" placeholder
                holder.iconFrame.visibility = View.GONE
                holder.addIcon.visibility = if (inEditMode || hasGestures) View.VISIBLE else View.INVISIBLE
                holder.labelView.visibility = View.GONE

                // Change placeholder appearance if has gesture apps
                if (hasGestures && !inEditMode) {
                    holder.addIcon.text = "⋯"
                    holder.addIcon.textSize = 24f
                } else {
                    holder.addIcon.text = "+"
                    holder.addIcon.textSize = 32f
                }
            } else {
                // Show app
                holder.addIcon.visibility = View.GONE
                holder.iconFrame.visibility = View.VISIBLE
                holder.labelView.visibility = if (showLabels) View.VISIBLE else View.GONE

                val appInfo = allAppsProvider().find { it.packageName == cell.mainApp }
                val iconOverride = iconOverrideProvider.invoke(cell.mainApp) ?: AppIconOverride()
                val scalePercent = iconOverride.scalePercent.coerceIn(50, 150)
                val scaledIconSize = (iconSizeDp * (scalePercent / 100f)).toInt().coerceAtLeast(1)

                holder.iconFrame.layoutParams = LinearLayout.LayoutParams(iconSizeDp.dp, iconSizeDp.dp)
                holder.iconView.layoutParams = FrameLayout.LayoutParams(
                    scaledIconSize.dp,
                    scaledIconSize.dp,
                    Gravity.CENTER
                )

                val bgImageUri = iconOverride.backgroundImageUri
                holder.iconBackground.setImageDrawable(null)
                if (bgImageUri != null) {
                    holder.iconBackground.visibility = View.VISIBLE
                    holder.iconBackground.setImageURI(android.net.Uri.parse(bgImageUri))
                } else {
                    holder.iconBackground.visibility = View.GONE
                }

                val bgColor = iconOverride.backgroundColor
                val hasBgImage = iconOverride.backgroundImageUri != null
                val resolvedFrameColor = ColorSettingUtils.resolveColor(context, iconFrameBackgroundColor)
                val fallbackColor = resolvedFrameColor.takeIf { it != Color.TRANSPARENT }
                val finalBgColor = when {
                    bgColor != null -> bgColor
                    hasBgImage -> Color.TRANSPARENT
                    fallbackColor != null -> fallbackColor
                    else -> Color.TRANSPARENT
                }
                holder.iconFrame.setBackgroundColor(finalBgColor)

                if (appInfo != null) {
                    holder.iconView.setImageDrawable(null)
                    if (iconOverride.iconUri != null) {
                        holder.iconView.setImageURI(android.net.Uri.parse(iconOverride.iconUri))
                    } else {
                        holder.iconView.setImageDrawable(appInfo.icon)
                    }
                    holder.labelView.text = appInfo.label
                } else {
                    holder.iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                    holder.labelView.text = "Unknown"
                }
            }

            // Show gesture indicator
            holder.gestureIndicator.visibility =
                if (showGestureIndicator && hasGestures && !inEditMode) View.VISIBLE else View.GONE

            // Update icon size
            holder.addIcon.layoutParams = LinearLayout.LayoutParams(iconSizeDp.dp, iconSizeDp.dp)

            // Setup touch handling with drag animation
            var startX = 0f
            var startY = 0f
            var isLongPress = false
            var isDragging = false
            val maxDragOffset = 30.dp.toFloat()  // Max drag distance in pixels
            val triggerThreshold = 25.dp.toFloat()  // Threshold to trigger gesture (in dp)
            val cancelZone = 15.dp.toFloat()  // If dragged back within this zone, cancel gesture

            val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val longPressRunnable = Runnable {
                isLongPress = true
                // Vibrate feedback
                holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos != RecyclerView.NO_POSITION) {
                    onCellLongClick(adapterPos)
                }
            }

            // Helper function to block touch interception on ALL parent levels
            // This ensures global gesture detectors at activity level are also blocked
            fun blockAllParentInterception(view: View, disallow: Boolean) {
                var parent = view.parent
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(disallow)
                    parent = (parent as? View)?.parent
                }
            }

            holder.itemView.setOnTouchListener { view, event ->
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) return@setOnTouchListener false

                val currentCell = gridCells.getOrNull(adapterPos) ?: return@setOnTouchListener false
                val cellHasGestures = currentCell.hasGestureApps()

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        isLongPress = false
                        isDragging = false
                        longPressHandler.postDelayed(longPressRunnable, 500)

                        // If this cell has gesture apps configured, disable global gestures
                        // This prevents global desktop gestures from triggering
                        if (cellHasGestures && !isEditMode()) {
                            MainActivity.isGlobalGestureDisabled = true
                            blockAllParentInterception(view, true)
                        }

                        // Scale down slightly on press
                        view.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(100)
                            .start()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - startX
                        val dy = event.y - startY
                        val absDx = abs(dx)
                        val absDy = abs(dy)

                        // Cancel long press if moved
                        if (absDx > 10 || absDy > 10) {
                            longPressHandler.removeCallbacks(longPressRunnable)

                            if (!isDragging) {
                                isDragging = true
                                // Block ALL parents from intercepting touch events
                                blockAllParentInterception(view, true)

                                // Reset scale when starting to drag
                                view.scaleX = 1f
                                view.scaleY = 1f
                            }
                        }

                        if (isDragging && !isEditMode()) {
                            // Calculate clamped offset for visual feedback
                            val clampedDx = dx.coerceIn(-maxDragOffset, maxDragOffset)
                            val clampedDy = dy.coerceIn(-maxDragOffset, maxDragOffset)

                            // Apply translation with easing (drag feels slightly resistant)
                            view.translationX = clampedDx * 0.6f
                            view.translationY = clampedDy * 0.6f

                            // Add slight rotation based on horizontal drag
                            view.rotation = clampedDx * 0.1f
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressHandler.removeCallbacks(longPressRunnable)

                        // Re-enable global gestures and allow parents to intercept again
                        MainActivity.isGlobalGestureDisabled = false
                        blockAllParentInterception(view, false)

                        // Animate back to original position
                        view.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .rotation(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                            .start()

                        if (isLongPress) {
                            return@setOnTouchListener true
                        }

                        val dx = event.x - startX
                        val dy = event.y - startY
                        val absDx = abs(dx)
                        val absDy = abs(dy)

                        if (isEditMode()) {
                            // In edit mode, just handle tap
                            if (absDx < triggerThreshold && absDy < triggerThreshold) {
                                onCellClick(adapterPos)
                            }
                            return@setOnTouchListener true
                        }

                        // Cancel zone: if user dragged back near the start, cancel the gesture
                        // This allows users to "abort" a gesture by dragging back
                        if (absDx < cancelZone && absDy < cancelZone) {
                            // User cancelled the gesture by dragging back to center
                            // Only trigger tap if there's a main app AND user didn't drag at all
                            if (!isDragging && currentCell.mainApp.isNotEmpty()) {
                                onCellClick(adapterPos)
                            }
                            // Always consume event if cell has gestures to prevent global gesture
                            return@setOnTouchListener cellHasGestures || currentCell.mainApp.isNotEmpty()
                        }

                        // Check if swipe is past trigger threshold
                        if (absDx < triggerThreshold && absDy < triggerThreshold) {
                            // Tap (small movement, not quite a swipe)
                            if (currentCell.mainApp.isNotEmpty()) {
                                onCellClick(adapterPos)
                                return@setOnTouchListener true
                            }
                            // Consume event if cell has gestures to prevent global gesture
                            return@setOnTouchListener cellHasGestures
                        }

                        // Swipe detected - check if app is configured for this direction
                        val gestureType: GestureType
                        val hasAppForGesture: Boolean

                        if (absDx > absDy) {
                            // Horizontal swipe
                            if (dx > 0) {
                                gestureType = GestureType.SWIPE_RIGHT
                                hasAppForGesture = currentCell.swipeRightApp.isNotEmpty()
                            } else {
                                gestureType = GestureType.SWIPE_LEFT
                                hasAppForGesture = currentCell.swipeLeftApp.isNotEmpty()
                            }
                        } else {
                            // Vertical swipe
                            if (dy > 0) {
                                gestureType = GestureType.SWIPE_DOWN
                                hasAppForGesture = currentCell.swipeDownApp.isNotEmpty()
                            } else {
                                gestureType = GestureType.SWIPE_UP
                                hasAppForGesture = currentCell.swipeUpApp.isNotEmpty()
                            }
                        }

                        // Launch app if gesture is configured
                        if (hasAppForGesture) {
                            onCellGesture(adapterPos, gestureType)
                        }

                        // Always consume event if cell has any gestures to prevent global gesture
                        cellHasGestures || currentCell.mainApp.isNotEmpty()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)

                        // Re-enable global gestures and allow all parents to intercept again
                        MainActivity.isGlobalGestureDisabled = false
                        blockAllParentInterception(view, false)

                        // Animate back to original position
                        view.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .rotation(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                        false
                    }
                    else -> false
                }
            }
        }

        override fun getItemCount(): Int = gridCells.size

        fun updateGridCells(newCells: MutableList<GridCellConfig>) {
            gridCells = newCells
            notifyDataSetChanged()
        }

        fun setShowLabels(show: Boolean) {
            showLabels = show
            notifyDataSetChanged()
        }

        fun setShowGestureIndicator(show: Boolean) {
            showGestureIndicator = show
            notifyDataSetChanged()
        }

        fun setIconSize(sizeDp: Int) {
            iconSizeDp = sizeDp
            notifyDataSetChanged()
        }

        fun setIconFrameBackgroundColor(color: Int) {
            iconFrameBackgroundColor = color
            notifyDataSetChanged()
        }

        private val Int.dp: Int
            get() = (this * density).toInt()
    }
}
