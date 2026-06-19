package com.flightbox.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-memory first-frame extractor. Pulls the closest sync frame from
 * a video via [MediaMetadataRetriever] and caches the resulting
 * [Bitmap] in a small LRU map.
 *
 * Step 7 will replace this with a disk-backed cache; for now memory
 * only is enough to make the strip snappy on re-visits within a
 * session.
 */
object ThumbnailLoader {

    private const val MAX_CACHE_SIZE = 24
    private val cache = object : LruCache<Uri, Bitmap>(MAX_CACHE_SIZE) {}
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun load(context: Context, uri: Uri, onLoaded: (Bitmap?) -> Unit) {
        cache.get(uri)?.let { onLoaded(it); return }
        scope.launch {
            val bitmap = extractFrame(context.applicationContext, uri)
            if (bitmap != null) cache.put(uri, bitmap)
            withContext(Dispatchers.Main) { onLoaded(bitmap) }
        }
    }

    private fun extractFrame(context: Context, uri: Uri): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }
}