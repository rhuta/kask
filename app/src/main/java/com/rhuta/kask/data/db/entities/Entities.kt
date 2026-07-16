package com.rhuta.kask.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rhuta.kask.domain.model.ContentType
import com.rhuta.kask.domain.model.TaskAction
import java.util.UUID

/**
 * Persisted record of every AI task + result pair.
 * Written automatically after each successful inference.
 * Auto-pruned after [HISTORY_RETENTION_DAYS].
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val contentType: String,               // ContentType name
    val taskAction: String,                // TaskAction name
    val inputPreview: String,              // first 120 chars of input or filename
    val outputPreview: String,             // first 120 chars of output
    val fullOutput: String,                // complete result text
    val inputUri: String? = null,          // original file URI if applicable
    val conversationJson: String? = null,  // JSON serialized Conversation
    val processingTimeMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val HISTORY_RETENTION_DAYS = 30
    }
}

/**
 * User-saved item in the Library.
 * Created when the user explicitly taps "Save" on a Result.
 * Survives auto-pruning; only deleted by the user.
 */
@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,                     // user-editable display name
    val contentType: String,               // ContentType name
    val textContent: String? = null,       // for notes and text results
    val fileUri: String? = null,           // for PDFs, audio files
    val tags: String = "",                 // comma-separated tag list
    val isFavourite: Boolean = false,
    val isPinned: Boolean = false,         // pin to top of list
    val savedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis()
)

// ---- Extension mappers ------------------------------------------------

fun HistoryEntity.contentTypeEnum(): ContentType =
    runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.TEXT)

fun HistoryEntity.taskActionEnum(): TaskAction =
    runCatching { TaskAction.valueOf(taskAction) }.getOrDefault(TaskAction.FREE_FORM)

fun LibraryEntity.contentTypeEnum(): ContentType =
    runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.TEXT)

fun LibraryEntity.tagList(): List<String> =
    if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }
