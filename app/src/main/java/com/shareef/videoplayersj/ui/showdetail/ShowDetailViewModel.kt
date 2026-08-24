package com.shareef.videoplayersj.ui.showdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shareef.videoplayersj.data.repository.LibraryRepository
import com.shareef.videoplayersj.model.LibraryVideo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ShowDetailViewModel(
    libraryRepository: LibraryRepository,
    showId: Long,
) : ViewModel() {

    val episodes: StateFlow<List<LibraryVideo>> = libraryRepository.observeEpisodes(showId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val title: StateFlow<String?> = libraryRepository.observeShowTitle(showId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
