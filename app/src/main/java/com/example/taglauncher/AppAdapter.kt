package com.example.taglauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppAdapter(
    private val context: Context,
    private var appList: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onHideApp: ((AppInfo) -> Unit)? = null,
    private val onManageTags: ((AppInfo) -> Unit)? = null,
    private val getDescription: ((AppInfo) -> String?)? = null,
    private val setDescription: ((AppInfo, String) -> Unit)? = null,
    private var showLabels: Boolean = true,
    private var iconFrameSizeDp: Int = 48,
    private var iconSizeDp: Int = 48
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>(), Filterable {

    private var filteredList: List<AppInfo> = appList

    // Additional style properties
    private var iconShape: String = "default"
    private var iconPaddingDp: Int = 0
    private var labelSizeSp: Int = 12
    private var labelColor: Int = Color.WHITE
    private var labelMaxLines: Int = 1
    private var labelMarginTopDp: Int = 4

    private val density = context.resources.displayMetrics.density
    private val selectedPackages = mutableSetOf<String>()
    private var selectionMode = false
    private var longPressEnabled = true

    var onSelectionChanged: ((Int) -> Unit)? = null

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIconFrame: FrameLayout = itemView.findViewById(R.id.appIconFrame)
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appLabel: TextView = itemView.findViewById(R.id.appLabel)
        val selectionOverlay: View = itemView.findViewById(R.id.appSelectionOverlay)
        val selectionCheck: ImageView = itemView.findViewById(R.id.appSelectionCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = filteredList[position]
        val isSelected = selectedPackages.contains(appInfo.packageName)
        holder.appIcon.setImageDrawable(appInfo.icon)

        // Apply icon frame size
        val iconFrameSizePx = (iconFrameSizeDp * density).toInt()
        holder.appIconFrame.layoutParams.width = iconFrameSizePx
        holder.appIconFrame.layoutParams.height = iconFrameSizePx

        // Apply icon size
        val iconSizePx = (iconSizeDp * density).toInt()
        holder.appIcon.layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)

        // Apply icon padding
        val iconPaddingPx = (iconPaddingDp * density).toInt()
        holder.appIcon.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)

        // Apply icon shape clipping
        applyIconShape(holder.appIconFrame, iconFrameSizePx)

        // Apply label styling
        if (showLabels) {
            holder.appLabel.text = appInfo.label
            holder.appLabel.visibility = View.VISIBLE
            holder.appLabel.textSize = labelSizeSp.toFloat()
            holder.appLabel.setTextColor(labelColor)
            holder.appLabel.maxLines = labelMaxLines

            // Apply label margin top
            val labelParams = holder.appLabel.layoutParams as? LinearLayout.LayoutParams
            labelParams?.let {
                it.topMargin = (labelMarginTopDp * density).toInt()
                holder.appLabel.layoutParams = it
            }
        } else {
            holder.appLabel.visibility = View.GONE
        }

        holder.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.selectionCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(appInfo)
            } else {
                onAppClick(appInfo)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!longPressEnabled) {
                return@setOnLongClickListener false
            }
            if (selectionMode) {
                toggleSelection(appInfo)
            } else {
                showContextMenu(appInfo)
            }
            true
        }
    }

    /**
     * Apply icon shape clipping using ViewOutlineProvider.
     */
    private fun applyIconShape(targetView: View, sizePx: Int) {
        when (iconShape) {
            "circle" -> {
                targetView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, sizePx, sizePx)
                    }
                }
                targetView.clipToOutline = true
            }
            "rounded" -> {
                targetView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val cornerRadius = sizePx * 0.2f // 20% corner radius
                        outline.setRoundRect(0, 0, sizePx, sizePx, cornerRadius)
                    }
                }
                targetView.clipToOutline = true
            }
            "square" -> {
                targetView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRect(0, 0, sizePx, sizePx)
                    }
                }
                targetView.clipToOutline = true
            }
            else -> {
                // Default: no clipping
                targetView.outlineProvider = ViewOutlineProvider.BACKGROUND
                targetView.clipToOutline = false
            }
        }
    }

    override fun getItemCount(): Int = filteredList.size

    private fun showContextMenu(appInfo: AppInfo) {
        val density = context.resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()

        val initialDescription = getDescription?.invoke(appInfo)?.trim().orEmpty()

        val actionsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null
        var shouldSave = true
        var hasSaved = false

        val descriptionInput = EditText(context).apply {
            hint = context.getString(R.string.description)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 6
            setText(initialDescription)
            setSelection(text.length)
        }

        fun persistDescription() {
            if (hasSaved || setDescription == null) return
            val updated = descriptionInput.text.toString().trim()
            if (updated != initialDescription) {
                setDescription.invoke(appInfo, updated)
            }
            hasSaved = true
        }

        fun addAction(label: String, action: () -> Unit) {
            val item = TextView(context).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                val outValue = TypedValue()
                if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                    setBackgroundResource(outValue.resourceId)
                }
                setOnClickListener {
                    persistDescription()
                    dialog?.dismiss()
                    action()
                }
            }
            actionsContainer.addView(item)
        }

        if (onSelectionChanged != null) {
            addAction("Select") {
                startSelection(appInfo)
            }
        }
        if (onManageTags != null) {
            addAction(context.getString(R.string.tags)) { onManageTags.invoke(appInfo) }
        }
        if (onHideApp != null) {
            addAction(context.getString(R.string.hide_app)) { onHideApp.invoke(appInfo) }
        }
        addAction(context.getString(R.string.app_info)) { openAppInfo(appInfo.packageName) }
        addAction(context.getString(R.string.uninstall)) { uninstallApp(appInfo.packageName) }

        val descriptionLabel = TextView(context).apply {
            text = context.getString(R.string.description)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
            setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(4))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(8))
            addView(actionsContainer)
            addView(descriptionLabel)
            addView(descriptionInput)
        }

        val scrollView = ScrollView(context).apply {
            addView(container)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(appInfo.label)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .setNegativeButton(android.R.string.cancel) { _, _ -> shouldSave = false }
            .create()

        dialog.setOnDismissListener {
            if (shouldSave) {
                persistDescription()
            }
        }

        dialog.show()
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun updateList(newList: List<AppInfo>) {
        appList = newList
        filteredList = newList
        if (selectedPackages.isNotEmpty()) {
            val validPackages = newList.map { it.packageName }.toSet()
            selectedPackages.retainAll(validPackages)
            selectionMode = selectedPackages.isNotEmpty()
            onSelectionChanged?.invoke(selectedPackages.size)
        }
        notifyDataSetChanged()
    }

    fun getSelectedApps(): List<AppInfo> {
        if (selectedPackages.isEmpty()) return emptyList()
        return appList.filter { selectedPackages.contains(it.packageName) }
    }

    fun hasSelection(): Boolean {
        return selectedPackages.isNotEmpty()
    }

    fun clearSelection() {
        if (selectedPackages.isEmpty()) return
        selectedPackages.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun setLongPressEnabled(enabled: Boolean) {
        longPressEnabled = enabled
    }

    fun selectAllVisible() {
        if (filteredList.isEmpty()) return
        selectedPackages.clear()
        filteredList.forEach { app -> selectedPackages.add(app.packageName) }
        selectionMode = selectedPackages.isNotEmpty()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedPackages.size)
    }

    private fun startSelection(appInfo: AppInfo) {
        selectionMode = true
        selectedPackages.clear()
        selectedPackages.add(appInfo.packageName)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedPackages.size)
    }

    private fun toggleSelection(appInfo: AppInfo) {
        if (selectedPackages.contains(appInfo.packageName)) {
            selectedPackages.remove(appInfo.packageName)
        } else {
            selectedPackages.add(appInfo.packageName)
        }
        if (selectedPackages.isEmpty()) {
            selectionMode = false
        } else {
            selectionMode = true
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedPackages.size)
    }

    fun setShowLabels(show: Boolean) {
        showLabels = show
        notifyDataSetChanged()
    }

    fun setIconSize(sizeDp: Int) {
        iconSizeDp = sizeDp
        notifyDataSetChanged()
    }

    fun setIconFrameSize(sizeDp: Int) {
        iconFrameSizeDp = sizeDp
        notifyDataSetChanged()
    }

    fun setIconShape(shape: String) {
        iconShape = shape
        notifyDataSetChanged()
    }

    fun setIconPadding(paddingDp: Int) {
        iconPaddingDp = paddingDp
        notifyDataSetChanged()
    }

    fun setLabelSize(sizeSp: Int) {
        labelSizeSp = sizeSp
        notifyDataSetChanged()
    }

    fun setLabelColor(color: Int) {
        labelColor = color
        notifyDataSetChanged()
    }

    fun setLabelMaxLines(maxLines: Int) {
        labelMaxLines = maxLines
        notifyDataSetChanged()
    }

    fun setLabelMarginTop(marginDp: Int) {
        labelMarginTopDp = marginDp
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase()?.trim() ?: ""
                val results = FilterResults()

                results.values = if (query.isEmpty()) {
                    appList
                } else {
                    appList.filter { app ->
                        app.label.lowercase().contains(query)
                    }
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as? List<AppInfo> ?: appList
                notifyDataSetChanged()
            }
        }
    }
}
