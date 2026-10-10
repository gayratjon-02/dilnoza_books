package dilnoza.books.core.di

import javax.inject.Qualifier

/** Scope that outlives screens; used for writes that must finish even after a ViewModel is cleared. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
