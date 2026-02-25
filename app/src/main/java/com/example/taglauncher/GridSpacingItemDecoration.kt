package com.example.taglauncher

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemDecoration that applies configurable horizontal and vertical spacing between grid items.
 */
class GridSpacingItemDecoration(
    private var horizontalSpacingPx: Int,
    private var verticalSpacingPx: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.left = horizontalSpacingPx / 2
        outRect.right = horizontalSpacingPx / 2
        outRect.top = verticalSpacingPx / 2
        outRect.bottom = verticalSpacingPx / 2
    }

    /**
     * Update the spacing values.
     * @param horizontalPx Horizontal spacing in pixels
     * @param verticalPx Vertical spacing in pixels
     */
    fun updateSpacing(horizontalPx: Int, verticalPx: Int) {
        horizontalSpacingPx = horizontalPx
        verticalSpacingPx = verticalPx
    }
}
