package io.github.vvb2060.keyattestation.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

class HomeItemDecoration(context: Context) : ItemDecoration() {

    // Grouped items sit on one tonal, rounded surface.
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, 0)
    }
    private val radius = context.resources.displayMetrics.density * 28
    private val rect = RectF()
    private val cardMargin: Int = (context.resources.displayMetrics.density * 8).roundToInt()
    private val cardPadding: Int = (context.resources.displayMetrics.density * 12).roundToInt()

    private fun hasTopMargin(@Suppress("UNUSED_PARAMETER") adapter: HomeAdapter, position: Int): Boolean {
        return position == 0
    }

    private fun hasBottomMargin(adapter: HomeAdapter, position: Int): Boolean {
        return position == adapter.itemCount - 1 || !(adapter.allowFrameAt(position) && adapter.allowFrameAt(position + 1) && !adapter.shouldCommitFrameAt(position))
    }

    private fun hasTopPadding(adapter: HomeAdapter, position: Int): Boolean {
        return adapter.allowFrameAt(position) && (position == 0 || adapter.shouldCommitFrameAt(position - 1) || !adapter.allowFrameAt(position - 1))
    }

    private fun hasBottomPadding(adapter: HomeAdapter, position: Int): Boolean {
        return adapter.shouldCommitFrameAt(position)// && (position == adapter.itemCount - 1 || adapter.shouldCommitFrameAt(position))
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as HomeAdapter
        val position = parent.getChildAdapterPosition(view)

        if (hasTopMargin(adapter, position)) {
            outRect.top = cardMargin
        }
        if (hasTopPadding(adapter, position)) {
            outRect.top += cardPadding
        }

        if (hasBottomMargin(adapter, position)) {
            outRect.bottom = cardMargin
        }
        if (hasBottomPadding(adapter, position)) {
            outRect.bottom += cardPadding
        }

        outRect.left = cardMargin
        outRect.right = cardMargin
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.childCount == 0) {
            return
        }
        val adapter = parent.adapter as HomeAdapter
        var invalidatedPosition = true
        var left = 0
        var top = 0
        var right = 0
        var bottom: Int

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)

            if (!adapter.allowFrameAt(position)) {
                continue
            }

            if (invalidatedPosition) {
                left = child.left
                top = child.top
                right = child.right
                invalidatedPosition = false
            }

            if ((i == parent.childCount - 1) || adapter.shouldCommitFrameAt(position)) {
                bottom = child.bottom

                rect.set(left.toFloat(), (top - cardPadding).toFloat(), right.toFloat(), (bottom + cardPadding).toFloat())
                c.drawRoundRect(rect, radius, radius, paint)

                invalidatedPosition = true
            } else {
                left = child.left.coerceAtLeast(left)
                right = child.right.coerceAtLeast(right)
            }
        }
    }
}
