package com.rhuta.kask.domain.engine

/**
 * Robust filter for AI "thinking" process blocks.
 * Supports partial tags and maintains a buffer to ensure only final answers are emitted.
 */
class ThinkingStateFilter(
    private val onToken: (String) -> Unit
) {
    private var isThinking = false
    private val buffer = StringBuilder()

    fun onTextReceived(text: String) {
        if (text.isEmpty()) return
        buffer.append(text)

        while (buffer.isNotEmpty()) {
            if (!isThinking) {
                val openIdx = buffer.indexOf("<think")
                if (openIdx != -1) {
                    val tagEnd = buffer.indexOf(">", openIdx)
                    if (tagEnd != -1) {
                        // Deliver content before thinking start
                        val before = buffer.substring(0, openIdx)
                        if (before.isNotEmpty()) onToken(before)
                        isThinking = true
                        buffer.delete(0, tagEnd + 1)
                    } else {
                        // Partial tag like "<think". Wait for more data.
                        val before = buffer.substring(0, openIdx)
                        if (before.isNotEmpty()) onToken(before)
                        buffer.delete(0, openIdx)
                        break
                    }
                } else {
                    // Check for potential tag start at the end of buffer
                    val lastBracket = buffer.lastIndexOf("<")
                    if (lastBracket != -1 && "<think".startsWith(buffer.substring(lastBracket))) {
                        val before = buffer.substring(0, lastBracket)
                        if (before.isNotEmpty()) onToken(before)
                        buffer.delete(0, lastBracket)
                        break
                    } else {
                        // No tag in buffer, deliver everything
                        onToken(buffer.toString())
                        buffer.setLength(0)
                    }
                }
            } else {
                val closeIdx = buffer.indexOf("</think")
                if (closeIdx != -1) {
                    val tagEnd = buffer.indexOf(">", closeIdx)
                    if (tagEnd != -1) {
                        isThinking = false
                        buffer.delete(0, tagEnd + 1)
                    } else {
                        // Partial close tag. Wait.
                        buffer.delete(0, closeIdx)
                        break
                    }
                } else {
                    // In thinking state, discard buffer but watch for potential close tag
                    val lastBracket = buffer.lastIndexOf("<")
                    if (lastBracket != -1 && "</think".startsWith(buffer.substring(lastBracket))) {
                        buffer.delete(0, lastBracket)
                        break
                    } else {
                        buffer.setLength(0)
                    }
                }
            }
        }
    }

    fun flush() {
        if (buffer.isNotEmpty() && !isThinking) {
            onToken(buffer.toString())
        }
        buffer.setLength(0)
    }
}
