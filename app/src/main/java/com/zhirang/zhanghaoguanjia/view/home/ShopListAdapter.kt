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
import com.zhirang.zhanghaoguanjia.bean.dto.AdvancedFeatureDto
import com.zhirang.zhanghaoguanjia.util.PlatformIconLoader
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry

class ShopListAdapter(
    private val onItemClick: (Int, Shop) -> Unit,
    private val onEditClick: (Int, Shop) -> Unit,
    private val onWechatClick: (Int, Shop) -> Unit,
    private val onQuickShareClick: (Int, Shop) -> Unit,
    private val onAdvancedFeatureClick: (Int, Shop, AdvancedFeatureType) -> Unit,
    private val onAutoRenewClick: (Int, Shop) -> Unit,
    private val onDeleteClick: (Int, Shop) -> Unit,
    private val onRepairClick: (Int, Shop) -> Unit
) : RecyclerView.Adapter<ShopListAdapter.VH>() {

    private var shops: List<Shop> = emptyList()
    private var expandedShopId: Long? = null
    private var showRemainingDays: Boolean = true
    private var showAutoRenewControls: Boolean = true
    private var reorderMode: Boolean = false
    private var draggingShopId: Long? = null
    private var advancedFeatures: Map<String, AdvancedFeatureDto> = emptyMap()
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

    fun setAdvancedFeatures(features: Map<String, AdvancedFeatureDto>) {
        if (advancedFeatures == features) {
            return
        }
        advancedFeatures = features
        notifyDataSetChanged()
    }

    fun setReorderMode(
        enabled: Boolean,
        draggingId: Long? = draggingShopId,
        refreshItems: Boolean = true
    ) {
        if (reorderMode == enabled && draggingShopId == draggingId) {
            return
        }
        reorderMode = enabled
        draggingShopId = draggingId
        if (refreshItems) {
            notifyDataSetChanged()
        }
    }

    fun refreshReorderVisualState(excludeShopId: Long? = null) {
        shops.forEachIndexed { index, shop ->
            if (shop.id != excludeShopId) {
                notifyItemChanged(index)
            }
        }
    }

    fun applyReorderVisualState(holder: RecyclerView.ViewHolder) {
        val shopHolder = holder as? VH ?: return
        val position = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
            ?: holder.absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
            ?: return
        val shop = shops.getOrNull(position) ?: return
        shopHolder.applyReorderVisualState(shop)
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
        setSubscriptionDisplayMode(showRemainingDays = show, showAutoRenewControls = show)
    }

    fun setSubscriptionDisplayMode(showRemainingDays: Boolean, showAutoRenewControls: Boolean) {
        if (this.showRemainingDays == showRemainingDays && this.showAutoRenewControls == showAutoRenewControls) {
            return
        }
        this.showRemainingDays = showRemainingDays
        this.showAutoRenewControls = showAutoRenewControls
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
        private val shopId: TextView = itemView.findViewById(R.id.shopId)
        private val shopRemark: TextView = itemView.findViewById(R.id.shopRemark)
        private val shopFeatureBar: View = itemView.findViewById(R.id.shopFeatureBar)
        private val btnQuickShare: View = itemView.findViewById(R.id.btnQuickShare)
        private val btnWechat: View = itemView.findViewById(R.id.btnWechat)
        private val btnFeatureBadReviewLocation: View = itemView.findViewById(R.id.btnFeatureBadReviewLocation)
        private val btnFeatureBusinessReport: View = itemView.findViewById(R.id.btnFeatureBusinessReport)
        private val btnFeatureOutboundPraise: View = itemView.findViewById(R.id.btnFeatureOutboundPraise)
        private val btnFeatureReviewAppeal: View = itemView.findViewById(R.id.btnFeatureReviewAppeal)
        private val btnFeaturePrivateTraffic: View = itemView.findViewById(R.id.btnFeaturePrivateTraffic)
        private val tvFeatureBadReviewLocationTitle: TextView = itemView.findViewById(R.id.tvFeatureBadReviewLocationTitle)
        private val tvFeatureBadReviewLocationLine1: TextView = itemView.findViewById(R.id.tvFeatureBadReviewLocationLine1)
        private val tvFeatureBadReviewLocationLine2: TextView = itemView.findViewById(R.id.tvFeatureBadReviewLocationLine2)
        private val tvFeatureBusinessReportTitle: TextView = itemView.findViewById(R.id.tvFeatureBusinessReportTitle)
        private val tvFeatureBusinessReportLine1: TextView = itemView.findViewById(R.id.tvFeatureBusinessReportLine1)
        private val tvFeatureBusinessReportLine2: TextView = itemView.findViewById(R.id.tvFeatureBusinessReportLine2)
        private val tvFeatureOutboundPraiseTitle: TextView = itemView.findViewById(R.id.tvFeatureOutboundPraiseTitle)
        private val tvFeatureOutboundPraiseLine1: TextView = itemView.findViewById(R.id.tvFeatureOutboundPraiseLine1)
        private val tvFeatureOutboundPraiseLine2: TextView = itemView.findViewById(R.id.tvFeatureOutboundPraiseLine2)
        private val tvFeatureReviewAppealTitle: TextView = itemView.findViewById(R.id.tvFeatureReviewAppealTitle)
        private val tvFeatureReviewAppealLine1: TextView = itemView.findViewById(R.id.tvFeatureReviewAppealLine1)
        private val tvFeatureReviewAppealLine2: TextView = itemView.findViewById(R.id.tvFeatureReviewAppealLine2)
        private val tvFeaturePrivateTrafficTitle: TextView = itemView.findViewById(R.id.tvFeaturePrivateTrafficTitle)
        private val tvFeaturePrivateTrafficLine1: TextView = itemView.findViewById(R.id.tvFeaturePrivateTrafficLine1)
        private val tvFeaturePrivateTrafficLine2: TextView = itemView.findViewById(R.id.tvFeaturePrivateTrafficLine2)
        private val daysContainer: View = itemView.findViewById(R.id.daysContainer)
        private val remainingDaysBadge: TextView = itemView.findViewById(R.id.remainingDaysBadge)
        private val autoRenewTriangle: View = itemView.findViewById(R.id.autoRenewTriangle)

        private val btnEdit: View? = itemView.findViewById(R.id.btnEdit)
        private val dividerBeforeAutoRenew: View? = itemView.findViewById(R.id.dividerBeforeAutoRenew)
        private val btnAutoRenew: View? = itemView.findViewById(R.id.btnAutoRenew)
        private val dividerAfterAutoRenew: View? = itemView.findViewById(R.id.dividerAfterAutoRenew)
        private val btnDelete: View? = itemView.findViewById(R.id.btnDelete)
        private val swipeRepairAction: View? = itemView.findViewById(R.id.swipeRepairAction)
        private val btnRepair: View? = itemView.findViewById(R.id.btnRepair)

        fun bind(shop: Shop) {
            cardContainer.bringToFront()
            val expanded = shop.id == expandedShopId
            swipeRepairAction?.visibility = if (expanded) View.VISIBLE else View.INVISIBLE
            cardContainer.translationX = if (expanded) -repairActionWidthPx(itemView) else 0f
            applyReorderVisualState(shop)

            shopName.text = shop.shopName
            val verifiedIdentity = shop.hasVerifiedIdentity
            val localIdentityVerified = shop.localIdentityVerified == true
            shopId.text = "店铺ID: ${if (verifiedIdentity) shop.shopId else "-"}"
            val isWechatCard = shop.packageName == WECHAT_PACKAGE
            btnWechat.visibility = if (isWechatCard) View.GONE else View.VISIBLE
            shopFeatureBar.visibility = if (isWechatCard) View.GONE else View.VISIBLE
            bindFeatureLabels()
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
            autoRenewTriangle.visibility =
                if (showAutoRenewControls && shop.autoRenew) View.VISIBLE else View.GONE
            dividerBeforeAutoRenew?.visibility = if (showAutoRenewControls) View.VISIBLE else View.GONE
            btnAutoRenew?.visibility = if (showAutoRenewControls) View.VISIBLE else View.GONE
            dividerAfterAutoRenew?.visibility = if (showAutoRenewControls) View.VISIBLE else View.GONE

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
                available = (platformItem?.available ?: true) && localIdentityVerified
            )
            shopLogo.contentDescription = if (localIdentityVerified) {
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
                    onLongPressDragStart?.invoke(this)
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
            bindAdvancedFeatureClick(btnFeatureBadReviewLocation, AdvancedFeatureType.BAD_REVIEW_LOCATION)
            bindAdvancedFeatureClick(btnFeatureBusinessReport, AdvancedFeatureType.BUSINESS_REPORT)
            bindAdvancedFeatureClick(btnFeatureOutboundPraise, AdvancedFeatureType.OUTBOUND_PRAISE)
            bindAdvancedFeatureClick(btnFeatureReviewAppeal, AdvancedFeatureType.REVIEW_APPEAL)
            bindAdvancedFeatureClick(btnFeaturePrivateTraffic, AdvancedFeatureType.PRIVATE_TRAFFIC)
            btnAutoRenew?.setOnClickListener {
                if (reorderMode || !showAutoRenewControls) return@setOnClickListener
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

        private fun bindAdvancedFeatureClick(view: View, featureType: AdvancedFeatureType) {
            view.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAdvancedFeatureClick(pos, shops[pos], featureType)
                }
            }
        }

        private fun bindFeatureLabels() {
            bindFeatureBlock(
                titleView = tvFeatureBadReviewLocationTitle,
                line1View = tvFeatureBadReviewLocationLine1,
                line2View = tvFeatureBadReviewLocationLine2,
                featureType = AdvancedFeatureType.BAD_REVIEW_LOCATION,
                fallbackTitleResId = R.string.shop_feature_bad_review_location
            )
            bindFeatureBlock(
                titleView = tvFeatureBusinessReportTitle,
                line1View = tvFeatureBusinessReportLine1,
                line2View = tvFeatureBusinessReportLine2,
                featureType = AdvancedFeatureType.BUSINESS_REPORT,
                fallbackTitleResId = R.string.shop_feature_business_report
            )
            bindFeatureBlock(
                titleView = tvFeatureOutboundPraiseTitle,
                line1View = tvFeatureOutboundPraiseLine1,
                line2View = tvFeatureOutboundPraiseLine2,
                featureType = AdvancedFeatureType.OUTBOUND_PRAISE,
                fallbackTitleResId = R.string.shop_feature_outbound_praise
            )
            bindFeatureBlock(
                titleView = tvFeatureReviewAppealTitle,
                line1View = tvFeatureReviewAppealLine1,
                line2View = tvFeatureReviewAppealLine2,
                featureType = AdvancedFeatureType.REVIEW_APPEAL,
                fallbackTitleResId = R.string.shop_feature_review_appeal
            )
            bindFeatureBlock(
                titleView = tvFeaturePrivateTrafficTitle,
                line1View = tvFeaturePrivateTrafficLine1,
                line2View = tvFeaturePrivateTrafficLine2,
                featureType = AdvancedFeatureType.PRIVATE_TRAFFIC,
                fallbackTitleResId = R.string.shop_feature_private_traffic
            )
        }

        private fun bindFeatureBlock(
            titleView: TextView,
            line1View: TextView,
            line2View: TextView,
            featureType: AdvancedFeatureType,
            fallbackTitleResId: Int
        ) {
            val feature = advancedFeatures[featureType.code]
            titleView.text = feature?.title?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(fallbackTitleResId)
            line1View.text = feature?.line1?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.shop_feature_custom_line_placeholder)
            line2View.text = feature?.line2?.takeIf { it.isNotBlank() }
                ?: itemView.context.getString(R.string.shop_feature_custom_line_placeholder)
        }

        fun applyReorderVisualState(shop: Shop) {
            if (!reorderMode) {
                cardContainer.clearAnimation()
                itemView.alpha = 1f
                itemView.scaleX = 1f
                itemView.scaleY = 1f
                itemView.rotation = 0f
                return
            }
            if (cardContainer.animation == null) {
                cardContainer.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.shop_card_wiggle))
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

enum class AdvancedFeatureType(
    val code: String,
    val fallbackTitleResId: Int
) {
    BAD_REVIEW_LOCATION("bad_review_location", R.string.shop_feature_bad_review_location),
    BUSINESS_REPORT("business_report", R.string.shop_feature_business_report),
    OUTBOUND_PRAISE("outbound_praise", R.string.shop_feature_outbound_praise),
    REVIEW_APPEAL("review_appeal", R.string.shop_feature_review_appeal),
    PRIVATE_TRAFFIC("private_traffic", R.string.shop_feature_private_traffic)
}
