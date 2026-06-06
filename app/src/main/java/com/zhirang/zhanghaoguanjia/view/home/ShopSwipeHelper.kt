package com.zhirang.zhanghaoguanjia.view.home

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.zhirang.zhanghaoguanjia.R

class ShopSwipeHelper(
    private val adapter: ShopListAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    enum class Action {
        EDIT,
        AUTO_RENEW,
        DELETE
    }

    companion object {
        private const val SWIPE_THRESHOLD = 0.3f
        private const val ANIMATION_DURATION = 280L
        private const val ACTION_BUTTON_WIDTH_DP = 216f // 72dp * 3 buttons
    }

    private var expandedPosition = RecyclerView.NO_POSITION
    private val interpolator = DecelerateInterpolator()

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not using default swipe behavior - we handle it manually
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return SWIPE_THRESHOLD
    }

    override fun onChildDraw(
        c: android.graphics.Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val cardView = getCardView(viewHolder)

            cardView?.let {
                keepCardAboveActions(viewHolder.itemView, it)
                val maxSwipe = -getActionButtonWidth(recyclerView)
                val clampedDx = dX.coerceIn(maxSwipe, 0f)
                it.translationX = clampedDx
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val cardView = getCardView(viewHolder)
        cardView?.let {
            val currentTranslation = it.translationX
            val actionButtonWidth = getActionButtonWidth(recyclerView)

            if (currentTranslation <= -actionButtonWidth * SWIPE_THRESHOLD) {
                // Expand
                collapseOtherItems(viewHolder.bindingAdapterPosition)
                keepCardAboveActions(viewHolder.itemView, it)
                animateSwipe(it, -actionButtonWidth)
                expandedPosition = viewHolder.bindingAdapterPosition
            } else {
                // Collapse
                keepCardAboveActions(viewHolder.itemView, it)
                animateSwipe(it, 0f)
                if (expandedPosition == viewHolder.bindingAdapterPosition) {
                    expandedPosition = RecyclerView.NO_POSITION
                }
            }
        }
    }

    private fun getCardView(viewHolder: RecyclerView.ViewHolder): View? {
        return viewHolder.itemView.findViewById<View>(R.id.cardView)
    }

    private fun keepCardAboveActions(itemView: View, cardView: View) {
        itemView.findViewById<View>(R.id.swipeActions)?.apply {
            translationZ = 0f
            elevation = 0f
        }
        cardView.bringToFront()
        cardView.translationZ = 1f
    }

    private fun getActionButtonWidth(recyclerView: RecyclerView): Float {
        val density = recyclerView.context.resources.displayMetrics.density
        return ACTION_BUTTON_WIDTH_DP * density
    }

    fun getExpandedPosition(): Int = expandedPosition

    fun hitTestAction(recyclerView: RecyclerView, child: View, x: Float, y: Float): Action? {
        val position = recyclerView.getChildAdapterPosition(child)
        val cardView = child.findViewById<View>(R.id.cardView) ?: return null
        if (cardView.translationX >= -1f) return null
        if (expandedPosition != RecyclerView.NO_POSITION && position != expandedPosition) return null

        val localX = x - child.left
        val localY = y - child.top
        if (localY < 0f || localY > child.height) return null

        val actionWidth = getActionButtonWidth(recyclerView)
        val actionStart = child.width - actionWidth
        if (localX < actionStart || localX > child.width) return null

        val buttonWidth = actionWidth / 3f
        return when (((localX - actionStart) / buttonWidth).toInt().coerceIn(0, 2)) {
            0 -> Action.EDIT
            1 -> Action.AUTO_RENEW
            else -> Action.DELETE
        }
    }

    private fun animateSwipe(view: View, targetX: Float) {
        view.animate()
            .translationX(targetX)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(interpolator)
            .start()
    }

    fun collapseExpandedItem(recyclerView: RecyclerView) {
        if (expandedPosition != RecyclerView.NO_POSITION) {
            val holder = recyclerView.findViewHolderForAdapterPosition(expandedPosition)
            holder?.let {
                getCardView(it)?.let { cardView ->
                    keepCardAboveActions(it.itemView, cardView)
                    animateSwipe(cardView, 0f)
                }
            }
            expandedPosition = RecyclerView.NO_POSITION
        }
    }

    private fun collapseOtherItems(currentPosition: Int) {
        if (expandedPosition != RecyclerView.NO_POSITION && expandedPosition != currentPosition) {
            // The actual collapse happens when the view holder is bound or accessed
            expandedPosition = RecyclerView.NO_POSITION
        }
    }

    fun isExpanded(position: Int): Boolean = expandedPosition == position

    fun resetExpandedPosition() {
        expandedPosition = RecyclerView.NO_POSITION
    }
}
