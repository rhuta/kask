package com.rhuta.kask.domain.model

/**
 * The type of content given to the AI.
 */
enum class ContentType {
    TEXT, PDF, IMAGE, AUDIO
}

/**
 * Stages of the audio recording flow.
 */
enum class RecordingStage {
    IDLE,
    RECORDING,
    REVIEW
}

/**
 * The action the user wants to perform on the content.
 */
enum class TaskAction {
    REWRITE,
    SUMMARIZE,
    TRANSLATE,
    EXTRACT,
    FIX_GRAMMAR,
    CHANGE_TONE,
    TRANSCRIBE,
    OCR,
    DESCRIBE,
    ASK_QUESTION,
    FREE_FORM
}

/**
 * Suggested action chip shown on the Home screen.
 * The visible set is derived from the current [ContentType].
 */
data class ActionChip(
    val action: TaskAction,
    val label: String,
    val contentTypes: Set<ContentType>,     // which input types expose this chip
    val defaultPrompt: String               // text to put in user bubble
)

val ALL_ACTION_CHIPS = listOf(
    ActionChip(TaskAction.SUMMARIZE,  "Summarize", setOf(ContentType.PDF),   "Summarize this document."),
    ActionChip(TaskAction.DESCRIBE,   "Describe",  setOf(ContentType.IMAGE), "Describe this image in detail."),
    ActionChip(TaskAction.TRANSCRIBE, "Transcribe",setOf(ContentType.AUDIO), "Transcribe this audio.")
)

fun actionsFor(contentType: ContentType): List<ActionChip> =
    if (contentType == ContentType.TEXT) emptyList() 
    else ALL_ACTION_CHIPS.filter { contentType in it.contentTypes }
