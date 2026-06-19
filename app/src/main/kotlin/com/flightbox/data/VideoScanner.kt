package com.flightbox.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recursively walks a SAF tree URI and yields every file whose MIME
 * type starts with "video/". Hidden entries (names starting with ".")
 * are skipped, so Android-created bookkeeping folders like
 * `.thumbnails` and `.nomedia` do not pollute the list.
 */
class VideoScanner(private val context: Context) {

    /**
     * Returns all videos found under [treeUri]. Order is filesystem
     * order, which on most devices is creation / alphabetical.
     */
    suspend fun scan(treeUri: Uri): List<VideoItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<VideoItem>()
        walk(root, out)
        out
    }

    private fun walk(dir: DocumentFile, out: MutableList<VideoItem>) {
        val children: Array<DocumentFile> = try {
            dir.listFiles()
        } catch (_: Exception) {
            return
        }
        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(child, out)
            } else if (child.isFile) {
                val mime = child.type
                if (mime != null && mime.startsWith("video/")) {
                    out.add(
                        VideoItem(
                            uri = child.uri,
                            displayName = name,
                            durationMs = -1L,
                            sizeBytes = child.length().coerceAtLeast(0L)
                        )
                    )
                }
            }
        }
    }
}