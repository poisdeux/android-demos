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


class ChordDiagramView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        setHasFixedSize(true)
        adapter = MyAdapter()

        val gridLayoutManager = GridLayoutClusteringManager( VERTICAL, 8, adapter?.itemCount ?: 0)
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

    private class GridLayoutClusteringManager(@RecyclerView.Orientation val orientation: Int, val spanCount: Int, val maxItems: Int): RecyclerView.LayoutManager() {
        internal var childWidth: Int = 0
        private var verticalScrollOffset = 0
        private var maxVerticalScrollOffset = 0
        private var amountOfVisibleRows = 0
        private var maxRows = 0

        override fun generateDefaultLayoutParams(): LayoutParams {
            return LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        override fun onLayoutChildren(recycler: Recycler, state: State?) {
            if (childCount == 0) { // empty layout so this is either the first run or layout size changed
                val totalSpace = width - paddingRight - paddingLeft
                childWidth = totalSpace / spanCount

                maxRows = maxItems / spanCount
                if (maxItems > maxRows * spanCount) {
                    //Add one extra row to accomodate last items that do not occupy all cells in the last row
                    maxRows++
                }

                amountOfVisibleRows = height / childWidth + 1 // 1 extra for rows at bottom/top boundaries

                maxVerticalScrollOffset = childWidth * (maxRows - amountOfVisibleRows + 1)
            }

            //Put all attached views onto the scrap
            //We then get the required views from the recycler.
            //Recycler may choose to use the scrap to return the view.
            //We add the required views to the layout
            //Afterwards any views left on the scrap can be recycled.
            detachAndScrapAttachedViews(recycler)

            val firstVisibleRow = verticalScrollOffset/childWidth
            var lastVisibleRow = firstVisibleRow + amountOfVisibleRows
            if (lastVisibleRow > maxRows)
                lastVisibleRow = maxRows

            for (row in firstVisibleRow until lastVisibleRow) {
                addRow(row, recycler, 0, row * childWidth - verticalScrollOffset)
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
                if (verticalScrollOffset <= 0)
                    return 0 // End reached

                verticalScrollOffset += dy
                val scrollBy: Int
                if (verticalScrollOffset < 0) {
                    //When the start of the list is reached, scrollVerticallyBy must return the scrolled distance with
                    //an opposite sign.
                    scrollBy = dy + -verticalScrollOffset
                    verticalScrollOffset = 0
                } else {
                    scrollBy = dy
                }
                scrolled = scrollBy
            } else if ( dy > 0 ) {

                if (verticalScrollOffset >= maxVerticalScrollOffset)
                    return 0 // End reached

                verticalScrollOffset += dy
                val scrollBy: Int
                if (verticalScrollOffset > maxVerticalScrollOffset) {
                    //When the end of the list is reached, scrollVerticallyBy must return the scrolled distance with
                    //an opposite sign.
                    scrollBy = (maxVerticalScrollOffset - verticalScrollOffset) - dy
                    verticalScrollOffset = maxVerticalScrollOffset
                } else {
                    scrollBy = dy
                }
                scrolled = scrollBy
            }

            onLayoutChildren(recycler, state)

            return scrolled
        }

        override fun canScrollHorizontally(): Boolean = orientation == HORIZONTAL

        override fun scrollHorizontallyBy(dx: Int, recycler: Recycler?, state: State): Int {
            recycler?.let {
                onLayoutChildren(recycler, state)
            }
            return dx
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
                layoutChildView(column, v, xPos, yPos)
            }
        }

        private fun layoutChildView(column: Int, view: View, xPos: Int, yPos: Int) {
            val left = column * childWidth + xPos
            val right = left + childWidth
            val top = yPos
            val bottom = top + childWidth

            measureChildWithMargins(view, childWidth, childWidth)

            layoutDecoratedWithMargins(view, left, top, right, bottom)
        }
    }
}