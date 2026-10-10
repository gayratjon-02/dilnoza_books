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

    /** [fileName] is relative to the book's image folder; [width]/[height] are the stored pixel size. */
    data class Image(
        val fileName: String,
        val width: Int,
        val height: Int,
        val caption: String = "",
        val widthPercent: Int = 100,
        val align: TextAlignment = TextAlignment.CENTER,
    ) : BlockContent {
        companion object {
            const val MIN_WIDTH_PERCENT = 20
            const val MAX_WIDTH_PERCENT = 100
        }
    }
}

enum class TextAlignment { START, CENTER, END, JUSTIFY }

/** Gap between neighbouring positions so a block can be inserted without renumbering its siblings. */
const val POSITION_STEP = 1024
