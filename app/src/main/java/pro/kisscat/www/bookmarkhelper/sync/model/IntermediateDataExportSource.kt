package pro.kisscat.www.bookmarkhelper.sync.model

import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserDataType

data class IntermediateExportSelection(
    val snapshots: List<BrowserDataSnapshot>,
    val requestedRecordCount: Int,
    val matchedRecordCount: Int,
    val missingRecordCount: Int,
)

/** Builds browser-neutral export batches without exposing the internal SQLite schema. */
object IntermediateDataExportSource {
    fun snapshots(
        type: BrowserDataType,
        fromUnixSeconds: Long = 0L,
        toUnixSeconds: Long = Long.MAX_VALUE,
    ): List<BrowserDataSnapshot> = selection(
        type,
        fromUnixSeconds,
        toUnixSeconds,
        selectedRecordIds = null,
    ).snapshots

    fun selection(
        type: BrowserDataType,
        fromUnixSeconds: Long = 0L,
        toUnixSeconds: Long = Long.MAX_VALUE,
        selectedRecordIds: Set<Long>? = null,
    ): IntermediateExportSelection {
        require(fromUnixSeconds >= 0L && toUnixSeconds >= fromUnixSeconds) {
            "Invalid export time range"
        }
        require(selectedRecordIds == null || selectedRecordIds.isNotEmpty()) {
            "Selected record IDs must not be empty"
        }
        val rows = IntermediateDataRepository.rowsForExport(type.itemKind(), selectedRecordIds)
        return assemble(type, rows, fromUnixSeconds, toUnixSeconds, selectedRecordIds?.size ?: rows.size)
    }

    internal fun assemble(
        type: BrowserDataType,
        rows: List<ManagedDataRow>,
        fromUnixSeconds: Long,
        toUnixSeconds: Long,
        requestedRecordCount: Int,
    ): IntermediateExportSelection {
        val exportedAt = System.currentTimeMillis()
        val snapshots = BrowserId.entries.mapNotNull { source ->
            // A record may retain multiple provenance tags. Assign it once to a
            // deterministic batch source so provenance does not inflate export counts.
            val sourced = rows.filter { row ->
                (row.sources.minByOrNull(BrowserId::ordinal) ?: BrowserId.VIA) == source
            }
            if (sourced.isEmpty()) return@mapNotNull null
            when (type) {
                BrowserDataType.BOOKMARKS -> BrowserDataSnapshot(
                    source = source,
                    exportedAtEpochMillis = exportedAt,
                    bookmarks = sourced.map { row ->
                        CanonicalBookmark(row.title, row.url, row.detail, row.timestampEpochMillis)
                    },
                )
                BrowserDataType.HISTORY -> BrowserDataSnapshot(
                    source = source,
                    exportedAtEpochMillis = exportedAt,
                    history = sourced.mapNotNull { row ->
                        val visitedAt = row.timestampEpochMillis ?: return@mapNotNull null
                        val seconds = visitedAt / 1_000L
                        if (seconds < fromUnixSeconds || seconds > toUnixSeconds) null
                        else CanonicalHistoryVisit(row.title, row.url, visitedAt)
                    },
                )
                BrowserDataType.OPEN_TABS -> BrowserDataSnapshot(
                    source = source,
                    exportedAtEpochMillis = exportedAt,
                    openTabs = sourced.mapNotNull { row ->
                        val openedAt = row.timestampEpochMillis ?: return@mapNotNull null
                        CanonicalOpenTab(row.title, row.url, openedAt)
                    },
                )
            }
        }
        return IntermediateExportSelection(
            snapshots = snapshots,
            requestedRecordCount = requestedRecordCount,
            matchedRecordCount = rows.size,
            missingRecordCount = (requestedRecordCount - rows.size).coerceAtLeast(0),
        )
    }

    private fun BrowserDataType.itemKind() = when (this) {
        BrowserDataType.BOOKMARKS -> IntermediateItemKind.BOOKMARK
        BrowserDataType.HISTORY -> IntermediateItemKind.HISTORY
        BrowserDataType.OPEN_TABS -> IntermediateItemKind.OPEN_TAB
    }
}
