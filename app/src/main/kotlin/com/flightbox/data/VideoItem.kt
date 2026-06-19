package com.flightbox.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A single video file discovered in the user-chosen folder.
 *
 * Fields are filled in stages:
 * - `uri` and `displayName` come from the SAF tree walk (Step 2).
 * - `durationMs` and `sizeBytes` are extracted via MediaMetadataRetriever
 *   on a background thread; -1L means "not yet known".
 *
 * Parcelable so the player activity can carry items in its Intent extras
 * without rebuilding the list.
 */
@Parcelize
data class VideoItem(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long = -1L,
    val sizeBytes: Long = -1L
) : Parcelable