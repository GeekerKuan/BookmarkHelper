package pro.kisscat.www.bookmarkhelper.sync.adapter

import android.content.Context
import pro.kisscat.www.bookmarkhelper.converter.support.BasicBrowser
import pro.kisscat.www.bookmarkhelper.sync.history.HistorySyncService
import pro.kisscat.www.bookmarkhelper.sync.tabs.OpenTabsSyncService
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserDataMapper
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserDataSnapshot
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId

enum class BrowserDataType { BOOKMARKS, HISTORY, OPEN_TABS }
enum class BrowserDataOperation { READ, WRITE }

data class BrowserAdapterCapabilities(
    val readable: Set<BrowserDataType>,
    val writable: Set<BrowserDataType>,
) {
    fun supports(type: BrowserDataType, operation: BrowserDataOperation) = when (operation) {
        BrowserDataOperation.READ -> type in readable
        BrowserDataOperation.WRITE -> type in writable
    }
}

data class BrowserWriteResult(
    val inserted: Int,
    val skipped: Int = 0,
    val updated: Int = 0,
) {
    init {
        require(inserted >= 0 && skipped >= 0 && updated >= 0) {
            "Browser write counts must not be negative"
        }
    }

    val processed: Int get() = inserted + skipped + updated
}

class UnsupportedBrowserDataOperationException(
    browser: BrowserId,
    type: BrowserDataType,
    operation: BrowserDataOperation,
) : UnsupportedOperationException("$browser does not support $operation for $type")

/** Browser-specific databases and JSON stay behind this canonical boundary. */
interface BrowserDataAdapter {
    val browser: BrowserId
    val capabilities: BrowserAdapterCapabilities
    fun read(
        type: BrowserDataType,
        fromUnixSeconds: Long = 0L,
        toUnixSeconds: Long = Long.MAX_VALUE,
    ): BrowserDataSnapshot
    fun write(type: BrowserDataType, snapshot: BrowserDataSnapshot): BrowserWriteResult
}

class LegacyBookmarkAdapter(
    override val browser: BrowserId,
    private val implementation: BasicBrowser,
) : BrowserDataAdapter {
    override val capabilities = BrowserAdapterCapabilities(
        readable = setOf(BrowserDataType.BOOKMARKS),
        writable = setOf(BrowserDataType.BOOKMARKS),
    )

    override fun read(type: BrowserDataType, fromUnixSeconds: Long, toUnixSeconds: Long): BrowserDataSnapshot {
        require(type == BrowserDataType.BOOKMARKS) { "$browser cannot read $type yet" }
        return BrowserDataMapper.fromLegacyBookmarks(browser, implementation.readBookmark().orEmpty())
    }

    override fun write(type: BrowserDataType, snapshot: BrowserDataSnapshot): BrowserWriteResult {
        require(type == BrowserDataType.BOOKMARKS) { "$browser cannot write $type yet" }
        val inserted = implementation.appendBookmark(BrowserDataMapper.toLegacyBookmarks(snapshot))
        return BrowserWriteResult(inserted, (snapshot.bookmarks.size - inserted).coerceAtLeast(0))
    }
}

/** Via adapter: bookmarks and the modern Via history table share one canonical API. */
class ViaDataAdapter(
    context: Context,
    private val implementation: BasicBrowser,
) : BrowserDataAdapter {
    private val appContext = context.applicationContext
    override val browser = BrowserId.VIA
    override val capabilities = BrowserAdapterCapabilities(
        readable = setOf(BrowserDataType.BOOKMARKS, BrowserDataType.HISTORY, BrowserDataType.OPEN_TABS),
        writable = setOf(BrowserDataType.BOOKMARKS, BrowserDataType.HISTORY),
    )

    override fun read(
        type: BrowserDataType,
        fromUnixSeconds: Long,
        toUnixSeconds: Long,
    ): BrowserDataSnapshot = when (type) {
        BrowserDataType.BOOKMARKS -> BrowserDataMapper.fromLegacyBookmarks(
            browser,
            implementation.readBookmark().orEmpty(),
        )
        BrowserDataType.HISTORY -> BrowserDataSnapshot(
            source = browser,
            history = HistorySyncService.readViaHistory(appContext, fromUnixSeconds, toUnixSeconds),
        )
        BrowserDataType.OPEN_TABS -> BrowserDataSnapshot(
            source = browser,
            openTabs = OpenTabsSyncService.readViaTabs(appContext),
        )
    }

    override fun write(type: BrowserDataType, snapshot: BrowserDataSnapshot): BrowserWriteResult =
        when (type) {
            BrowserDataType.BOOKMARKS -> ViaDataWriteService.writeBookmarks(
                appContext,
                snapshot.bookmarks,
            ).toBrowserWriteResult()
            BrowserDataType.HISTORY -> ViaDataWriteService.writeHistory(
                appContext,
                snapshot.history,
            ).toBrowserWriteResult()
            BrowserDataType.OPEN_TABS -> throw UnsupportedBrowserDataOperationException(
                browser,
                type,
                BrowserDataOperation.WRITE,
            )
        }
}

