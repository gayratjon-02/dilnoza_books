package dilnoza.books.ui.editor.page

import dilnoza.books.domain.model.BlockContent
import dilnoza.books.editor.layout.FlowItem
import dilnoza.books.editor.layout.PageMeasurer
import dilnoza.books.editor.layout.PageSpec

/**
 * Measured blocks of the open chapter for one page size. It lives in the view model, so it survives
 * rotation, and [prime] fills it off the main thread before a chapter or a new page size is shown:
 * measuring a long chapter during composition would block the first frame for a long time.
 * Afterwards only blocks whose content changed are measured again, on the main thread.
 */
class BlockLayoutCache(private val newMeasurer: (PageSpec) -> PageMeasurer) {
    private class Entry(val spec: PageSpec, val measurer: PageMeasurer, val items: HashMap<String, Pair<Any, FlowItem>>)

    @Volatile
    private var entry: Entry? = null

    /** Measures [title] and [blocks] (id to layout content) for [spec]; safe to call on any thread. */
    fun prime(spec: PageSpec, title: String, blocks: List<Pair<String, BlockContent>>) {
        val measurer = newMeasurer(spec)
        val items = HashMap<String, Pair<Any, FlowItem>>(blocks.size * 2)
        items[PageMeasurer.TITLE_ID] = title to measurer.measureTitle(title)
        for ((id, content) in blocks) items[id] = content to measurer.measure(id, content)
        entry = Entry(spec, measurer, items)
    }

    /** The cached item of [id] if it was measured from [key], else [measure]s it with the [spec] measurer. */
    fun item(spec: PageSpec, id: String, key: Any, measure: PageMeasurer.() -> FlowItem): FlowItem {
        val current = entryFor(spec)
        current.items[id]?.let { (cachedKey, item) -> if (cachedKey == key) return item }
        return current.measurer.measure().also { current.items[id] = key to it }
    }

    /** Drops blocks that are gone. */
    fun retain(spec: PageSpec, ids: Set<String>) {
        entryFor(spec).items.keys.retainAll(ids)
    }

    private fun entryFor(spec: PageSpec): Entry {
        entry?.let { if (it.spec == spec) return it }
        return Entry(spec, newMeasurer(spec), HashMap()).also { entry = it }
    }
}
