package dilnoza.books.data.mapper

import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.richtext.normalized
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Converts block content to the JSON stored in `blocks.content_json`.
 * DTOs carry their own version field so the stored format can evolve independently of domain models.
 */
object BlockContentCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "kind"
    }

    fun typeOf(content: BlockContent): String = when (content) {
        is BlockContent.Heading -> TYPE_HEADING
        is BlockContent.Paragraph -> TYPE_PARAGRAPH
        is BlockContent.ListItem -> TYPE_LIST_ITEM
        is BlockContent.Quote -> TYPE_QUOTE
        BlockContent.Divider -> TYPE_DIVIDER
        BlockContent.PageBreak -> TYPE_PAGE_BREAK
        is BlockContent.Table -> TYPE_TABLE
        is BlockContent.Image -> TYPE_IMAGE
    }

    fun encode(content: BlockContent): String = json.encodeToString(BlockContentDto.serializer(), content.toDto())

    fun decode(raw: String): BlockContent = json.decodeFromString(BlockContentDto.serializer(), raw).toDomain()

    private fun BlockContent.toDto(): BlockContentDto = when (this) {
        is BlockContent.Heading -> BlockContentDto.Heading(level = level, text = text.toDto(), align = align.name)
        is BlockContent.Paragraph -> BlockContentDto.Paragraph(text = text.toDto(), align = align.name)
        is BlockContent.ListItem -> BlockContentDto.ListItem(
            ordered = ordered,
            indent = indent,
            text = text.toDto(),
            align = align.name,
        )
        is BlockContent.Quote -> BlockContentDto.Quote(text = text.toDto(), align = align.name)
        BlockContent.Divider -> BlockContentDto.Divider()
        BlockContent.PageBreak -> BlockContentDto.PageBreak()
        is BlockContent.Table -> BlockContentDto.Table(
            rows = rows.map { row -> row.map { it.toDto() } },
            headerRow = headerRow,
        )
        is BlockContent.Image -> BlockContentDto.Image(
            file = fileName,
            width = width,
            height = height,
            caption = caption,
            widthPercent = widthPercent,
            align = align.name,
        )
    }

    private fun BlockContentDto.toDomain(): BlockContent = when (this) {
        is BlockContentDto.Heading -> BlockContent.Heading(
            level = level.coerceIn(BlockContent.Heading.MIN_LEVEL, BlockContent.Heading.MAX_LEVEL),
            text = text.toDomain(),
            align = alignmentOf(align, TextAlignment.START),
        )
        is BlockContentDto.Paragraph -> BlockContent.Paragraph(
            text = text.toDomain(),
            align = alignmentOf(align, TextAlignment.START),
        )
        is BlockContentDto.ListItem -> BlockContent.ListItem(
            ordered = ordered,
            indent = indent.coerceIn(0, BlockContent.ListItem.MAX_INDENT),
            text = text.toDomain(),
            align = alignmentOf(align, TextAlignment.START),
        )
        is BlockContentDto.Quote -> BlockContent.Quote(
            text = text.toDomain(),
            align = alignmentOf(align, TextAlignment.START),
        )
        is BlockContentDto.Divider -> BlockContent.Divider
        is BlockContentDto.PageBreak -> BlockContent.PageBreak
        is BlockContentDto.Table -> BlockContent.Table(rows = rectangular(rows), headerRow = headerRow)
        is BlockContentDto.Image -> BlockContent.Image(
            fileName = file,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            caption = caption,
            widthPercent = widthPercent.coerceIn(
                BlockContent.Image.MIN_WIDTH_PERCENT,
                BlockContent.Image.MAX_WIDTH_PERCENT,
            ),
            align = alignmentOf(align, TextAlignment.CENTER),
        )
    }

    private fun RichText.toDto() = RichTextDto(
        text = text,
        spans = normalized().spans.map { span ->
            val style = span.style
            SpanDto(
                start = span.start,
                end = span.end,
                bold = style.bold.takeIf { it },
                italic = style.italic.takeIf { it },
                underline = style.underline.takeIf { it },
                strikethrough = style.strikethrough.takeIf { it },
                color = style.color,
                background = style.background,
                size = style.fontSizeSp,
                font = style.fontFamily,
            )
        },
    )

    private fun RichTextDto.toDomain() = RichText(
        text = text,
        spans = spans.map { span ->
            StyleSpan(
                start = span.start,
                end = span.end,
                style = SpanStyleData(
                    bold = span.bold == true,
                    italic = span.italic == true,
                    underline = span.underline == true,
                    strikethrough = span.strikethrough == true,
                    color = span.color,
                    background = span.background,
                    fontSizeSp = span.size?.takeIf { it in MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP },
                    fontFamily = span.font?.takeIf { it.isNotBlank() },
                ),
            )
        },
    ).normalized()

    /** Pads short rows with empty cells; a table without cells becomes a single empty cell. */
    private fun rectangular(rows: List<List<RichTextDto>>): List<List<RichText>> {
        val width = rows.maxOfOrNull { it.size } ?: 0
        if (width == 0) return listOf(listOf(RichText.Empty))
        return rows.map { row -> List(width) { col -> row.getOrNull(col)?.toDomain() ?: RichText.Empty } }
    }

    private fun alignmentOf(name: String, fallback: TextAlignment) =
        TextAlignment.entries.firstOrNull { it.name == name } ?: fallback

    private const val MIN_FONT_SIZE_SP = 6
    private const val MAX_FONT_SIZE_SP = 200

    const val TYPE_HEADING = "heading"
    const val TYPE_PARAGRAPH = "paragraph"
    const val TYPE_LIST_ITEM = "list_item"
    const val TYPE_QUOTE = "quote"
    const val TYPE_DIVIDER = "divider"
    const val TYPE_PAGE_BREAK = "page_break"
    const val TYPE_TABLE = "table"
    const val TYPE_IMAGE = "image"
}

