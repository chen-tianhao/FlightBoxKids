package com.flightbox

import android.app.Application
import com.flightbox.data.VideoItem

/**
 * Process-wide singleton holder.
 *
 * [videoCache] avoids serialising the video list through Intent extras
 * (which triggers TransactionTooLargeException beyond ~1 MB). Because
 * both [MainActivity] and [PlayerActivity] live in the same process,
 * a simple companion-object slot is safe and fast.
 *
 * The cache is a single-use slot:
 *  1. Writer sets it before starting [PlayerActivity].
 *  2. Reader consumes it in onCreate and immediately clears it.
 */
class FlightBoxApp : Application() {
    companion object {
        @JvmStatic
        var videoCache: List<VideoItem>? = null
    }
}