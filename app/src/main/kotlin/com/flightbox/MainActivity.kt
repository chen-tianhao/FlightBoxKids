package com.flightbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flightbox.data.FolderPreference
import com.flightbox.databinding.ActivityMainBinding
import com.flightbox.player.PlayerActivity
import com.flightbox.ui.MainViewModel
import com.flightbox.ui.ScanUiState
import kotlinx.coroutines.launch

/**
 * Entry screen.
 *
 * Concerns:
 *  1. Pick / change / clear the source folder (see [FolderPreference]).
 *  2. Surface the scan progress / result owned by [MainViewModel].
 *  3. Hand the folder off to [PlayerActivity] when the user taps Play.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var folderPref: FolderPreference
    private val viewModel: MainViewModel by viewModels()

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onFolderPicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderPref = FolderPreference(this)

        binding.pickButton.setOnClickListener { pickFolder.launch(null) }
        binding.changeButton.setOnClickListener { pickFolder.launch(null) }
        binding.clearButton.setOnClickListener {
            releaseCurrentPermission()
            folderPref.clear()
            viewModel.cancelScan()
            refreshUi()
        }
        binding.rescanButton.setOnClickListener { viewModel.rescan() }
        binding.playButton.setOnClickListener {
            val uri = folderPref.folderUri ?: return@setOnClickListener
            val videos = viewModel.uiState.value.videos
            if (videos.isEmpty()) return@setOnClickListener
            // Hand the enriched list through a process-level slot so
            // PlayerActivity does not re-scan and cannot hit
            // TransactionTooLargeException via the Intent.
            FlightBoxApp.videoCache = videos
            PlayerActivity.start(this, uri)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderScanState(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        viewModel.rescan()
    }

    private fun onFolderPicked(newUri: Uri) {
        releaseCurrentPermission()
        try {
            contentResolver.takePersistableUriPermission(
                newUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.error_persist_permission, Toast.LENGTH_LONG).show()
            return
        }
        folderPref.folderUri = newUri
        Toast.makeText(this, R.string.toast_folder_picked, Toast.LENGTH_SHORT).show()
        refreshUi()
        viewModel.rescan()
    }

    private fun releaseCurrentPermission() {
        val old = folderPref.folderUri ?: return
        try {
            contentResolver.releasePersistableUriPermission(
                old,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Best effort.
        }
    }

    private fun refreshUi() {
        val uri = folderPref.folderUri
        if (uri == null) {
            binding.emptyState.isVisible = true
            binding.filledState.isVisible = false
            binding.pickButton.isVisible = true
            binding.changeButton.isVisible = false
            binding.clearButton.isVisible = false
        } else {
            binding.emptyState.isVisible = false
            binding.filledState.isVisible = true
            binding.pickButton.isVisible = false
            binding.changeButton.isVisible = true
            binding.clearButton.isVisible = true

            binding.folderName.text =
                queryTreeDisplayName(uri) ?: getString(R.string.folder_name_fallback)
            binding.folderUri.text = uri.toString()
            binding.permissionWarning.isVisible = !folderPref.isValid(this)
        }
    }

    private fun renderScanState(state: ScanUiState) {
        binding.scanProgress.isVisible = state.isScanning
        binding.rescanButton.isEnabled = !state.isScanning
        binding.videoCount.text = when {
            state.isScanning -> getString(R.string.scan_scanning)
            state.videos.isEmpty() -> getString(R.string.scan_empty)
            else -> resources.getQuantityString(
                R.plurals.scan_count,
                state.videos.size,
                state.videos.size
            )
        }
        // Play only makes sense when there is at least one video.
        binding.playButton.isVisible = state.videos.isNotEmpty() && !state.isScanning
    }

    private fun queryTreeDisplayName(treeUri: Uri): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        return contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }
}