package com.rhuta.kask.domain.util

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared context for cross-screen chat continuation.
 * Follows AINI's "Consume-on-Read" pattern to prevent ghost reloads.
 */
@Singleton
class SharedChatContext @Inject constructor() {
    private val _pendingHistoryId = MutableStateFlow<String?>(null)

    fun setPendingChat(historyId: String?) {
        _pendingHistoryId.value = historyId
    }

    fun consumePendingChat(): String? {
        val id = _pendingHistoryId.value
        _pendingHistoryId.value = null
        return id
    }
}
