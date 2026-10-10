package dilnoza.books.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "cover_color") val coverColor: Int,
    @ColumnInfo(name = "page_size") val pageSize: String,
    @ColumnInfo(name = "margin_mm") val marginMm: Int,
    @ColumnInfo(name = "font_family") val fontFamily: String,
    @ColumnInfo(name = "header_text") val headerText: String?,
    @ColumnInfo(name = "footer_text") val footerText: String?,
    @ColumnInfo(name = "show_page_numbers") val showPageNumbers: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class BookSummaryRow(
    @Embedded val book: BookEntity,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
)
