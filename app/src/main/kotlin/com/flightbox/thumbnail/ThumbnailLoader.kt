package com.flightbox.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-memory first-frame extractor. Pulls the closest sync frame from
 * a video via [MediaMetadataRetriever] and caches the resulting
 * [Bitmap] in a small byte-budgeted LRU map.
 *
 * Each cached bitmap is downscaled to at most [TARGET_MAX_PX] on its
 * long edge so a long library cannot blow the app's memory budget
 * on a low-end device. On API 27+ we use the framework's
 * [MediaMetadataRetriever.getScaledFrameAtTime] to skip the full-res
 * allocation entirely; on API 26 (minSdk) we fall back to
 * [Bitmap.createScaledBitmap] over a regular frame.
 */
object ThumbnailLoader {

    // Cap the in-memory cache at ~24 MB so a long library cannot OOM
    // a low-end device. Sized by bytes, not by count.
    private const val MAX_CACHE_BYTES = 24 * 1024 * 1024

    // Largest dimension of the cached thumbnail. 480 px is plenty for
    // a strip card even on a tablet (cards are typically 240-360 dp
    // wide; 720-1080 px on xxhdpi, and 480x270 looks fine when
    // stretched). For 9:16 content the long side is 480, short ~270.
    private const val TARGET_MAX_PX = 480

    private val cache = object : LruCache<Uri, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: Uri, value: Bitmap): Int = value.byteCount
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun load(context: Context, uri: Uri, onLoaded: (Bitmap?) -> Unit) {
        cache.get(uri)?.let { onLoaded(it); return }
        scope.launch {
            val bitmap = extractFrame(context.applicationContext, uri, TARGET_MAX_PX)
            if (bitmap != null) cache.put(uri, bitmap)
            withContext(Dispatchers.Main) { onLoaded(bitmap) }
        }
    }

    private fun extractFrame(context: Context, uri: Uri, targetMaxPx: Int): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // API 27+: ask the framework for a pre-scaled frame,
                // which avoids allocating the full-resolution bitmap.
                mmr.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetMaxPx,
                    targetMaxPx
                )
            } else {
                // API 26 fallback: pull the full frame and scale.
                val full = mmr.getFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return null
                scaleToMax(full, targetMaxPx)
            }
        } catch (_: Exception) {
            null
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private fun scaleToMax(src: Bitmap, maxPx: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxPx && h <= maxPx) return src
        val scale = maxPx.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }
}