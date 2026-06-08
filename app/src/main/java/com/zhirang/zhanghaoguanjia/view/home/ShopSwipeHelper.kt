package com.zhirang.zhanghaoguanjia.view.home

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.zhirang.zhanghaoguanjia.R

class ShopSwipeHelper : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    companion object {
        private const val SWIPE_THRESHOLD = 0.3f
        private const val ANIMATION_DURATION = 220L
        private const val ACTION_WIDTH_DP = 96f
    }

    private var expandedPosition = RecyclerView.NO_POSITION
    private val interpolator = DecelerateInterpolator()
    private var shopIdProvider: ((Int) -> Long?)? = null
    private var expandedShopIdProvider: (() -> Long?)? = null
    private var onExpandedShopChanged: ((Long?) -> Unit)? = null

    fun bindState(
        shopIdProvider: (Int) -> Long?,
        expandedShopIdProvider: () -> Long?,
        onExpandedShopChanged: (Long?) -> Unit
    ) {
        this.shopIdProvider = shopIdProvider
        this.expandedShopIdProvider = expandedShopIdProvider
        this.onExpandedShopChanged = onExpandedShopChanged
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        when (direction) {
            ItemTouchHelper.LEFT -> expandItem(viewHolder)
            ItemTouchHelper.RIGHT -> {
                getCardContainer(viewHolder)?.let { view ->
                    view.bringToFront()
                    animateSwipe(view, 0f)
                }
                clearExpandedState()
            }
        }
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
            val position = viewHolder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                ?: viewHolder.absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                ?: RecyclerView.NO_POSITION
            if (position != RecyclerView.NO_POSITION && expandedPosition != RecyclerView.NO_POSITION && expandedPosition != position) {
                collapseOtherItem(recyclerView, position)
            }
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
            getRepairAction(viewHolder)?.bringToFront()
            getRepairAction(viewHolder)?.visibility = View.VISIBLE
            animateSwipe(cardContainer, -actionWidth)
            expandedPosition = viewHolder.bindingAdapterPosition
            onExpandedShopChanged?.invoke(getShopId(viewHolder))
        } else {
            cardContainer.bringToFront()
            animateSwipe(cardContainer, 0f)
            if (expandedPosition == viewHolder.bindingAdapterPosition) {
                clearExpandedState()
            }
        }
    }

    fun hitTestRepair(recyclerView: RecyclerView, child: View, x: Float, y: Float): Boolean {
        if (expandedPosition == RecyclerView.NO_POSITION) return false
        val position = recyclerView.getChildAdapterPosition(child)
        if (position != expandedPosition) return false
        if (!isExpandedChild(child, position)) return false

        val localX = x - child.left
        val localY = y - child.top
        if (localY < 0f || localY > child.height) return false

        val actionStart = child.width - getActionWidth(recyclerView)
        return localX in actionStart..child.width.toFloat()
    }

    fun hitTestExpandedRepair(recyclerView: RecyclerView, x: Float, y: Float): Boolean {
        val holder = findExpandedHolder(recyclerView) ?: return false
        val child = holder.itemView
        val localX = x - child.left
        val localY = y - child.top
        if (localY < 0f || localY > child.height) return false

        val actionStart = child.width - getActionWidth(recyclerView)
        return localX in actionStart..child.width.toFloat()
    }

    fun hitTestExpandedItem(recyclerView: RecyclerView, x: Float, y: Float): Boolean {
        val holder = findExpandedHolder(recyclerView) ?: return false
        val child = holder.itemView
        val localX = x - child.left
        val localY = y - child.top
        return localX in 0f..child.width.toFloat() && localY in 0f..child.height.toFloat()
    }

    fun dispatchRepairClickIfHit(recyclerView: RecyclerView, x: Float, y: Float): Boolean {
        val holder = findExpandedHolder(recyclerView) ?: return false
        val child = holder.itemView
        val localX = x - child.left
        val localY = y - child.top
        if (localY < 0f || localY > child.height) return false
        val actionStart = child.width - getActionWidth(recyclerView)
        if (localX !in actionStart..child.width.toFloat()) return false
        getRepairButton(holder)?.performClick()
        return true
    }

    fun getExpandedPosition(): Int = expandedPosition

    fun collapseExpandedItem(recyclerView: RecyclerView) {
        val holder = findExpandedHolder(recyclerView)
        holder?.let {
            getCardContainer(it)?.let { view ->
                view.bringToFront()
                animateSwipe(view, 0f)
            }
        }
        clearExpandedState()
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
        clearExpandedState()
    }

    private fun getCardContainer(viewHolder: RecyclerView.ViewHolder): View? {
        return viewHolder.itemView.findViewById(R.id.cardContainer)
    }

    private fun getRepairAction(viewHolder: RecyclerView.ViewHolder): View? {
        return viewHolder.itemView.findViewById(R.id.swipeRepairAction)
    }

    private fun getRepairButton(viewHolder: RecyclerView.ViewHolder): View? {
        return viewHolder.itemView.findViewById(R.id.btnRepair)
    }

    private fun getActionWidth(recyclerView: RecyclerView): Float {
        return ACTION_WIDTH_DP * recyclerView.context.resources.displayMetrics.density
    }

    private fun getActionWidth(view: View): Float {
        return ACTION_WIDTH_DP * view.resources.displayMetrics.density
    }

    private fun expandItem(viewHolder: RecyclerView.ViewHolder) {
        val position = viewHolder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
            ?: viewHolder.absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
            ?: return
        val shopId = getShopId(viewHolder) ?: return
        val cardContainer = getCardContainer(viewHolder) ?: return
        getRepairAction(viewHolder)?.bringToFront()
        getRepairAction(viewHolder)?.visibility = View.VISIBLE
        animateSwipe(cardContainer, -getActionWidth(cardContainer))
        expandedPosition = position
        onExpandedShopChanged?.invoke(shopId)
    }

    private fun animateSwipe(view: View, targetX: Float) {
        view.animate()
            .translationX(targetX)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(interpolator)
            .start()
    }

    private fun clearExpandedState() {
        expandedPosition = RecyclerView.NO_POSITION
        onExpandedShopChanged?.invoke(null)
    }

    private fun findExpandedHolder(recyclerView: RecyclerView): RecyclerView.ViewHolder? {
        val expandedId = expandedShopIdProvider?.invoke() ?: return null
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val position = recyclerView.getChildAdapterPosition(child)
            if (position != RecyclerView.NO_POSITION && shopIdProvider?.invoke(position) == expandedId) {
                expandedPosition = position
                return recyclerView.getChildViewHolder(child)
            }
        }
        return null
    }

    private fun isExpandedChild(child: View, position: Int): Boolean {
        val expandedId = expandedShopIdProvider?.invoke() ?: return false
        return shopIdProvider?.invoke(position) == expandedId &&
                child.findViewById<View>(R.id.swipeRepairAction)?.visibility == View.VISIBLE
    }

    private fun getShopId(viewHolder: RecyclerView.ViewHolder): Long? {
        val position = viewHolder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
            ?: viewHolder.absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
            ?: return null
        return shopIdProvider?.invoke(position)
    }
}
