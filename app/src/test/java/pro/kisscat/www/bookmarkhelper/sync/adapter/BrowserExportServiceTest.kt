package pro.kisscat.www.bookmarkhelper.sync.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserDataSnapshot
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalBookmark
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalHistoryVisit

class BrowserExportServiceTest {
    @Test
    fun deduplicatesAndValidatesOnceBeforeWritingEveryTarget() {
        val via = FakeAdapter(BrowserId.VIA, writable = setOf(BrowserDataType.BOOKMARKS))
        val edge = FakeAdapter(BrowserId.EDGE, writable = setOf(BrowserDataType.BOOKMARKS))
        val service = BrowserExportService(BrowserAdapterRegistry.of(via, edge))
        val snapshot = BrowserDataSnapshot(
            source = BrowserId.VIA,
            bookmarks = listOf(
                CanonicalBookmark("A", "https://example.com", "One"),
                CanonicalBookmark("B", "https://EXAMPLE.com/", "Two"),
                CanonicalBookmark("Invalid", "file:///private", ""),
            ),
        )

        val result = service.export(
            BrowserDataType.BOOKMARKS,
            listOf(snapshot),
            setOf(BrowserId.VIA, BrowserId.EDGE),
        )

        assertTrue(result.isFullySuccessful)
        assertEquals(1, via.lastSnapshot!!.bookmarks.size)
        assertEquals(1, edge.lastSnapshot!!.bookmarks.size)
        assertEquals(3, result.targets.single { it.browser == BrowserId.VIA }.requested)
        assertEquals(2, result.targets.single { it.browser == BrowserId.VIA }.skipped)
    }

    @Test
    fun reportsUnsupportedWithoutCallingAdapter() {
        val edge = FakeAdapter(BrowserId.EDGE, writable = emptySet())
        val result = BrowserExportService(BrowserAdapterRegistry.of(edge)).export(
            BrowserDataType.OPEN_TABS,
            listOf(BrowserDataSnapshot(source = BrowserId.VIA)),
            setOf(BrowserId.EDGE),
        ).targets.single()

        assertEquals(BrowserExportStatus.UNSUPPORTED, result.status)
        assertEquals(0, edge.writeCalls)
        assertFalse(result.isSuccessful)
    }

    @Test
    fun oneTargetFailureNeverMakesBatchSuccessful() {
        val via = FakeAdapter(BrowserId.VIA, writable = setOf(BrowserDataType.HISTORY))
        val edge = FakeAdapter(
            BrowserId.EDGE,
            writable = setOf(BrowserDataType.HISTORY),
            failure = IllegalStateException("schema changed"),
        )
        val snapshot = BrowserDataSnapshot(
            source = BrowserId.VIA,
            history = listOf(CanonicalHistoryVisit("A", "https://example.com", 1_000L)),
        )

        val result = BrowserExportService(BrowserAdapterRegistry.of(via, edge)).export(
            BrowserDataType.HISTORY,
            listOf(snapshot),
            setOf(BrowserId.VIA, BrowserId.EDGE),
        )

        assertEquals(BrowserExportStatus.SUCCESS, result.targets[0].status)
        assertEquals(BrowserExportStatus.FAILED, result.targets[1].status)
        assertFalse(result.isFullySuccessful)
    }

    @Test
    fun inconsistentWriterCountIsPartialRatherThanFalseSuccess() {
        val edge = FakeAdapter(
            BrowserId.EDGE,
            writable = setOf(BrowserDataType.BOOKMARKS),
            fixedResult = BrowserWriteResult(inserted = 0, skipped = 0),
        )
        val snapshot = BrowserDataSnapshot(
            source = BrowserId.VIA,
            bookmarks = listOf(CanonicalBookmark("A", "https://example.com")),
        )

        val result = BrowserExportService(BrowserAdapterRegistry.of(edge)).export(
            BrowserDataType.BOOKMARKS,
            listOf(snapshot),
            setOf(BrowserId.EDGE),
        ).targets.single()

        assertEquals(BrowserExportStatus.PARTIAL, result.status)
        assertFalse(result.isSuccessful)
    }

    private class FakeAdapter(
        override val browser: BrowserId,
        writable: Set<BrowserDataType>,
        private val failure: Throwable? = null,
        private val fixedResult: BrowserWriteResult? = null,
    ) : BrowserDataAdapter {
        override val capabilities = BrowserAdapterCapabilities(emptySet(), writable)
        var writeCalls = 0
        var lastSnapshot: BrowserDataSnapshot? = null

        override fun read(
            type: BrowserDataType,
            fromUnixSeconds: Long,
            toUnixSeconds: Long,
        ) = error("not used")

        override fun write(type: BrowserDataType, snapshot: BrowserDataSnapshot): BrowserWriteResult {
            writeCalls++
            lastSnapshot = snapshot
            failure?.let { throw it }
            return fixedResult ?: BrowserWriteResult(
                inserted = when (type) {
                    BrowserDataType.BOOKMARKS -> snapshot.bookmarks.size
                    BrowserDataType.HISTORY -> snapshot.history.size
                    BrowserDataType.OPEN_TABS -> snapshot.openTabs.size
                },
            )
        }
    }
}
