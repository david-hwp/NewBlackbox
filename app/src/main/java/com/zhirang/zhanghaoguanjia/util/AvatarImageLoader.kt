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
import java.util.Collections
import java.util.concurrent.Executors

object AvatarImageLoader {
    private val client = OkHttpClient()
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(20) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val missingThumbnails = Collections.synchronizedSet(mutableSetOf<String>())

    fun bind(imageView: ImageView, fallbackView: TextView, avatarUrl: String?, initial: String) {
        fallbackView.text = initial.take(1).ifBlank { "我" }
        val rawUrl = avatarUrl?.takeIf { it.isNotBlank() }
        if (rawUrl == null) {
            showFallback(imageView, fallbackView)
            return
        }

        val resolvedUrl = RetrofitClient.resolveUrl(rawUrl)
        imageView.tag = resolvedUrl
        val thumbnailUrl = thumbnailUrl(resolvedUrl)
        (cache.get(thumbnailUrl) ?: cache.get(resolvedUrl))?.let { bitmap ->
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
            fallbackView.visibility = View.GONE
            return
        }

        showFallback(imageView, fallbackView)
        executor.execute {
            val loaded = loadPreferredBitmap(thumbnailUrl, resolvedUrl) ?: return@execute
            val loadedUrl = loaded.first
            val bitmap = loaded.second
            cache.put(loadedUrl, bitmap)
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

    private fun loadPreferredBitmap(thumbnailUrl: String, originalUrl: String): Pair<String, Bitmap>? {
        if (thumbnailUrl != originalUrl && !missingThumbnails.contains(thumbnailUrl)) {
            loadBitmap(thumbnailUrl)?.let { bitmap ->
                return thumbnailUrl to bitmap
            }
            missingThumbnails.add(thumbnailUrl)
        }
        return loadBitmap(originalUrl)?.let { originalUrl to it }
    }

    private fun thumbnailUrl(url: String): String {
        if (url.endsWith(".thumb.jpg")) {
            return url
        }
        val queryStart = url.indexOf('?')
        val hashStart = url.indexOf('#')
        val splitIndex = listOf(queryStart, hashStart).filter { it >= 0 }.minOrNull() ?: url.length
        val path = url.substring(0, splitIndex)
        val suffix = url.substring(splitIndex)
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        val thumbPath = if (dot > slash) {
            path.substring(0, dot) + ".thumb.jpg"
        } else {
            "$path.thumb.jpg"
        }
        return thumbPath + suffix
    }
}
