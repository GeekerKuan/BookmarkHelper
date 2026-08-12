package pro.kisscat.www.bookmarkhelper.sync.model

import org.junit.Assert.assertEquals
import org.junit.Test
import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserDataType

class IntermediateDataExportSourceTest {
    @Test
    fun missingSelectedIdsAreCountedAndNeverExpandedToAllRows() {
        val rows = listOf(
            ManagedDataRow(
                IntermediateItemRef(10L, IntermediateItemKind.BOOKMARK),
                "A",
                "https://a.example",
                "Folder",
                null,
                setOf(BrowserId.VIA),
            ),
            ManagedDataRow(
                IntermediateItemRef(20L, IntermediateItemKind.BOOKMARK),
                "B",
                "https://b.example",
                "",
                null,
                setOf(BrowserId.EDGE),
            ),
        )

        val selection = IntermediateDataExportSource.assemble(
            BrowserDataType.BOOKMARKS,
            rows,
            0L,
            Long.MAX_VALUE,
            requestedRecordCount = 3,
        )

        assertEquals(3, selection.requestedRecordCount)
        assertEquals(2, selection.matchedRecordCount)
        assertEquals(1, selection.missingRecordCount)
        assertEquals(2, selection.snapshots.sumOf { it.bookmarks.size })
    }

    @Test
    fun historyRangeFiltersMatchedRowsWithoutChangingMissingIdCount() {
        val rows = listOf(
            ManagedDataRow(
                IntermediateItemRef(1L, IntermediateItemKind.HISTORY),
                "Old",
                "https://old.example",
                "",
                10_000L,
                setOf(BrowserId.VIA),
            ),
            ManagedDataRow(
                IntermediateItemRef(2L, IntermediateItemKind.HISTORY),
                "In range",
                "https://new.example",
                "",
                20_000L,
                setOf(BrowserId.VIA),
            ),
        )

        val selection = IntermediateDataExportSource.assemble(
            BrowserDataType.HISTORY,
            rows,
            fromUnixSeconds = 15L,
            toUnixSeconds = 25L,
            requestedRecordCount = 2,
        )

        assertEquals(2, selection.matchedRecordCount)
        assertEquals(0, selection.missingRecordCount)
        assertEquals(listOf("https://new.example"), selection.snapshots.single().history.map { it.url })
    }
}
