package com.example.taglauncher.component.impl

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taglauncher.AppAdapter
import com.example.taglauncher.AppInfo
import com.example.taglauncher.GridSpacingItemDecoration
import com.example.taglauncher.PreferencesManager
import com.example.taglauncher.component.BaseDesktopComponent
import com.example.taglauncher.component.ComponentType
import com.example.taglauncher.desktop.ComponentBounds
import com.example.taglauncher.persistence.ComponentSettings
import com.example.taglauncher.persistence.SettingDefinition

/**
 * App Drawer component that displays a grid of all installed apps.
 * Shows all apps from the system, supports tag filtering and search.
 */
class AppDrawerComponent(
    context: Context,
    componentId: String,
    bounds: ComponentBounds,
    settings: ComponentSettings,
    private val preferencesManager: PreferencesManager
) : BaseDesktopComponent(context, componentId, ComponentType.APP_DRAWER, bounds, settings) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private var containerView: View? = null
    private var itemDecoration: GridSpacingItemDecoration? = null

    private var allApps: List<AppInfo> = emptyList()
    private var visibleApps: List<AppInfo> = emptyList()
    private var currentFilterTag: String? = null
    private var allAppsProvider: (() -> List<AppInfo>)? = null

    // Callbacks for external handling
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onHideApp: ((AppInfo) -> Unit)? = null
    var onManageTags: ((AppInfo) -> Unit)? = null

    /**
     * Set the provider for all installed apps.
     * This allows the parent to provide apps instead of loading them ourselves.
     */
    fun setAllAppsProvider(provider: () -> List<AppInfo>) {
        allAppsProvider = provider
    }

    override fun createView(): View {
        // Create container with rounded corners support
        val container = android.widget.FrameLayout(context)

        recyclerView = RecyclerView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
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
        loadApps()
        setupRecyclerView()
        applyVisualSettings()
    }

    private fun setupRecyclerView() {
        val columns = getSetting("columns", 4)
        recyclerView.layoutManager = GridLayoutManager(context, columns)

        // Setup item decoration for spacing
        val horizontalSpacingDp = getSetting("horizontalSpacing", 0)
        val verticalSpacingDp = getSetting("verticalSpacing", 4)
        val horizontalSpacingPx = dpToPx(horizontalSpacingDp.toFloat())
        val verticalSpacingPx = dpToPx(verticalSpacingDp.toFloat())

        itemDecoration = GridSpacingItemDecoration(horizontalSpacingPx, verticalSpacingPx)
        recyclerView.addItemDecoration(itemDecoration!!)

        // Apply grid padding
        val gridPaddingDp = getSetting("gridPadding", 0)
        val gridPaddingPx = dpToPx(gridPaddingDp.toFloat())
        recyclerView.setPadding(gridPaddingPx, gridPaddingPx, gridPaddingPx, gridPaddingPx)

        appAdapter = AppAdapter(
            context = context,
            appList = visibleApps,
            onAppClick = { appInfo ->
                if (!isInEditMode) {
                    onAppClick?.invoke(appInfo)
                }
            },
            onHideApp = { appInfo ->
                onHideApp?.invoke(appInfo)
                reloadVisibleApps()
                appAdapter.updateList(visibleApps)
            },
            onManageTags = { appInfo -> onManageTags?.invoke(appInfo) },
            showLabels = getSetting("showLabels", true),
            iconSizeDp = getSetting("iconSize", 48)
        )

        // Apply additional style settings
        appAdapter.setIconShape(getSetting("iconShape", "default"))
        appAdapter.setIconPadding(getSetting("iconPadding", 0))
        appAdapter.setLabelSize(getSetting("labelSize", 12))
        appAdapter.setLabelColor(getSetting("labelColor", Color.WHITE))
        appAdapter.setLabelMaxLines(getSetting("labelMaxLines", 1))
        appAdapter.setLabelMarginTop(getSetting("labelMarginTop", 2))

        recyclerView.adapter = appAdapter

        // Apply maxRows limiting if set
        applyMaxRowsLimit()
    }

    private fun loadApps() {
        // Use provider if available, otherwise load from PackageManager
        allApps = allAppsProvider?.invoke() ?: loadAppsFromPackageManager()
        reloadVisibleApps()
    }

    private fun loadAppsFromPackageManager(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = context.packageManager.queryIntentActivities(intent, 0)

        return resolveInfoList
            .filter { it.activityInfo.packageName != context.packageName }
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(context.packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(context.packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun reloadVisibleApps() {
        val hiddenApps = preferencesManager.getHiddenApps()
        val baseApps = allApps.filter { !hiddenApps.contains(it.packageName) }

        visibleApps = if (currentFilterTag == null) {
            baseApps
        } else {
            val taggedPackages = preferencesManager.getAppsWithTag(currentFilterTag!!)
            baseApps.filter { taggedPackages.contains(it.packageName) }
        }
    }

    /**
     * Filter apps by tag.
     * @param tagId The tag ID to filter by, or null to show all apps.
     */
    fun filterByTag(tagId: String?) {
        currentFilterTag = tagId
        settings.set("filterTag", tagId ?: "all")
        reloadVisibleApps()
        if (::appAdapter.isInitialized) {
            appAdapter.updateList(visibleApps)
        }
    }

    /**
     * Apply text filter for search.
     */
    fun applySearchFilter(query: String) {
        if (::appAdapter.isInitialized) {
            appAdapter.filter.filter(query)
        }
    }

    /**
     * Clear search filter.
     */
    fun clearSearchFilter() {
        if (::appAdapter.isInitialized) {
            appAdapter.filter.filter("")
        }
    }

    /**
     * Refresh the app list (e.g., after app install/uninstall).
     */
    fun refreshApps() {
        loadApps()
        if (::appAdapter.isInitialized) {
            appAdapter.updateList(visibleApps)
        }
    }

    /**
     * Get all visible apps.
     */
    fun getVisibleApps(): List<AppInfo> = visibleApps

    override fun getSettingsSchema(): List<SettingDefinition> {
        return listOf(
            // Grid Layout
            SettingDefinition.IntRange(
                key = "columns",
                label = "Columns",
                description = "Number of columns in the grid",
                default = 4,
                min = 2,
                max = 12
            ),
            SettingDefinition.IntRange(
                key = "maxRows",
                label = "Max Rows",
                description = "Maximum visible rows (0 = unlimited)",
                default = 0,
                min = 0,
                max = 10
            ),

            // Spacing
            SettingDefinition.IntRange(
                key = "horizontalSpacing",
                label = "Horizontal Spacing",
                description = "Gap between columns (dp)",
                default = 0,
                min = 0,
                max = 32
            ),
            SettingDefinition.IntRange(
                key = "verticalSpacing",
                label = "Vertical Spacing",
                description = "Gap between rows (dp)",
                default = 4,
                min = 0,
                max = 48
            ),
            SettingDefinition.IntRange(
                key = "gridPadding",
                label = "Grid Padding",
                description = "Padding around the grid (dp)",
                default = 0,
                min = 0,
                max = 32
            ),

            // Icons
            SettingDefinition.IntRange(
                key = "iconSize",
                label = "Icon Size",
                description = "Size of app icons (dp)",
                default = 48,
                min = 32,
                max = 80,
                step = 4
            ),
            SettingDefinition.Choice(
                key = "iconShape",
                label = "Icon Shape",
                description = "Shape mask for icons",
                default = "default",
                options = listOf(
                    "default" to "Default",
                    "circle" to "Circle",
                    "rounded" to "Rounded",
                    "square" to "Square"
                )
            ),
            SettingDefinition.IntRange(
                key = "iconPadding",
                label = "Icon Padding",
                description = "Padding inside icon bounds (dp)",
                default = 0,
                min = 0,
                max = 16
            ),

            // Labels
            SettingDefinition.Toggle(
                key = "showLabels",
                label = "Show Labels",
                description = "Display app names below icons",
                default = true
            ),
            SettingDefinition.IntRange(
                key = "labelSize",
                label = "Label Size",
                description = "Text size (sp)",
                default = 12,
                min = 8,
                max = 18
            ),
            SettingDefinition.Color(
                key = "labelColor",
                label = "Label Color",
                description = "Text color for labels",
                default = Color.WHITE
            ),
            SettingDefinition.IntRange(
                key = "labelMaxLines",
                label = "Label Max Lines",
                description = "Maximum lines for label text",
                default = 1,
                min = 1,
                max = 3
            ),
            SettingDefinition.IntRange(
                key = "labelMarginTop",
                label = "Label Margin",
                description = "Gap between icon and label (dp)",
                default = 2,
                min = 0,
                max = 16
            ),

            // Background
            SettingDefinition.Color(
                key = "backgroundColor",
                label = "Background Color",
                description = "Background color of the grid",
                default = Color.TRANSPARENT
            ),
            SettingDefinition.IntRange(
                key = "cornerRadius",
                label = "Corner Radius",
                description = "Corner radius (dp)",
                default = 0,
                min = 0,
                max = 32
            )
        )
    }

    override fun onSettingsChanged(key: String, value: Any) {
        when (key) {
            "columns" -> {
                val columns = (value as? Int) ?: 4
                (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = columns
                applyMaxRowsLimit()
            }
            "maxRows" -> {
                applyMaxRowsLimit()
            }
            "horizontalSpacing", "verticalSpacing" -> {
                val horizontalSpacing = getSetting("horizontalSpacing", 0)
                val verticalSpacing = getSetting("verticalSpacing", 4)
                itemDecoration?.updateSpacing(
                    dpToPx(horizontalSpacing.toFloat()),
                    dpToPx(verticalSpacing.toFloat())
                )
                recyclerView.invalidateItemDecorations()
            }
            "gridPadding" -> {
                val gridPaddingPx = dpToPx((value as? Int ?: 0).toFloat())
                recyclerView.setPadding(gridPaddingPx, gridPaddingPx, gridPaddingPx, gridPaddingPx)
            }
            "showLabels" -> {
                val showLabels = (value as? Boolean) ?: true
                if (::appAdapter.isInitialized) {
                    appAdapter.setShowLabels(showLabels)
                }
                applyMaxRowsLimit()
            }
            "iconSize" -> {
                val iconSize = (value as? Int) ?: 48
                if (::appAdapter.isInitialized) {
                    appAdapter.setIconSize(iconSize)
                }
                applyMaxRowsLimit()
            }
            "iconShape" -> {
                val shape = (value as? String) ?: "default"
                if (::appAdapter.isInitialized) {
                    appAdapter.setIconShape(shape)
                }
            }
            "iconPadding" -> {
                val padding = (value as? Int) ?: 0
                if (::appAdapter.isInitialized) {
                    appAdapter.setIconPadding(padding)
                }
            }
            "labelSize" -> {
                val size = (value as? Int) ?: 12
                if (::appAdapter.isInitialized) {
                    appAdapter.setLabelSize(size)
                }
                applyMaxRowsLimit()
            }
            "labelColor" -> {
                val color = (value as? Int) ?: Color.WHITE
                if (::appAdapter.isInitialized) {
                    appAdapter.setLabelColor(color)
                }
            }
            "labelMaxLines" -> {
                val maxLines = (value as? Int) ?: 1
                if (::appAdapter.isInitialized) {
                    appAdapter.setLabelMaxLines(maxLines)
                }
                applyMaxRowsLimit()
            }
            "labelMarginTop" -> {
                val margin = (value as? Int) ?: 2
                if (::appAdapter.isInitialized) {
                    appAdapter.setLabelMarginTop(margin)
                }
                applyMaxRowsLimit()
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

    /**
     * Apply max rows height limiting if maxRows > 0.
     * Calculates the height needed for the specified number of rows and constrains the RecyclerView.
     */
    private fun applyMaxRowsLimit() {
        val maxRows = getSetting("maxRows", 0)

        if (maxRows <= 0) {
            // No limit - use MATCH_PARENT
            recyclerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            return
        }

        // Wait for layout to be ready to calculate item heights
        recyclerView.post {
            if (!::appAdapter.isInitialized || appAdapter.itemCount == 0) {
                return@post
            }

            // Calculate row height based on current settings
            val iconSizeDp = getSetting("iconSize", 48)
            val showLabels = getSetting("showLabels", true)
            val labelSizeSp = getSetting("labelSize", 12)
            val labelMaxLines = getSetting("labelMaxLines", 1)
            val labelMarginTopDp = getSetting("labelMarginTop", 2)
            val verticalSpacingDp = getSetting("verticalSpacing", 4)
            val gridPaddingDp = getSetting("gridPadding", 0)

            // Estimate row height
            val iconSizePx = dpToPx(iconSizeDp.toFloat())
            val verticalSpacingPx = dpToPx(verticalSpacingDp.toFloat())
            val itemPaddingPx = dpToPx(2f) * 2 // top and bottom padding from item_app.xml

            var rowHeight = iconSizePx + itemPaddingPx + verticalSpacingPx

            if (showLabels) {
                // Approximate label height: labelSize * maxLines + marginTop
                val labelHeightPx = (labelSizeSp * context.resources.displayMetrics.scaledDensity * labelMaxLines).toInt()
                val labelMarginTopPx = dpToPx(labelMarginTopDp.toFloat())
                rowHeight += labelHeightPx + labelMarginTopPx
            }

            // Calculate total height for maxRows
            val gridPaddingPx = dpToPx(gridPaddingDp.toFloat())
            val totalHeight = (rowHeight * maxRows) + (gridPaddingPx * 2)

            // Apply height constraint
            recyclerView.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                totalHeight
            )
        }
    }

    override fun disableInteraction(view: View) {
        // Disable RecyclerView scrolling
        recyclerView.suppressLayout(true)
        recyclerView.isNestedScrollingEnabled = false
    }

    override fun enableInteraction(view: View) {
        // Re-enable RecyclerView scrolling
        recyclerView.suppressLayout(false)
        recyclerView.isNestedScrollingEnabled = true
    }

    override fun onBoundsChanged(newBounds: ComponentBounds) {
        super.onBoundsChanged(newBounds)
        // RecyclerView will be resized by parent layout
    }
}
