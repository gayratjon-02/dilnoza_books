package dilnoza.books.data.mapper

import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.TextAlignment
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
        classDiscriminator = "kind"
    }

    fun typeOf(content: BlockContent): String = when (content) {
        is BlockContent.Heading -> TYPE_HEADING
        is BlockContent.Paragraph -> TYPE_PARAGRAPH
    }

    fun encode(content: BlockContent): String = json.encodeToString(BlockContentDto.serializer(), content.toDto())

    fun decode(raw: String): BlockContent = json.decodeFromString(BlockContentDto.serializer(), raw).toDomain()

    private fun BlockContent.toDto(): BlockContentDto = when (this) {
        is BlockContent.Heading -> BlockContentDto.Heading(level = level, text = RichTextDto(text.text))
        is BlockContent.Paragraph -> BlockContentDto.Paragraph(text = RichTextDto(text.text), align = align.name)
    }

    private fun BlockContentDto.toDomain(): BlockContent = when (this) {
        is BlockContentDto.Heading -> BlockContent.Heading(level = level.coerceIn(1, 3), text = RichText(text.text))
        is BlockContentDto.Paragraph -> BlockContent.Paragraph(
            text = RichText(text.text),
            align = TextAlignment.entries.firstOrNull { it.name == align } ?: TextAlignment.START,
        )
    }

    const val TYPE_HEADING = "heading"
    const val TYPE_PARAGRAPH = "paragraph"
}

@Serializable
private sealed interface BlockContentDto {

    @Serializable
    @SerialName(BlockContentCodec.TYPE_HEADING)
    data class Heading(val v: Int = 1, val level: Int, val text: RichTextDto) : BlockContentDto

    @Serializable
    @SerialName(BlockContentCodec.TYPE_PARAGRAPH)
    data class Paragraph(val v: Int = 1, val text: RichTextDto, val align: String = "START") : BlockContentDto
}

@Serializable
private data class RichTextDto(val text: String)
