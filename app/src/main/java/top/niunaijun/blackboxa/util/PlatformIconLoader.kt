package top.niunaijun.blackboxa.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.Platform
import top.niunaijun.blackboxa.bean.dto.PlatformItemDto
import top.niunaijun.blackboxa.network.RetrofitClient
import java.net.URL
import java.util.concurrent.Executors

object PlatformIconLoader {
    private val executor = Executors.newFixedThreadPool(2)
    private val remoteCache = mutableMapOf<String, Bitmap>()
    private val packageIconCache = mutableMapOf<String, Drawable>()

    fun bind(
        imageView: ImageView,
        item: PlatformItemDto?,
        platform: Platform,
        packageName: String? = item?.packageName,
        available: Boolean = item?.available ?: true
    ) {
        val fallbackRes = fallbackRes(platform)
        val context = imageView.context.applicationContext
        imageView.tag = iconKey(item, platform, packageName)
        clearDisabledFilter(imageView)

        val hostIcon = loadInstalledPackageIcon(context, packageName)
        if (hostIcon != null) {
            imageView.setImageDrawable(hostIcon)
            applyDisabledState(imageView, available)
            return
        }

        imageView.setImageResource(fallbackRes)
        loadRemoteIcon(imageView, item, fallbackRes, available)
        applyDisabledState(imageView, available)
    }

    fun drawableForPackage(context: Context, packageName: String?): Drawable? =
        loadInstalledPackageIcon(context.applicationContext, packageName)

    fun applyDisabledState(imageView: ImageView, available: Boolean) {
        imageView.alpha = if (available) 1f else 0.38f
        imageView.colorFilter = if (available) {
            null
        } else {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
    }

    @DrawableRes
    fun fallbackRes(platform: Platform): Int = when (platform) {
        Platform.MEITUAN -> R.drawable.meituan
        Platform.TAOBAO -> R.drawable.qianniu
        Platform.JD -> R.drawable.jd
        Platform.KUAISHOU -> R.drawable.kuaishou
        Platform.XIAOHONGSHU -> R.drawable.xiaohongshu
        Platform.ALI -> R.drawable.koubei
    }

    private fun loadInstalledPackageIcon(context: Context, packageName: String?): Drawable? {
        if (packageName.isNullOrBlank()) {
            return null
        }
        packageIconCache[packageName]?.let { return it.constantState?.newDrawable() ?: it }
        return try {
            val icon = context.packageManager.getApplicationIcon(packageName)
            packageIconCache[packageName] = icon
            icon.constantState?.newDrawable() ?: icon
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun loadRemoteIcon(
        imageView: ImageView,
        item: PlatformItemDto?,
        @DrawableRes fallbackRes: Int,
        available: Boolean
    ) {
        val url = item?.iconKey
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("/") }
            ?: return
        val resolvedUrl = RetrofitClient.resolveUrl(url)
        val expectedTag = imageView.tag
        remoteCache[resolvedUrl]?.let {
            imageView.setImageBitmap(it)
            applyDisabledState(imageView, available)
            return
        }
        executor.execute {
            val bitmap = runCatching {
                URL(resolvedUrl).openStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            imageView.post {
                if (imageView.tag != expectedTag) {
                    return@post
                }
                if (bitmap != null) {
                    remoteCache[resolvedUrl] = bitmap
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(fallbackRes)
                }
                applyDisabledState(imageView, available)
            }
        }
    }

    private fun clearDisabledFilter(imageView: ImageView) {
        imageView.alpha = 1f
        imageView.colorFilter = null
    }

    private fun iconKey(item: PlatformItemDto?, platform: Platform, packageName: String?): String {
        return listOf(platform.id, packageName.orEmpty(), item?.iconKey.orEmpty(), item?.available.toString())
            .joinToString("#")
    }

}
