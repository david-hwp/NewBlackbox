package com.zhirang.zhanghaoguanjia.view.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformItemDto
import com.zhirang.zhanghaoguanjia.util.PlatformIconLoader

class PlatformSidebarAdapter(
    private val onItemClick: (Int, PlatformItemDto) -> Unit
) : RecyclerView.Adapter<PlatformSidebarAdapter.VH>() {

    private var platforms: List<PlatformItemDto> = emptyList()
    private var selectedPosition = 0

    // 每个平台的店铺数量（临时数据，实际应从外部传入）
    private var shopCounts: Map<Platform, Int> = emptyMap()

    fun setShopCounts(counts: Map<Platform, Int>) {
        shopCounts = counts
        notifyDataSetChanged()
    }

    fun submitList(newPlatforms: List<PlatformItemDto>) {
        platforms = newPlatforms
        selectedPosition = platforms.indexOfFirst { it.available }.takeIf { it >= 0 } ?: 0
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_platform, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = platforms[position]
        holder.bind(item, position == selectedPosition)
        holder.itemView.setOnClickListener {
            if (!item.available) {
                onItemClick(position, item)
                return@setOnClickListener
            }
            if (position != selectedPosition) {
                val oldPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
            }
            onItemClick(position, item)
        }
    }

    override fun getItemCount(): Int = platforms.size

    fun getSelectedPlatform(): Platform? = platforms.getOrNull(selectedPosition)?.platform

    fun selectPosition(position: Int) {
        if (position in platforms.indices && position != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indicator: View = itemView.findViewById(R.id.indicator)
        private val platformContent: View = itemView.findViewById(R.id.platformContent)
        private val platformLogo: ImageView = itemView.findViewById(R.id.platformLogo)
        private val platformName: TextView = itemView.findViewById(R.id.platformName)
        private val platformCount: TextView = itemView.findViewById(R.id.platformCount)

        fun bind(item: PlatformItemDto, isSelected: Boolean) {
            platformName.text = item.displayName

            PlatformIconLoader.bind(platformLogo, item, item.platform, item.packageName, item.available)

            // 显示店铺数量
            val count = shopCounts[item.platform] ?: 0
            platformCount.text = "${count}家"
            itemView.isEnabled = true
            itemView.alpha = if (item.available) 1f else 0.72f

            if (isSelected && item.available) {
                indicator.visibility = View.VISIBLE
                platformContent.setBackgroundResource(R.drawable.bg_platform_item_selected)
                platformName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.accent_green)
                )
            } else {
                indicator.visibility = View.INVISIBLE
                platformContent.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.transparent)
                )
                platformName.setTextColor(
                    ContextCompat.getColor(itemView.context, if (item.available) R.color.fg_2 else R.color.meta)
                )
            }
        }

    }
}
