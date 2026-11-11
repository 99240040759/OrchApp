package com.example.data.parser

sealed interface DocxBlock {
    data class Heading(val text: String, val level: Int) : DocxBlock
    data class Paragraph(val text: String) : DocxBlock
    data class BulletItem(val text: String) : DocxBlock
    data class TableGrid(val rows: List<List<String>>) : DocxBlock
}

data class DocxDocument(
    val title: String,
    val blocks: List<DocxBlock>
)

data class XlsxSheet(
    val name: String,
    val rows: List<List<String>>
)

data class XlsxWorkbook(
    val sheets: List<XlsxSheet>
)

data class PptxSlide(
    val slideNumber: Int,
    val title: String,
    val bodyBullets: List<String>,
    val speakerNotes: String = ""
)

data class PptxDeck(
    val slides: List<PptxSlide>
)
