package com.example.taglauncher

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.LruCache
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
import java.util.Locale
import java.util.concurrent.Executors
import com.example.taglauncher.AppIconOverride
import com.example.taglauncher.ColorSettingUtils

class AppAdapter(
    private val context: Context,
    private var appList: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onHideApp: ((AppInfo) -> Unit)? = null,
    private val onUnhideApp: ((AppInfo) -> Unit)? = null,
    private val onManageTags: ((AppInfo) -> Unit)? = null,
    private val onEditIcon: ((AppInfo) -> Unit)? = null,
    private val getDescription: ((AppInfo) -> String?)? = null,
    private val setDescription: ((AppInfo, String) -> Unit)? = null,
    private val getIconOverride: ((String) -> AppIconOverride?)? = null,
    private val isAppHidden: ((AppInfo) -> Boolean)? = null,
    private var iconFrameBackgroundColor: Int = Color.TRANSPARENT,
    private var showLabels: Boolean = true,
    private var iconFrameSizeDp: Int = 48,
    private var iconSizeDp: Int = 48
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>(), Filterable {

    private var filteredList: List<AppInfo> = appList
    private var highlightedPackageName: String? = null

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
    private var resolvedIconFrameBackgroundColor: Int =
        ColorSettingUtils.resolveColor(context, iconFrameBackgroundColor)
    private val outlineProviderCache = mutableMapOf<String, ViewOutlineProvider>()
    private val renderedAssetCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pendingAssetLoads = mutableSetOf<String>()
    private val assetExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    var onSelectionChanged: ((Int) -> Unit)? = null

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIconFrame: FrameLayout = itemView.findViewById(R.id.appIconFrame)
        val appIconBackground: ImageView = itemView.findViewById(R.id.appIconBackground)
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appLabel: TextView = itemView.findViewById(R.id.appLabel)
        val touchpadHighlight: View = itemView.findViewById(R.id.appTouchpadHighlight)
        val selectionOverlay: View = itemView.findViewById(R.id.appSelectionOverlay)
        val selectionCheck: ImageView = itemView.findViewById(R.id.appSelectionCheck)
        var boundAppInfo: AppInfo? = null
        var appliedFrameSizePx: Int = -1
        var appliedIconSizePx: Int = -1
        var appliedIconPaddingPx: Int = Int.MIN_VALUE
        var appliedIconShapeKey: String = ""
        var appliedBackgroundKey: String? = null
        var appliedFrameColor: Int = Int.MIN_VALUE
        var appliedIconKey: String? = null
        var appliedLabelVisibility: Int = Int.MIN_VALUE
        var appliedLabelText: String? = null
        var appliedLabelSizeSp: Int = Int.MIN_VALUE
        var appliedLabelColor: Int = Int.MIN_VALUE
        var appliedLabelMaxLines: Int = Int.MIN_VALUE
        var appliedLabelMarginTopPx: Int = Int.MIN_VALUE
        var appliedTouchpadHighlightState: Boolean = false
        var appliedSelectionState: Boolean = false
    }

    init {
        setHasStableIds(true)
        preloadRenderedAssets(filteredList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view).also { holder ->
            holder.itemView.setOnClickListener {
                val appInfo = holder.boundAppInfo ?: return@setOnClickListener
                if (selectionMode) {
                    toggleSelection(appInfo)
                } else {
                    onAppClick(appInfo)
                }
            }

            holder.itemView.setOnLongClickListener {
                val appInfo = holder.boundAppInfo ?: return@setOnLongClickListener false
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
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = filteredList[position]
        holder.boundAppInfo = appInfo
        val isSelected = selectedPackages.contains(appInfo.packageName)
        val isTouchpadHighlighted = !isSelected && highlightedPackageName == appInfo.packageName
        val iconOverride = getIconOverride?.invoke(appInfo.packageName)
        val scalePercent = (iconOverride?.scalePercent ?: 100).coerceIn(50, 150)

        // Apply icon frame size
        val iconFrameSizePx = (iconFrameSizeDp * density).toInt()
        if (holder.appliedFrameSizePx != iconFrameSizePx) {
            holder.appIconFrame.layoutParams = holder.appIconFrame.layoutParams.apply {
                width = iconFrameSizePx
                height = iconFrameSizePx
            }
            holder.appliedFrameSizePx = iconFrameSizePx
        }

        // Apply icon size
        val scaledIconSizeDp = (iconSizeDp * (scalePercent / 100f)).toInt().coerceAtLeast(1)
        val iconSizePx = (scaledIconSizeDp * density).toInt()
        if (holder.appliedIconSizePx != iconSizePx) {
            val iconLayoutParams = (holder.appIcon.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            iconLayoutParams.width = iconSizePx
            iconLayoutParams.height = iconSizePx
            iconLayoutParams.gravity = Gravity.CENTER
            holder.appIcon.layoutParams = iconLayoutParams
            holder.appliedIconSizePx = iconSizePx
        }

        // Apply icon padding
        val iconPaddingPx = (iconPaddingDp * density).toInt()
        if (holder.appliedIconPaddingPx != iconPaddingPx) {
            holder.appIcon.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)
            holder.appliedIconPaddingPx = iconPaddingPx
        }

        // Apply icon shape clipping
        val shapeKey = "$iconShape:$iconFrameSizePx"
        if (holder.appliedIconShapeKey != shapeKey) {
            applyIconShape(holder.appIconFrame, iconFrameSizePx)
            holder.appliedIconShapeKey = shapeKey
        }

        val bgImageUri = iconOverride?.backgroundImageUri
        bindBackgroundImage(holder, appInfo, bgImageUri, iconFrameSizePx)

        val bgColor = iconOverride?.backgroundColor
        val hasBgImage = iconOverride?.backgroundImageUri != null
        val fallbackColor = resolvedIconFrameBackgroundColor.takeIf { it != Color.TRANSPARENT }
        val finalBgColor = when {
            bgColor != null -> bgColor
            hasBgImage -> Color.TRANSPARENT
            fallbackColor != null -> fallbackColor
            else -> Color.TRANSPARENT
        }
        if (holder.appliedFrameColor != finalBgColor) {
            holder.appIconFrame.setBackgroundColor(finalBgColor)
            holder.appliedFrameColor = finalBgColor
        }

        val iconUri = iconOverride?.iconUri
        bindIcon(holder, appInfo, iconUri, iconSizePx)

        // Apply label styling
        val labelVisibility = if (showLabels) View.VISIBLE else View.GONE
        if (holder.appliedLabelVisibility != labelVisibility) {
            holder.appLabel.visibility = labelVisibility
            holder.appliedLabelVisibility = labelVisibility
        }
        if (showLabels) {
            if (holder.appliedLabelText != appInfo.label) {
                holder.appLabel.text = appInfo.label
                holder.appliedLabelText = appInfo.label
            }
            if (holder.appliedLabelSizeSp != labelSizeSp) {
                holder.appLabel.textSize = labelSizeSp.toFloat()
                holder.appliedLabelSizeSp = labelSizeSp
            }
            if (holder.appliedLabelColor != labelColor) {
                holder.appLabel.setTextColor(labelColor)
                holder.appliedLabelColor = labelColor
            }
            if (holder.appliedLabelMaxLines != labelMaxLines) {
                holder.appLabel.maxLines = labelMaxLines
                holder.appliedLabelMaxLines = labelMaxLines
            }

            val labelMarginTopPx = (labelMarginTopDp * density).toInt()
            if (holder.appliedLabelMarginTopPx != labelMarginTopPx) {
                val labelParams = holder.appLabel.layoutParams as? LinearLayout.LayoutParams
                labelParams?.let {
                    it.topMargin = labelMarginTopPx
                    holder.appLabel.layoutParams = it
                }
                holder.appliedLabelMarginTopPx = labelMarginTopPx
            }
        }

        if (holder.appliedTouchpadHighlightState != isTouchpadHighlighted) {
            holder.touchpadHighlight.visibility = if (isTouchpadHighlighted) View.VISIBLE else View.GONE
            holder.appliedTouchpadHighlightState = isTouchpadHighlighted
        }

        if (holder.appliedSelectionState != isSelected) {
            holder.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.selectionCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.appliedSelectionState = isSelected
        }
    }

    /**
     * Apply icon shape clipping using ViewOutlineProvider.
     */
    private fun applyIconShape(targetView: View, sizePx: Int) {
        val cacheKey = "$iconShape:$sizePx"
        val outlineProvider = outlineProviderCache.getOrPut(cacheKey) {
            when (iconShape) {
                "circle" -> object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, sizePx, sizePx)
                    }
                }
                "rounded" -> object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val cornerRadius = sizePx * 0.2f
                        outline.setRoundRect(0, 0, sizePx, sizePx, cornerRadius)
                    }
                }
                "square" -> object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRect(0, 0, sizePx, sizePx)
                    }
                }
                else -> ViewOutlineProvider.BACKGROUND
            }
        }

        when (iconShape) {
            "circle" -> {
                targetView.outlineProvider = outlineProvider
                targetView.clipToOutline = true
            }
            "rounded" -> {
                targetView.outlineProvider = outlineProvider
                targetView.clipToOutline = true
            }
            "square" -> {
                targetView.outlineProvider = outlineProvider
                targetView.clipToOutline = true
            }
            else -> {
                // Default: no clipping
                targetView.outlineProvider = outlineProvider
                targetView.clipToOutline = false
            }
        }
    }

    override fun getItemId(position: Int): Long = filteredList[position].packageName.hashCode().toLong()

    override fun getItemCount(): Int = filteredList.size

    private fun bindBackgroundImage(
        holder: AppViewHolder,
        appInfo: AppInfo,
        backgroundUri: String?,
        frameSizePx: Int
    ) {
        val requestKey = backgroundUri?.let { "bg:$it:$frameSizePx" } ?: ""
        val keyChanged = holder.appliedBackgroundKey != requestKey
        holder.appliedBackgroundKey = requestKey

        if (backgroundUri == null) {
            holder.appIconBackground.setImageDrawable(null)
            holder.appIconBackground.visibility = View.GONE
            return
        }

        holder.appIconBackground.visibility = View.VISIBLE
        val cachedBitmap = renderedAssetCache.get(requestKey)
        if (cachedBitmap != null) {
            holder.appIconBackground.setImageBitmap(cachedBitmap)
            return
        }

        if (keyChanged) {
            holder.appIconBackground.setImageDrawable(null)
        }
        if (holder.appIconBackground.drawable == null) {
            holder.appIconBackground.visibility = View.INVISIBLE
        }

        loadRenderedAsset(
            requestKey = requestKey,
            renderer = { decodeUriBitmap(backgroundUri, frameSizePx, frameSizePx) }
        ) { bitmap ->
            if (holder.boundAppInfo?.packageName == appInfo.packageName &&
                holder.appliedBackgroundKey == requestKey
            ) {
                holder.appIconBackground.visibility = View.VISIBLE
                holder.appIconBackground.setImageBitmap(bitmap)
            }
        }
    }

    private fun bindIcon(
        holder: AppViewHolder,
        appInfo: AppInfo,
        iconUri: String?,
        iconSizePx: Int
    ) {
        val requestKey = iconUri?.let { "icon-uri:$it:$iconSizePx" }
            ?: "icon-drawable:${appInfo.packageName}:$iconSizePx"
        val keyChanged = holder.appliedIconKey != requestKey
        holder.appliedIconKey = requestKey

        val cachedBitmap = renderedAssetCache.get(requestKey)
        if (cachedBitmap != null) {
            holder.appIcon.setImageBitmap(cachedBitmap)
            return
        }

        if (keyChanged || iconUri == null) {
            holder.appIcon.setImageDrawable(appInfo.icon)
        }

        loadRenderedAsset(
            requestKey = requestKey,
            renderer = {
                if (iconUri != null) {
                    decodeUriBitmap(iconUri, iconSizePx, iconSizePx)
                } else {
                    renderDrawableBitmap(appInfo.icon, iconSizePx, iconSizePx)
                }
            }
        ) { bitmap ->
            if (holder.boundAppInfo?.packageName == appInfo.packageName &&
                holder.appliedIconKey == requestKey
            ) {
                holder.appIcon.setImageBitmap(bitmap)
            }
        }
    }

    private fun preloadRenderedAssets(apps: List<AppInfo>) {
        if (apps.isEmpty()) {
            return
        }

        val frameSizePx = (iconFrameSizeDp * density).toInt().coerceAtLeast(1)
        apps.forEach { appInfo ->
            val iconOverride = getIconOverride?.invoke(appInfo.packageName)
            val scalePercent = (iconOverride?.scalePercent ?: 100).coerceIn(50, 150)
            val iconSizePx = ((iconSizeDp * (scalePercent / 100f)) * density).toInt().coerceAtLeast(1)

            val iconKey = iconOverride?.iconUri?.let { "icon-uri:$it:$iconSizePx" }
                ?: "icon-drawable:${appInfo.packageName}:$iconSizePx"
            loadRenderedAsset(
                requestKey = iconKey,
                renderer = {
                    if (iconOverride?.iconUri != null) {
                        decodeUriBitmap(iconOverride.iconUri!!, iconSizePx, iconSizePx)
                    } else {
                        renderDrawableBitmap(appInfo.icon, iconSizePx, iconSizePx)
                    }
                }
            )

            iconOverride?.backgroundImageUri?.let { backgroundUri ->
                val backgroundKey = "bg:$backgroundUri:$frameSizePx"
                loadRenderedAsset(
                    requestKey = backgroundKey,
                    renderer = { decodeUriBitmap(backgroundUri, frameSizePx, frameSizePx) }
                )
            }
        }
    }

    private fun loadRenderedAsset(
        requestKey: String,
        renderer: () -> Bitmap?,
        onLoaded: ((Bitmap) -> Unit)? = null
    ) {
        val cachedBitmap = renderedAssetCache.get(requestKey)
        if (cachedBitmap != null) {
            onLoaded?.invoke(cachedBitmap)
            return
        }

        val shouldStartLoad = synchronized(pendingAssetLoads) {
            pendingAssetLoads.add(requestKey)
        }
        if (!shouldStartLoad) {
            return
        }

        assetExecutor.execute {
            val bitmap = try {
                renderer()
            } catch (_: Exception) {
                null
            }

            if (bitmap != null) {
                renderedAssetCache.put(requestKey, bitmap)
            }

            mainHandler.post {
                synchronized(pendingAssetLoads) {
                    pendingAssetLoads.remove(requestKey)
                }
                if (bitmap != null) {
                    onLoaded?.invoke(bitmap)
                }
            }
        }
    }

    private fun renderDrawableBitmap(drawable: Drawable, width: Int, height: Int): Bitmap? {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val source = drawable.constantState?.newDrawable(context.resources)?.mutate() ?: drawable
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        source.setBounds(0, 0, safeWidth, safeHeight)
        source.draw(canvas)
        return bitmap
    }

    private fun decodeUriBitmap(uriString: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val uri = Uri.parse(uriString)
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        if (decodedBitmap.width == targetWidth && decodedBitmap.height == targetHeight) {
            return decodedBitmap
        }

        val scaledBitmap = Bitmap.createScaledBitmap(
            decodedBitmap,
            targetWidth.coerceAtLeast(1),
            targetHeight.coerceAtLeast(1),
            true
        )
        if (scaledBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }
        return scaledBitmap
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun showContextMenu(appInfo: AppInfo) {
        val density = context.resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()

        val englishLabel = getLocalizedLabel(appInfo.packageName, Locale.ENGLISH) ?: appInfo.label
        val chineseLabel = getLocalizedLabel(appInfo.packageName, Locale.SIMPLIFIED_CHINESE) ?: appInfo.label
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
        if (onEditIcon != null) {
            addAction("Edit Icon") { onEditIcon.invoke(appInfo) }
        }
        if (onManageTags != null) {
            addAction(context.getString(R.string.tags)) { onManageTags.invoke(appInfo) }
        }
        if (onHideApp != null) {
            val hidden = isAppHidden?.invoke(appInfo) == true
            if (hidden) {
                if (onUnhideApp != null) {
                    addAction(context.getString(R.string.show_app)) { onUnhideApp.invoke(appInfo) }
                }
            } else {
                addAction(context.getString(R.string.hide_app)) { onHideApp.invoke(appInfo) }
            }
        }
        addAction(context.getString(R.string.app_info)) { openAppInfo(appInfo.packageName) }
        addAction(context.getString(R.string.uninstall)) { uninstallApp(appInfo.packageName) }

        val descriptionLabel = TextView(context).apply {
            text = context.getString(R.string.description)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.GRAY)
            setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(4))
        }

        val nameRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }

        val englishText = TextView(context).apply {
            text = englishLabel
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val chineseText = TextView(context).apply {
            text = chineseLabel
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        nameRow.addView(englishText)
        nameRow.addView(chineseText)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(8))
            addView(nameRow)
            addView(actionsContainer)
            addView(descriptionLabel)
            addView(descriptionInput)
        }

        val scrollView = ScrollView(context).apply {
            addView(container)
        }

        dialog = MaterialAlertDialogBuilder(context)
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

    private fun getLocalizedLabel(packageName: String, locale: Locale): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val baseContext = context.createPackageContext(packageName, 0)
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            val localizedContext = baseContext.createConfigurationContext(config)
            when {
                appInfo.labelRes != 0 -> localizedContext.resources.getString(appInfo.labelRes)
                appInfo.nonLocalizedLabel != null -> appInfo.nonLocalizedLabel.toString()
                else -> pm.getApplicationLabel(appInfo).toString()
            }
        } catch (_: Exception) {
            null
        }
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
        if (highlightedPackageName != null && newList.none { it.packageName == highlightedPackageName }) {
            highlightedPackageName = null
        }
        if (selectedPackages.isNotEmpty()) {
            val validPackages = newList.map { it.packageName }.toSet()
            selectedPackages.retainAll(validPackages)
            selectionMode = selectedPackages.isNotEmpty()
            onSelectionChanged?.invoke(selectedPackages.size)
        }
        notifyDataSetChanged()
        preloadRenderedAssets(filteredList)
    }

    fun getFilteredAppAt(position: Int): AppInfo? {
        return filteredList.getOrNull(position)
    }

    fun getTouchpadHighlightedApp(): AppInfo? {
        val highlightedPackage = highlightedPackageName ?: return null
        return filteredList.firstOrNull { it.packageName == highlightedPackage }
    }

    fun setTouchpadHighlightedPackage(packageName: String?) {
        if (highlightedPackageName == packageName) return

        val previousPackage = highlightedPackageName
        highlightedPackageName = packageName
        notifyPackageChanged(previousPackage)
        notifyPackageChanged(packageName)
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
        preloadRenderedAssets(filteredList)
    }

    fun setIconFrameSize(sizeDp: Int) {
        iconFrameSizeDp = sizeDp
        notifyDataSetChanged()
        preloadRenderedAssets(filteredList)
    }

    fun setIconShape(shape: String) {
        iconShape = shape
        outlineProviderCache.clear()
        notifyDataSetChanged()
    }

    fun setIconPadding(paddingDp: Int) {
        iconPaddingDp = paddingDp
        notifyDataSetChanged()
    }

    fun setIconFrameBackgroundColor(color: Int) {
        iconFrameBackgroundColor = color
        resolvedIconFrameBackgroundColor = ColorSettingUtils.resolveColor(context, color)
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
                if (highlightedPackageName != null && filteredList.none { it.packageName == highlightedPackageName }) {
                    highlightedPackageName = null
                }
                notifyDataSetChanged()
                preloadRenderedAssets(filteredList)
            }
        }
    }

    private fun notifyPackageChanged(packageName: String?) {
        val targetPackage = packageName ?: return
        val index = filteredList.indexOfFirst { it.packageName == targetPackage }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }
}
