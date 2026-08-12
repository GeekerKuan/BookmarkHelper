package pro.kisscat.www.bookmarkhelper.sync.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BrowserRecordIdentityTest {
    @Test
    fun bookmarkIdentityIgnoresHostAndSchemeCase() {
        assertEquals(
            BrowserRecordIdentity.bookmark("HTTPS://Example.COM", "Example", "Work"),
            BrowserRecordIdentity.bookmark("https://example.com/", "Example", "Work"),
        )
    }

    @Test
    fun sameHistoryVisitAcrossBrowsersHasOneIdentity() {
        val via = BrowserRecordIdentity.history("https://example.com/page", 1_700_000_000_000)
        val edge = BrowserRecordIdentity.history("https://EXAMPLE.com/page", 1_700_000_000_000)
        assertEquals(via, edge)
    }

    @Test
    fun historyAtDifferentTimesRemainsDistinct() {
        assertNotEquals(
            BrowserRecordIdentity.history("https://example.com", 1000),
            BrowserRecordIdentity.history("https://example.com", 1001),
        )
    }

    @Test
    fun bookmarkFolderRemainsPartOfIdentity() {
        assertNotEquals(
            BrowserRecordIdentity.bookmark("https://example.com", "Example", "Work"),
            BrowserRecordIdentity.bookmark("https://example.com", "Example", "Personal"),
        )
    }

    @Test
    fun repeatedOpenTabSnapshotKeepsOneIdentity() {
        assertEquals(
            BrowserRecordIdentity.openTab("https://example.com/page", 1_000),
            BrowserRecordIdentity.openTab("https://EXAMPLE.com/page", 9_000),
        )
    }
}
