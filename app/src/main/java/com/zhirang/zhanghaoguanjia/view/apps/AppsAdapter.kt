package com.zhirang.zhanghaoguanjia.view.apps

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.bean.AppInfo
import com.zhirang.zhanghaoguanjia.databinding.ItemAppBinding
import android.util.Log
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.view.ViewTreeObserver
import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformItemDto
import com.zhirang.zhanghaoguanjia.util.PlatformIconLoader
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry



class AppsAdapter : RVHolderFactory() {
    
    companion object {
        private const val TAG = "AppsAdapter"
        private const val MAX_ICON_SIZE = 96 
        private val DEFAULT_ICON_COLOR = Color.parseColor("#CCCCCC")
    }

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return try {
            AppsVH(inflate(R.layout.item_app, parent))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating ViewHolder: ${e.message}")
            
            FallbackAppsVH(inflate(R.layout.item_app, parent))
        }
    }

    class AppsVH(itemView: View) : RVHolder<AppInfo>(itemView) {
        val binding = ItemAppBinding.bind(itemView)
        private var currentIcon: Drawable? = null
        private var isAttached = false

        init {
            try {
                
                binding.icon.scaleType = ImageView.ScaleType.CENTER_CROP
                
                
                itemView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (isAttached) {
                            itemView.viewTreeObserver.removeOnPreDrawListener(this)
                        }
                        return true
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing ViewHolder: ${e.message}")
            }
        }

        override fun setContent(item: AppInfo, isSelected: Boolean, payload: Any?) {
            try {
                
                setIconSafely(item)
                
                
                val displayName = when {
                    // Priority 1: show shop name if available (e.g., 罗家臭豆腐(东瓜山店))
                    !item.shopName.isNullOrBlank() -> item.shopName
                    // Priority 2: show app name + shopId if shopId available
                    !item.shopId.isNullOrBlank() -> "${item.name}-${item.shopId}"
                    // Fallback: just app name
                    else -> item.name
                }
                binding.name.text = displayName

                // Show shopId as subtitle when shopName is displayed, or when shopId exists alone
                if (!item.shopId.isNullOrBlank()) {
                    binding.shopId.visibility = View.VISIBLE
                    binding.shopId.text = item.shopId
                } else {
                    binding.shopId.visibility = View.GONE
                }

                if (item.isXpModule) {
                    binding.cornerLabel.visibility = View.VISIBLE
                } else {
                    binding.cornerLabel.visibility = View.INVISIBLE
                }

                isAttached = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting content for ${item.packageName}: ${e.message}")
                setSafeDefaults()
            }
        }

        private fun setIconSafely(item: AppInfo) {
            try {
                if (item.icon != null) {
                    
                    val optimizedIcon = optimizeIcon(item.icon)
                    binding.icon.setImageDrawable(optimizedIcon)
                    PlatformIconLoader.applyDisabledState(binding.icon, item.platformAvailable)
                    currentIcon = optimizedIcon
                } else {
                    bindFallbackPlatformIcon(item)
                    currentIcon = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set icon for ${item.packageName}: ${e.message}")
                binding.icon.setImageDrawable(createDefaultIcon())
                PlatformIconLoader.applyDisabledState(binding.icon, item.platformAvailable)
                currentIcon = null
            }
        }

        private fun bindFallbackPlatformIcon(item: AppInfo) {
            val packageName = item.platformPackageName ?: item.packageName
            val packagePlatform = PlatformRegistry.preferredPlatformForPackage(packageName)
            val platform = packagePlatform?.platform ?: Platform.from(packageName, item.name)
            val platformItem = PlatformItemDto(
                platform = platform,
                displayName = item.name,
                packageName = packageName,
                iconKey = item.platformIconUrl ?: platform.id,
                available = item.platformAvailable
            )
            PlatformIconLoader.bind(
                imageView = binding.icon,
                item = platformItem,
                platform = platform,
                packageName = packageName,
                available = item.platformAvailable
            )
        }

        private fun optimizeIcon(icon: Drawable): Drawable {
            return try {
                
                if (icon is BitmapDrawable) {
                    val bitmap = icon.bitmap
                    if (bitmap.width > MAX_ICON_SIZE || bitmap.height > MAX_ICON_SIZE) {
                        
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap, MAX_ICON_SIZE, MAX_ICON_SIZE, true
                        )
                        BitmapDrawable(itemView.resources, scaledBitmap)
                    } else {
                        icon
                    }
                } else {
                    icon
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error optimizing icon: ${e.message}")
                icon
            }
        }

        private fun createDefaultIcon(): Drawable {
            return try {
                ColorDrawable(DEFAULT_ICON_COLOR)
            } catch (e: Exception) {
                Log.w(TAG, "Error creating default icon: ${e.message}")
                ColorDrawable(Color.GRAY)
            }
        }

        private fun setSafeDefaults() {
            try {
                binding.icon.setImageDrawable(createDefaultIcon())
                binding.name.text = "Unknown App"
                binding.shopId.visibility = View.GONE
                binding.cornerLabel.visibility = View.INVISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Error setting safe defaults: ${e.message}")
            }
        }
    }

    
    class FallbackAppsVH(itemView: View) : RVHolder<AppInfo>(itemView) {
        val binding = ItemAppBinding.bind(itemView)

        override fun setContent(item: AppInfo, isSelected: Boolean, payload: Any?) {
            try {

                binding.icon.setImageDrawable(ColorDrawable(DEFAULT_ICON_COLOR))
                PlatformIconLoader.applyDisabledState(binding.icon, item.platformAvailable)
                val displayName = when {
                    !item.shopName.isNullOrBlank() -> item.shopName
                    !item.shopId.isNullOrBlank() -> "${item.name}-${item.shopId}"
                    else -> item.name
                }
                binding.name.text = displayName
                if (!item.shopId.isNullOrBlank()) {
                    binding.shopId.visibility = View.VISIBLE
                    binding.shopId.text = item.shopId
                } else {
                    binding.shopId.visibility = View.GONE
                }
                binding.cornerLabel.visibility = View.INVISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Error in fallback ViewHolder: ${e.message}")
            }
        }
    }
}
