package dilnoza.books.editor.commands

/** An open slash command: "/" sits at [slashIndex] and [filter] is the text typed after it up to the cursor. */
data class SlashQuery(val slashIndex: Int, val filter: String)

object SlashCommands {

    /**
     * Returns the active query when a "/" at a word start (index 0 or right after whitespace)
     * is followed only by non-whitespace up to [cursor]. A "/" inside a word, such as "and/or", is ignored.
     */
    fun query(text: String, cursor: Int): SlashQuery? {
        if (cursor < 1 || cursor > text.length) return null
        var i = cursor - 1
        while (i >= 0) {
            val c = text[i]
            if (c.isWhitespace()) return null
            if (c == '/') break
            i--
        }
        if (i < 0) return null
        if (i > 0 && !text[i - 1].isWhitespace()) return null
        return SlashQuery(slashIndex = i, filter = text.substring(i + 1, cursor))
    }

    /**
     * Keeps commands whose keywords contain [filter] (case-insensitive). Commands with a keyword starting
     * with [filter] come first; original order is kept within each group. A blank filter keeps everything.
     */
    fun <T> filter(commands: List<T>, filter: String, keywords: (T) -> List<String>): List<T> {
        val needle = filter.trim().lowercase()
        if (needle.isEmpty()) return commands
        val prefix = mutableListOf<T>()
        val substring = mutableListOf<T>()
        for (command in commands) {
            val words = keywords(command).map { it.lowercase() }
            when {
                words.any { it.startsWith(needle) } -> prefix += command
                words.any { it.contains(needle) } -> substring += command
            }
        }
        return prefix + substring
    }
}
