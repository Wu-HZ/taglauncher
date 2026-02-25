package com.example.taglauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Full-screen overlay for global app search.
 * Triggered by swipe gesture on desktop.
 */
class GlobalSearchOverlay(context: Context) : FrameLayout(context) {

    private val searchInput: EditText
    private val resultsRecyclerView: RecyclerView
    private val resultsAdapter: SearchResultsAdapter
    private val emptyStateText: TextView
    private val contentContainer: LinearLayout

    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var onAppClick: ((AppInfo) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    private val density = context.resources.displayMetrics.density

    init {
        // Full screen overlay with dim background
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#E6000000")) // 90% opacity black
        isClickable = true
        isFocusable = true

        // Content container (centered card-like area)
        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = 60.dp
                leftMargin = 16.dp
                rightMargin = 16.dp
                bottomMargin = 60.dp
            }
        }

        // Search bar container
        val searchContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp
            ).apply {
                bottomMargin = 16.dp
            }

            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = 28.dp.toFloat()
            }
            background = bgDrawable
            setPadding(20.dp, 0, 20.dp, 0)
        }

        // Search icon
        val searchIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {
                marginEnd = 12.dp
            }
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.parseColor("#888888"))
        }

        // Search input
        searchInput = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Search apps..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 18f
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterApps(s?.toString() ?: "")
                }
            })

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    // Launch first result if any
                    if (filteredApps.isNotEmpty()) {
                        onAppClick?.invoke(filteredApps[0])
                    }
                    true
                } else {
                    false
                }
            }
        }

        // Clear button
        val clearButton = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply {
                marginStart = 8.dp
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#888888"))
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                searchInput.setText("")
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        searchContainer.addView(searchIcon)
        searchContainer.addView(searchInput)
        searchContainer.addView(clearButton)

        // Empty state text
        emptyStateText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 48.dp
            }
            text = "No apps found"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        // Results RecyclerView
        resultsRecyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = GridLayoutManager(context, 4)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        resultsAdapter = SearchResultsAdapter(
            context = context,
            onAppClick = { appInfo ->
                onAppClick?.invoke(appInfo)
            }
        )
        resultsRecyclerView.adapter = resultsAdapter

        // Add item decoration for spacing
        resultsRecyclerView.addItemDecoration(GridSpacingItemDecoration(8.dp, 16.dp))

        contentContainer.addView(searchContainer)
        contentContainer.addView(emptyStateText)
        contentContainer.addView(resultsRecyclerView)

        addView(contentContainer)

        // Dismiss when clicking outside content
        setOnClickListener {
            dismiss()
        }

        // Prevent clicks on content from dismissing
        contentContainer.setOnClickListener { /* consume click */ }

        // Handle back button
        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                true
            } else {
                false
            }
        }
    }

    /**
     * Set the list of all apps for searching.
     */
    fun setApps(apps: List<AppInfo>) {
        allApps = apps
        filteredApps = apps
        resultsAdapter.updateApps(filteredApps)
        updateEmptyState()
    }

    /**
     * Set callback for app click.
     */
    fun setOnAppClickListener(listener: (AppInfo) -> Unit) {
        onAppClick = listener
    }

    /**
     * Set callback for dismiss.
     */
    fun setOnDismissListener(listener: () -> Unit) {
        onDismiss = listener
    }

    /**
     * Show the search overlay with animation.
     */
    fun show(parent: ViewGroup) {
        if (this.parent != null) return

        alpha = 0f
        contentContainer.translationY = 100.dp.toFloat()

        parent.addView(this)
        requestFocus()

        // Animate in
        animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        contentContainer.animate()
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Show keyboard and focus search
        post {
            searchInput.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Dismiss the search overlay with animation.
     */
    fun dismiss() {
        // Hide keyboard first
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)

        // Animate out
        animate()
            .alpha(0f)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (parent as? ViewGroup)?.removeView(this@GlobalSearchOverlay)
                    searchInput.setText("")
                    onDismiss?.invoke()
                }
            })
            .start()

        contentContainer.animate()
            .translationY(50.dp.toFloat())
            .setDuration(150)
            .start()
    }

    /**
     * Check if overlay is currently showing.
     */
    fun isShowing(): Boolean = parent != null

    private fun filterApps(query: String) {
        val trimmedQuery = query.trim().lowercase()

        filteredApps = if (trimmedQuery.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.lowercase().contains(trimmedQuery)
            }
        }

        resultsAdapter.updateApps(filteredApps)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (filteredApps.isEmpty() && searchInput.text.isNotEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            resultsRecyclerView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            resultsRecyclerView.visibility = View.VISIBLE
        }
    }

    private val Int.dp: Int
        get() = (this * density).toInt()

    /**
     * Adapter for search results.
     */
    private class SearchResultsAdapter(
        private val context: Context,
        private val onAppClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

        private var apps: List<AppInfo> = emptyList()
        private val density = context.resources.displayMetrics.density

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val iconView: ImageView = itemView.findViewById(android.R.id.icon)
            val labelView: TextView = itemView.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(8.dp, 12.dp, 8.dp, 12.dp)

                // Ripple effect
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
            }

            val iconView = ImageView(context).apply {
                id = android.R.id.icon
                layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val labelView = TextView(context).apply {
                id = android.R.id.text1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6.dp
                }
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            container.addView(iconView)
            container.addView(labelView)

            return ViewHolder(container)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.iconView.setImageDrawable(app.icon)
            holder.labelView.text = app.label
            holder.itemView.setOnClickListener {
                onAppClick(app)
            }
        }

        override fun getItemCount(): Int = apps.size

        fun updateApps(newApps: List<AppInfo>) {
            apps = newApps
            notifyDataSetChanged()
        }

        private val Int.dp: Int
            get() = (this * density).toInt()
    }
}
