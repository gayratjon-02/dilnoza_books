package dilnoza.books.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dilnoza.books.ui.book.BookScreen
import dilnoza.books.ui.editor.EditorScreen
import dilnoza.books.ui.library.LibraryScreen
import dilnoza.books.ui.navigation.BookRoute
import dilnoza.books.ui.navigation.EditorRoute
import dilnoza.books.ui.navigation.LibraryRoute

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenBook = { bookId -> navController.navigate(BookRoute(bookId)) },
                onOpenNewBook = { bookId, chapterId ->
                    // A new book opens straight into its first chapter; Back leads to the chapter list.
                    navController.navigate(BookRoute(bookId))
                    navController.navigate(EditorRoute(chapterId))
                },
            )
        }
        composable<BookRoute> {
            BookScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenChapter = { chapterId -> navController.navigate(EditorRoute(chapterId)) },
            )
        }
        composable<EditorRoute> {
            EditorScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}
