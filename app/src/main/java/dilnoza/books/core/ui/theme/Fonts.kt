package dilnoza.books.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dilnoza.books.R

/**
 * Bundled book font. Page content must never use system fonts: they differ between devices,
 * which would move line and page breaks. Covers Latin (incl. oʻ gʻ), Cyrillic and Uzbek Cyrillic.
 */
val BookSerif = FontFamily(
    Font(R.font.noto_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.noto_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.noto_serif_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.noto_serif_bold_italic, FontWeight.Bold, FontStyle.Italic),
)
