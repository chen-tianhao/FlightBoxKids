package com.flightbox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flightbox.data.FolderPreference
import com.flightbox.data.VideoItem
import com.flightbox.data.VideoRepository
import com.flightbox.data.VideoScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the main (folder / scan) screen. */
data class ScanUiState(
    val isScanning: Boolean = false,
    val videos: List<VideoItem> = emptyList()
)

/**
 * Owns the scan pipeline and exposes a [StateFlow] the Activity
 * collects. The scan survives configuration changes (rotation) because
 * the ViewModel does, but is cancelled when the user clears the
 * folder or picks a different one.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val folderPref = FolderPreference(application)
    private val repository = VideoRepository(
        application,
        VideoScanner(application)
    )

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    /**
     * Start (or restart) a scan of the currently saved folder. If no
     * folder is saved, the state is reset and nothing happens.
     */
    fun rescan() {
        scanJob?.cancel()
        val uri = folderPref.folderUri ?: run {
            _uiState.value = ScanUiState()
            return
        }
        _uiState.update { it.copy(isScanning = true, videos = emptyList()) }
        scanJob = viewModelScope.launch {
            repository.videosInTree(uri).collect { list ->
                _uiState.update { ScanUiState(isScanning = false, videos = list) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.value = ScanUiState()
    }
}