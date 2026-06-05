package top.niunaijun.blackboxa.view.home

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.Shop
import top.niunaijun.blackboxa.util.PlatformIconLoader
import top.niunaijun.blackboxa.util.PlatformRegistry

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
        private val swipeActions: View = itemView.findViewById(R.id.swipeActions)
        private val shopLogo: ImageView = itemView.findViewById(R.id.shopLogo)
        private val shopName: TextView = itemView.findViewById(R.id.shopName)
        private val newTag: TextView = itemView.findViewById(R.id.newTag)
        private val shopId: TextView = itemView.findViewById(R.id.shopId)
        private val remainingDaysBadge: TextView = itemView.findViewById(R.id.remainingDaysBadge)
        private val autoRenewTriangle: View = itemView.findViewById(R.id.autoRenewTriangle)

        // Swipe action buttons
        private val btnEdit: View? = itemView.findViewById(R.id.btnEdit)
        private val btnAutoRenew: View? = itemView.findViewById(R.id.btnAutoRenew)
        private val btnDelete: View? = itemView.findViewById(R.id.btnDelete)

        fun bind(shop: Shop) {
            cardView.translationX = 0f
            cardView.bringToFront()
            swipeActions.isClickable = true
            swipeActions.isFocusable = false

            shopName.text = shop.shopName
            newTag.visibility = if (shop.isNew) View.VISIBLE else View.GONE
            shopId.text = "ID: ${if (shop.isNew) "-" else shop.shopId}"

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

            val platformItem = PlatformRegistry.get(shop.platform)
            PlatformIconLoader.bind(
                imageView = shopLogo,
                item = platformItem,
                platform = shop.platform,
                packageName = shop.packageName ?: platformItem?.packageName,
                available = platformItem?.available ?: true
            )

            // Card click
            cardView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos, shops[pos])
                }
            }

            // Swipe action clicks
            btnEdit?.setImmediateClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEditClick(pos, shops[pos])
                }
            }
            btnAutoRenew?.setImmediateClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAutoRenewClick(pos, shops[pos])
                }
            }
            btnDelete?.setImmediateClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClick(pos, shops[pos])
                }
            }
        }

        private fun View.setImmediateClickListener(action: () -> Unit) {
            isClickable = true
            isFocusable = true
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                itemView.parent?.requestDisallowInterceptTouchEvent(true)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        action()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        true
                    }
                    else -> true
                }
            }
        }
    }
}
