package dilnoza.books.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

/** The book's cover picture, or its colour with the first letter when it has none. */
@Composable
fun BookCover(
    title: String,
    color: Int,
    modifier: Modifier = Modifier,
    file: File? = null,
    width: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(width = width, height = width * 1.4f)
            .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp))
            .background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().take(1).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        // Drawn over the letter, which stays visible while the picture loads or if the file is gone.
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        content()
    }
}
