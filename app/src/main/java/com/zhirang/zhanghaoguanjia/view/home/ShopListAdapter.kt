package com.zhirang.zhanghaoguanjia.view.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val onAutoRenewClick: (Int, Shop) -> Unit,
    private val onDeleteClick: (Int, Shop) -> Unit,
    private val onRepairClick: (Int, Shop) -> Unit
) : RecyclerView.Adapter<ShopListAdapter.VH>() {

    private var shops: List<Shop> = emptyList()
    private var expandedShopId: Long? = null

    fun submitList(newList: List<Shop>) {
        shops = newList
        if (expandedShopId != null && shops.none { it.id == expandedShopId }) {
            expandedShopId = null
        }
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

            shopName.text = shop.shopName
            val verifiedIdentity = shop.hasVerifiedIdentity
            newTag.visibility = if (shop.isNew) View.VISIBLE else View.GONE
            shopId.text = "店铺ID: ${if (verifiedIdentity) shop.shopId else "-"}"

            // 剩余天数显示（带颜色逻辑）
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
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos, shops[pos])
                }
            }

            btnEdit?.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEditClick(pos, shops[pos])
                }
            }
            btnAutoRenew?.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAutoRenewClick(pos, shops[pos])
                }
            }
            btnDelete?.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClick(pos, shops[pos])
                }
            }
            btnRepair?.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onRepairClick(pos, shops[pos])
                }
            }
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
}
