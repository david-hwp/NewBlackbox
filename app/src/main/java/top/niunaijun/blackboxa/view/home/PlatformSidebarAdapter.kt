package top.niunaijun.blackboxa.view.home

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.Platform
import top.niunaijun.blackboxa.bean.dto.PlatformItemDto
import top.niunaijun.blackboxa.network.RetrofitClient
import java.net.URL
import java.util.concurrent.Executors

class PlatformSidebarAdapter(
    private val onItemClick: (Int, PlatformItemDto) -> Unit
) : RecyclerView.Adapter<PlatformSidebarAdapter.VH>() {

    private var platforms: List<PlatformItemDto> = emptyList()
    private var selectedPosition = 0
    private val iconExecutor = Executors.newFixedThreadPool(2)
    private val iconCache = mutableMapOf<String, android.graphics.Bitmap>()

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
            if (item.available && position != selectedPosition) {
                val oldPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
                onItemClick(position, item)
            }
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
        private val platformLogo: ImageView = itemView.findViewById(R.id.platformLogo)
        private val platformName: TextView = itemView.findViewById(R.id.platformName)
        private val platformCount: TextView = itemView.findViewById(R.id.platformCount)

        fun bind(item: PlatformItemDto, isSelected: Boolean) {
            platformName.text = item.displayName

            // 加载平台 Logo 图片
            val logoRes = when (item.platform) {
                Platform.MEITUAN -> R.drawable.meituan
                Platform.TAOBAO -> R.drawable.qianniu
                Platform.JD -> R.drawable.jd
                Platform.KUAISHOU -> R.drawable.kuaishou
                Platform.XIAOHONGSHU -> R.drawable.xiaohongshu
                Platform.ALI -> R.drawable.koubei
            }
            platformLogo.setImageResource(logoRes)
            loadRemoteIcon(item, platformLogo, logoRes)
            platformLogo.alpha = if (item.available) 1f else 0.32f

            // 显示店铺数量
            val count = shopCounts[item.platform] ?: 0
            platformCount.text = "${count}家"
            itemView.isEnabled = item.available
            itemView.alpha = if (item.available) 1f else 0.72f

            if (isSelected && item.available) {
                indicator.visibility = View.VISIBLE
                itemView.setBackgroundResource(R.drawable.bg_platform_item_selected)
                platformName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.accent_green)
                )
            } else {
                indicator.visibility = View.INVISIBLE
                itemView.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.transparent)
                )
                platformName.setTextColor(
                    ContextCompat.getColor(itemView.context, if (item.available) R.color.fg_2 else R.color.meta)
                )
            }
        }

        private fun loadRemoteIcon(item: PlatformItemDto, imageView: ImageView, fallbackRes: Int) {
            val url = item.iconKey.takeIf { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("/") }
                ?: return
            val resolvedUrl = RetrofitClient.resolveUrl(url)
            val cached = iconCache[resolvedUrl]
            if (cached != null) {
                imageView.setImageBitmap(cached)
                return
            }
            val expectedPosition = bindingAdapterPosition
            iconExecutor.execute {
                runCatching {
                    URL(resolvedUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }.onSuccess { bitmap ->
                    if (bitmap != null) {
                        iconCache[resolvedUrl] = bitmap
                        imageView.post {
                            if (bindingAdapterPosition == expectedPosition) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                }.onFailure {
                    imageView.post {
                        if (bindingAdapterPosition == expectedPosition) {
                            imageView.setImageResource(fallbackRes)
                        }
                    }
                }
            }
        }
    }
}
