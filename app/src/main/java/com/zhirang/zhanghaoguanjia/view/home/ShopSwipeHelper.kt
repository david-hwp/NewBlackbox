package com.zhirang.zhanghaoguanjia.view.home

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.zhirang.zhanghaoguanjia.R

class ShopSwipeHelper : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    companion object {
        private const val SWIPE_THRESHOLD = 0.3f
        private const val ANIMATION_DURATION = 220L
        private const val ACTION_WIDTH_DP = 96f
    }

    private var expandedPosition = RecyclerView.NO_POSITION
    private val interpolator = DecelerateInterpolator()

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = SWIPE_THRESHOLD

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
            val cardContainer = getCardContainer(viewHolder) ?: return
            val maxSwipe = -getActionWidth(recyclerView)
            cardContainer.translationX = dX.coerceIn(maxSwipe, 0f)
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val cardContainer = getCardContainer(viewHolder) ?: return
        val actionWidth = getActionWidth(recyclerView)
        if (cardContainer.translationX <= -actionWidth * SWIPE_THRESHOLD) {
            collapseOtherItem(recyclerView, viewHolder.bindingAdapterPosition)
            viewHolder.itemView.findViewById<View>(R.id.swipeRepairAction)?.bringToFront()
            animateSwipe(cardContainer, -actionWidth)
            expandedPosition = viewHolder.bindingAdapterPosition
        } else {
            cardContainer.bringToFront()
            animateSwipe(cardContainer, 0f)
            if (expandedPosition == viewHolder.bindingAdapterPosition) {
                expandedPosition = RecyclerView.NO_POSITION
            }
        }
    }

    fun hitTestRepair(recyclerView: RecyclerView, child: View, x: Float, y: Float): Boolean {
        if (expandedPosition == RecyclerView.NO_POSITION) return false
        val position = recyclerView.getChildAdapterPosition(child)
        if (position != expandedPosition) return false

        val localX = x - child.left
        val localY = y - child.top
        if (localY < 0f || localY > child.height) return false

        val actionStart = child.width - getActionWidth(recyclerView)
        return localX in actionStart..child.width.toFloat()
    }

    fun getExpandedPosition(): Int = expandedPosition

    fun collapseExpandedItem(recyclerView: RecyclerView) {
        if (expandedPosition == RecyclerView.NO_POSITION) return
        val holder = recyclerView.findViewHolderForAdapterPosition(expandedPosition)
        holder?.let {
            getCardContainer(it)?.let { view ->
                view.bringToFront()
                animateSwipe(view, 0f)
            }
        }
        expandedPosition = RecyclerView.NO_POSITION
    }

    fun resetExpandedPosition() {
        expandedPosition = RecyclerView.NO_POSITION
    }

    private fun collapseOtherItem(recyclerView: RecyclerView, currentPosition: Int) {
        if (expandedPosition == RecyclerView.NO_POSITION || expandedPosition == currentPosition) return
        val holder = recyclerView.findViewHolderForAdapterPosition(expandedPosition)
        holder?.let {
            getCardContainer(it)?.let { view ->
                view.bringToFront()
                animateSwipe(view, 0f)
            }
        }
        expandedPosition = RecyclerView.NO_POSITION
    }

    private fun getCardContainer(viewHolder: RecyclerView.ViewHolder): View? {
        return viewHolder.itemView.findViewById(R.id.cardContainer)
    }

    private fun getActionWidth(recyclerView: RecyclerView): Float {
        return ACTION_WIDTH_DP * recyclerView.context.resources.displayMetrics.density
    }

    private fun animateSwipe(view: View, targetX: Float) {
        view.animate()
            .translationX(targetX)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(interpolator)
            .start()
    }
}
