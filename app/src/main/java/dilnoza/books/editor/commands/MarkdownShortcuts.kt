package dilnoza.books.editor.commands

enum class ShortcutTarget { HEADING_1, HEADING_2, HEADING_3, BULLET_LIST, NUMBERED_LIST, QUOTE, DIVIDER }

/** [markerLength] chars at the start of the text are the marker and should be removed on conversion. */
data class ShortcutMatch(val target: ShortcutTarget, val markerLength: Int)

object MarkdownShortcuts {

    // Keeps the number parseable as Int by callers that want to start the list there.
    private const val MAX_NUMBER_DIGITS = 9

    private val spaceMarkers = mapOf(
        "#" to ShortcutTarget.HEADING_1,
        "##" to ShortcutTarget.HEADING_2,
        "###" to ShortcutTarget.HEADING_3,
        "-" to ShortcutTarget.BULLET_LIST,
        "*" to ShortcutTarget.BULLET_LIST,
        ">" to ShortcutTarget.QUOTE,
    )

    /**
     * Returns a match only when the change from [oldText] to [newText] is a single typed character
     * ending at [cursor] that completes a marker at index 0. Pastes, deletions and replacements never match,
     * so a marker that merely appears in pasted text does not convert the block.
     */
    fun detect(oldText: String, newText: String, cursor: Int): ShortcutMatch? {
        if (!isSingleCharInsertion(oldText, newText, cursor)) return null
        val typed = newText[cursor - 1]
        val marker = newText.substring(0, cursor - 1)

        if (typed == '-' && newText == "---") return ShortcutMatch(ShortcutTarget.DIVIDER, 3)
        if (typed != ' ') return null

        spaceMarkers[marker]?.let { return ShortcutMatch(it, cursor) }
        if (isNumberMarker(marker)) return ShortcutMatch(ShortcutTarget.NUMBERED_LIST, cursor)
        return null
    }

    private fun isSingleCharInsertion(oldText: String, newText: String, cursor: Int): Boolean {
        if (newText.length != oldText.length + 1) return false
        if (cursor < 1 || cursor > newText.length) return false
        return newText.regionMatches(0, oldText, 0, cursor - 1) &&
            newText.regionMatches(cursor, oldText, cursor - 1, oldText.length - (cursor - 1))
    }

    private fun isNumberMarker(marker: String): Boolean {
        if (marker.length < 2 || !marker.endsWith('.')) return false
        val digits = marker.substring(0, marker.length - 1)
        return digits.length <= MAX_NUMBER_DIGITS && digits.all { it in '0'..'9' }
    }
}
