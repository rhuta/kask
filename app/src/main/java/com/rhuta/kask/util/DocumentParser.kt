package com.rhuta.kask.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

object DocumentParser {
    private const val TAG = "DocumentParser"
    private const val MAX_CHARS = 12000
    private const val MAX_PDF_PAGES = 50

    fun extractText(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = when {
                    mimeType.contains("pdf", ignoreCase = true) -> {
                        extractTextFromPdf(context, inputStream)
                    }
                    mimeType.contains("wordprocessingml.document", ignoreCase = true) || 
                    uri.path?.endsWith(".docx", ignoreCase = true) == true -> {
                        extractTextFromDocx(inputStream)
                    }
                    else -> {
                        // Default to plain text for .txt, .md, etc.
                        inputStream.bufferedReader().use { it.readText() }
                    }
                }
                
                if (text.length > MAX_CHARS) {
                    text.take(MAX_CHARS) + "\n... [Text truncated for context limit] ..."
                } else {
                    text
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from $uri", e)
            "Error: Could not extract text from file."
        }
    }

    private fun extractTextFromPdf(context: Context, inputStream: InputStream): String {
        return try {
            PDFBoxResourceLoader.init(context)
            // Use temp file for large PDFs to avoid OOM
            PDDocument.load(inputStream, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                val stripper = PDFTextStripper()
                stripper.endPage = MAX_PDF_PAGES
                stripper.getText(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction failed", e)
            throw e
        }
    }

    private fun extractTextFromDocx(inputStream: InputStream): String {
        return try {
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            val sb = StringBuilder()
            
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    sb.append(parseDocxXml(zipInputStream))
                    break
                }
                entry = zipInputStream.nextEntry
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "DOCX extraction failed", e)
            throw e
        }
    }

    private fun parseDocxXml(inputStream: InputStream): String {
        val sb = StringBuilder()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "w:t") {
                        sb.append(parser.nextText())
                    } else if (name == "w:p" || name == "w:br" || name == "w:cr") {
                        sb.append("\n")
                    }
                }
            }
            eventType = parser.next()
        }
        return sb.toString().trim()
    }
}
