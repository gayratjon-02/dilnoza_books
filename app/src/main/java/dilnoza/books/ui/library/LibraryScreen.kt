package dilnoza.books.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dilnoza.books.R
import dilnoza.books.domain.model.BookSummary
import dilnoza.books.ui.components.ConfirmDeleteDialog
import dilnoza.books.ui.components.EmptyState
import dilnoza.books.ui.components.TextFieldsDialog

@Composable
fun LibraryScreen(
    onOpenBook: (bookId: String) -> Unit,
    onOpenNewBook: (bookId: String, chapterId: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.OpenNewBook -> onOpenNewBook(event.bookId, event.chapterId)
            }
        }
    }

    LibraryContent(state = state, onAction = viewModel::onAction, onOpenBook = onOpenBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.books.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_new_book)) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.books.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                modifier = Modifier.padding(padding),
                action = {
                    Button(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.library_start_writing))
                    }
                },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.books, key = { it.book.id }) { summary ->
                    BookCard(
                        summary = summary,
                        onClick = { onOpenBook(summary.book.id) },
                        onEdit = { editingId = summary.book.id },
                        onDelete = { deletingId = summary.book.id },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    val firstChapterTitle = stringResource(R.string.chapter_default_title, 1)
    if (showCreate) {
        TextFieldsDialog(
            title = stringResource(R.string.book_create_title),
            confirmLabel = stringResource(R.string.action_create),
            primaryLabel = stringResource(R.string.book_field_title),
            initialPrimary = "",
            secondaryLabel = stringResource(R.string.book_field_author),
            onConfirm = { title, author ->
                showCreate = false
                onAction(LibraryAction.CreateBook(title, author, firstChapterTitle))
            },
            onDismiss = { showCreate = false },
        )
    }

    state.books.firstOrNull { it.book.id == editingId }?.book?.let { book ->
        TextFieldsDialog(
            title = stringResource(R.string.book_edit_title),
            confirmLabel = stringResource(R.string.action_save),
            primaryLabel = stringResource(R.string.book_field_title),
            initialPrimary = book.title,
            secondaryLabel = stringResource(R.string.book_field_author),
            initialSecondary = book.author,
            onConfirm = { title, author ->
                editingId = null
                onAction(LibraryAction.UpdateBook(book.id, title, author))
            },
            onDismiss = { editingId = null },
        )
    }

    state.books.firstOrNull { it.book.id == deletingId }?.book?.let { book ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.book_delete_title),
            body = stringResource(R.string.book_delete_body, book.title),
            onConfirm = {
                deletingId = null
                onAction(LibraryAction.DeleteBook(book.id))
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun BookCard(
    summary: BookSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = summary.book
    val context = LocalContext.current
    val edited = remember(book.updatedAt) {
        DateUtils.formatDateTime(
            context,
            book.updatedAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
        )
    }
    var menuOpen by remember { mutableStateOf(false) }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(title = book.title, color = book.coverColor)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.chapter_count, summary.chapterCount, summary.chapterCount) +
                        " · " + stringResource(R.string.library_edited, edited),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
