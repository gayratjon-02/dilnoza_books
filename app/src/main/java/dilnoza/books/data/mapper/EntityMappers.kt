package dilnoza.books.data.mapper

import dilnoza.books.data.db.entity.BlockEntity
import dilnoza.books.data.db.entity.BookEntity
import dilnoza.books.data.db.entity.BookSummaryRow
import dilnoza.books.data.db.entity.ChapterEntity
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.BookSummary
import dilnoza.books.domain.model.Chapter
import dilnoza.books.domain.model.PageSize

fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    description = description,
    coverPath = coverPath,
    coverColor = coverColor,
    pageSize = PageSize.entries.firstOrNull { it.name == pageSize } ?: PageSize.A5,
    marginMm = marginMm,
    fontFamily = fontFamily,
    headerText = headerText,
    footerText = footerText,
    showPageNumbers = showPageNumbers,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Book.toEntity() = BookEntity(
    id = id,
    title = title,
    author = author,
    description = description,
    coverPath = coverPath,
    coverColor = coverColor,
    pageSize = pageSize.name,
    marginMm = marginMm,
    fontFamily = fontFamily,
    headerText = headerText,
    footerText = footerText,
    showPageNumbers = showPageNumbers,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BookSummaryRow.toDomain() = BookSummary(book = book.toDomain(), chapterCount = chapterCount)

fun ChapterEntity.toDomain() = Chapter(
    id = id,
    bookId = bookId,
    position = position,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BlockEntity.toDomain() = Block(
    id = id,
    chapterId = chapterId,
    position = position,
    content = BlockContentCodec.decode(contentJson),
)

fun Block.toEntity(updatedAt: Long) = BlockEntity(
    id = id,
    chapterId = chapterId,
    position = position,
    type = BlockContentCodec.typeOf(content),
    contentJson = BlockContentCodec.encode(content),
    updatedAt = updatedAt,
)
