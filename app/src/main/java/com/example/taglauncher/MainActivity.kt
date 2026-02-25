package com.example.taglauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taglauncher.component.ComponentType
import com.example.taglauncher.component.DesktopComponent
import com.example.taglauncher.component.impl.AppDrawerComponent
import com.example.taglauncher.component.impl.AppGridComponent
import com.example.taglauncher.component.impl.SearchBarComponent
import com.example.taglauncher.desktop.ComponentBounds
import com.example.taglauncher.desktop.DesktopCanvasView
import com.example.taglauncher.desktop.DesktopLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.lang.reflect.Method

class MainActivity : AppCompatActivity() {

    // Component-based desktop system
    private lateinit var desktopCanvas: DesktopCanvasView
    private lateinit var layoutManager: DesktopLayoutManager

    // System UI elements (not components)
    private lateinit var tagFab: FloatingActionButton
    private lateinit var tagRingMenu: TagRingMenuView
    private lateinit var editModeToolbar: MaterialCardView
    private lateinit var editToolbarDragHandle: MaterialButton
    private lateinit var addComponentButton: MaterialButton
    private lateinit var pasteComponentButton: MaterialButton
    private lateinit var doneEditButton: MaterialButton

    // Component toolbar
    private lateinit var componentToolbar: MaterialCardView
    private lateinit var componentToolbarDragHandle: MaterialButton
    private lateinit var componentSettingsButton: MaterialButton
    private lateinit var componentCopyButton: MaterialButton
    private lateinit var componentCutButton: MaterialButton
    private lateinit var componentSaveButton: MaterialButton
    private lateinit var componentDeleteButton: MaterialButton

    // Search overlay
    private lateinit var searchOverlay: FrameLayout
    private lateinit var searchOverlayBackground: View
    private lateinit var searchBarCard: MaterialCardView
    private lateinit var searchOverlayInput: EditText
    private lateinit var searchOverlayClear: ImageView
    private var isSearchOverlayVisible = false

    private lateinit var preferencesManager: PreferencesManager

    private var allApps: List<AppInfo> = emptyList()
    private var visibleApps: List<AppInfo> = emptyList()
    private var appChangeReceiver: AppChangeReceiver? = null

    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleGestureDetector: android.view.ScaleGestureDetector
    private var initialPinchSpan = 0f

    private var isTagMenuActive = false
    private var lastSelectedTagId: String? = null
    private var currentTagFilter: TagItem? = null

    // Ribbon action mode
    private var currentRibbonAction: String? = null

    companion object {
        const val RIBBON_ACTION_EDIT_TAG = "ribbon_edit_tag"
        const val RIBBON_ACTION_EDIT_APPS = "ribbon_edit_apps"
        const val RIBBON_ACTION_PIN_TAG = "ribbon_pin_tag"

        // Flag to temporarily disable global gestures (used by AppGridComponent)
        @Volatile
        var isGlobalGestureDisabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        setContentView(R.layout.activity_main)

        preferencesManager = PreferencesManager(this)

        initViews()
        setupWindowInsets()
        setupBackHandler()
        setupGestureDetector()
        setupPinchGesture()

        loadApps()
        initializeDesktop()
        setupTagMenu()
        setupEditModeToolbar()
        registerAppChangeReceiver()
    }

    override fun onResume() {
        super.onResume()
        refreshComponents()
        updateTagFabPosition()
        updateTagFabAppearance()
    }

    private fun initViews() {
        desktopCanvas = findViewById(R.id.desktopCanvas)
        tagFab = findViewById(R.id.tagFab)
        tagRingMenu = findViewById(R.id.tagRingMenu)
        editModeToolbar = findViewById(R.id.editModeToolbar)
        editToolbarDragHandle = findViewById(R.id.editToolbarDragHandle)
        addComponentButton = findViewById(R.id.addComponentButton)
        pasteComponentButton = findViewById(R.id.pasteComponentButton)
        doneEditButton = findViewById(R.id.doneEditButton)

        // Component toolbar
        componentToolbar = findViewById(R.id.componentToolbar)
        componentToolbarDragHandle = findViewById(R.id.componentToolbarDragHandle)
        componentSettingsButton = findViewById(R.id.componentSettingsButton)
        componentCopyButton = findViewById(R.id.componentCopyButton)
        componentCutButton = findViewById(R.id.componentCutButton)
        componentSaveButton = findViewById(R.id.componentSaveButton)
        componentDeleteButton = findViewById(R.id.componentDeleteButton)

        // Search overlay
        searchOverlay = findViewById(R.id.searchOverlay)
        searchOverlayBackground = findViewById(R.id.searchOverlayBackground)
        searchBarCard = findViewById(R.id.searchBarCard)
        searchOverlayInput = findViewById(R.id.searchOverlayInput)
        searchOverlayClear = findViewById(R.id.searchOverlayClear)

        setupSearchOverlay()
    }

    private fun initializeDesktop() {
        layoutManager = DesktopLayoutManager(this, preferencesManager, desktopCanvas)

        layoutManager.layoutListener = object : DesktopLayoutManager.LayoutListener {
            override fun onLayoutLoaded(hasAppGrid: Boolean) {
                updateTagFabVisibility()
                wireUpComponents()
            }

            override fun onComponentAdded(component: DesktopComponent) {
                wireUpComponent(component)
                updateTagFabVisibility()
            }

            override fun onComponentRemoved(componentId: String) {
                updateTagFabVisibility()
                hideComponentToolbar()
            }

            override fun onComponentsChanged() {
                // Layout auto-saved by DesktopLayoutManager
            }
        }

        // Set up canvas event listener for selection and manipulation
        desktopCanvas.canvasEventListener = object : DesktopCanvasView.CanvasEventListener {
            override fun onComponentSelected(component: DesktopComponent?) {
                if (component != null) {
                    showComponentToolbar()
                } else {
                    hideComponentToolbar()
                }
            }

            override fun onComponentMoved(component: DesktopComponent, newBounds: ComponentBounds) {
                layoutManager.updateComponentBounds(component.componentId, newBounds)
            }

            override fun onComponentResized(component: DesktopComponent, newBounds: ComponentBounds) {
                layoutManager.updateComponentBounds(component.componentId, newBounds)
                component.onBoundsChanged(newBounds)
            }
        }

        layoutManager.loadLayout()
    }

