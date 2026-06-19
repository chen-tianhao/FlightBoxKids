package com.flightbox.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.flightbox.data.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State owner for the FlightBox player.
 *
 * The activity is locked to landscape, so there is exactly one player
 * (the "current video" shown in the top 2/3). The bottom 1/3 is a
 * ViewPager2 of thumbnails that simply re-targets [currentIndex] when
 * the user lands on a new page.
 *
 * Position memory: we remember where playback was paused per video
 * so that switching back resumes correctly.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** One ExoPlayer; the activity attaches it to the top-area PlayerView. */
    val player: ExoPlayer = ExoPlayer.Builder(application).build().also {
        it.repeatMode = Player.REPEAT_MODE_OFF
    }

    /** Per-video last-known position (ms). Switch back to a video and resume. */
    private val positions = mutableMapOf<Int, Long>()

    fun setVideos(list: List<VideoItem>) {
        _videos.value = list
        _currentIndex.value = 0
        positions.clear()
    }

    /**
     * Switch to a specific index. Idempotent: if the index is already
     * current, this is a no-op.
     */
    fun selectIndex(index: Int) {
        val max = (_videos.value.size - 1).coerceAtLeast(0)
        val safe = index.coerceIn(0, max)
        if (safe == _currentIndex.value) return
        // Persist the position of the video we are leaving.
        positions[_currentIndex.value] = player.currentPosition
        _currentIndex.value = safe
        loadCurrent()
    }

    /**
     * Load the video at [currentIndex] into the player and start
     * playing from the saved position (or 0).
     */
    fun loadCurrent() {
        val item = _videos.value.getOrNull(_currentIndex.value) ?: return
        player.stop()
        player.setMediaItem(MediaItem.fromUri(item.uri))
        val resume = positions[_currentIndex.value] ?: 0L
        if (resume > 0L) player.seekTo(resume)
        player.prepare()
        player.play()
    }

    fun pause() { player.playWhenReady = false }
    fun resume() { player.playWhenReady = true }

    fun currentVideo(): VideoItem? = _videos.value.getOrNull(_currentIndex.value)

    fun positionFor(index: Int): Long = positions[index] ?: 0L

    /** Save the current position for the currently-playing index. */
    fun persistCurrentPosition() {
        if (_videos.value.isNotEmpty()) {
            positions[_currentIndex.value] = player.currentPosition
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
