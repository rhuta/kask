package com.rhuta.kask.ui.markdown

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * JVM-side Markdown to HTML parser using Flexmark.
 * Supports GitHub Flavored Markdown and LaTeX blocks.
 */
object MarkdownParser {
    private val options = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TaskListExtension.create()
        ))
        set(HtmlRenderer.GENERATE_HEADER_ID, true)
        set(HtmlRenderer.ESCAPE_HTML, false)
        set(HtmlRenderer.SOFT_BREAK, "<br/>")
    }

    private val parser = Parser.builder(options).build()
    private val renderer = HtmlRenderer.builder(options).build()

    /**
     * Advanced parse with LaTeX protection.
     * Prevents Markdown engine from inserting <br/> tags inside $$ blocks.
     */
    fun parse(markdown: String): String {
        val mathBlocks = mutableListOf<String>()
        val placeholderPrefix = "MATHBLOCKPLACEHOLDER"
        
        // 1. Extract $$...$$ blocks
        var protectedMarkdown = markdown
        // Non-regex multi-line capture for stability
        var startIdx = protectedMarkdown.indexOf("$$")
        while (startIdx != -1) {
            val endIdx = protectedMarkdown.indexOf("$$", startIdx + 2)
            if (endIdx != -1) {
                val block = protectedMarkdown.substring(startIdx, endIdx + 2)
                val placeholder = "$placeholderPrefix${mathBlocks.size}"
                mathBlocks.add(block)
                protectedMarkdown = protectedMarkdown.substring(0, startIdx) + placeholder + protectedMarkdown.substring(endIdx + 2)
                startIdx = protectedMarkdown.indexOf("$$")
            } else {
                break
            }
        }

        // 2. Convert remaining Markdown to HTML
        val document = parser.parse(protectedMarkdown)
        var html = renderer.render(document)

        // 3. Restore the original LaTeX blocks exactly as they were (no <br/>)
        mathBlocks.forEachIndexed { index, originalBlock ->
            val placeholder = "$placeholderPrefix$index"
            html = html.replace(placeholder, originalBlock)
        }

        return html
    }
}
