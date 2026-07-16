package com.rhuta.kask.domain.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.util.zip.ZipInputStream

class TextExtractor(private val context: Context) {

    suspend fun extractText(uri: Uri): String = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)
        val fileName = uri.path?.lowercase() ?: ""
        
        return@withContext try {
            when {
                mimeType == "application/pdf" || fileName.endsWith(".pdf") -> {
                    extractFromPdf(uri)
                }
                mimeType?.contains("officedocument.wordprocessingml") == true || fileName.endsWith(".docx") -> {
                    extractFromDocx(uri)
                }
                mimeType?.contains("officedocument.presentationml") == true || fileName.endsWith(".pptx") -> {
                    extractFromPptx(uri)
                }
                mimeType?.contains("officedocument.spreadsheetml") == true || fileName.endsWith(".xlsx") -> {
                    extractFromXlsx(uri)
                }
                else -> {
                    extractFromText(uri)
                }
            }
        } catch (e: Exception) {
            "Error extracting text: ${e.message}"
        }
    }

    private fun extractFromDocx(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val result = parseDocxXml(zipInputStream)
                        zipInputStream.closeEntry()
                        return result
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
                "Could not find document content in Word file."
            } ?: "Failed to open Word stream"
        } catch (e: Exception) {
            "Error parsing Word document: ${e.message}"
        }
    }

    private fun extractFromPptx(uri: Uri): String {
        return try {
            val builder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                val characterLimit = 8000
                val slideContents = mutableMapOf<Int, String>()
                
                while (entry != null) {
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        val slideNumber = entry.name.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0
                        val text = parsePptxXml(zipInputStream)
                        slideContents[slideNumber] = text
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
                
                slideContents.keys.sorted().forEach { slideNum ->
                    if (builder.length < characterLimit) {
                        builder.append("\n[Slide $slideNum]\n").append(slideContents[slideNum])
                    }
                }
                
                if (builder.length >= characterLimit) {
                    builder.setLength(characterLimit)
                    builder.append("\n\n[Note: Document truncated for AI context optimization]")
                }
                
                if (builder.isEmpty()) "Could not find any slide content in PowerPoint file."
                else builder.toString().trim()
            } ?: "Failed to open PowerPoint stream"
        } catch (e: Exception) {
            "Error parsing PowerPoint document: ${e.message}"
        }
    }

    private fun parsePptxXml(inputStream: java.io.InputStream): String {
        val builder = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "t") builder.append(parser.nextText()).append(" ")
                }
                XmlPullParser.END_TAG -> {
                    if (name == "p") builder.append("\n")
                }
            }
            eventType = parser.next()
        }
        return builder.toString().trim()
    }

    private fun parseDocxXml(inputStream: java.io.InputStream): String {
        val builder = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        val characterLimit = 8000
        while (eventType != XmlPullParser.END_DOCUMENT && builder.length < characterLimit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "t") builder.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> {
                    if (name == "p" || name == "br" || name == "cr") builder.append("\n")
                }
            }
            eventType = parser.next()
        }
        if (builder.length >= characterLimit) builder.append("\n\n[Note: Document truncated]")
        return builder.toString().trim()
    }

    private fun extractFromXlsx(uri: Uri): String {
        return try {
            val sharedStrings = mutableListOf<String>()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        sharedStrings.addAll(parseSharedStrings(zipInputStream))
                        zipInputStream.closeEntry()
                        break
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }

            val builder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                val limit = 8000
                while (entry != null && builder.length < limit) {
                    if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                        val sheetName = entry.name.removePrefix("xl/worksheets/").removeSuffix(".xml")
                        builder.append("\n[$sheetName]\n")
                        builder.append(parseXlsxSheet(zipInputStream, sharedStrings, limit - builder.length))
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
                if (builder.isEmpty()) "No spreadsheet data found." else builder.toString().trim()
            } ?: "Failed to open Excel stream"
        } catch (e: Exception) {
            "Error parsing Excel: ${e.message}"
        }
    }

    private fun parseSharedStrings(inputStream: java.io.InputStream): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "t") list.add(parser.nextText())
            eventType = parser.next()
        }
        return list
    }

    private fun parseXlsxSheet(inputStream: java.io.InputStream, sharedStrings: List<String>, limit: Int): String {
        val builder = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)
        var eventType = parser.eventType
        var isShared = false
        while (eventType != XmlPullParser.END_DOCUMENT && builder.length < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "c") isShared = parser.getAttributeValue(null, "t") == "s"
                    else if (name == "v") {
                        val value = parser.nextText()
                        if (isShared) {
                            val idx = value.toIntOrNull() ?: -1
                            if (idx in sharedStrings.indices) builder.append(sharedStrings[idx]).append("\t")
                        } else builder.append(value).append("\t")
                    }
                }
                XmlPullParser.END_TAG -> if (name == "row") builder.append("\n")
            }
            eventType = parser.next()
        }
        return builder.toString()
    }

    private fun extractFromPdf(uri: Uri): String {
        PDFBoxResourceLoader.init(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                try {
                    val stripper = PDFTextStripper().apply { startPage = 1; endPage = 10 }
                    val text = stripper.getText(document)
                    if (text.length > 8000) text.take(8000) + "\n\n[Note: Document truncated]" else text
                } finally { document.close() }
            } ?: "Failed to open PDF stream"
        } catch (e: IOException) { "Error parsing PDF: ${e.message}" }
    }

    private fun extractFromText(uri: Uri): String {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            // SAFETY: Do not attempt to read binary audio/image files as plain text
            if (mimeType.startsWith("audio") || mimeType.startsWith("image") || mimeType.contains("octet-stream")) {
                return ""
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: ""
        } catch (e: Exception) { "Error reading file: ${e.message}" }
    }
}
