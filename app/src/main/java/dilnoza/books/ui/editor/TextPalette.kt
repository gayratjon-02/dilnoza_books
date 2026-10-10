package dilnoza.books.ui.editor

import androidx.annotation.StringRes
import dilnoza.books.R

/**
 * A colour the writer can give to text. The book stores [light]; [dark] is how the same choice is
 * drawn on a dark page, so coloured text stays readable after switching themes.
 */
data class PaletteColor(val light: Int, val dark: Int, @param:StringRes val label: Int)

/** Text and highlight colours are part of the book's content, so they are fixed values, not theme roles. */
object TextPalette {
    val text = listOf(
        PaletteColor(0xFFC62828.toInt(), 0xFFEF9A9A.toInt(), R.string.color_red),
        PaletteColor(0xFFD84315.toInt(), 0xFFFFAB91.toInt(), R.string.color_orange),
        PaletteColor(0xFF9E7700.toInt(), 0xFFFFD54F.toInt(), R.string.color_gold),
        PaletteColor(0xFF2E7D32.toInt(), 0xFFA5D6A7.toInt(), R.string.color_green),
        PaletteColor(0xFF00796B.toInt(), 0xFF80CBC4.toInt(), R.string.color_teal),
        PaletteColor(0xFF1565C0.toInt(), 0xFF90CAF9.toInt(), R.string.color_blue),
        PaletteColor(0xFF3949AB.toInt(), 0xFF9FA8DA.toInt(), R.string.color_indigo),
        PaletteColor(0xFF7B1FA2.toInt(), 0xFFCE93D8.toInt(), R.string.color_purple),
        PaletteColor(0xFF6D4C41.toInt(), 0xFFBCAAA4.toInt(), R.string.color_brown),
        PaletteColor(0xFF616161.toInt(), 0xFFBDBDBD.toInt(), R.string.color_gray),
    )

    val highlight = listOf(
        PaletteColor(0xFFFFF59D.toInt(), 0xFF5C5200.toInt(), R.string.color_yellow),
        PaletteColor(0xFFFFE0B2.toInt(), 0xFF5D3A00.toInt(), R.string.color_orange),
        PaletteColor(0xFFF8BBD0.toInt(), 0xFF5E1F38.toInt(), R.string.color_pink),
        PaletteColor(0xFFC8E6C9.toInt(), 0xFF1E4620.toInt(), R.string.color_green),
        PaletteColor(0xFFBBDEFB.toInt(), 0xFF0D3C61.toInt(), R.string.color_blue),
        PaletteColor(0xFFE1BEE7.toInt(), 0xFF4A2357.toInt(), R.string.color_purple),
        PaletteColor(0xFFE0E0E0.toInt(), 0xFF424242.toInt(), R.string.color_gray),
    )

    private val darkByLight = (text + highlight).associate { it.light to it.dark }

    /** How a stored colour is drawn; colours from outside the palette are drawn as stored. */
    fun resolve(argb: Int, dark: Boolean): Int = if (dark) darkByLight[argb] ?: argb else argb
}

/** Text sizes offered in the toolbar; null keeps the block's own size. */
enum class TextSize(val sp: Int?, @param:StringRes val label: Int) {
    SMALL(14, R.string.format_size_small),
    NORMAL(null, R.string.format_size_normal),
    LARGE(22, R.string.format_size_large),
    EXTRA_LARGE(28, R.string.format_size_extra_large),
}
