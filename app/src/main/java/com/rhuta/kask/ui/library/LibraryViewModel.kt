package com.rhuta.kask.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhuta.kask.data.db.entities.LibraryEntity
import com.rhuta.kask.data.repository.KaskRepository
import com.rhuta.kask.domain.model.ContentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview

enum class LibraryFilter(val label: String) {
    ALL("All"), 
    PURE_TEXT("Pure Text"), 
    IMAGES("Images"),
    DOCUMENTS("Docs"), 
    AUDIO("Audio")
}

enum class LibrarySort(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    TITLE("Alphabetical")
}

data class LibraryUiState(
    val items: List<LibraryEntity> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val activeSort: LibrarySort = LibrarySort.DATE_DESC,
    val isLoading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: KaskRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    private val _sort = MutableStateFlow(LibrarySort.DATE_DESC)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val _items = combine(
        _searchQuery.debounce(300.milliseconds), 
        _filter
    ) { query, filter ->
        val typeString = when (filter) {
            LibraryFilter.ALL -> null
            LibraryFilter.PURE_TEXT -> ContentType.TEXT.name
            LibraryFilter.IMAGES -> ContentType.IMAGE.name
            LibraryFilter.DOCUMENTS -> ContentType.PDF.name
            LibraryFilter.AUDIO -> ContentType.AUDIO.name
        }
        repository.searchAndFilterLibrary(query, typeString)
    }.flatMapLatest { it }

    val uiState: StateFlow<LibraryUiState> = combine(
        _searchQuery,
        _filter,
        _sort,
        _items
    ) { query, filter, sort, items ->
        val sortedItems = when(sort) {
            LibrarySort.DATE_DESC -> items.sortedWith(compareByDescending<LibraryEntity> { it.isPinned }.thenByDescending { it.savedAt })
            LibrarySort.DATE_ASC -> items.sortedWith(compareByDescending<LibraryEntity> { it.isPinned }.thenBy { it.savedAt })
            LibrarySort.TITLE -> items.sortedWith(compareByDescending<LibraryEntity> { it.isPinned }.thenBy { it.title.lowercase() })
        }
        LibraryUiState(
            items = sortedItems,
            searchQuery = query,
            activeFilter = filter,
            activeSort = sort,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onFilterChange(filter: LibraryFilter) { _filter.value = filter }
    fun onSortChange(sort: LibrarySort) { _sort.value = sort }

    fun togglePin(item: LibraryEntity) {
        viewModelScope.launch {
            repository.setPinned(item.id, !item.isPinned)
        }
    }

    fun deleteItem(item: LibraryEntity) {
        viewModelScope.launch { repository.deleteLibraryItem(item) }
    }

    fun renameItem(item: LibraryEntity, newTitle: String) {
        viewModelScope.launch {
            repository.updateLibraryItem(item.copy(title = newTitle))
        }
    }
}
