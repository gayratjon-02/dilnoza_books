package dilnoza.books.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Placeholder cover: the book's colour with its first letter, until custom covers arrive. */
@Composable
fun BookCover(
    title: String,
    color: Int,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
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
    }
}
