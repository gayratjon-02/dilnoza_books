package dilnoza.books.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {

    @Test
    fun `small images are not subsampled or scaled`() {
        assertEquals(1, ImageSizing.sampleSize(800, 600))
        assertEquals(800 to 600, ImageSizing.fit(800, 600))
    }

    @Test
    fun `image exactly at the limit is kept`() {
        assertEquals(1, ImageSizing.sampleSize(2048, 1000))
        assertEquals(2048 to 1000, ImageSizing.fit(2048, 1000))
    }

    @Test
    fun `subsampling never drops below the limit`() {
        assertEquals(1, ImageSizing.sampleSize(4000, 3000))
        assertEquals(2, ImageSizing.sampleSize(4096, 3072))
        assertEquals(4, ImageSizing.sampleSize(8192, 6144))
        assertEquals(2, ImageSizing.sampleSize(3000, 8000))
    }

    @Test
    fun `fit keeps aspect ratio for landscape and portrait`() {
        assertEquals(2048 to 1536, ImageSizing.fit(4000, 3000))
        assertEquals(922 to 2048, ImageSizing.fit(1080, 2400))
    }

    @Test
    fun `extreme panoramas keep at least one pixel`() {
        assertEquals(2048 to 1, ImageSizing.fit(100_000, 10))
    }
}
