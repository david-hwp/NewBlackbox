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
    private val onDeleteClick: (Int, Shop) -> Unit
) : RecyclerView.Adapter<ShopListAdapter.VH>() {

    private var shops: List<Shop> = emptyList()

    fun submitList(newList: List<Shop>) {
        shops = newList
        notifyDataSetChanged()
    }

    fun getShops(): List<Shop> = shops

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
        private val shopLogo: ImageView = itemView.findViewById(R.id.shopLogo)
        private val shopName: TextView = itemView.findViewById(R.id.shopName)
        private val newTag: TextView = itemView.findViewById(R.id.newTag)
        private val shopId: TextView = itemView.findViewById(R.id.shopId)
        private val remainingDaysBadge: TextView = itemView.findViewById(R.id.remainingDaysBadge)
        private val autoRenewTriangle: View = itemView.findViewById(R.id.autoRenewTriangle)

        private val btnEdit: View? = itemView.findViewById(R.id.btnEdit)
        private val btnAutoRenew: View? = itemView.findViewById(R.id.btnAutoRenew)
        private val btnDelete: View? = itemView.findViewById(R.id.btnDelete)

        fun bind(shop: Shop) {
            cardView.translationX = 0f

            shopName.text = shop.shopName
            newTag.visibility = if (shop.isNew) View.VISIBLE else View.GONE
            shopId.text = "店铺ID: ${if (shop.isNew) "-" else shop.shopId}"

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
                available = platformItem?.available ?: true
            )

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
        }
    }
}
