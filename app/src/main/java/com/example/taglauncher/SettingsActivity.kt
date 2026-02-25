package com.example.taglauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var settingsAdapter: SettingsAdapter

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { performExport(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { performImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        setContentView(R.layout.activity_settings)

        preferencesManager = PreferencesManager(this)

        setupToolbar()
        setupRecyclerView()
        setupWindowInsets()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.settingsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        settingsAdapter = SettingsAdapter(buildSettingsItems()) { item ->
            handleSettingClick(item)
        }
        recyclerView.adapter = settingsAdapter
    }

    private fun buildSettingsItems(): List<SettingsItem> {
        return listOf(
            // Gestures Category
            SettingsItem.Header("Gestures"),
            SettingsItem.Choice(
                id = "swipe_down",
                title = "Swipe Down Action",
                summary = getSwipeDownLabel(preferencesManager.getSwipeDownAction()),
                icon = android.R.drawable.ic_menu_revert
            ),
            SettingsItem.Choice(
                id = "double_tap",
                title = "Double Tap Action",
                summary = getDoubleTapLabel(preferencesManager.getDoubleTapAction()),
                icon = android.R.drawable.ic_menu_compass
            ),

            // Apps Category
            SettingsItem.Header("Apps"),
            SettingsItem.Navigation(
                id = "hidden_apps",
                title = "Hidden Apps",
                summary = "${preferencesManager.getHiddenApps().size} apps hidden",
                icon = android.R.drawable.ic_menu_close_clear_cancel
            ),

            // Data Category
            SettingsItem.Header("Data"),
            SettingsItem.Navigation(
                id = "export_tags",
                title = "Export Tags",
                summary = "Export app tags to JSON file",
                icon = android.R.drawable.ic_menu_upload
            ),
            SettingsItem.Navigation(
                id = "import_tags",
                title = "Import Tags",
                summary = "Import app tags from JSON file",
                icon = android.R.drawable.ic_menu_save
            ),

            // Tag Button Category
            SettingsItem.Header("Tag Button"),
            SettingsItem.Choice(
                id = "tag_icon",
                title = "Button Icon",
                summary = getTagIconLabel(preferencesManager.getTagButtonIcon()),
                icon = android.R.drawable.ic_menu_gallery
            ),
            SettingsItem.Choice(
                id = "tag_size",
                title = "Button Size",
                summary = "${preferencesManager.getTagButtonSize()} dp",
                icon = android.R.drawable.ic_menu_zoom
            ),
            SettingsItem.Choice(
                id = "tag_alpha",
                title = "Button Transparency",
                summary = "${preferencesManager.getTagButtonAlpha()}%",
                icon = android.R.drawable.ic_menu_view
            ),
            SettingsItem.Choice(
                id = "tag_color",
                title = "Button Color",
                summary = "Tap to change",
                icon = android.R.drawable.ic_menu_edit
            ),
            SettingsItem.Choice(
                id = "tag_position",
                title = "Button Position",
                summary = getTagPositionLabel(preferencesManager.getTagButtonPosition()),
                icon = android.R.drawable.ic_menu_myplaces
            ),
            SettingsItem.Choice(
                id = "tag_offset_x",
                title = "Horizontal Offset",
                summary = "${preferencesManager.getTagButtonOffsetX()} px",
                icon = android.R.drawable.ic_menu_crop
            ),
            SettingsItem.Choice(
                id = "tag_offset_y",
                title = "Vertical Offset",
                summary = "${preferencesManager.getTagButtonOffsetY()} px",
                icon = android.R.drawable.ic_menu_crop
            ),

            // About Category
            SettingsItem.Header("About"),
            SettingsItem.Info(
                id = "version",
                title = "Version",
                summary = getVersionName(),
                icon = android.R.drawable.ic_menu_info_details
            ),
            SettingsItem.Navigation(
                id = "reset",
                title = "Reset to Defaults",
                summary = "Restore all settings to default values",
                icon = android.R.drawable.ic_menu_rotate
            )
        )
    }

    private fun handleSettingClick(item: SettingsItem) {
        when (item) {
            is SettingsItem.Toggle -> handleToggle(item)
            is SettingsItem.Choice -> handleChoice(item)
            is SettingsItem.Navigation -> handleNavigation(item)
            else -> {}
        }
    }

    private fun handleToggle(item: SettingsItem.Toggle) {
        // No toggle settings remaining after component-based refactor
        refreshSettings()
    }

    private fun handleChoice(item: SettingsItem.Choice) {
        when (item.id) {
            "swipe_down" -> showSwipeDownDialog()
            "double_tap" -> showDoubleTapDialog()
            "tag_icon" -> showTagIconDialog()
            "tag_size" -> showTagSizeDialog()
            "tag_alpha" -> showTagAlphaDialog()
            "tag_color" -> showTagColorDialog()
            "tag_position" -> showTagPositionDialog()
            "tag_offset_x" -> showTagOffsetDialog(isHorizontal = true)
            "tag_offset_y" -> showTagOffsetDialog(isHorizontal = false)
        }
    }

    private fun handleNavigation(item: SettingsItem.Navigation) {
        when (item.id) {
            "hidden_apps" -> {
                startActivity(Intent(this, HiddenAppsActivity::class.java))
            }
            "export_tags" -> exportTags()
            "import_tags" -> importTags()
            "reset" -> showResetConfirmDialog()
        }
    }

    private fun showSwipeDownDialog() {
        val options = arrayOf("Open Notifications", "Focus Search Bar")
        val currentIndex = preferencesManager.getSwipeDownAction()

        MaterialAlertDialogBuilder(this)
            .setTitle("Swipe Down Action")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                preferencesManager.setSwipeDownAction(which)
                refreshSettings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDoubleTapDialog() {
        val options = arrayOf("None", "Lock Screen", "Screen Off")
        val currentIndex = preferencesManager.getDoubleTapAction()

        MaterialAlertDialogBuilder(this)
            .setTitle("Double Tap Action")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                preferencesManager.setDoubleTapAction(which)
                refreshSettings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTagIconDialog() {
        val options = arrayOf(
            "Tag",
            "Label",
            "Circle",
            "Grid",
            "Star",
            "Plus"
        )
        val currentIndex = preferencesManager.getTagButtonIcon()

        MaterialAlertDialogBuilder(this)
            .setTitle("Tag Button Icon")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                preferencesManager.setTagButtonIcon(which)
                refreshSettings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTagSizeDialog() {
        val currentValue = preferencesManager.getTagButtonSize()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val valueText = android.widget.TextView(this).apply {
            text = "$currentValue dp"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
        }

        val seekBar = android.widget.SeekBar(this).apply {
            max = 48  // Range: 32 to 80
            progress = currentValue - 32
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 32
                    valueText.text = "$value dp"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }

        container.addView(valueText)
        container.addView(android.widget.Space(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 16
            )
        })
        container.addView(seekBar)

        MaterialAlertDialogBuilder(this)
            .setTitle("Button Size")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val newValue = seekBar.progress + 32
                preferencesManager.setTagButtonSize(newValue)
                refreshSettings()
            }
            .setNeutralButton("Reset") { _, _ ->
                preferencesManager.setTagButtonSize(PreferencesManager.DEFAULT_TAG_BUTTON_SIZE)
                refreshSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTagAlphaDialog() {
        val currentValue = preferencesManager.getTagButtonAlpha()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val valueText = android.widget.TextView(this).apply {
            text = "$currentValue%"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
        }

        val description = android.widget.TextView(this).apply {
            text = "10% = Very transparent, 100% = Fully opaque"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
        }

        val seekBar = android.widget.SeekBar(this).apply {
            max = 90  // Range: 10 to 100
            progress = currentValue - 10
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 10
                    valueText.text = "$value%"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }

        container.addView(valueText)
        container.addView(android.widget.Space(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 16
            )
        })
        container.addView(seekBar)
        container.addView(android.widget.Space(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 8
            )
        })
        container.addView(description)

        MaterialAlertDialogBuilder(this)
            .setTitle("Button Transparency")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val newValue = seekBar.progress + 10
                preferencesManager.setTagButtonAlpha(newValue)
                refreshSettings()
            }
            .setNeutralButton("Reset") { _, _ ->
                preferencesManager.setTagButtonAlpha(PreferencesManager.DEFAULT_TAG_BUTTON_ALPHA)
                refreshSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTagColorDialog() {
        val colors = listOf(
            android.graphics.Color.parseColor("#000000") to "Black",
            android.graphics.Color.parseColor("#FFFFFF") to "White",
            android.graphics.Color.parseColor("#E57373") to "Red",
            android.graphics.Color.parseColor("#64B5F6") to "Blue",
            android.graphics.Color.parseColor("#81C784") to "Green",
            android.graphics.Color.parseColor("#FFB74D") to "Orange",
            android.graphics.Color.parseColor("#BA68C8") to "Purple",
            android.graphics.Color.parseColor("#4DB6AC") to "Teal",
            android.graphics.Color.parseColor("#F06292") to "Pink",
            android.graphics.Color.parseColor("#78909C") to "Blue Grey"
        )

        val currentColor = preferencesManager.getTagButtonColor()
        var selectedColor = currentColor

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val colorContainer = android.widget.GridLayout(this).apply {
            columnCount = 5
            rowCount = 2
        }

        val colorViews = mutableListOf<android.view.View>()
        colors.forEach { (color, _) ->
            val colorView = android.view.View(this).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 80
                    height = 80
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(color)
                alpha = if (color == currentColor) 1.0f else 0.4f
                setOnClickListener {
                    selectedColor = color
                    colorViews.forEach { v ->
                        v.alpha = if (v == this) 1.0f else 0.4f
                    }
                }
            }
            colorViews.add(colorView)
            colorContainer.addView(colorView)
        }

        container.addView(colorContainer)

        MaterialAlertDialogBuilder(this)
            .setTitle("Button Color")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                preferencesManager.setTagButtonColor(selectedColor)
                refreshSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTagPositionDialog() {
        val options = arrayOf(
            "Bottom Right",
            "Bottom Left",
            "Bottom Center",
            "Right Center",
            "Left Center"
        )
        val currentIndex = preferencesManager.getTagButtonPosition()

        MaterialAlertDialogBuilder(this)
            .setTitle("Tag Button Position")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                preferencesManager.setTagButtonPosition(which)
                refreshSettings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTagOffsetDialog(isHorizontal: Boolean) {
        val title = if (isHorizontal) "Horizontal Offset" else "Vertical Offset"
        val currentValue = if (isHorizontal) {
            preferencesManager.getTagButtonOffsetX()
        } else {
            preferencesManager.getTagButtonOffsetY()
        }

        // Create a layout with SeekBar and value display
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val valueText = android.widget.TextView(this).apply {
            text = "$currentValue px"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
        }

        val description = android.widget.TextView(this).apply {
            text = if (isHorizontal) {
                "Negative = Left, Positive = Right"
            } else {
                "Negative = Up, Positive = Down"
            }
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
        }

        val seekBar = android.widget.SeekBar(this).apply {
            max = 1000  // Range: -500 to +500
            progress = currentValue + 500  // Convert to 0-1000 range
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress - 500
                    valueText.text = "$value px"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }

        container.addView(valueText)
        container.addView(android.widget.Space(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 16
            )
        })
        container.addView(seekBar)
        container.addView(android.widget.Space(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 8
            )
        })
        container.addView(description)

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val newValue = seekBar.progress - 500
                if (isHorizontal) {
                    preferencesManager.setTagButtonOffsetX(newValue)
                } else {
                    preferencesManager.setTagButtonOffsetY(newValue)
                }
                refreshSettings()
            }
            .setNeutralButton("Reset") { _, _ ->
                if (isHorizontal) {
                    preferencesManager.setTagButtonOffsetX(0)
                } else {
                    preferencesManager.setTagButtonOffsetY(0)
                }
                refreshSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset to Defaults")
            .setMessage("This will reset all launcher settings to their default values. This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                preferencesManager.resetToDefaults()
                refreshSettings()
                Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==================== Import/Export ====================

    private fun exportTags() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "taglauncher_tags_${dateFormat.format(Date())}.json"
        exportLauncher.launch(fileName)
    }

    private fun performExport(uri: Uri) {
        try {
            val allApps = loadAllApps()
            val jsonContent = preferencesManager.exportTagsToJson(allApps)

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray())
            }

            val tagCount = preferencesManager.getAllTags().size
            Toast.makeText(this, "Exported $tagCount tags successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importTags() {
        importLauncher.launch(arrayOf("application/json", "*/*"))
    }

    private fun performImport(uri: Uri) {
        try {
            val jsonContent = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: throw Exception("Could not read file")

            val installedPackages = loadAllApps().map { it.packageName }.toSet()
            val result = preferencesManager.importTagsFromJson(jsonContent, installedPackages)

            if (result.success) {
                val message = buildString {
                    append("Import complete!\n")
                    append("Tags imported: ${result.tagsImported}\n")
                    append("Apps updated: ${result.appsUpdated}\n")
                    if (result.appsSkipped > 0) {
                        append("Apps skipped (not installed): ${result.appsSkipped}")
                    }
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle("Import Successful")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                Toast.makeText(this, "Import failed: ${result.error}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAllApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        return resolveInfoList
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
    }

    private fun refreshSettings() {
        settingsAdapter.updateItems(buildSettingsItems())
    }

    override fun onResume() {
        super.onResume()
        refreshSettings()
    }

    private fun getSwipeDownLabel(action: Int): String {
        return when (action) {
            PreferencesManager.SWIPE_ACTION_NOTIFICATIONS -> "Open Notifications"
            PreferencesManager.SWIPE_ACTION_SEARCH -> "Focus Search Bar"
            else -> "Open Notifications"
        }
    }

    private fun getDoubleTapLabel(action: Int): String {
        return when (action) {
            PreferencesManager.DOUBLE_TAP_ACTION_NONE -> "None"
            PreferencesManager.DOUBLE_TAP_ACTION_LOCK -> "Lock Screen"
            PreferencesManager.DOUBLE_TAP_ACTION_SLEEP -> "Screen Off"
            else -> "None"
        }
    }

    private fun getTagPositionLabel(position: Int): String {
        return when (position) {
            PreferencesManager.TAG_POSITION_BOTTOM_RIGHT -> "Bottom Right"
            PreferencesManager.TAG_POSITION_BOTTOM_LEFT -> "Bottom Left"
            PreferencesManager.TAG_POSITION_BOTTOM_CENTER -> "Bottom Center"
            PreferencesManager.TAG_POSITION_RIGHT_CENTER -> "Right Center"
            PreferencesManager.TAG_POSITION_LEFT_CENTER -> "Left Center"
            else -> "Bottom Right"
        }
    }

    private fun getTagIconLabel(icon: Int): String {
        return when (icon) {
            PreferencesManager.TAG_ICON_TAG -> "Tag"
            PreferencesManager.TAG_ICON_LABEL -> "Label"
            PreferencesManager.TAG_ICON_CIRCLE -> "Circle"
            PreferencesManager.TAG_ICON_GRID -> "Grid"
            PreferencesManager.TAG_ICON_STAR -> "Star"
            PreferencesManager.TAG_ICON_PLUS -> "Plus"
            else -> "Tag"
        }
    }

    private fun getVersionName(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }

    private fun setupWindowInsets() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, insets.top, 0, 0)
            recyclerView.setPadding(0, 0, 0, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
