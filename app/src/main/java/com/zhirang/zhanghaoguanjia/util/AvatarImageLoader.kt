package com.zhirang.zhanghaoguanjia.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors

object AvatarImageLoader {
    private val client = OkHttpClient()
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(20) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun bind(imageView: ImageView, fallbackView: TextView, avatarUrl: String?, initial: String) {
        fallbackView.text = initial.take(1).ifBlank { "我" }
        val rawUrl = avatarUrl?.takeIf { it.isNotBlank() }
        if (rawUrl == null) {
            showFallback(imageView, fallbackView)
            return
        }

        val resolvedUrl = RetrofitClient.resolveUrl(rawUrl)
        imageView.tag = resolvedUrl
        cache.get(resolvedUrl)?.let { bitmap ->
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
            fallbackView.visibility = View.GONE
            return
        }

        showFallback(imageView, fallbackView)
        executor.execute {
            val bitmap = loadBitmap(resolvedUrl) ?: return@execute
            cache.put(resolvedUrl, bitmap)
            mainHandler.post {
                if (imageView.tag == resolvedUrl) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                    fallbackView.visibility = View.GONE
                }
            }
        }
    }

    private fun showFallback(imageView: ImageView, fallbackView: TextView) {
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        fallbackView.visibility = View.VISIBLE
    }

    private fun loadBitmap(url: String): Bitmap? {
        return try {
            val requestBuilder = Request.Builder().url(url)
            TokenManager.getInstance().getToken()?.takeIf { it.isNotBlank() }?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                response.body?.byteStream()?.use(BitmapFactory::decodeStream)
            }
        } catch (e: Exception) {
            null
        }
    }
}