    private fun wireUpComponents() {
        layoutManager.getAllComponents().forEach { component ->
            wireUpComponent(component)
        }
    }

    private fun wireUpComponent(component: DesktopComponent) {
        when (component) {
            is AppDrawerComponent -> {
                component.setAllAppsProvider { visibleApps }
                component.onAppClick = { appInfo ->
                    launchApp(appInfo.packageName)
                    // Clear search filter after launching app
                    clearSearchFilter()
                }
                component.onAddToDock = { appInfo ->
                    // Dock component removed - this callback no longer used
                }
                component.onHideApp = { appInfo -> hideApp(appInfo) }
                component.onManageTags = { appInfo -> showTagsDialog(appInfo) }
                component.refreshApps()
            }
            is AppGridComponent -> {
                component.setAllAppsProvider { allApps }
                component.onAppClick = { appInfo -> launchApp(appInfo.packageName) }
                component.refresh()
            }
            is SearchBarComponent -> {
                component.onSearchTextChanged = { query ->
                    applySearchFilter(query)
                }
                component.onSearchSubmitted = { _ ->
                    // Hide keyboard handled by SearchBarComponent
                }
            }
        }
    }

    private fun refreshComponents() {
        // Refresh all components with current data
        layoutManager.getComponentsByType(ComponentType.APP_DRAWER).forEach { component ->
            (component as? AppDrawerComponent)?.refreshApps()
        }
        layoutManager.getComponentsByType(ComponentType.APP_GRID).forEach { component ->
            (component as? AppGridComponent)?.refresh()
        }
    }

    private fun updateTagFabVisibility() {
        val hasAppDrawer = layoutManager.hasAppDrawerComponent()
        tagFab.visibility = if (hasAppDrawer) View.VISIBLE else View.GONE
    }

    private fun updateTagFabPosition() {
        val position = preferencesManager.getTagButtonPosition()
        val offsetX = preferencesManager.getTagButtonOffsetX()
        val offsetY = preferencesManager.getTagButtonOffsetY()

        val params = tagFab.layoutParams as FrameLayout.LayoutParams

        // Set gravity based on position
        params.gravity = when (position) {
            PreferencesManager.TAG_POSITION_BOTTOM_RIGHT -> android.view.Gravity.BOTTOM or android.view.Gravity.END
            PreferencesManager.TAG_POSITION_BOTTOM_LEFT -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            PreferencesManager.TAG_POSITION_BOTTOM_CENTER -> android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            PreferencesManager.TAG_POSITION_RIGHT_CENTER -> android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            PreferencesManager.TAG_POSITION_LEFT_CENTER -> android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            else -> android.view.Gravity.BOTTOM or android.view.Gravity.END
        }

        // Set base margins based on position
        val baseSideMargin = dpToPx(16)
        val baseBottomMargin = dpToPx(96)
        val baseCenterMargin = dpToPx(16)

        when (position) {
            PreferencesManager.TAG_POSITION_BOTTOM_RIGHT -> {
                params.marginEnd = baseSideMargin + offsetX
                params.marginStart = 0
                params.bottomMargin = baseBottomMargin - offsetY
                params.topMargin = 0
            }
            PreferencesManager.TAG_POSITION_BOTTOM_LEFT -> {
                params.marginStart = baseSideMargin + offsetX
                params.marginEnd = 0
                params.bottomMargin = baseBottomMargin - offsetY
                params.topMargin = 0
            }
            PreferencesManager.TAG_POSITION_BOTTOM_CENTER -> {
                params.marginStart = offsetX
                params.marginEnd = 0
                params.bottomMargin = baseBottomMargin - offsetY
                params.topMargin = 0
            }
            PreferencesManager.TAG_POSITION_RIGHT_CENTER -> {
                params.marginEnd = baseCenterMargin + offsetX
                params.marginStart = 0
                params.bottomMargin = 0
                params.topMargin = offsetY
            }
            PreferencesManager.TAG_POSITION_LEFT_CENTER -> {
                params.marginStart = baseCenterMargin + offsetX
                params.marginEnd = 0
                params.bottomMargin = 0
                params.topMargin = offsetY
            }
        }

        tagFab.layoutParams = params
    }

