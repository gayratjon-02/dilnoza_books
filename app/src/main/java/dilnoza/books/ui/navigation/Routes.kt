package dilnoza.books.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class BookRoute(val bookId: String)

@Serializable
data class EditorRoute(val chapterId: String)
