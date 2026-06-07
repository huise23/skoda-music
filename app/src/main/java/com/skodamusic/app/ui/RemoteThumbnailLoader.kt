package com.skodamusic.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

class RemoteThumbnailLoader(
    private val backgroundExecutor: AppBackgroundExecutor,
    private val log: (String) -> Unit
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4000L, TimeUnit.MILLISECONDS)
        .readTimeout(6000L, TimeUnit.MILLISECONDS)
        .build()
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE_ITEMS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_CACHE_ITEMS
        }
    }

    fun load(imageView: ImageView, rawUrl: String, placeholderRes: Int) {
        val url = rawUrl.trim()
        imageView.setImageResource(placeholderRes)
        imageView.tag = url
        if (url.isEmpty()) {
            return
        }
        synchronized(cache) {
            cache[url]?.let { cached ->
                imageView.setImageBitmap(cached)
                return
            }
        }
        backgroundExecutor.execute {
            val bitmap = download(url)
            imageView.post {
                if (imageView.tag != url) {
                    return@post
                }
                if (bitmap != null) {
                    synchronized(cache) {
                        cache[url] = bitmap
                    }
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(placeholderRes)
                }
            }
        }
    }

    private fun download(url: String): Bitmap? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log("thumbnail load http=${response.code()} host=${hostOnly(url)}")
                    return null
                }
                response.body()?.byteStream()?.use { stream -> BitmapFactory.decodeStream(stream) }
            }
        }.onFailure { error ->
            log("thumbnail load failed type=${error.javaClass.simpleName} host=${hostOnly(url)}")
        }.getOrNull()
    }

    private fun hostOnly(url: String): String {
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore("/")
    }

    companion object {
        private const val MAX_CACHE_ITEMS = 48
    }
}
