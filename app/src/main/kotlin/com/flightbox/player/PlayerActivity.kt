package com.flightbox.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.flightbox.FlightBoxApp
import com.flightbox.R
import com.flightbox.data.VideoItem
import com.flightbox.data.VideoScanner
import com.flightbox.databinding.ActivityPlayerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Player - LANDSCAPE ONLY.
 *
 * The activity is locked to landscape by AndroidManifest. The screen
 * is split into a top 2/3 player area and a bottom 1/3 thumbnail
 * strip. The strip:
 *  - Is visible by default.
 *  - Toggles to hidden when the child taps the top 2/3 area, and
 *    toggles back to visible on a subsequent tap.
 *  - Auto-hides 5 seconds after the last touch anywhere on the
 *    screen.
 *
 * The bottom strip is a horizontal carousel of 4:3 thumbnails.
 * Swiping the strip scrolls the carousel freely; tapping a
 * thumbnail is what actually switches videos. The back button
 * (top-left of the strip) is the only way to leave the player; the
 * system back key does the same.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    private var scanJob: Job? = null
    private var autoHideJob: Job? = null

    /** Public visibility state of the bottom strip. */
    private var stripVisible: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Always landscape. Setting this here in addition to the
        // manifest is a belt-and-braces measure against weird OEM
        // launch behaviours.
        // (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE is set in manifest)

        // Immersive - hide the system bars.
        enterImmersiveMode()

        // Top 2/3 tap - toggle strip.
        binding.topArea.setOnClickListener { toggleStrip() }

        // Back button (top-left of the strip) - leave the player.
        binding.backButton.setOnClickListener { finish() }

        // System back also leaves (it does not toggle the strip; the
        // strip is purely a per-session UI element).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        // Strip scrolling resets the auto-hide timer.
        val touchListenerTouchResetter = View.OnTouchListener { v, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) scheduleAutoHide()
            false  // do not consume
        }
        binding.topArea.setOnTouchListener(touchListenerTouchResetter)
        binding.stripContainer.setOnTouchListener(touchListenerTouchResetter)
        binding.thumbPager.setOnTouchListener(touchListenerTouchResetter)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentIndex.collect { index ->
                    updateTitle(index)
                    (binding.thumbPager.adapter as? ThumbnailStripAdapter)
                        ?.setSelectedIndex(index)
                    // When the model moves (via a tap on a thumbnail,
                    // or a future auto-advance), scroll the strip so
                    // the new selection is visible. Swiping the strip
                    // itself is purely a scroll gesture; only tapping
                    // a thumbnail actually switches videos.
                    if (stripVisible) {
                        binding.thumbPager.smoothScrollToPosition(index)
                    }
                }
            }
        }

        if (viewModel.videos.value.isEmpty()) {
            loadVideosOrScan()
        } else {
            setupStrip()
        }
    }

    private fun loadVideosOrScan() {
        val cached = FlightBoxApp.videoCache
        FlightBoxApp.videoCache = null
        if (!cached.isNullOrEmpty()) {
            viewModel.setVideos(cached)
            setupStrip()
            return
        }
        @Suppress("DEPRECATION")
        val extras = intent.getParcelableArrayListExtra<VideoItem>(EXTRA_VIDEOS)
        if (!extras.isNullOrEmpty()) {
            viewModel.setVideos(extras)
            setupStrip()
            return
        }
        val treeUriString = intent.getStringExtra(EXTRA_TREE_URI)
        if (treeUriString == null) { finish(); return }
        val treeUri = treeUriString.toUri()
        scanJob = lifecycleScope.launch {
            val scanned = VideoScanner(this@PlayerActivity).scan(treeUri)
            if (scanned.isEmpty()) {
                Toast.makeText(
                    this@PlayerActivity,
                    R.string.player_no_videos,
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return@launch
            }
            viewModel.setVideos(scanned)
            setupStrip()
        }
    }

    /**
     * Wire up the bottom strip (RecyclerView + adapter + tap routing).
     * Called once, after the video list is in the ViewModel.
     */
    private fun setupStrip() {
        val videos = viewModel.videos.value
        binding.playerView.player = viewModel.player
        binding.thumbPager.layoutManager = LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false
        )
        binding.thumbPager.adapter = ThumbnailStripAdapter(this, videos) { position ->
            // Tap a thumbnail - switch to that video.
            if (position != viewModel.currentIndex.value) {
                viewModel.selectIndex(position)
            } else {
                // Tapping the already-playing thumbnail just re-shows
                // the strip if it is hidden, and restarts the timer.
                showStrip()
            }
        }
        // Bring the initial selection into view without animation.
        binding.thumbPager.scrollToPosition(viewModel.currentIndex.value)
        viewModel.loadCurrent()
        updateTitle(viewModel.currentIndex.value)
        // Make sure the strip starts visible.
        showStrip()
    }

    private fun updateTitle(index: Int) {
        val video = viewModel.videos.value.getOrNull(index) ?: return
        binding.titleOverlay.text = video.displayName
    }

    // ----- strip visibility -------------------------------------------------

    private fun toggleStrip() {
        if (stripVisible) hideStrip() else showStrip()
    }

    private fun showStrip() {
        if (!stripVisible) {
            binding.stripContainer.visibility = View.VISIBLE
            stripVisible = true
        }
        scheduleAutoHide()
    }

    private fun hideStrip() {
        if (stripVisible) {
            binding.stripContainer.visibility = View.GONE
            stripVisible = false
        }
        autoHideJob?.cancel()
        autoHideJob = null
    }

    /**
     * (Re)start the 5-second auto-hide timer. Any touch anywhere on
     * the screen resets it; if no touch happens within 5 seconds, the
     * strip is hidden so the top video gets the full screen.
     */
    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = lifecycleScope.launch {
            delay(AUTO_HIDE_DELAY_MS)
            if (stripVisible) {
                binding.stripContainer.visibility = View.GONE
                stripVisible = false
            }
        }
    }

    // ----- lifecycle --------------------------------------------------------

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onPause() {
        super.onPause()
        autoHideJob?.cancel()
        viewModel.persistCurrentPosition()
        viewModel.pause()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.videos.value.isNotEmpty()) {
            viewModel.resume()
        }
        // If the strip was visible when we paused, keep it visible
        // (and restart the auto-hide timer). If it was hidden, leave
        // it hidden so the user comes back to full-screen.
        if (stripVisible) scheduleAutoHide()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
        autoHideJob?.cancel()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        private const val EXTRA_TREE_URI = "extra_tree_uri"
        private const val EXTRA_VIDEOS = "extra_videos"
        private const val AUTO_HIDE_DELAY_MS = 5_000L

        fun start(context: Context, treeUri: Uri, videos: List<VideoItem> = emptyList()) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_TREE_URI, treeUri.toString())
                if (videos.isNotEmpty()) {
                    putParcelableArrayListExtra(EXTRA_VIDEOS, ArrayList(videos))
                }
            }
            context.startActivity(intent)
        }
    }
}