package com.zhirang.zhanghaoguanjia.view.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.bean.Shop
import com.zhirang.zhanghaoguanjia.util.PlatformIconLoader
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry

class ShopListAdapter(
    private val onItemClick: (Int, Shop) -> Unit,
    private val onEditClick: (Int, Shop) -> Unit,
    private val onWechatClick: (Int, Shop) -> Unit,
    private val onQuickShareClick: (Int, Shop) -> Unit,
    private val onAutoRenewClick: (Int, Shop) -> Unit,
    private val onDeleteClick: (Int, Shop) -> Unit,
    private val onRepairClick: (Int, Shop) -> Unit
) : RecyclerView.Adapter<ShopListAdapter.VH>() {

    private var shops: List<Shop> = emptyList()
    private var expandedShopId: Long? = null
    private var showRemainingDays: Boolean = true
    private var reorderMode: Boolean = false
    private var draggingShopId: Long? = null
    private var onLongPressDragStart: ((RecyclerView.ViewHolder) -> Unit)? = null

    fun submitList(newList: List<Shop>) {
        shops = newList
        if (expandedShopId != null && shops.none { it.id == expandedShopId }) {
            expandedShopId = null
        }
        notifyDataSetChanged()
    }

    fun setOnLongPressDragStart(listener: (RecyclerView.ViewHolder) -> Unit) {
        onLongPressDragStart = listener
    }

    fun setReorderMode(enabled: Boolean, draggingId: Long? = draggingShopId) {
        if (reorderMode == enabled && draggingShopId == draggingId) {
            return
        }
        reorderMode = enabled
        draggingShopId = draggingId
        notifyDataSetChanged()
    }

    fun setDraggingShopId(shopId: Long?) {
        if (draggingShopId == shopId) {
            return
        }
        val previousId = draggingShopId
        draggingShopId = shopId
        previousId?.let { notifyShopChanged(it) }
        shopId?.let { notifyShopChanged(it) }
    }

    fun isReorderMode(): Boolean = reorderMode

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in shops.indices || toPosition !in shops.indices || fromPosition == toPosition) {
            return false
        }
        val mutable = shops.toMutableList()
        val moved = mutable.removeAt(fromPosition)
        mutable.add(toPosition, moved)
        shops = mutable
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun setShowRemainingDays(show: Boolean) {
        if (showRemainingDays == show) {
            return
        }
        showRemainingDays = show
        notifyDataSetChanged()
    }

    fun getShops(): List<Shop> = shops

    fun getExpandedShopId(): Long? = expandedShopId

    fun getShopIdAt(position: Int): Long? = shops.getOrNull(position)?.id

    fun getExpandedShop(): Shop? = expandedShopId?.let { id ->
        shops.firstOrNull { it.id == id }
    }

    fun setExpandedShopId(shopId: Long?) {
        if (expandedShopId == shopId) {
            return
        }
        val previousId = expandedShopId
        expandedShopId = shopId?.takeIf { id -> shops.any { it.id == id } }
        previousId?.let { notifyShopChanged(it) }
        expandedShopId?.let { notifyShopChanged(it) }
    }

    fun clearExpandedShop() {
        setExpandedShopId(null)
    }

    fun refreshShop(shopId: Long) {
        notifyShopChanged(shopId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shop_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(shops[position])
    }

    override fun getItemCount(): Int = shops.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: View = itemView.findViewById(R.id.cardView)
        private val cardContainer: View = itemView.findViewById(R.id.cardContainer)
        private val shopLogo: ImageView = itemView.findViewById(R.id.shopLogo)
        private val shopName: TextView = itemView.findViewById(R.id.shopName)
        private val newTag: TextView = itemView.findViewById(R.id.newTag)
        private val shopId: TextView = itemView.findViewById(R.id.shopId)
        private val shopRemark: TextView = itemView.findViewById(R.id.shopRemark)
        private val shopFeatureBar: View = itemView.findViewById(R.id.shopFeatureBar)
        private val btnQuickShare: View = itemView.findViewById(R.id.btnQuickShare)
        private val btnWechat: View = itemView.findViewById(R.id.btnWechat)
        private val daysContainer: View = itemView.findViewById(R.id.daysContainer)
        private val remainingDaysBadge: TextView = itemView.findViewById(R.id.remainingDaysBadge)
        private val autoRenewTriangle: View = itemView.findViewById(R.id.autoRenewTriangle)

        private val btnEdit: View? = itemView.findViewById(R.id.btnEdit)
        private val btnAutoRenew: View? = itemView.findViewById(R.id.btnAutoRenew)
        private val btnDelete: View? = itemView.findViewById(R.id.btnDelete)
        private val swipeRepairAction: View? = itemView.findViewById(R.id.swipeRepairAction)
        private val btnRepair: View? = itemView.findViewById(R.id.btnRepair)

        fun bind(shop: Shop) {
            cardContainer.bringToFront()
            val expanded = shop.id == expandedShopId
            swipeRepairAction?.visibility = if (expanded) View.VISIBLE else View.INVISIBLE
            cardContainer.translationX = if (expanded) -repairActionWidthPx(itemView) else 0f
            bindReorderVisualState(shop)

            shopName.text = shop.shopName
            val verifiedIdentity = shop.hasVerifiedIdentity
            newTag.visibility = if (shop.isNew) View.VISIBLE else View.GONE
            shopId.text = "店铺ID: ${if (verifiedIdentity) shop.shopId else "-"}"
            val isWechatCard = shop.packageName == WECHAT_PACKAGE
            btnWechat.visibility = if (isWechatCard) View.GONE else View.VISIBLE
            shopFeatureBar.visibility = if (isWechatCard) View.GONE else View.VISIBLE
            val remark = shop.remark?.trim().orEmpty()
            if (remark.isNotEmpty()) {
                shopRemark.text = "备注: $remark"
                shopRemark.visibility = View.VISIBLE
            } else {
                shopRemark.text = ""
                shopRemark.visibility = View.GONE
            }

            // 剩余天数显示（带颜色逻辑）
            daysContainer.visibility = if (showRemainingDays) View.VISIBLE else View.GONE
            remainingDaysBadge.text = "${shop.remainingDays}天"
            val daysColor = when {
                shop.remainingDays <= 3 -> R.color.danger
                shop.remainingDays <= 7 -> R.color.warn
                else -> R.color.fg
            }
            remainingDaysBadge.setTextColor(
                ContextCompat.getColor(itemView.context, daysColor)
            )

            // 自动续时三角标
            autoRenewTriangle.visibility = if (shop.autoRenew) View.VISIBLE else View.GONE

            val packageName = shop.packageName?.takeIf { it.isNotBlank() }
            val platformItem = PlatformRegistry.preferredPlatformForPackage(packageName)
            val iconPlatform = platformItem?.platform
                ?: packageName?.let { com.zhirang.zhanghaoguanjia.bean.Platform.from(it, shop.shopName) }
                ?: shop.platform
            PlatformIconLoader.bind(
                imageView = shopLogo,
                item = platformItem,
                platform = iconPlatform,
                packageName = packageName,
                available = (platformItem?.available ?: true) && verifiedIdentity
            )
            shopLogo.contentDescription = if (verifiedIdentity) {
                "${shop.shopName}已登录"
            } else {
                "${shop.shopName}未登录"
            }

            // Card click
            cardView.setOnClickListener {
                if (reorderMode) {
                    return@setOnClickListener
                }
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos, shops[pos])
                }
            }
            cardView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    expandedShopId = null
                    draggingShopId = shops[pos].id
                    reorderMode = true
                    onLongPressDragStart?.invoke(this)
                    itemView.post { notifyDataSetChanged() }
                    true
                } else {
                    false
                }
            }

            btnEdit?.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEditClick(pos, shops[pos])
                }
            }
            btnWechat.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onWechatClick(pos, shops[pos])
                }
            }
            btnQuickShare.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onQuickShareClick(pos, shops[pos])
                }
            }
            btnAutoRenew?.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAutoRenewClick(pos, shops[pos])
                }
            }
            btnDelete?.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClick(pos, shops[pos])
                }
            }
            btnRepair?.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onRepairClick(pos, shops[pos])
                }
            }
        }

        private fun bindReorderVisualState(shop: Shop) {
            if (!reorderMode) {
                cardView.clearAnimation()
                itemView.alpha = 1f
                itemView.scaleX = 1f
                itemView.scaleY = 1f
                itemView.rotation = 0f
                return
            }
            if (cardView.animation == null) {
                cardView.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.shop_card_wiggle))
            }
            val dragging = shop.id == draggingShopId
            itemView.alpha = if (dragging) 1f else 0.72f
            itemView.scaleX = if (dragging) 1.015f else 1f
            itemView.scaleY = if (dragging) 1.015f else 1f
        }
    }

    private fun notifyShopChanged(shopId: Long) {
        val position = shops.indexOfFirst { it.id == shopId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    private fun repairActionWidthPx(view: View): Float {
        return 96f * view.resources.displayMetrics.density
    }

    private companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
    }
}
