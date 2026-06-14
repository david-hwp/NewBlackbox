package com.zhirang.zhanghaoguanjia.view.home

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.zhirang.zhanghaoguanjia.R

class ShopSwipeHelper : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {

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
    private var reorderEnabledProvider: (() -> Boolean)? = null
    private var onMoveItem: ((Int, Int) -> Boolean)? = null
    private var onDragStarted: ((Int) -> Unit)? = null
    private var onDragFinished: (() -> Unit)? = null
    private var dragging = false

    fun bindState(
        shopIdProvider: (Int) -> Long?,
        expandedShopIdProvider: () -> Long?,
        onExpandedShopChanged: (Long?) -> Unit,
        reorderEnabledProvider: () -> Boolean,
        onMoveItem: (Int, Int) -> Boolean,
        onDragStarted: (Int) -> Unit,
        onDragFinished: () -> Unit
    ) {
        this.shopIdProvider = shopIdProvider
        this.expandedShopIdProvider = expandedShopIdProvider
        this.onExpandedShopChanged = onExpandedShopChanged
        this.reorderEnabledProvider = reorderEnabledProvider
        this.onMoveItem = onMoveItem
        this.onDragStarted = onDragStarted
        this.onDragFinished = onDragFinished
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return if (isReorderEnabled()) {
            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        } else {
            makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
        }
    }

    override fun isLongPressDragEnabled(): Boolean = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (!isReorderEnabled()) {
            return false
        }
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
            return false
        }
        return onMoveItem?.invoke(from, to) == true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        if (isReorderEnabled()) {
            return
        }
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

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            dragging = true
            val position = viewHolder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                ?: viewHolder.absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                ?: RecyclerView.NO_POSITION
            if (position != RecyclerView.NO_POSITION) {
                onDragStarted?.invoke(position)
            }
        }
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
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        } else if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
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
        if (dragging || isReorderEnabled()) {
            dragging = false
            super.clearView(recyclerView, viewHolder)
            onDragFinished?.invoke()
            return
        }
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

    private fun isReorderEnabled(): Boolean = reorderEnabledProvider?.invoke() == true

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
