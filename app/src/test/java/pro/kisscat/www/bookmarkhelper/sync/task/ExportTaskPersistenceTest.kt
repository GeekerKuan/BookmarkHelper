package pro.kisscat.www.bookmarkhelper.sync.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId

class ExportTaskPersistenceTest {
    @Test
    fun selectedIdsRoundTripWithoutBecomingAllRecords() {
        val original = PersistedExportTask(
            id = 42L,
            kind = SyncTaskKind.HISTORY,
            targets = setOf(BrowserId.VIA, BrowserId.EDGE),
            historyRange = HistoryRange(100L, 200L, "自定义范围"),
            selectedRecordIds = setOf(9L, 3L, 7L),
            createdAtMillis = 1234L,
        )

        val restored = ExportTaskPersistence.decode(ExportTaskPersistence.encode(original))

        assertEquals(original, restored)
        assertEquals(setOf(3L, 7L, 9L), restored.selectedRecordIds)
    }

    @Test
    fun explicitAllModeRoundTripsAsNullOnly() {
        val original = PersistedExportTask(
            id = 43L,
            kind = SyncTaskKind.BOOKMARKS,
            targets = setOf(BrowserId.EDGE),
            historyRange = null,
            selectedRecordIds = null,
            createdAtMillis = 5678L,
        )

        val restored = ExportTaskPersistence.decode(ExportTaskPersistence.encode(original))

        assertNull(restored.selectedRecordIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun idsModeRejectsEmptySelectionInsteadOfFallingBackToAll() {
        ExportTaskPersistence.decode(
            """{"version":1,"id":44,"kind":"BOOKMARKS","targets":["EDGE"],"createdAtMillis":1,"selectionMode":"IDS","selectedRecordIds":[]}"""
        )
    }
}
