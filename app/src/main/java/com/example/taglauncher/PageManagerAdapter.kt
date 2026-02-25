package com.example.taglauncher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taglauncher.desktop.PageThumbnailView
import com.example.taglauncher.persistence.DesktopLayoutData
import com.google.android.material.card.MaterialCardView

class PageManagerAdapter(
    private var layoutData: DesktopLayoutData,
    private var pageWidthDp: Float,
    private var pageHeightDp: Float,
    private var currentPage: Int,
    private val onSelectPage: (Int) -> Unit,
    private val onSetHome: (Int) -> Unit,
    private val onDeletePage: (Int) -> Unit
) : RecyclerView.Adapter<PageManagerAdapter.PageViewHolder>() {

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.pageCard)
        val thumbnail: PageThumbnailView = itemView.findViewById(R.id.pageThumbnail)
        val label: TextView = itemView.findViewById(R.id.pageLabel)
        val homeButton: ImageButton = itemView.findViewById(R.id.pageHomeButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.pageDeleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page_thumbnail, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val pageIndex = position
        val isHome = pageIndex == layoutData.homePage
        val isCurrent = pageIndex == currentPage

        holder.label.text = "Page ${pageIndex + 1}"
        holder.thumbnail.setData(pageIndex, pageWidthDp, pageHeightDp, layoutData.components)

        holder.card.strokeWidth = if (isCurrent) dpToPx(holder.itemView, 2) else 0
        holder.card.strokeColor = if (isCurrent) Color.parseColor("#4CAF50") else Color.TRANSPARENT

        holder.homeButton.setImageResource(
            if (isHome) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
        holder.homeButton.setColorFilter(
            if (isHome) Color.parseColor("#FFD54F") else Color.parseColor("#80FFFFFF")
        )

        holder.homeButton.setOnClickListener { onSetHome(pageIndex) }
        holder.deleteButton.setOnClickListener { onDeletePage(pageIndex) }
        holder.card.setOnClickListener { onSelectPage(pageIndex) }
    }

    override fun getItemCount(): Int = layoutData.pageCount

    fun updateSnapshot(
        newLayoutData: DesktopLayoutData,
        pageWidthDp: Float,
        pageHeightDp: Float,
        currentPage: Int
    ) {
        this.layoutData = newLayoutData
        this.pageWidthDp = pageWidthDp
        this.pageHeightDp = pageHeightDp
        this.currentPage = currentPage
        notifyDataSetChanged()
    }

    private fun dpToPx(view: View, dp: Int): Int {
        return (dp * view.resources.displayMetrics.density).toInt()
    }
}
