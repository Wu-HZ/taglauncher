package com.example.taglauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
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

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appLabel: TextView = itemView.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = filteredList[position]
        holder.appIcon.setImageDrawable(appInfo.icon)

        // Apply icon size
        val iconSizePx = (iconSizeDp * density).toInt()
        holder.appIcon.layoutParams.width = iconSizePx
        holder.appIcon.layoutParams.height = iconSizePx

        // Apply icon padding
        val iconPaddingPx = (iconPaddingDp * density).toInt()
        holder.appIcon.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)

        // Apply icon shape clipping
        applyIconShape(holder.appIcon, iconSizePx)

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

        holder.itemView.setOnClickListener {
            onAppClick(appInfo)
        }

        holder.itemView.setOnLongClickListener {
            showContextMenu(appInfo)
            true
        }
    }

    /**
     * Apply icon shape clipping using ViewOutlineProvider.
     */
    private fun applyIconShape(imageView: ImageView, sizePx: Int) {
        when (iconShape) {
            "circle" -> {
                imageView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, sizePx, sizePx)
                    }
                }
                imageView.clipToOutline = true
            }
            "rounded" -> {
                imageView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val cornerRadius = sizePx * 0.2f // 20% corner radius
                        outline.setRoundRect(0, 0, sizePx, sizePx, cornerRadius)
                    }
                }
                imageView.clipToOutline = true
            }
            "square" -> {
                imageView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRect(0, 0, sizePx, sizePx)
                    }
                }
                imageView.clipToOutline = true
            }
            else -> {
                // Default: no clipping
                imageView.outlineProvider = ViewOutlineProvider.BACKGROUND
                imageView.clipToOutline = false
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
        notifyDataSetChanged()
    }

    fun setShowLabels(show: Boolean) {
        showLabels = show
        notifyDataSetChanged()
    }

    fun setIconSize(sizeDp: Int) {
        iconSizeDp = sizeDp
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
