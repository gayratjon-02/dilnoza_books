package dilnoza.books.domain.model

data class Block(
    val id: String,
    val chapterId: String,
    val position: Int,
    val content: BlockContent,
)

sealed interface BlockContent {
    data class Heading(val level: Int, val text: RichText) : BlockContent
    data class Paragraph(val text: RichText, val align: TextAlignment = TextAlignment.START) : BlockContent
}

enum class TextAlignment { START, CENTER, END, JUSTIFY }

data class RichText(val text: String) {
    companion object {
        val Empty = RichText("")
    }
}

/** Gap between neighbouring positions so a block can be inserted without renumbering its siblings. */
const val POSITION_STEP = 1024
