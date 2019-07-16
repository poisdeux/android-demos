package com.example.recyclerviewsquaregriddemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min


class ChordDiagramView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        setHasFixedSize(true)
        adapter = MyAdapter()

        val gridLayoutManager = GridLayoutClusteringManager( VERTICAL, 8)
        layoutManager = gridLayoutManager

        ContextCompat.getDrawable(context, R.drawable.measure_line)?.let {
            addItemDecoration(MeasureBarDivider(it))
        }
    }

    private class MyAdapter: RecyclerView.Adapter<MyAdapter.MyViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item, parent, false)
            return MyViewHolder(view)
        }

        override fun getItemCount(): Int {
            return 200
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.numberTextView.text = position.toString()
        }

        class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val numberTextView: TextView = view.findViewById(R.id.tv_cell_number)
        }
    }

    private class MeasureBarDivider(val drawableDivider: Drawable): RecyclerView.ItemDecoration() {
        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {
            val childCount = parent.childCount
            for (i in 0 until childCount - 1) {
                if (i % 4 != 0) continue

                val child = parent.getChildAt(i)

                val dividerTop = child.top
                val dividerBottom = child.bottom

                val params = child.layoutParams as LayoutParams

                val dividerLeft = child.left - (params.leftMargin + (drawableDivider.intrinsicWidth / 2))
                val dividerRight = dividerLeft + drawableDivider.intrinsicWidth

                drawableDivider.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom)
                drawableDivider.draw(c)
            }
        }
    }

    private class GridLayoutClusteringManager(@RecyclerView.Orientation val orientation: Int, val spanCount: Int): RecyclerView.LayoutManager() {
        internal var childWidth: Int = 0
        private var firstPosition = 0
        private var lastPosition = 0

        override fun generateDefaultLayoutParams(): LayoutParams {
            return LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        override fun onLayoutChildren(recycler: Recycler, state: State?) {
            val totalSpace = width - paddingRight - paddingLeft
            childWidth = totalSpace / spanCount

            //Put all attached view onto the scrap
            //We then get the required views from the recycler.
            //Recycler may choose to use the scrap to return the view.
            //We add the required views to the layout
            //Afterwards any views left on the scrap can be recycled.
            detachAndScrapAttachedViews(recycler)

            val rows = height / childWidth + 1 // 1 extra for rows at bottom/top boundaries
            lastPosition = firstPosition + ( rows * spanCount ) - 1
            if ( lastPosition >= itemCount )
                lastPosition = itemCount - 1

            for (row in 0 until rows) {
                addRow(row, recycler, 0, row * childWidth)
            }

            val scrapListCopy = recycler.scrapList.toList()
            scrapListCopy.forEach {
                recycler.recycleView(it.itemView)
            }
        }

        override fun canScrollVertically(): Boolean = orientation == VERTICAL

        override fun scrollVerticallyBy(dy: Int, recycler: Recycler?, state: State?): Int {
            if (recycler == null || state == null)
                return 0

            var scrolled = 0
            if ( dy < 0 ) {
                while (scrolled > dy) {
                    val topView = getChildAt(0)
                    val hangingTop = max(-getDecoratedTop(topView!!), 0)
                    val scrollBy = min(scrolled - dy, hangingTop)
                    scrolled -= scrollBy
                    offsetChildrenVertical(scrollBy)

                    if (topView.bottom > 0 && firstPosition > 0) {
                        firstPosition -= spanCount
                        if (firstPosition < 0) firstPosition = 0
                        prependRow(firstPosition, recycler, topView.left, topView.top)
                    } else {
                        break
                    }
                }
            } else if ( dy > 0 ) {
                while (scrolled < dy) {
                    val bottomView = getChildAt(childCount - 1) ?: return 0
                    val hangingBottom = max(getDecoratedBottom(bottomView) - height, 0)
                    val scrollBy = -min(dy - scrolled, hangingBottom)
                    scrolled -= scrollBy
                    offsetChildrenVertical(scrollBy)

                    if (bottomView.top < height && lastPosition < itemCount - 1) { // bottomView is visible so we now should add an additional row if possible
                        lastPosition += spanCount
                        if (state.itemCount < lastPosition) lastPosition = state.itemCount - 1
                        addRow((lastPosition + 1) / spanCount - 1, recycler, 0, getDecoratedBottom(bottomView))
                    } else {
                        break
                    }
                }
            }
            return scrolled
        }

        override fun canScrollHorizontally(): Boolean = orientation == HORIZONTAL

        override fun scrollHorizontallyBy(dx: Int, recycler: Recycler?, state: State): Int {
            recycler?.let {
                onLayoutChildren(recycler, state)
            }
            return dx
        }

        private fun prependRow(row: Int, recycler: Recycler, horizontalOffset: Int, verticalOffset: Int) {
            val startIndex = row * spanCount
            val endIndex = startIndex + spanCount

            for ((column, position) in (startIndex until endIndex).withIndex()) {
                val v = recycler.getViewForPosition(position)
                addView(v, column)
                layoutChildView(row, column, childWidth, childWidth, v, horizontalOffset, verticalOffset)
            }
        }

        /**
         * @param row: zero-based
         */
        private fun addRow(row: Int, recycler: Recycler, xPos: Int, yPos: Int) {
            val startIndex = row * spanCount
            val endIndex = startIndex + spanCount

            for ((column, position) in (startIndex until endIndex).withIndex()) {
                val v = recycler.getViewForPosition(position)
                addView(v)
                layoutChildView(row, column, childWidth, childWidth, v, xPos, yPos)
            }
        }

        private fun layoutChildView(row: Int, column: Int, viewWidth: Int, viewHeight: Int, view: View, xPos: Int, yPos: Int) {
            val left = column * viewWidth + xPos
            val right = left + viewWidth
            val top = yPos
            val bottom = top + viewHeight

            measureChildWithMargins(view, viewWidth, viewHeight)

            layoutDecoratedWithMargins(view, left, top, right, bottom)
        }
    }
}