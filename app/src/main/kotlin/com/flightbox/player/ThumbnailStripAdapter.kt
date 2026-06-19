package com.flightbox.player

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flightbox.data.VideoItem
import com.flightbox.databinding.ItemThumbnailBinding
import com.flightbox.thumbnail.ThumbnailLoader

/**
 * Adapter for the bottom-1/3 horizontal strip in the player.
 *
 * Each card shows a thumbnail of the corresponding video and a
 * duration badge. There is NO playback inside the strip — picking
 * a card is a "switch to" gesture, not a "play this in-place"
 * gesture. The activity routes the choice through [PlayerViewModel].
 *
 * The currently-playing card gets a highlight overlay so the child
 * can see which video is playing in the top area.
 */
class ThumbnailStripAdapter(
    private val context: Context,
    private val videos: List<VideoItem>,
    private val onThumbnailClick: (Int) -> Unit
) : RecyclerView.Adapter<ThumbnailStripAdapter.VH>() {

    private var selectedIndex: Int = 0

    fun setSelectedIndex(index: Int) {
        if (index == selectedIndex || index !in 0 until itemCount) return
        val previous = selectedIndex
        selectedIndex = index
        // Refresh the two changed cards only — minimal work, no full
        // notifyDataSetChanged which can detach the current ViewPager2
        // page mid-scroll.
        notifyItemChanged(previous)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemThumbnailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val video = videos[position]
        with(holder.binding) {
            duration.text = formatDuration(video.durationMs)
            selectedOverlay.visibility =
                if (position == selectedIndex) View.VISIBLE else View.GONE
            root.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onThumbnailClick(pos)
            }
            thumbnail.setImageDrawable(null)
            ThumbnailLoader.load(context, video.uri) { bitmap ->
                if (bitmap != null && holder.bindingAdapterPosition == position) {
                    thumbnail.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun getItemCount(): Int = videos.size

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    class VH(val binding: ItemThumbnailBinding) : RecyclerView.ViewHolder(binding.root)
}
