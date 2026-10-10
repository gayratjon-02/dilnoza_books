package dilnoza.books.data.files

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure size maths for image import, kept free of Android types so it can be unit tested. */
object ImageSizing {

    /** Longest edge of a stored image; big enough for print-quality A5 pages, small enough for the book file. */
    const val MAX_EDGE = 2048

    /** Largest power-of-two subsampling that still keeps the longest edge at or above [maxEdge]. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        val longest = max(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** Scales [width]x[height] down (never up) so the longest edge fits in [maxEdge], keeping the aspect ratio. */
    fun fit(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
