package com.example.taglauncher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.taglauncher.persistence.DesktopLayoutData
import com.google.android.material.card.MaterialCardView

class PageManagerAdapter(
    private var layoutData: DesktopLayoutData,
    private var pageWidthDp: Float,
    private var pageHeightDp: Float,
    private var currentPage: Int,
    private val onSelectPage: (Int) -> Unit,
    private val onSetHome: (Int) -> Unit,
    private val onDeletePage: (Int) -> Unit,
    private val onAddPage: () -> Unit,
    private val previewProvider: (Int, Int, Int) -> android.graphics.Bitmap?
) : RecyclerView.Adapter<PageManagerAdapter.PageViewHolder>() {

    private val pageOrder = mutableListOf<Int>()

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.pageCard)
        val thumbnail: ImageView = itemView.findViewById(R.id.pageThumbnail)
        val label: TextView = itemView.findViewById(R.id.pageLabel)
        val homeButton: ImageButton = itemView.findViewById(R.id.pageHomeButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.pageDeleteButton)
        val overlayBar: View = itemView.findViewById(R.id.pageOverlayBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page_thumbnail, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        if (position == layoutData.pageCount) {
            bindAddCard(holder)
            return
        }
        val pageIndex = pageOrder.getOrNull(position) ?: position
        val isHome = pageIndex == layoutData.homePage
        val isCurrent = pageIndex == currentPage
        val ratio = if (pageWidthDp > 0f) pageHeightDp / pageWidthDp else 1f

        holder.label.text = ""
        holder.label.visibility = View.INVISIBLE
        holder.overlayBar.visibility = View.VISIBLE
        holder.thumbnail.tag = pageIndex
        holder.thumbnail.setImageDrawable(null)
        holder.thumbnail.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.thumbnail.setBackgroundColor(Color.TRANSPARENT)
        val width = holder.thumbnail.width
        if (width > 0) {
            renderPreview(holder, pageIndex, width, ratio)
        } else {
            holder.thumbnail.post {
                if (holder.thumbnail.tag != pageIndex) return@post
                val measuredWidth = holder.thumbnail.width
                if (measuredWidth <= 0) return@post
                renderPreview(holder, pageIndex, measuredWidth, ratio)
            }
        }

        holder.card.strokeWidth = if (isCurrent) dpToPx(holder.itemView, 2) else 0
        holder.card.strokeColor = if (isCurrent) Color.parseColor("#4CAF50") else Color.TRANSPARENT

        holder.homeButton.setImageResource(
            if (isHome) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
        holder.homeButton.setColorFilter(
            if (isHome) Color.parseColor("#FFD54F") else Color.parseColor("#80FFFFFF")
        )

        holder.homeButton.visibility = View.VISIBLE
        holder.deleteButton.visibility = View.VISIBLE
        holder.homeButton.setOnClickListener { onSetHome(pageIndex) }
        holder.deleteButton.setOnClickListener { onDeletePage(pageIndex) }
        holder.card.setOnClickListener { onSelectPage(pageIndex) }
    }

    override fun getItemCount(): Int = layoutData.pageCount + 1

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
        resetPageOrder()
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition ||
            fromPosition !in pageOrder.indices ||
            toPosition !in pageOrder.indices
        ) {
            return
        }
        val item = pageOrder.removeAt(fromPosition)
        pageOrder.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun realPageCount(): Int = layoutData.pageCount

    private fun dpToPx(view: View, dp: Int): Int {
        return (dp * view.resources.displayMetrics.density).toInt()
    }

    private fun renderPreview(
        holder: PageViewHolder,
        pageIndex: Int,
        width: Int,
        ratio: Float
    ) {
        val desiredHeight = (width * ratio).toInt().coerceAtLeast(1)
        val imageParams = holder.thumbnail.layoutParams
        if (imageParams.height != desiredHeight) {
            imageParams.height = desiredHeight
            holder.thumbnail.layoutParams = imageParams
        }
        val cardParams = holder.card.layoutParams
        if (cardParams.height != desiredHeight) {
            cardParams.height = desiredHeight
            holder.card.layoutParams = cardParams
        }
        val bitmap = previewProvider(pageIndex, width, desiredHeight)
        if (bitmap != null && holder.thumbnail.tag == pageIndex) {
            holder.thumbnail.setImageBitmap(bitmap)
        }
    }

    private fun resetPageOrder() {
        pageOrder.clear()
        pageOrder.addAll(layoutData.pageCount.let { count -> (0 until count) })
    }

    private fun bindAddCard(holder: PageViewHolder) {
        holder.card.strokeWidth = 0
        holder.card.strokeColor = Color.TRANSPARENT
        holder.label.text = ""
        holder.label.visibility = View.GONE
        holder.overlayBar.visibility = View.GONE
        holder.thumbnail.tag = "add"
        holder.thumbnail.setImageResource(android.R.drawable.ic_input_add)
        holder.thumbnail.scaleType = ImageView.ScaleType.CENTER
        holder.thumbnail.setBackgroundColor(Color.parseColor("#22000000"))
        holder.homeButton.visibility = View.GONE
        holder.deleteButton.visibility = View.GONE
        holder.card.setOnClickListener { onAddPage() }

        val ratio = if (pageWidthDp > 0f) pageHeightDp / pageWidthDp else 1f
        val width = holder.thumbnail.width
        if (width > 0) {
            val desiredHeight = (width * ratio).toInt().coerceAtLeast(1)
            val imageParams = holder.thumbnail.layoutParams
            if (imageParams.height != desiredHeight) {
                imageParams.height = desiredHeight
                holder.thumbnail.layoutParams = imageParams
            }
            val cardParams = holder.card.layoutParams
            if (cardParams.height != desiredHeight) {
                cardParams.height = desiredHeight
                holder.card.layoutParams = cardParams
            }
        } else {
            holder.thumbnail.post {
                val measuredWidth = holder.thumbnail.width
                if (measuredWidth <= 0) return@post
                val desiredHeight = (measuredWidth * ratio).toInt().coerceAtLeast(1)
                val imageParams = holder.thumbnail.layoutParams
                if (imageParams.height != desiredHeight) {
                    imageParams.height = desiredHeight
                    holder.thumbnail.layoutParams = imageParams
                }
                val cardParams = holder.card.layoutParams
                if (cardParams.height != desiredHeight) {
                    cardParams.height = desiredHeight
                    holder.card.layoutParams = cardParams
                }
            }
        }
    }

    init {
        resetPageOrder()
    }
}
