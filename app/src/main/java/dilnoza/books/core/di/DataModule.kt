package dilnoza.books.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dilnoza.books.data.db.AppDatabase
import dilnoza.books.data.repository.BlockRepositoryImpl
import dilnoza.books.data.repository.BookRepositoryImpl
import dilnoza.books.data.repository.ChapterRepositoryImpl
import dilnoza.books.domain.repository.BlockRepository
import dilnoza.books.domain.repository.BookRepository
import dilnoza.books.domain.repository.ChapterRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Chapters and blocks rely on ON DELETE CASCADE.
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                },
            )
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    abstract fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository

    @Binds
    abstract fun bindBlockRepository(impl: BlockRepositoryImpl): BlockRepository
}
