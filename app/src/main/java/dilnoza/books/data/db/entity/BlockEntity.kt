package dilnoza.books.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapter_id", "position")],
)
data class BlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    val position: Int,
    val type: String,
    @ColumnInfo(name = "content_json") val contentJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