/** Edge adapter: Bookmarks JSON and Chromium History stay behind one canonical API. */
class EdgeDataAdapter(
    context: Context,
    private val implementation: BasicBrowser,
) : BrowserDataAdapter {
    private val appContext = context.applicationContext
    override val browser = BrowserId.EDGE
    override val capabilities = BrowserAdapterCapabilities(
        readable = setOf(BrowserDataType.BOOKMARKS, BrowserDataType.HISTORY, BrowserDataType.OPEN_TABS),
        writable = setOf(BrowserDataType.BOOKMARKS, BrowserDataType.HISTORY),
    )

    override fun read(
        type: BrowserDataType,
        fromUnixSeconds: Long,
        toUnixSeconds: Long,
    ): BrowserDataSnapshot = when (type) {
        BrowserDataType.BOOKMARKS -> BrowserDataMapper.fromLegacyBookmarks(
            browser,
            implementation.readBookmark().orEmpty(),
        )
        BrowserDataType.HISTORY -> BrowserDataSnapshot(
            source = browser,
            history = HistorySyncService.readEdgeHistory(appContext, fromUnixSeconds, toUnixSeconds),
        )
        BrowserDataType.OPEN_TABS -> BrowserDataSnapshot(
            source = browser,
            openTabs = OpenTabsSyncService.readEdgeTabs(appContext),
        )
    }

    override fun write(type: BrowserDataType, snapshot: BrowserDataSnapshot): BrowserWriteResult =
        when (type) {
            BrowserDataType.BOOKMARKS -> {
                val inserted = implementation.appendBookmark(BrowserDataMapper.toLegacyBookmarks(snapshot))
                BrowserWriteResult(inserted, (snapshot.bookmarks.size - inserted).coerceAtLeast(0))
            }
            BrowserDataType.HISTORY -> HistorySyncService.importHistory(appContext, snapshot.history).let {
                BrowserWriteResult(it.imported, it.skipped, it.repaired)
            }
            BrowserDataType.OPEN_TABS -> throw UnsupportedBrowserDataOperationException(
                browser,
                type,
                BrowserDataOperation.WRITE,
            )
        }
}

class BrowserAdapterRegistry private constructor(
    private val adapters: Map<BrowserId, BrowserDataAdapter>,
) {
    operator fun get(browser: BrowserId): BrowserDataAdapter =
        requireNotNull(adapters[browser]) { "No adapter registered for $browser" }

    fun supporting(type: BrowserDataType, operation: BrowserDataOperation): List<BrowserId> =
        adapters.values.filter { it.capabilities.supports(type, operation) }.map { it.browser }

    fun capabilityMatrix(): Map<BrowserId, BrowserAdapterCapabilities> =
        adapters.mapValues { it.value.capabilities }

    companion object {
        fun of(vararg adapters: BrowserDataAdapter) = BrowserAdapterRegistry(
            adapters.associateBy(BrowserDataAdapter::browser),
        )

        fun viaAndEdge(context: Context, via: BasicBrowser, edge: BasicBrowser) = BrowserAdapterRegistry(
            mapOf(
                BrowserId.VIA to ViaDataAdapter(context, via),
                BrowserId.EDGE to EdgeDataAdapter(context, edge),
            )
        )
    }
}

private fun ViaDataWriteService.WriteResult.toBrowserWriteResult() =
    BrowserWriteResult(inserted, skipped, updated)
