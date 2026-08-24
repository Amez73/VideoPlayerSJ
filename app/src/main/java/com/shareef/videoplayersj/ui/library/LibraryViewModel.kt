package com.shareef.videoplayersj.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shareef.videoplayersj.data.repository.FolderRepository
import com.shareef.videoplayersj.data.repository.LibraryRepository
import com.shareef.videoplayersj.model.LibraryShow
import com.shareef.videoplayersj.model.LibraryVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val shows: List<LibraryShow> = emptyList(),
    val movies: List<LibraryVideo> = emptyList(),
    val isScanning: Boolean = false,
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val isScanning = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeShows(),
        libraryRepository.observeUngroupedVideos(),
        isScanning,
    ) { shows, movies, scanning ->
        LibraryUiState(shows = shows, movies = movies, isScanning = scanning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun onFolderPicked(treeUri: Uri, displayName: String) {
        viewModelScope.launch {
            folderRepository.addFolder(treeUri, displayName)
            rescan()
        }
    }

    fun rescan() {
        viewModelScope.launch {
            isScanning.value = true
            try {
                libraryRepository.rescanAll()
            } finally {
                isScanning.value = false
            }
        }
    }
}
