package dilnoza.books.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dilnoza.books.R
import dilnoza.books.domain.model.Book
import dilnoza.books.domain.model.Chapter
import dilnoza.books.domain.model.PageSize
import dilnoza.books.ui.components.ConfirmDeleteDialog
import dilnoza.books.ui.components.EmptyState
import dilnoza.books.ui.components.TextFieldsDialog
import dilnoza.books.ui.library.BookCover

@Composable
fun BookScreen(
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit,
    viewModel: BookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BookEvent.OpenChapter -> onOpenChapter(event.chapterId)
            }
        }
    }

    // The book was deleted elsewhere; nothing to show here any more.
    LaunchedEffect(state.loading, state.book) {
        if (!state.loading && state.book == null) onBack()
    }

    val book = state.book ?: return
    BookContent(
        book = book,
        chapters = state.chapters,
        onAction = viewModel::onAction,
        onBack = onBack,
        onOpenChapter = onOpenChapter,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookContent(
    book: Book,
    chapters: List<Chapter>,
    onAction: (BookAction) -> Unit,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    var editingBook by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val newChapterTitle = stringResource(R.string.chapter_default_title, chapters.size + 1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { editingBook = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.book_edit_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAction(BookAction.AddChapter(newChapterTitle)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.chapter_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item(key = "header") {
                BookHeader(book = book, onPageSize = { onAction(BookAction.SetPageSize(it)) })
            }
            if (chapters.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.Article,
                        title = stringResource(R.string.chapters_empty_title),
                        body = stringResource(R.string.chapters_empty_body),
                    )
                }
            }
            itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                ChapterRow(
                    number = index + 1,
                    chapter = chapter,
                    canMoveUp = index > 0,
                    canMoveDown = index < chapters.lastIndex,
                    onClick = { onOpenChapter(chapter.id) },
                    onRename = { renamingId = chapter.id },
                    onMove = { offset -> onAction(BookAction.MoveChapter(chapter.id, offset)) },
                    onDelete = { deletingId = chapter.id },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (editingBook) {
        TextFieldsDialog(
            title = stringResource(R.string.book_edit_title),
            confirmLabel = stringResource(R.string.action_save),
            primaryLabel = stringResource(R.string.book_field_title),
            initialPrimary = book.title,
            secondaryLabel = stringResource(R.string.book_field_author),
            initialSecondary = book.author,
            onConfirm = { title, author ->
                editingBook = false
                onAction(BookAction.UpdateBook(title, author))
            },
            onDismiss = { editingBook = false },
        )
    }

    chapters.firstOrNull { it.id == renamingId }?.let { chapter ->
        TextFieldsDialog(
            title = stringResource(R.string.chapter_rename_title),
            confirmLabel = stringResource(R.string.action_save),
            primaryLabel = stringResource(R.string.chapter_field_title),
            initialPrimary = chapter.title,
            onConfirm = { title, _ ->
                renamingId = null
                onAction(BookAction.RenameChapter(chapter.id, title))
            },
            onDismiss = { renamingId = null },
        )
    }

    chapters.firstOrNull { it.id == deletingId }?.let { chapter ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.chapter_delete_title),
            body = stringResource(R.string.chapter_delete_body, chapter.title),
            onConfirm = {
                deletingId = null
                onAction(BookAction.DeleteChapter(chapter.id))
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun BookHeader(book: Book, onPageSize: (PageSize) -> Unit) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(title = book.title, color = book.coverColor, width = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(book.title, style = MaterialTheme.typography.headlineSmall)
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                PageSizeSelector(selected = book.pageSize, onSelect = onPageSize)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ChapterRow(
    number: Int,
    chapter: Chapter,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        headlineContent = { Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    if (canMoveUp) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_up)) },
                            leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMove(-1)
                            },
                        )
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_down)) },
                            leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMove(1)
                            },
                        )
                    }
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
        },
    )
}

@Composable
private fun PageSizeSelector(selected: PageSize, onSelect: (PageSize) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(R.string.book_page_size, stringResource(selected.label()))) },
            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PageSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(stringResource(size.label())) },
                    leadingIcon = {
                        if (size == selected) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        onSelect(size)
                    },
                )
            }
        }
    }
}

private fun PageSize.label() = when (this) {
    PageSize.A5 -> R.string.page_size_a5
    PageSize.A4 -> R.string.page_size_a4
    PageSize.LETTER -> R.string.page_size_letter
}
