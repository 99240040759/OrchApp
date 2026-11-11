package com.example.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object OfficeAndArtifactParser {

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var read: Int
                    while (zis.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                    }
                    entries[entry.name] = out.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    fun parseDocx(fileName: String, bytes: ByteArray): DocxDocument {
        val entries = unzipEntries(bytes)
        val docXml = entries["word/document.xml"]?.toString(Charsets.UTF_8)
            ?: return DocxDocument(
                title = fileName,
                blocks = listOf(DocxBlock.Paragraph("Unable to locate word/document.xml in DOCX archive."))
            )

        val blocks = mutableListOf<DocxBlock>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(docXml))

        var inParagraph = false
        var inTable = false
        var paragraphStyle: String? = null
        var isBullet = false
        val paragraphBuilder = StringBuilder()

        var currentTableRows = mutableListOf<List<String>>()
        var currentRowCells = mutableListOf<String>()
        var inTableCell = false
        val cellBuilder = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "tbl" -> {
                            inTable = true
                            currentTableRows = mutableListOf()
                        }
                        "tr" -> if (inTable) {
                            currentRowCells = mutableListOf()
                        }
                        "tc" -> if (inTable) {
                            inTableCell = true
                            cellBuilder.clear()
                        }
                        "p" -> if (!inTable) {
                            inParagraph = true
                            paragraphStyle = null
                            isBullet = false
                            paragraphBuilder.clear()
                        }
                        "pStyle" -> if (inParagraph) {
                            paragraphStyle = parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val")
                        }
                        "numPr" -> if (inParagraph) {
                            isBullet = true
                        }
                        "t" -> {
                            val text = parser.nextText()
                            if (inTable && inTableCell) {
                                if (cellBuilder.isNotEmpty() && !cellBuilder.endsWith(" ")) {
                                    cellBuilder.append(" ")
                                }
                                cellBuilder.append(text)
                            } else if (inParagraph) {
                                paragraphBuilder.append(text)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "tc" -> if (inTable && inTableCell) {
                            currentRowCells.add(cellBuilder.toString().trim())
                            inTableCell = false
                        }
                        "tr" -> if (inTable && currentRowCells.isNotEmpty()) {
                            currentTableRows.add(currentRowCells.toList())
                        }
                        "tbl" -> if (inTable) {
                            if (currentTableRows.isNotEmpty()) {
                                blocks.add(DocxBlock.TableGrid(currentTableRows.toList()))
                            }
                            inTable = false
                        }
                        "p" -> if (inParagraph && !inTable) {
                            val text = paragraphBuilder.toString().trim()
                            if (text.isNotEmpty()) {
                                val styleLower = paragraphStyle?.lowercase().orEmpty()
                                when {
                                    styleLower.contains("heading1") || styleLower == "title" ->
                                        blocks.add(DocxBlock.Heading(text, 1))
                                    styleLower.contains("heading2") || styleLower == "subtitle" ->
                                        blocks.add(DocxBlock.Heading(text, 2))
                                    styleLower.contains("heading") ->
                                        blocks.add(DocxBlock.Heading(text, 3))
                                    isBullet ->
                                        blocks.add(DocxBlock.BulletItem(text))
                                    else ->
                                        blocks.add(DocxBlock.Paragraph(text))
                                }
                            }
                            inParagraph = false
                        }
                    }
                }
            }
            event = parser.next()
        }

        return DocxDocument(
            title = fileName,
            blocks = if (blocks.isEmpty()) {
                listOf(DocxBlock.Paragraph("(Empty Word Document)"))
            } else {
                blocks
            }
        )
    }

    fun parseXlsxOrCsv(fileName: String, bytes: ByteArray): XlsxWorkbook {
        if (fileName.endsWith(".csv", ignoreCase = true)) {
            val text = bytes.toString(Charsets.UTF_8)
            val rows = text.lines()
                .filter { it.isNotBlank() }
                .map { line -> line.split(",").map { it.trim().removeSurrounding("\"") } }
            return XlsxWorkbook(sheets = listOf(XlsxSheet(name = "CSV Data", rows = rows)))
        }

        val entries = unzipEntries(bytes)
        val sharedStrings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let { ssXml ->
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(ssXml))
            var inSi = false
            val sb = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.substringAfterLast(':') ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "si") {
                            inSi = true
                            sb.clear()
                        } else if (tag == "t" && inSi) {
                            sb.append(parser.nextText())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "si" && inSi) {
                            sharedStrings.add(sb.toString())
                            inSi = false
                        }
                    }
                }
                event = parser.next()
            }
        }

        val sheetNames = mutableListOf<String>()
        entries["xl/workbook.xml"]?.toString(Charsets.UTF_8)?.let { wbXml ->
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(wbXml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.substringAfterLast(':') ?: ""
                if (event == XmlPullParser.START_TAG && tag == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                    sheetNames.add(name)
                }
                event = parser.next()
            }
        }

        val sheetEntries = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sorted()

        val sheets = sheetEntries.mapIndexed { idx, entryPath ->
            val sheetXml = entries[entryPath]?.toString(Charsets.UTF_8).orEmpty()
            val rows = parseWorksheetXml(sheetXml, sharedStrings)
            val sheetTitle = sheetNames.getOrNull(idx) ?: "Sheet ${idx + 1}"
            XlsxSheet(name = sheetTitle, rows = rows)
        }

        return XlsxWorkbook(
            sheets = if (sheets.isEmpty()) {
                listOf(XlsxSheet("Sheet 1", listOf(listOf("No worksheet rows found"))))
            } else {
                sheets
            }
        )
    }

    private fun parseWorksheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var currentRow = mutableListOf<String>()
        var cellType: String? = null
        var cellValue = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t")
                            cellValue = ""
                        }
                        "v", "t" -> {
                            val raw = parser.nextText()
                            cellValue = if (cellType == "s") {
                                val index = raw.trim().toIntOrNull()
                                if (index != null && index in sharedStrings.indices) {
                                    sharedStrings[index]
                                } else {
                                    raw
                                }
                            } else {
                                raw
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "c" -> currentRow.add(cellValue)
                        "row" -> if (currentRow.any { it.isNotBlank() }) {
                            rows.add(currentRow.toList())
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    fun parsePptx(bytes: ByteArray): PptxDeck {
        val entries = unzipEntries(bytes)
        val slidePaths = entries.keys
            .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { path ->
                Regex("slide(\\d+)\\.xml").find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            }

        val slides = slidePaths.mapIndexed { index, path ->
            val slideNum = index + 1
            val xml = entries[path]?.toString(Charsets.UTF_8).orEmpty()
            val paragraphs = extractPptxParagraphs(xml)
            val title = paragraphs.firstOrNull() ?: "Slide $slideNum"
            val bullets = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()

            val notesPath = "ppt/notesSlides/notesSlide$slideNum.xml"
            val notes = entries[notesPath]?.toString(Charsets.UTF_8)?.let { notesXml ->
                extractPptxParagraphs(notesXml)
                    .filterNot { it == slideNum.toString() }
                    .joinToString("\n")
            }.orEmpty()

            PptxSlide(
                slideNumber = slideNum,
                title = title,
                bodyBullets = bullets,
                speakerNotes = notes
            )
        }

        return PptxDeck(
            slides = if (slides.isEmpty()) {
                listOf(PptxSlide(1, "Presentation Preview", listOf("No slide XML entries found.")))
            } else {
                slides
            }
        )
    }

    private fun extractPptxParagraphs(xml: String): List<String> {
        val paragraphs = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val currentParagraph = StringBuilder()
        var inParagraph = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (tag == "p") {
                        inParagraph = true
                        currentParagraph.clear()
                    } else if (tag == "t" && inParagraph) {
                        currentParagraph.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "p" && inParagraph) {
                        val text = currentParagraph.toString().trim()
                        if (text.isNotEmpty()) {
                            paragraphs.add(text)
                        }
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }
}