@Serializable
private sealed interface BlockContentDto {

    @Serializable
    @SerialName(BlockContentCodec.TYPE_HEADING)
    data class Heading(
        val v: Int = 2,
        val level: Int,
        val text: RichTextDto,
        val align: String = "START",
    ) : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_PARAGRAPH)
    data class Paragraph(val v: Int = 2, val text: RichTextDto, val align: String = "START") : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_LIST_ITEM)
    data class ListItem(
        val v: Int = 1,
        val ordered: Boolean = false,
        val indent: Int = 0,
        val text: RichTextDto,
        val align: String = "START",
    ) : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_QUOTE)
    data class Quote(val v: Int = 1, val text: RichTextDto, val align: String = "START") : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_DIVIDER)
    data class Divider(val v: Int = 1) : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_PAGE_BREAK)
    data class PageBreak(val v: Int = 1) : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_TABLE)
    data class Table(
        val v: Int = 1,
        val rows: List<List<RichTextDto>> = emptyList(),
        val headerRow: Boolean = false,
    ) : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_IMAGE)
    data class Image(
        val v: Int = 1,
        val file: String,
        val width: Int,
        val height: Int,
        val caption: String = "",
        val widthPercent: Int = 100,
        val align: String = "CENTER",
    ) : BlockContentDto
}

/** v1 stored text without [spans]; the empty default keeps that JSON readable. */
@Serializable
private data class RichTextDto(val text: String, val spans: List<SpanDto> = emptyList())

/** Compact names keep long styled chapters small; absent fields mean "not set". */
@Serializable
private data class SpanDto(
    @SerialName("s") val start: Int,
    @SerialName("e") val end: Int,
    @SerialName("b") val bold: Boolean? = null,
    @SerialName("i") val italic: Boolean? = null,
    @SerialName("u") val underline: Boolean? = null,
    @SerialName("st") val strikethrough: Boolean? = null,
    @SerialName("c") val color: Int? = null,
    @SerialName("bg") val background: Int? = null,
    @SerialName("sz") val size: Int? = null,
    @SerialName("f") val font: String? = null,
)
