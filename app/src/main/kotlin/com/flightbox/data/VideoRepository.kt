package com.flightbox.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Two-stage video pipeline:
 *  1. Cheap filesystem walk via [VideoScanner] produces the initial list.
 *  2. Slower per-file metadata extraction enriches each item in place.
 *
 * Emits a fresh list snapshot at every progress point so the UI can
 * show "Found N videos" the moment the walk finishes, then update
 * durations as they are resolved.
 */
class VideoRepository(
    private val context: Context,
    private val scanner: VideoScanner
) {

    fun videosInTree(treeUri: Uri): Flow<List<VideoItem>> = flow {
        val scanned = scanner.scan(treeUri)
        emit(scanned)

        val current = scanned.toMutableList()
        for (i in current.indices) {
            val item = current[i]
            val updated = item.copy(durationMs = readDurationMs(item.uri))
            current[i] = updated
            emit(current.toList())
        }
    }.flowOn(Dispatchers.IO)

    private fun readDurationMs(uri: Uri): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: -1L
        } catch (_: Exception) {
            -1L
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }
}