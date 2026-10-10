package dilnoza.books.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

private val Base = Typography()

// Serif headings give the app a book-like feel; body text keeps the default sans for UI readability.
internal val DilnozaTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = Base.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = Base.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = Base.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = Base.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = Base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = Base.titleLarge.copy(fontFamily = FontFamily.Serif),
)
