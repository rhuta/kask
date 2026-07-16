package com.rhuta.kask.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhuta.kask.data.db.entities.HistoryEntity
import com.rhuta.kask.data.db.entities.LibraryEntity
import com.rhuta.kask.data.db.entities.contentTypeEnum
import com.rhuta.kask.data.db.entities.taskActionEnum
import com.rhuta.kask.data.repository.KaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val items: List<HistoryEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: KaskRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = _searchQuery
        .debounce(300L)
        .flatMapLatest { query ->
            val flow = if (query.isBlank()) repository.observeHistory()
                       else repository.searchHistory(query)
            flow.map { items ->
                HistoryUiState(items = items, searchQuery = query, isLoading = false)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(isLoading = true)
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(item: HistoryEntity) {
        viewModelScope.launch { repository.deleteHistory(item) }
    }

    fun undoDelete(item: HistoryEntity) {
        viewModelScope.launch { repository.saveHistory(item) }
    }

    fun saveToLibrary(item: HistoryEntity) {
        viewModelScope.launch {
            val libraryItem = LibraryEntity(
                title = "${item.taskActionEnum().name}: ${item.inputPreview.take(30)}",
                contentType = item.contentType,
                textContent = item.fullOutput,
                fileUri = item.inputUri
            )
            repository.saveLibraryItem(libraryItem)
            repository.deleteHistory(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
