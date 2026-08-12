package pro.kisscat.www.bookmarkhelper.sync.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark

class BrowserDataMapperTest {
    @Test
    fun legacyBookmarkRoundTripPreservesPortableFields() {
        val original = Bookmark().apply {
            title = "Example"
            url = " https://example.com/path "
            folder = "Work/Research"
        }

        val snapshot = BrowserDataMapper.fromLegacyBookmarks(BrowserId.VIA, listOf(original))
        val restored = BrowserDataMapper.toLegacyBookmarks(snapshot).single()

        assertEquals(BrowserId.VIA, snapshot.source)
        assertEquals(BrowserDataSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals("Example", restored.title)
        assertEquals("https://example.com/path", restored.url)
        assertEquals("Work/Research", restored.folder)
    }

    @Test
    fun emptyUrlsAreRejectedAtAdapterBoundary() {
        val blank = Bookmark().apply { url = "  " }
        val valid = Bookmark().apply { url = "https://example.com" }

        val snapshot = BrowserDataMapper.fromLegacyBookmarks(BrowserId.VIA, listOf(blank, valid))

        assertEquals(1, snapshot.bookmarks.size)
        assertFalse(snapshot.bookmarks.single().url.isBlank())
    }
}
