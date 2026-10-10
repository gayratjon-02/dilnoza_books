package dilnoza.books.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dilnoza.books.data.db.dao.BlockDao
import dilnoza.books.data.db.dao.BookDao
import dilnoza.books.data.db.dao.ChapterDao
import dilnoza.books.data.db.entity.BlockEntity
import dilnoza.books.data.db.entity.BookEntity
import dilnoza.books.data.db.entity.ChapterEntity

@Database(
    entities = [BookEntity::class, ChapterEntity::class, BlockEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun blockDao(): BlockDao

    companion object {
        const val NAME = "dilnoza_books.db"
    }
}