    private fun updateTagFabAppearance() {
        val icon = preferencesManager.getTagButtonIcon()
        val size = preferencesManager.getTagButtonSize()
        val alpha = preferencesManager.getTagButtonAlpha()
        val color = preferencesManager.getTagButtonColor()

        // Set icon
        val iconRes = when (icon) {
            PreferencesManager.TAG_ICON_TAG -> R.drawable.ic_tag
            PreferencesManager.TAG_ICON_LABEL -> R.drawable.ic_label
            PreferencesManager.TAG_ICON_CIRCLE -> R.drawable.ic_circle
            PreferencesManager.TAG_ICON_GRID -> R.drawable.ic_grid
            PreferencesManager.TAG_ICON_STAR -> R.drawable.ic_star
            PreferencesManager.TAG_ICON_PLUS -> android.R.drawable.ic_input_add
            else -> R.drawable.ic_tag
        }
        tagFab.setImageResource(iconRes)

        // Set size
        val sizePx = dpToPx(size)
        tagFab.customSize = sizePx

        // Set alpha (transparency)
        val alphaFloat = alpha / 100f
        tagFab.alpha = alphaFloat

        // Set background color
        tagFab.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun applySearchFilter(query: String) {
        layoutManager.getComponentsByType(ComponentType.APP_DRAWER).forEach { component ->
            (component as? AppDrawerComponent)?.applySearchFilter(query)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPinchGesture() {
        scaleGestureDetector = android.view.ScaleGestureDetector(
            this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
                    initialPinchSpan = detector.currentSpan
                    return true
                }

                override fun onScaleEnd(detector: android.view.ScaleGestureDetector) {
                    val finalSpan = detector.currentSpan
                    // Pinch-in detected (fingers moved together)
                    if (finalSpan < initialPinchSpan * 0.7f) {
                        showEditMenu()
                    }
                }
            }
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Only process global gestures if not disabled by child views (e.g., AppGridComponent)
        if (!isGlobalGestureDisabled) {
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        }
        // Continue normal event dispatch
        return super.dispatchTouchEvent(event)
    }

    private fun showEditMenu() {
        val options = arrayOf("Edit Desktop", "Settings")

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Menu")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> enterEditMode()
                    1 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enterEditMode() {
        layoutManager.enterEditMode()
        editModeToolbar.visibility = View.VISIBLE
        tagFab.visibility = View.GONE
        updatePasteButtonState()
    }

    private fun updatePasteButtonState() {
        val hasClipboardData = com.example.taglauncher.clipboard.ComponentClipboard.hasData()
        pasteComponentButton.isEnabled = hasClipboardData
    }

    private fun exitEditMode() {
        layoutManager.exitEditMode()
        editModeToolbar.visibility = View.GONE
        hideComponentToolbar()
        // Reset toolbar positions
        editModeToolbar.translationX = 0f
        editModeToolbar.translationY = 0f
        componentToolbar.translationX = 0f
        componentToolbar.translationY = 0f
        updateTagFabVisibility()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEditModeToolbar() {
        // Drag handle for edit mode toolbar
        setupToolbarDrag(editToolbarDragHandle, editModeToolbar)

        // Drag handle for component toolbar
        setupToolbarDrag(componentToolbarDragHandle, componentToolbar)

        addComponentButton.setOnClickListener {
            showAddComponentDialog()
        }

        pasteComponentButton.setOnClickListener {
            val clipboardData = com.example.taglauncher.clipboard.ComponentClipboard.getDataWithOffset()
            if (clipboardData != null) {
                val component = layoutManager.addComponentFromData(clipboardData)
                if (component != null) {
                    wireUpComponent(component)
                    Toast.makeText(this, "Component pasted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to paste component", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        doneEditButton.setOnClickListener {
            exitEditMode()
        }

        // Component toolbar buttons
        componentSettingsButton.setOnClickListener {
            val selectedComponent = layoutManager.getSelectedComponent()
            if (selectedComponent != null) {
                showComponentSettingsDialog(selectedComponent)
            }
        }

        componentCopyButton.setOnClickListener {
            val selectedComponent = layoutManager.getSelectedComponent()
            if (selectedComponent != null) {
                com.example.taglauncher.clipboard.ComponentClipboard.copy(selectedComponent.toComponentData())
                updatePasteButtonState()
                Toast.makeText(this, "${selectedComponent.componentType.displayName} copied", Toast.LENGTH_SHORT).show()
            }
        }

        componentCutButton.setOnClickListener {
            val selectedComponent = layoutManager.getSelectedComponent()
            if (selectedComponent != null) {
                com.example.taglauncher.clipboard.ComponentClipboard.cut(selectedComponent.toComponentData())
                layoutManager.removeComponent(selectedComponent.componentId)
                hideComponentToolbar()
                updatePasteButtonState()
                Toast.makeText(this, "${selectedComponent.componentType.displayName} cut", Toast.LENGTH_SHORT).show()
            }
        }

        componentSaveButton.setOnClickListener {
            val selectedComponent = layoutManager.getSelectedComponent()
            if (selectedComponent != null) {
                showSaveTemplateDialog(selectedComponent)
            }
        }

        componentDeleteButton.setOnClickListener {
            val selectedComponent = layoutManager.getSelectedComponent()
            if (selectedComponent != null) {
                confirmDeleteComponent(selectedComponent)
            }
        }
    }

    private fun showComponentToolbar() {
        componentToolbar.visibility = View.VISIBLE
    }

    private fun hideComponentToolbar() {
        componentToolbar.visibility = View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToolbarDrag(dragHandle: View, toolbar: View) {
        var lastX = 0f
        var lastY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY

                    toolbar.translationX += dx
                    toolbar.translationY += dy

                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }
    }

    private fun confirmDeleteComponent(component: DesktopComponent) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Component")
            .setMessage("Are you sure you want to delete this ${component.componentType.displayName}?")
            .setPositiveButton("Delete") { _, _ ->
                layoutManager.removeComponent(component.componentId)
                hideComponentToolbar()
                Toast.makeText(this, "${component.componentType.displayName} deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showComponentSettingsDialog(component: DesktopComponent) {
        val settingsSchema = component.getSettingsSchema()
        if (settingsSchema.isEmpty()) {
            Toast.makeText(this, "No settings available for this component", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = com.example.taglauncher.edit.ComponentSettingsDialog(
            context = this,
            component = component,
            onSettingsChanged = {
                layoutManager.updateComponentSettings(component.componentId)
            }
        )
        dialog.show()
    }

    private fun showSaveTemplateDialog(component: DesktopComponent) {
        val nameInput = android.widget.EditText(this).apply {
            hint = "Template name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Save as Template")
            .setView(nameInput)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    val template = com.example.taglauncher.persistence.ComponentTemplate.fromComponent(
                        name = name,
                        componentType = component.componentType,
                        settings = component.settings,
                        bounds = component.bounds
                    )
                    if (preferencesManager.addComponentTemplate(template)) {
                        Toast.makeText(this, "Template '$name' saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to save template (max limit reached)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Template name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showAddComponentDialog() {
        val templates = preferencesManager.getComponentTemplates()
        val hasTemplates = templates.isNotEmpty()

        val options = mutableListOf<String>()

        // Add component types
        val componentTypes = ComponentType.entries.toTypedArray()
        componentTypes.forEach { options.add(it.displayName) }

        // Add separator and templates if any
        if (hasTemplates) {
            options.add("── From Template ──")
            templates.forEach { options.add("📋 ${it.name}") }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Component")
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which < componentTypes.size -> {
                        // Selected a component type
                        val selectedType = componentTypes[which]
                        val component = layoutManager.addComponent(selectedType)
                        wireUpComponent(component)
                        Toast.makeText(this, "${selectedType.displayName} added", Toast.LENGTH_SHORT).show()
                    }
                    which == componentTypes.size && hasTemplates -> {
                        // Selected separator - do nothing
                    }
                    hasTemplates -> {
                        // Selected a template
                        val templateIndex = which - componentTypes.size - 1
                        if (templateIndex >= 0 && templateIndex < templates.size) {
                            val template = templates[templateIndex]
                            val componentData = template.createComponentData()
                            val component = layoutManager.addComponentFromData(componentData)
                            if (component != null) {
                                wireUpComponent(component)
                                Toast.makeText(this, "Created from template '${template.name}'", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to create from template", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTagMenu() {
        updateTagRingMenu()

        tagRingMenu.onTagSelected = { tag, isRibbon, ringIndex, segmentIndex ->
            if (tag != null) {
                if (isRibbon) {
                    handleRibbonAction(tag)
                } else if (currentRibbonAction != null) {
                    applyRibbonActionToTag(tag, ringIndex, segmentIndex)
                } else {
                    filterAppsByTag(tag)
                    isTagMenuActive = false
                    tagRingMenu.hide()
                }
            } else {
                if (currentRibbonAction == null) {
                    isTagMenuActive = false
                    tagRingMenu.hide()
                }
            }
        }

        tagFab.setOnTouchListener { view, event ->
            val fabLocation = IntArray(2)
            view.getLocationInWindow(fabLocation)
            val windowX = event.x + fabLocation[0]
            val windowY = event.y + fabLocation[1]

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastSelectedTagId = null
                    showTagMenu()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTagMenuActive) {
                        tagRingMenu.updateSelectionAt(windowX, windowY)
                        val selectedTag = tagRingMenu.getSelectedTag()
                        val isRibbon = tagRingMenu.isSelectedFromRibbon()

                        if (selectedTag != null && selectedTag.id != lastSelectedTagId) {
                            lastSelectedTagId = selectedTag.id
                            if (!isRibbon && currentRibbonAction == null) {
                                filterAppsByTag(selectedTag)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isTagMenuActive) {
                        tagRingMenu.updateSelectionAt(windowX, windowY)
                        val selectedTag = tagRingMenu.getSelectedTag()
                        val isRibbon = tagRingMenu.isSelectedFromRibbon()
                        val ringIndex = tagRingMenu.getSelectedRingIndex()
                        val segmentIndex = tagRingMenu.getSelectedSegmentIndex()

                        if (selectedTag != null) {
                            if (isRibbon) {
                                handleRibbonAction(selectedTag)
                            } else if (currentRibbonAction != null) {
                                applyRibbonActionToTag(selectedTag, ringIndex, segmentIndex)
                            } else {
                                isTagMenuActive = false
                                tagRingMenu.hide()
                            }
                        } else {
                            if (currentRibbonAction == null) {
                                isTagMenuActive = false
                                tagRingMenu.hide()
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleRibbonAction(ribbonItem: TagItem) {
        when (ribbonItem.id) {
            RIBBON_ACTION_EDIT_TAG -> {
                if (currentRibbonAction == RIBBON_ACTION_EDIT_TAG) {
                    currentRibbonAction = null
                    tagRingMenu.setShowPinIndicators(false)
                    isTagMenuActive = false
                    tagRingMenu.hide()
                } else {
                    currentRibbonAction = RIBBON_ACTION_EDIT_TAG
                    tagRingMenu.setShowPinIndicators(false)
                    Toast.makeText(this, "Select a tag to edit (tap Edit again to exit)", Toast.LENGTH_SHORT).show()
                }
            }
            RIBBON_ACTION_EDIT_APPS -> {
                if (currentRibbonAction == RIBBON_ACTION_EDIT_APPS) {
                    currentRibbonAction = null
                    tagRingMenu.setShowPinIndicators(false)
                    isTagMenuActive = false
                    tagRingMenu.hide()
                } else {
                    currentRibbonAction = RIBBON_ACTION_EDIT_APPS
                    tagRingMenu.setShowPinIndicators(false)
                    Toast.makeText(this, "Select a tag to manage apps (tap Apps again to exit)", Toast.LENGTH_SHORT).show()
                }
            }
            RIBBON_ACTION_PIN_TAG -> {
                if (currentRibbonAction == RIBBON_ACTION_PIN_TAG) {
                    currentRibbonAction = null
                    tagRingMenu.setShowPinIndicators(false)
                    isTagMenuActive = false
                    tagRingMenu.hide()
                } else {
                    currentRibbonAction = RIBBON_ACTION_PIN_TAG
                    tagRingMenu.setShowPinIndicators(true)
                    Toast.makeText(this, "Tap a tag to pin/unpin it (tap Pin again to exit)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyRibbonActionToTag(tag: TagItem, ringIndex: Int, segmentIndex: Int) {
        if (tag.id == "all") {
            Toast.makeText(this, "Cannot modify 'All' tag", Toast.LENGTH_SHORT).show()
            return
        }

        when (currentRibbonAction) {
            RIBBON_ACTION_EDIT_TAG -> showEditTagDialog(tag)
            RIBBON_ACTION_EDIT_APPS -> showEditAppsForTagDialog(tag)
            RIBBON_ACTION_PIN_TAG -> showPinTagDialog(ringIndex, segmentIndex)
        }
    }

    private fun showEditTagDialog(tag: TagItem) {
        showCreateTagDialog({ updatedTag ->
            if (updatedTag != null) {
                Toast.makeText(this, "Tag '${updatedTag.label}' updated", Toast.LENGTH_SHORT).show()
                updateTagRingMenu()
            }
        }, tag)
    }

    private fun showEditAppsForTagDialog(tag: TagItem) {
        val taggedPackages = preferencesManager.getAppsWithTag(tag.id).toMutableSet()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val searchInput = android.widget.EditText(this).apply {
            hint = "Search apps..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        container.addView(searchInput)

        val listContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        val checkBoxMap = mutableMapOf<String, android.widget.CheckBox>()
        val itemMap = mutableMapOf<String, android.widget.LinearLayout>()

        visibleApps.forEach { app ->
            val itemLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val iconView = android.widget.ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = android.widget.LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(0, 0, 16, 0)
                }
            }

            val checkBox = android.widget.CheckBox(this).apply {
                text = app.label
                isChecked = taggedPackages.contains(app.packageName)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        taggedPackages.add(app.packageName)
                    } else {
                        taggedPackages.remove(app.packageName)
                    }
                }
            }

            itemLayout.addView(iconView)
            itemLayout.addView(checkBox)
            listContainer.addView(itemLayout)

            checkBoxMap[app.packageName] = checkBox
            itemMap[app.packageName] = itemLayout
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.lowercase()?.trim() ?: ""
                visibleApps.forEach { app ->
                    val itemLayout = itemMap[app.packageName]
                    if (query.isEmpty() || app.label.lowercase().contains(query)) {
                        itemLayout?.visibility = View.VISIBLE
                    } else {
                        itemLayout?.visibility = View.GONE
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.5).toInt()
            )
            addView(listContainer)
        }
        container.addView(scrollView)

        MaterialAlertDialogBuilder(this)
            .setTitle("Apps with tag '${tag.label}'")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                visibleApps.forEach { app ->
                    val currentTags = preferencesManager.getTagsForApp(app.packageName).toMutableList()
                    val shouldHaveTag = taggedPackages.contains(app.packageName)
                    val hasTag = currentTags.contains(tag.id)

                    if (shouldHaveTag && !hasTag) {
                        currentTags.add(tag.id)
                        preferencesManager.setTagsForApp(app.packageName, currentTags)
                    } else if (!shouldHaveTag && hasTag) {
                        currentTags.remove(tag.id)
                        preferencesManager.setTagsForApp(app.packageName, currentTags)
                    }
                }

                Toast.makeText(this, "Apps updated for '${tag.label}'", Toast.LENGTH_SHORT).show()
                updateTagRingMenu()

                if (currentTagFilter?.id == tag.id) {
                    filterAppsByTag(tag)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPinTagDialog(ringIndex: Int, segmentIndex: Int) {
        val allTags = preferencesManager.getAllTags()
        val pinnedPositions = preferencesManager.getPinnedTagPositions()
        val currentPinnedTagId = preferencesManager.getTagIdAtPinnedPosition(ringIndex, segmentIndex)

        if (allTags.isEmpty()) {
            Toast.makeText(this, "No tags available. Create a tag first.", Toast.LENGTH_SHORT).show()
            return
        }

        val tagLabels = mutableListOf<String>()
        val tagIds = mutableListOf<String?>()

        if (currentPinnedTagId != null) {
            val currentTag = allTags.find { it.id == currentPinnedTagId }
            tagLabels.add("Unpin (currently: ${currentTag?.label ?: "Unknown"})")
            tagIds.add(null)
        }

        allTags.forEach { tag ->
            val isPinned = pinnedPositions.containsKey(tag.id)
            val pinnedPos = pinnedPositions[tag.id]
            val label = if (isPinned && pinnedPos != null) {
                "${tag.label} (pinned at Ring ${pinnedPos.first + 1}, Slot ${pinnedPos.second + 1})"
            } else {
                tag.label
            }
            tagLabels.add(label)
            tagIds.add(tag.id)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }

        val positionText = android.widget.TextView(this).apply {
            text = "Pin a tag to Ring ${ringIndex + 1}, Slot ${segmentIndex + 1}"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(24, 8, 24, 16)
        }
        container.addView(positionText)

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.4).toInt()
            )
        }

        val listContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        var itemIndex = 0
        if (currentPinnedTagId != null) {
            val unpinItem = createPinDialogItem(
                label = tagLabels[0],
                color = android.graphics.Color.GRAY,
                isUnpin = true
            ) {
                preferencesManager.removePinnedTagPosition(currentPinnedTagId)
                Toast.makeText(this, "Tag unpinned", Toast.LENGTH_SHORT).show()
                updateTagRingMenu()
                updatePinIndicators()
                dialog?.dismiss()
            }
            listContainer.addView(unpinItem)
            itemIndex = 1
        }

        allTags.forEachIndexed { index, tag ->
            val isPinnedHere = tag.id == currentPinnedTagId
            val item = createPinDialogItem(
                label = tagLabels[itemIndex + index],
                color = tag.color,
                isUnpin = false,
                isCurrentlyPinnedHere = isPinnedHere
            ) {
                preferencesManager.setPinnedTagPosition(tag.id, ringIndex, segmentIndex)
                Toast.makeText(this, "'${tag.label}' pinned to this position", Toast.LENGTH_SHORT).show()
                updateTagRingMenu()
                updatePinIndicators()
                dialog?.dismiss()
            }
            listContainer.addView(item)
        }

        scrollView.addView(listContainer)
        container.addView(scrollView)

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Pin Tag")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun createPinDialogItem(
        label: String,
        color: Int,
        isUnpin: Boolean,
        isCurrentlyPinnedHere: Boolean = false,
        onClick: () -> Unit
    ): android.widget.LinearLayout {
        return android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
            isClickable = true
            isFocusable = true

            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)

            setOnClickListener { onClick() }

            val colorView = if (isUnpin) {
                android.widget.TextView(context).apply {
                    text = "\u2715"
                    textSize = 18f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = android.widget.LinearLayout.LayoutParams(48, 48).apply {
                        setMargins(0, 0, 16, 0)
                    }
                }
            } else {
                View(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(48, 48).apply {
                        setMargins(0, 0, 16, 0)
                    }
                    setBackgroundColor(color)
                }
            }
            addView(colorView)

            val textView = android.widget.TextView(context).apply {
                text = label
                textSize = 16f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                if (isCurrentlyPinnedHere) {
                    setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            addView(textView)

            if (isCurrentlyPinnedHere) {
                val pinIcon = android.widget.TextView(context).apply {
                    text = "\uD83D\uDCCC"
                    textSize = 16f
                }
                addView(pinIcon)
            }
        }
    }

    private fun showTagMenu() {
        isTagMenuActive = true

        val fabLocation = IntArray(2)
        tagFab.getLocationInWindow(fabLocation)
        val fabCenterX = fabLocation[0] + tagFab.width / 2f
        val fabCenterY = fabLocation[1] + tagFab.height / 2f

        val menuRadius = resources.displayMetrics.widthPixels * 0.7f

        tagRingMenu.setMaxRadius(menuRadius)
        tagRingMenu.setCenter(fabCenterX, fabCenterY)

        val (startAngle, sweepAngle) = when (preferencesManager.getTagButtonPosition()) {
            PreferencesManager.TAG_POSITION_BOTTOM_RIGHT -> 90f to 180f
            PreferencesManager.TAG_POSITION_BOTTOM_LEFT -> -90f to 180f
            PreferencesManager.TAG_POSITION_BOTTOM_CENTER -> 180f to 180f
            PreferencesManager.TAG_POSITION_RIGHT_CENTER -> 90f to 180f
            PreferencesManager.TAG_POSITION_LEFT_CENTER -> -90f to 180f
            else -> 90f to 180f
        }
        tagRingMenu.setArcAngles(startAngle, sweepAngle)
        tagRingMenu.show()
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If search overlay is visible, hide it
                if (isSearchOverlayVisible) {
                    hideSearchOverlay()
                    return
                }

                // If in edit mode, exit it
                if (layoutManager.isEditMode()) {
                    exitEditMode()
                    return
                }

                // Clear search in search bar components
                layoutManager.getComponentsByType(ComponentType.SEARCH_BAR).forEach { component ->
                    val searchBar = component as? SearchBarComponent
                    if (searchBar != null && searchBar.getSearchText().isNotEmpty()) {
                        searchBar.clearSearch()
                        return
                    }
                }

                // Clear tag filter
                if (currentTagFilter != null) {
                    clearTagFilter()
                }
            }
        })
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && e2.y - e1.y > 100 && kotlin.math.abs(velocityY) > 100) {
                    handleSwipeDown()
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                handleDoubleTap()
                return true
            }
        })
    }

    private fun handleSwipeDown() {
        when (preferencesManager.getSwipeDownAction()) {
            PreferencesManager.SWIPE_ACTION_NOTIFICATIONS -> expandNotificationPanel()
            PreferencesManager.SWIPE_ACTION_SEARCH -> showSearchOverlay()
        }
    }

    private fun handleDoubleTap() {
        when (preferencesManager.getDoubleTapAction()) {
            PreferencesManager.DOUBLE_TAP_ACTION_LOCK -> {
                Toast.makeText(this, "Lock screen requires device admin permission", Toast.LENGTH_SHORT).show()
            }
            PreferencesManager.DOUBLE_TAP_ACTION_SLEEP -> {
                Toast.makeText(this, "Screen off requires device admin permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Search Overlay Methods
    private fun setupSearchOverlay() {
        // Background click to dismiss
        searchOverlayBackground.setOnClickListener {
            hideSearchOverlay()
        }

        // Search input text change - filter the AppDrawerComponent
        searchOverlayInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                searchOverlayClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterAppDrawer(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Search action on keyboard
        searchOverlayInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // Clear button
        searchOverlayClear.setOnClickListener {
            searchOverlayInput.text.clear()
        }
    }

    private fun showSearchOverlay() {
        if (isSearchOverlayVisible) return
        isSearchOverlayVisible = true

        // Clear previous search
        searchOverlayInput.text.clear()

        // Show overlay
        searchOverlay.visibility = View.VISIBLE

        // Slide down animation for search bar
        searchBarCard.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                // Focus input and show keyboard
                searchOverlayInput.requestFocus()
                showKeyboard(searchOverlayInput)
            }
            .start()
    }

    private fun hideSearchOverlay() {
        if (!isSearchOverlayVisible) return
        isSearchOverlayVisible = false

        // Hide keyboard first
        hideKeyboard()

        // Keep the search filter active - don't clear it!
        // User can tap on filtered apps after closing the search bar

        // Slide up animation
        searchBarCard.animate()
            .translationY(-100f * resources.displayMetrics.density)
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                searchOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun filterAppDrawer(query: String) {
        // Find all AppDrawerComponents and apply the search filter
        layoutManager.getComponentsByType(ComponentType.APP_DRAWER).forEach { component ->
            (component as? AppDrawerComponent)?.applySearchFilter(query)
        }
    }

    private fun clearSearchFilter() {
        // Clear the search filter on all AppDrawerComponents
        filterAppDrawer("")
        // Also clear the search input if overlay is open
        if (isSearchOverlayVisible) {
            searchOverlayInput.text.clear()
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchOverlayInput.windowToken, 0)
        searchOverlayInput.clearFocus()
    }

    @SuppressLint("WrongConstant")
    private fun expandNotificationPanel() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expand: Method = statusBarManager.getMethod("expandNotificationsPanel")
            expand.invoke(statusBarService)
        } catch (e: Exception) {
            // Fallback: do nothing if reflection fails
        }
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        allApps = resolveInfoList
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }

        val installedPackages = allApps.map { it.packageName }.toSet()
        preferencesManager.cleanupUninstalledApps(installedPackages)

        reloadVisibleApps()
    }

    private fun reloadVisibleApps() {
        val hiddenApps = preferencesManager.getHiddenApps()
        visibleApps = allApps.filter { !hiddenApps.contains(it.packageName) }
    }

    private fun filterAppsByTag(tag: TagItem) {
        currentTagFilter = if (tag.id == "all") null else tag

        if (currentTagFilter == null) {
            reloadVisibleApps()
            layoutManager.getComponentsByType(ComponentType.APP_DRAWER).forEach { component ->
                (component as? AppDrawerComponent)?.filterByTag(null)
            }
        } else {
            layoutManager.getComponentsByType(ComponentType.APP_DRAWER).forEach { component ->
                (component as? AppDrawerComponent)?.filterByTag(tag.id)
            }
        }
    }

    private fun clearTagFilter() {
        currentTagFilter = null
        reloadVisibleApps()
        layoutManager.getComponentsByType(ComponentType.APP_DRAWER).forEach { component ->
            (component as? AppDrawerComponent)?.filterByTag(null)
        }
    }

    private fun hideApp(appInfo: AppInfo) {
        preferencesManager.hideApp(appInfo.packageName)
        reloadVisibleApps()
        refreshComponents()
        Toast.makeText(this, "${appInfo.label} hidden", Toast.LENGTH_SHORT).show()
    }

    private fun showTagsDialog(appInfo: AppInfo) {
        val allTags = preferencesManager.getAllTags()
        val currentTagIds = preferencesManager.getTagsForApp(appInfo.packageName).toMutableList()

        if (allTags.isEmpty()) {
            showCreateTagDialog({ newTag ->
                if (newTag != null) {
                    val updatedTagIds = preferencesManager.getTagsForApp(appInfo.packageName).toMutableList()
                    updatedTagIds.add(newTag.id)
                    preferencesManager.setTagsForApp(appInfo.packageName, updatedTagIds)
                    Toast.makeText(this, "Tag '${newTag.label}' added to ${appInfo.label}", Toast.LENGTH_SHORT).show()
                    updateTagRingMenu()
                    showTagsDialog(appInfo)
                }
            })
            return
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val hintText = android.widget.TextView(this).apply {
            text = "Long-press a tag to edit it"
            setTextColor(android.graphics.Color.GRAY)
            textSize = 12f
            setPadding(0, 0, 0, 16)
        }
        container.addView(hintText)

        val checkBoxes = mutableListOf<android.widget.CheckBox>()
        allTags.forEach { tag ->
            val itemLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val colorView = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(40, 40).apply {
                    setMargins(0, 0, 16, 0)
                }
                setBackgroundColor(tag.color)
            }

            val checkBox = android.widget.CheckBox(this).apply {
                text = tag.label
                isChecked = currentTagIds.contains(tag.id)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!currentTagIds.contains(tag.id)) {
                            currentTagIds.add(tag.id)
                        }
                    } else {
                        currentTagIds.remove(tag.id)
                    }
                }
            }
            checkBoxes.add(checkBox)

            itemLayout.addView(colorView)
            itemLayout.addView(checkBox)

            container.addView(itemLayout)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        allTags.forEachIndexed { index, tag ->
            val itemLayout = container.getChildAt(index + 1) as android.widget.LinearLayout
            itemLayout.setOnLongClickListener {
                preferencesManager.setTagsForApp(appInfo.packageName, currentTagIds)
                dialog?.dismiss()
                showCreateTagDialog({ updatedTag ->
                    if (updatedTag != null) {
                        Toast.makeText(this, "Tag '${updatedTag.label}' updated", Toast.LENGTH_SHORT).show()
                        updateTagRingMenu()
                    }
                    showTagsDialog(appInfo)
                }, tag)
                true
            }
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Tags for ${appInfo.label}")
            .setView(scrollView)
            .setPositiveButton("OK") { _, _ ->
                preferencesManager.setTagsForApp(appInfo.packageName, currentTagIds)
                Toast.makeText(this, "Tags updated", Toast.LENGTH_SHORT).show()
                updateTagRingMenu()
            }
            .setNeutralButton("New Tag") { _, _ ->
                preferencesManager.setTagsForApp(appInfo.packageName, currentTagIds)
                showCreateTagDialog({ newTag ->
                    if (newTag != null) {
                        val updatedTagIds = preferencesManager.getTagsForApp(appInfo.packageName).toMutableList()
                        updatedTagIds.add(newTag.id)
                        preferencesManager.setTagsForApp(appInfo.packageName, updatedTagIds)
                        Toast.makeText(this, "Tag '${newTag.label}' created and added", Toast.LENGTH_SHORT).show()
                        updateTagRingMenu()
                    }
                    showTagsDialog(appInfo)
                })
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun showCreateTagDialog(onTagCreated: (TagItem?) -> Unit, existingTag: TagItem? = null) {
        val isEditMode = existingTag != null

        if (!isEditMode && preferencesManager.getAllTags().size >= PreferencesManager.MAX_TAGS) {
            Toast.makeText(this, "Maximum of ${PreferencesManager.MAX_TAGS} tags reached", Toast.LENGTH_SHORT).show()
            onTagCreated(null)
            return
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = android.widget.EditText(this).apply {
            hint = "Tag name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            if (isEditMode) {
                setText(existingTag!!.label)
                setSelection(existingTag.label.length)
            }
        }

        val colorLabel = android.widget.TextView(this).apply {
            text = "Color:"
            setPadding(0, 24, 0, 8)
        }

        val colors = listOf(
            android.graphics.Color.parseColor("#E57373"),
            android.graphics.Color.parseColor("#64B5F6"),
            android.graphics.Color.parseColor("#81C784"),
            android.graphics.Color.parseColor("#FFB74D"),
            android.graphics.Color.parseColor("#BA68C8"),
            android.graphics.Color.parseColor("#4DB6AC"),
            android.graphics.Color.parseColor("#F06292"),
            android.graphics.Color.parseColor("#7986CB")
        )
        var selectedColor = if (isEditMode) existingTag!!.color else colors[0]

        val colorContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val colorViews = mutableListOf<View>()
        colors.forEach { color ->
            val colorView = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(color)
                setOnClickListener {
                    selectedColor = color
                    colorViews.forEach { v ->
                        v.alpha = if (v == this) 1.0f else 0.4f
                    }
                }
                alpha = if (color == selectedColor) 1.0f else 0.4f
            }
            colorViews.add(colorView)
            colorContainer.addView(colorView)
        }

        container.addView(nameInput)
        container.addView(colorLabel)
        container.addView(colorContainer)

        val dialogTitle = if (isEditMode) "Edit Tag" else "Create New Tag"
        val positiveButtonText = if (isEditMode) "Save" else "Create"

        MaterialAlertDialogBuilder(this)
            .setTitle(dialogTitle)
            .setView(container)
            .setPositiveButton(positiveButtonText) { _, _ ->
                val tagName = nameInput.text.toString().trim()
                if (tagName.isNotEmpty()) {
                    if (isEditMode) {
                        val updatedTag = TagItem(existingTag!!.id, tagName, selectedColor)
                        preferencesManager.updateTag(updatedTag)
                        onTagCreated(updatedTag)
                    } else {
                        val tagId = "tag_${System.currentTimeMillis()}"
                        val newTag = TagItem(tagId, tagName, selectedColor)
                        preferencesManager.addTag(newTag)
                        onTagCreated(newTag)
                    }
                } else {
                    Toast.makeText(this, "Tag name cannot be empty", Toast.LENGTH_SHORT).show()
                    onTagCreated(null)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                onTagCreated(null)
            }
            .show()
    }

    private fun updateTagRingMenu() {
        val userTags = preferencesManager.getAllTags()
        val pinnedPositions = preferencesManager.getPinnedTagPositions()

        preferencesManager.cleanupInvalidPins(userTags.map { it.id }.toSet())

        val allTag: TagItem? = TagItem("all", "All", android.graphics.Color.parseColor("#78909C"))

        val ringSizes = listOf(3, 5, 7, 10)

        val rings = mutableListOf<List<TagItem?>>()
        val usedTagIds = mutableSetOf<String>()

        for (ringIndex in 0 until 4) {
            val ringSize = ringSizes[ringIndex]
            val ring = MutableList<TagItem?>(ringSize) { null }

            for ((tagId, pos) in pinnedPositions) {
                if (pos.first == ringIndex && pos.second < ringSize) {
                    val tag = userTags.find { it.id == tagId }
                    if (tag != null) {
                        ring[pos.second] = tag
                        usedTagIds.add(tagId)
                    }
                }
            }

            rings.add(ring)
        }

        val unpinnedTags = userTags.filter { !usedTagIds.contains(it.id) }.toMutableList()

        val ring0 = rings[0].toMutableList()
        ring0[0] = allTag

        for (i in 1 until ringSizes[0]) {
            if (ring0[i] == null && unpinnedTags.isNotEmpty()) {
                ring0[i] = unpinnedTags.removeAt(0)
            }
        }
        rings[0] = ring0

        for (ringIndex in 1 until 4) {
            val ring = rings[ringIndex].toMutableList()
            for (i in 0 until ringSizes[ringIndex]) {
                if (ring[i] == null && unpinnedTags.isNotEmpty()) {
                    ring[i] = unpinnedTags.removeAt(0)
                }
            }
            rings[ringIndex] = ring
        }

        val ribbonItems: List<TagItem?> = listOf(
            TagItem(RIBBON_ACTION_EDIT_TAG, "Edit", android.graphics.Color.parseColor("#455A64")),
            TagItem(RIBBON_ACTION_EDIT_APPS, "Apps", android.graphics.Color.parseColor("#546E7A")),
            TagItem(RIBBON_ACTION_PIN_TAG, "Pin", android.graphics.Color.parseColor("#37474F"))
        )

        val ringConfigs = listOf(
            TagRingMenuView.RingConfig(rings[0], 0.10f, 0.24f),
            TagRingMenuView.RingConfig(rings[1], 0.24f, 0.40f),
            TagRingMenuView.RingConfig(rings[2], 0.40f, 0.58f),
            TagRingMenuView.RingConfig(rings[3], 0.58f, 0.78f),
            TagRingMenuView.RingConfig(ribbonItems, 0.78f, 0.95f, isRibbon = true)
        )

        tagRingMenu.setRings(ringConfigs)

        if (currentRibbonAction == RIBBON_ACTION_PIN_TAG) {
            updatePinIndicators()
        }
    }

    private fun updatePinIndicators() {
        val pinnedPositions = preferencesManager.getPinnedTagPositions()
        val positionSet = pinnedPositions.values.toSet()
        tagRingMenu.setPinnedPositions(positionSet)
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            startActivity(it)
        }
    }

    private fun registerAppChangeReceiver() {
        appChangeReceiver = AppChangeReceiver {
            runOnUiThread {
                loadApps()
                refreshComponents()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appChangeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(appChangeReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appChangeReceiver?.let {
            unregisterReceiver(it)
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Update edit mode toolbar margin to account for status bar
            val toolbarParams = editModeToolbar.layoutParams as android.widget.FrameLayout.LayoutParams
            toolbarParams.topMargin = insets.top
            editModeToolbar.layoutParams = toolbarParams

            // Let the desktop canvas handle its own insets if needed
            desktopCanvas.setPadding(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }
}
